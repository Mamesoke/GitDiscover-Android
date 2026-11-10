package com.mamesoke.gitdiscover.domain.usecase

import com.mamesoke.gitdiscover.domain.model.Repository
import com.mamesoke.gitdiscover.domain.repository.RepositorySearching
import com.mamesoke.gitdiscover.domain.search.QueryRules
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class SearchRepositoriesUseCase(
    private val repository: RepositorySearching,
    val queryRules: QueryRules
) {
    suspend operator fun invoke(text: String): List<Repository> {
        currentCoroutineContext().ensureActive()
        val query = queryRules.normalized(text)
        if (!queryRules.isValid(query)) return emptyList()
        val results = repository.search(query)
        currentCoroutineContext().ensureActive()
        return results
    }
}
