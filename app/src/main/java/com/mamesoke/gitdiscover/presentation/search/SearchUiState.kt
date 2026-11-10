package com.mamesoke.gitdiscover.presentation.search

import com.mamesoke.gitdiscover.domain.model.Repository
import com.mamesoke.gitdiscover.domain.repository.SearchError

data class SearchUiState(
    val queryText: String = "",
    val content: SearchContent = SearchContent.Idle,
    val selected: Repository? = null
)

sealed interface SearchContent {
    data object Idle : SearchContent
    data class Loading(val query: String) : SearchContent
    data class Results(val query: String, val repositories: List<Repository>) : SearchContent
    data class Empty(val query: String) : SearchContent
    data class Failed(val query: String, val error: SearchError, val retryAtMillis: Long? = null) : SearchContent
}
