package com.mamesoke.gitdiscover.data

import com.mamesoke.gitdiscover.FakeTime
import com.mamesoke.gitdiscover.RESPONSE
import com.mamesoke.gitdiscover.data.cache.SearchResultCache
import com.mamesoke.gitdiscover.data.network.*
import com.mamesoke.gitdiscover.data.repository.CachedRepositorySearchRepository
import com.mamesoke.gitdiscover.domain.repository.RepositorySearchException
import com.mamesoke.gitdiscover.domain.repository.SearchError
import com.mamesoke.gitdiscover.expectFailure
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import okhttp3.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test

class GitHubApiClientTest {
    private fun response(status: Int = 200, body: String = RESPONSE, vararg headers: Pair<String, String>) =
        HttpResponse(status, Headers.headersOf(*headers.flatMap { listOf(it.first, it.second) }.toTypedArray()), body)

    @Test fun mapsAllFieldsAndPreservesAbsentVersusEmptyOptionalFields() = runTest {
        var body = RESPONSE
        val api = GitHubApiClient(HttpClient { response(body = body) }, RateLimitGate(FakeTime()))
        val result = api.search("query").single()
        assertEquals(9_000_000_000L, result.id)
        assertEquals("repo", result.name)
        assertEquals("owner/repo", result.fullName)
        assertEquals("owner", result.ownerLogin)
        assertEquals("Description", result.description)
        assertEquals("Kotlin", result.language)
        assertEquals(1234L, result.stars)
        assertEquals(12L, result.forks)
        assertEquals("https://github.com/owner/repo", result.htmlUrl)
        body = RESPONSE.replace("\"description\":\"Description\",", "").replace("\"language\":\"Kotlin\",", "")
        assertNull(api.search("query").single().description)
        assertNull(api.search("query").single().language)
        body = RESPONSE.replace("\"Description\"", "null").replace("\"Kotlin\"", "\"\"")
        assertNull(api.search("query").single().description)
        assertEquals("", api.search("query").single().language)
    }

    @Test fun failsWholeResponseForInvalidUrlsOrMissingRequiredFields() = runTest {
        for (url in listOf("/owner/repo", "http://github.com/a", "https://evil.com/a", "https://github.com.evil.com/a", "https://user@github.com/a")) {
            val api = GitHubApiClient(HttpClient {
                response(body = RESPONSE.replace("https://github.com/owner/repo", url))
            }, RateLimitGate(FakeTime()))
            assertEquals(SearchError.InvalidResponse,
                expectFailure<RepositorySearchException> { api.search("query") }.error)
        }
        for (body in listOf("{", "{}", RESPONSE.replace("\"name\":\"repo\",", ""))) {
            val api = GitHubApiClient(HttpClient { response(body = body) }, RateLimitGate(FakeTime()))
            assertEquals(SearchError.Decoding,
                expectFailure<RepositorySearchException> { api.search("query") }.error)
        }
    }

    @Test fun mapsStatusAndConnectivityWithoutSuccessFallbacks() = runTest {
        for ((status, error) in listOf(403 to SearchError.AccessDenied, 500 to SearchError.Server,
            422 to SearchError.InvalidRequest, 302 to SearchError.InvalidResponse)) {
            val api = GitHubApiClient(HttpClient { response(status, "{}") }, RateLimitGate(FakeTime()))
            assertEquals(error, expectFailure<RepositorySearchException> { api.search("query") }.error)
        }
        val api = GitHubApiClient(HttpClient { throw IOException("offline") }, RateLimitGate(FakeTime()))
        assertEquals(SearchError.Connectivity,
            expectFailure<RepositorySearchException> { api.search("query") }.error)
    }

    @Test fun rateLimitClassificationsAndFallback() = runTest {
        val cases = listOf(
            response(429), response(403, "{}", "X-RateLimit-Remaining" to "0"),
            response(403, "{}", "Retry-After" to "invalid"),
            response(403, """{"message":"API rate limit exceeded"}"""),
            response(403, """{"message":"Secondary rate exceeded"}""")
        )
        for (limited in cases) {
            val time = FakeTime()
            val api = GitHubApiClient(HttpClient { limited }, RateLimitGate(time))
            val error = expectFailure<RepositorySearchException> { api.search("query") }
            assertEquals(SearchError.RateLimited, error.error)
            assertEquals(time.now + 60_000, error.retryAtMillis)
        }
    }

    @Test fun retryAfterPrecedenceFractionsDatesInvalidValuesAndEquality() = runTest {
        val time = FakeTime(1_000_000)
        suspend fun deadline(retry: String, reset: String = "1050"): Long =
            RateLimitGate(time).record(Headers.headersOf("Retry-After", retry, "X-RateLimit-Reset", reset))
        assertEquals(time.now + 1500, deadline("1.5"))
        assertEquals(1_050_000L, deadline("invalid"))
        for (invalid in listOf("-1", "NaN", "Infinity", "1e1000")) {
            assertEquals(1_050_500L, deadline(invalid, "1050.5"))
            assertEquals(time.now + 60_000, deadline(invalid, "-1"))
        }
        time.now = 0
        assertEquals(86_400_000L, deadline("Fri, 02 Jan 1970 00:00:00 GMT", "2"))
        val gate = RateLimitGate(time)
        gate.record(Headers.headersOf("Retry-After", "1.5"))
        time.now = 1499
        expectFailure<RepositorySearchException> { gate.check() }
        time.now = 1500
        gate.check()
    }

    @Test fun overlappingLimitsOnlyExtendDeadlineInEitherOrder() = runTest {
        for (delays in listOf(listOf("20", "90"), listOf("90", "20"))) {
            val time = FakeTime()
            val gate = RateLimitGate(time)
            for (seconds in delays) gate.record(Headers.headersOf("Retry-After", seconds))
            val error = expectFailure<RepositorySearchException> { gate.check() }
            assertEquals(time.now + 90_000, error.retryAtMillis)
        }
    }

    @Test fun overlappingHttpResponsesDoNotShortenCooldownOrCancelStartedCalls() = runTest {
        for (order in listOf(listOf(0, 1), listOf(1, 0))) {
            val time = FakeTime()
            val gate = RateLimitGate(time)
            val releases = List(2) { CompletableDeferred<Unit>() }
            val started = List(2) { CompletableDeferred<Unit>() }
            val api = GitHubApiClient(HttpClient { request ->
                val index = checkNotNull(request.url.queryParameter("q")).toInt()
                started[index].complete(Unit)
                releases[index].await()
                response(429, "{}", "Retry-After" to if (index == 0) "20" else "90")
            }, gate)
            val requests = List(2) { index ->
                async { expectFailure<RepositorySearchException> { api.search(index.toString()) } }
            }
            started.forEach { it.await() }
            for (index in order) {
                releases[index].complete(Unit)
                assertEquals(SearchError.RateLimited, requests[index].await().error)
            }
            assertEquals(time.now + 90_000,
                expectFailure<RepositorySearchException> { gate.check() }.retryAtMillis)
        }
    }

    @Test fun freshCacheBypassesCooldownAndDeadlineBlocksOtherQueriesWithoutHttp() = runTest {
        val time = FakeTime()
        var calls = 0
        val api = GitHubApiClient(HttpClient {
            calls++
            if (calls == 1) response() else response(429, "{}", "Retry-After" to "60")
        }, RateLimitGate(time))
        val cached = CachedRepositorySearchRepository(api, SearchResultCache(time))
        val first = cached.search("cached")
        expectFailure<RepositorySearchException> { cached.search("limited") }
        assertEquals(first, cached.search("CACHED"))
        expectFailure<RepositorySearchException> { cached.search("new") }
        assertEquals(2, calls)
        time.now += 60_000
        expectFailure<RepositorySearchException> { cached.search("new") }
        assertEquals(3, calls)
    }

    @Test fun realTransportReceivesExactQueriesHeadersAndOnlyOneAttempt() = runBlocking {
        val server = MockWebServer()
        server.start()
        val client = OkHttpClient.Builder().retryOnConnectionFailure(false)
            .followRedirects(false).followSslRedirects(false).build()
        try {
            val api = GitHubApiClient(OkHttpTransport(client), RateLimitGate(FakeTime()), server.url("/search/repositories"))
            val queries = listOf("C++ language:C++", "a % & = ; / \"é 日本語\"", "empty", "offline", "ratelimit")
            for (query in queries) {
                server.enqueue(MockResponse().setBody(RESPONSE))
                api.search(query)
                val request = requireNotNull(server.takeRequest(5, TimeUnit.SECONDS))
                assertEquals(query, request.requestUrl?.queryParameter("q"))
                assertFalse(request.requestUrl?.encodedQuery.orEmpty().contains("+"))
                assertEquals("30", request.requestUrl?.queryParameter("per_page"))
                assertEquals("1", request.requestUrl?.queryParameter("page"))
                assertEquals("/search/repositories", request.requestUrl?.encodedPath)
                assertEquals("application/vnd.github+json", request.getHeader("Accept"))
                assertEquals("2026-03-10", request.getHeader("X-GitHub-Api-Version"))
                assertEquals("GitDiscover", request.getHeader("User-Agent"))
            }
            assertEquals(queries.size, server.requestCount)
            assertFalse(client.retryOnConnectionFailure)
            assertFalse(client.followRedirects)
            assertNull(client.cache)
        } finally {
            server.shutdown()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    @Test fun cancelStartedRequestReachesCallAndTerminatesTransport() = runBlocking {
        val server = MockWebServer()
        server.start()
        val canceled = AtomicBoolean()
        val terminated = CountDownLatch(1)
        val client = OkHttpClient.Builder().retryOnConnectionFailure(false)
            .eventListener(object : EventListener() {
                override fun canceled(call: Call) { canceled.set(true) }
                override fun callFailed(call: Call, ioe: IOException) { terminated.countDown() }
            }).build()
        try {
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val api = GitHubApiClient(OkHttpTransport(client), RateLimitGate(FakeTime()), server.url("/search/repositories"))
            val request = async(Dispatchers.IO) { api.search("cancel") }
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            request.cancel()
            withTimeout(5_000) { request.join() }
            expectFailure<CancellationException> { request.await() }
            assertTrue(canceled.get())
            assertTrue(terminated.await(5, TimeUnit.SECONDS))
        } finally {
            server.shutdown()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    @Test fun lateNonCooperativeResponseIsRejected() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val api = GitHubApiClient(HttpClient {
            started.complete(Unit)
            withContext(NonCancellable) { release.await() }
            response()
        }, RateLimitGate(FakeTime()))
        val job = async { api.search("late") }
        started.await()
        job.cancel()
        release.complete(Unit)
        expectFailure<CancellationException> { job.await() }
    }

    @Test fun cancelWhileReadingBodyTerminatesCallbackWithoutConnectivityFailure() = runBlocking {
        val server = MockWebServer()
        server.start()
        val bodyStarted = CountDownLatch(1)
        val terminated = CountDownLatch(1)
        val client = OkHttpClient.Builder().retryOnConnectionFailure(false)
            .callTimeout(5, TimeUnit.SECONDS)
            .eventListener(object : EventListener() {
                override fun responseBodyStart(call: Call) { bodyStarted.countDown() }
                override fun callFailed(call: Call, ioe: IOException) { terminated.countDown() }
            }).build()
        try {
            server.enqueue(MockResponse().setBody(RESPONSE).throttleBody(1, 1, TimeUnit.SECONDS))
            val api = GitHubApiClient(OkHttpTransport(client), RateLimitGate(FakeTime()), server.url("/search/repositories"))
            val request = async(Dispatchers.IO) { api.search("cancel-body") }
            assertTrue(bodyStarted.await(5, TimeUnit.SECONDS))
            request.cancel()
            withTimeout(5_000) { request.join() }
            expectFailure<CancellationException> { request.await() }
            assertTrue(terminated.await(5, TimeUnit.SECONDS))
        } finally {
            server.shutdown()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }
}
