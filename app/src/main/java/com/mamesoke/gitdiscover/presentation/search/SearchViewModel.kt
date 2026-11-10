package com.mamesoke.gitdiscover.presentation.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mamesoke.gitdiscover.domain.time.TimeProvider
import com.mamesoke.gitdiscover.domain.model.Repository
import com.mamesoke.gitdiscover.domain.repository.RepositorySearchException
import com.mamesoke.gitdiscover.domain.repository.SearchError
import com.mamesoke.gitdiscover.domain.usecase.SearchRepositoriesUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SearchViewModel(
    private val useCase: SearchRepositoriesUseCase,
    private val savedState: SavedStateHandle,
    private val time: TimeProvider,
    private val reportUnexpectedError: (Throwable) -> Unit
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        SearchUiState(savedState["query"] ?: "", selected = restoreSelection())
    )
    val state = mutableState.asStateFlow()
    private var generation = 0L
    private var searchJob: Job? = null

    init {
        if (state.value.selected == null && useCase.queryRules.isValid(state.value.queryText)) {
            updateQuery(state.value.queryText)
        }
    }

    fun updateQuery(text: String) {
        val currentGeneration = ++generation
        searchJob?.cancel()
        searchJob = null
        savedState["query"] = text
        val query = useCase.queryRules.normalized(text)
        mutableState.update {
            it.copy(queryText = text, content =
                if (useCase.queryRules.isValid(query)) SearchContent.Loading(query) else SearchContent.Idle)
        }
        if (!useCase.queryRules.isValid(query)) return
        searchJob = viewModelScope.launch {
            try {
                delay(DEBOUNCE_MILLIS)
                currentCoroutineContext().ensureActive()
                if (generation != currentGeneration) return@launch
                val results = useCase(query)
                currentCoroutineContext().ensureActive()
                if (generation == currentGeneration) {
                    mutableState.update {
                        it.copy(content = if (results.isEmpty()) SearchContent.Empty(query)
                            else SearchContent.Results(query, results.toList()))
                    }
                }
            } catch (error: CancellationException) {
                if (generation == currentGeneration) {
                    mutableState.update { it.copy(content = SearchContent.Idle) }
                }
                throw error
            } catch (error: RepositorySearchException) {
                currentCoroutineContext().ensureActive()
                if (generation == currentGeneration) {
                    val retryAt = if (error.error == SearchError.RateLimited) {
                        maxOf(time.nowMillis(), error.retryAtMillis ?: (time.nowMillis() + 60_000))
                    } else null
                    mutableState.update {
                        it.copy(content = SearchContent.Failed(query, error.error, retryAt))
                    }
                }
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                if (generation == currentGeneration) {
                    reportUnexpectedError(error)
                    mutableState.update { it.copy(content = SearchContent.Failed(query, SearchError.Unexpected)) }
                }
            }
        }
    }

    fun canRetry(nowMillis: Long = time.nowMillis()): Boolean {
        val failure = state.value.content as? SearchContent.Failed ?: return false
        return useCase.queryRules.isValid(state.value.queryText) &&
            (failure.retryAtMillis == null || nowMillis >= failure.retryAtMillis)
    }

    fun retry() {
        if (canRetry()) updateQuery(state.value.queryText)
    }

    fun select(repository: Repository) {
        savedState["selected.id"] = repository.id
        savedState["selected.name"] = repository.name
        savedState["selected.fullName"] = repository.fullName
        savedState["selected.owner"] = repository.ownerLogin
        savedState["selected.description"] = repository.description
        savedState["selected.language"] = repository.language
        savedState["selected.stars"] = repository.stars
        savedState["selected.forks"] = repository.forks
        savedState["selected.url"] = repository.htmlUrl
        mutableState.update { it.copy(selected = repository) }
    }

    fun closeDetail() {
        savedState.keys().filter { it.startsWith("selected.") }.forEach { savedState.remove<Any?>(it) }
        mutableState.update { it.copy(selected = null) }
        if (state.value.content == SearchContent.Idle && useCase.queryRules.isValid(state.value.queryText)) {
            updateQuery(state.value.queryText)
        }
    }

    private fun restoreSelection(): Repository? {
        val id = savedState.get<Long>("selected.id") ?: return null
        return Repository(
            id,
            checkNotNull(savedState["selected.name"]),
            checkNotNull(savedState["selected.fullName"]),
            checkNotNull(savedState["selected.owner"]),
            savedState["selected.description"],
            savedState["selected.language"],
            checkNotNull(savedState["selected.stars"]),
            checkNotNull(savedState["selected.forks"]),
            checkNotNull(savedState["selected.url"])
        )
    }

    companion object {
        const val DEBOUNCE_MILLIS = 400L
    }
}
