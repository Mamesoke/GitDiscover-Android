package com.mamesoke.gitdiscover.domain.repository

import com.mamesoke.gitdiscover.domain.model.Repository

fun interface RepositorySearching {
    suspend fun search(query: String): List<Repository>
}

enum class SearchError {
    InvalidRequest, InvalidResponse, AccessDenied, Server, Decoding, Connectivity, RateLimited, Unexpected
}

class RepositorySearchException(
    val error: SearchError,
    val retryAtMillis: Long? = null,
    cause: Throwable? = null
) : Exception(error.name, cause)
