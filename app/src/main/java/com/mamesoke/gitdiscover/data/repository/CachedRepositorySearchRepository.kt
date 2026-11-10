package com.mamesoke.gitdiscover.data.repository

import com.mamesoke.gitdiscover.data.cache.SearchResultCache
import com.mamesoke.gitdiscover.domain.model.Repository
import com.mamesoke.gitdiscover.domain.repository.RepositorySearching
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class CachedRepositorySearchRepository(
    private val remote: RepositorySearching,
    private val cache: SearchResultCache
) : RepositorySearching {
    override suspend fun search(query: String): List<Repository> {
        currentCoroutineContext().ensureActive()
        cache.get(query)?.let { return it }
        val results = remote.search(query)
        currentCoroutineContext().ensureActive()
        cache.put(query, results)
        return results
    }
}
