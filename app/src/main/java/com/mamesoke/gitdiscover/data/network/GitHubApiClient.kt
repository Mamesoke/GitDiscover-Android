package com.mamesoke.gitdiscover.data.network

import com.mamesoke.gitdiscover.domain.model.Repository
import com.mamesoke.gitdiscover.domain.repository.RepositorySearchException
import com.mamesoke.gitdiscover.domain.repository.RepositorySearching
import com.mamesoke.gitdiscover.domain.repository.SearchError
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

class GitHubApiClient(
    private val http: HttpClient,
    private val gate: RateLimitGate,
    private val endpoint: HttpUrl = "https://api.github.com/search/repositories".toHttpUrl(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : RepositorySearching {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun search(query: String): List<Repository> = withContext(dispatcher) {
        currentCoroutineContext().ensureActive()
        gate.check()
        val request = Request.Builder()
            .url(endpoint.newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("per_page", "30")
                .addQueryParameter("page", "1")
                .build())
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2026-03-10")
            .header("User-Agent", "GitDiscover")
            .build()
        val response = try {
            http.execute(request)
        } catch (error: IOException) {
            currentCoroutineContext().ensureActive()
            throw RepositorySearchException(SearchError.Connectivity, cause = error)
        }
        currentCoroutineContext().ensureActive()
        val rateLimited = response.status == 429 || (response.status == 403 &&
            (response.headers["X-RateLimit-Remaining"]?.trim() == "0" ||
                response.headers["Retry-After"] != null || isRateLimitMessage(response.body)))
        if (rateLimited) {
            throw RepositorySearchException(SearchError.RateLimited, gate.record(response.headers))
        }
        when (response.status) {
            in 200..299 -> Unit
            403 -> throw RepositorySearchException(SearchError.AccessDenied)
            in 500..599 -> throw RepositorySearchException(SearchError.Server)
            in 400..499 -> throw RepositorySearchException(SearchError.InvalidRequest)
            else -> throw RepositorySearchException(SearchError.InvalidResponse)
        }
        val result = try {
            json.decodeFromString<GitHubSearchResponseDto>(response.body).items.map { it.toDomain() }
        } catch (error: SerializationException) {
            throw RepositorySearchException(SearchError.Decoding, cause = error)
        }
        currentCoroutineContext().ensureActive()
        result
    }

    private fun isRateLimitMessage(body: String): Boolean {
        val message = try {
            json.parseToJsonElement(body).jsonObject["message"]?.jsonPrimitive?.content
        } catch (_: SerializationException) {
            return false
        } catch (_: IllegalArgumentException) {
            return false
        }
        val normalized = message?.lowercase(Locale.ROOT).orEmpty()
        return "rate limit" in normalized || "secondary rate" in normalized
    }
}
