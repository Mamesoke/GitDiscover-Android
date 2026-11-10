package com.mamesoke.gitdiscover.data

import com.mamesoke.gitdiscover.FakeTime
import com.mamesoke.gitdiscover.data.cache.SearchResultCache
import com.mamesoke.gitdiscover.data.cache.cacheKey
import com.mamesoke.gitdiscover.data.repository.CachedRepositorySearchRepository
import com.mamesoke.gitdiscover.domain.repository.RepositorySearching
import com.mamesoke.gitdiscover.domain.repository.RepositorySearchException
import com.mamesoke.gitdiscover.domain.repository.SearchError
import com.mamesoke.gitdiscover.expectFailure
import com.mamesoke.gitdiscover.repository
import java.util.Locale
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchResultCacheTest {
    @Test fun hitsBeforeExpiryRefreshesAtExactBoundaryAndCachesReplacement() = runTest {
        val time = FakeTime()
        var calls = 0
        val remote = RepositorySearching { listOf(repository((++calls).toLong())) }
        val cached = CachedRepositorySearchRepository(remote, SearchResultCache(time))
        assertEquals(1L, cached.search("kotlin").single().id)
        time.now += 30_000
        cached.search("kotlin")
        time.now += 149_000
        cached.search("kotlin")
        assertEquals(1, calls)
        time.now += 1_000
        assertEquals(2L, cached.search("kotlin").single().id)
        assertEquals(2L, cached.search("KOTLIN").single().id)
        assertEquals(2, calls)
        cached.search("swift")
        assertEquals(3, calls)
    }

    @Test fun cachesEmptySuccessButNotErrors() = runTest {
        var calls = 0
        val cached = CachedRepositorySearchRepository(RepositorySearching {
            calls++
            if (calls == 1) throw RepositorySearchException(SearchError.Connectivity)
            emptyList()
        }, SearchResultCache(FakeTime()))
        expectFailure<RepositorySearchException> { cached.search("empty") }
        assertTrue(cached.search("empty").isEmpty())
        assertTrue(cached.search("empty").isEmpty())
        assertEquals(2, calls)
    }

    @Test fun normalizesKeysWithoutChangingRemoteExpression() = runTest {
        var calls = 0
        var expression = ""
        val cached = CachedRepositorySearchRepository(RepositorySearching {
            calls++
            expression = it
            listOf(repository())
        }, SearchResultCache(FakeTime()))
        cached.search("Kotlin  language:Kotlin")
        cached.search("\u00a0kotlin\t language:kotlin\n")
        assertEquals(1, calls)
        assertEquals("Kotlin  language:Kotlin", expression)
        cached.search("\"my  topic\"")
        cached.search("\"my topic\"")
        assertEquals(3, calls)
    }

    @Test fun preservesQuotedWhitespaceEscapesAndLocaleIndependence() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("i ä", cacheKey(" I \t Ä "))
            assertEquals("\"a\\\"  b\" c", cacheKey(" \"A\\\"  B\" \t C "))
            assertNotEquals(cacheKey("\"my  topic\""), cacheKey("\"my topic\""))
            assertEquals("c++ % & = ; /", cacheKey(" C++ % & = ; / "))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test fun canceledLateSuccessCannotPopulateCache() = runTest {
        val cache = SearchResultCache(FakeTime())
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val cached = CachedRepositorySearchRepository(RepositorySearching {
            started.complete(Unit)
            withContext(NonCancellable) { release.await() }
            listOf(repository())
        }, cache)
        val job = launch { cached.search("query") }
        started.await()
        job.cancel()
        release.complete(Unit)
        job.join()
        assertNull(cache.get("query"))
    }

    @Test fun cancellationInsideInsertionClockPreventsMutation() = runTest {
        var writing = false
        val cache = SearchResultCache(time = {
            if (writing) currentCoroutineContextJob?.cancel()
            1_000
        })
        val job = launch(start = CoroutineStart.LAZY) {
            writing = true
            cache.put("query", listOf(repository()))
        }
        currentCoroutineContextJob = job
        job.start()
        job.join()
        writing = false
        assertNull(cache.get("query"))
    }

    private var currentCoroutineContextJob: Job? = null

    @Test fun concurrentMissesAreNotCoalescedAndRemainSafe() = runTest {
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val cached = CachedRepositorySearchRepository(RepositorySearching {
            calls++
            release.await()
            listOf(repository())
        }, SearchResultCache(FakeTime()))
        val first = async { cached.search("same") }
        val second = async { cached.search("same") }
        runCurrent()
        assertEquals(2, calls)
        release.complete(Unit)
        assertEquals(first.await(), second.await())
        cached.search("same")
        assertEquals(2, calls)
    }
}
