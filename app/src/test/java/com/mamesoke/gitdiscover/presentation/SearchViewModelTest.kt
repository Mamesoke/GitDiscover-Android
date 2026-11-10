package com.mamesoke.gitdiscover.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.mamesoke.gitdiscover.FakeTime
import com.mamesoke.gitdiscover.data.cache.SearchResultCache
import com.mamesoke.gitdiscover.data.repository.CachedRepositorySearchRepository
import com.mamesoke.gitdiscover.domain.model.Repository
import com.mamesoke.gitdiscover.domain.repository.RepositorySearchException
import com.mamesoke.gitdiscover.domain.repository.RepositorySearching
import com.mamesoke.gitdiscover.domain.repository.SearchError
import com.mamesoke.gitdiscover.domain.usecase.SearchRepositoriesUseCase
import com.mamesoke.gitdiscover.presentation.search.*
import com.mamesoke.gitdiscover.queryRules
import com.mamesoke.gitdiscover.repository
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val time = FakeTime()
    private val stores = mutableListOf<ViewModelStore>()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() {
        stores.forEach { it.clear() }
        Dispatchers.resetMain()
    }

    private fun model(
        repository: RepositorySearching = RepositorySearching { listOf(repository()) },
        savedState: SavedStateHandle = SavedStateHandle(),
        unexpected: (Throwable) -> Unit = { throw AssertionError(it) }
    ): SearchViewModel {
        val model = SearchViewModel(SearchRepositoriesUseCase(repository, queryRules()), savedState, time, unexpected)
        stores += ViewModelStore().apply { put("search", model) }
        return model
    }

    @Test fun initialIdleInvalidQueriesAndExactDebounceBoundary() = runTest(dispatcher) {
        var calls = 0
        val vm = model(RepositorySearching { calls++; listOf(repository()) })
        assertEquals(SearchContent.Idle, vm.state.value.content)
        listOf("", "a", "👨‍👩‍👧‍👦").forEach { vm.updateQuery(it) }
        advanceUntilIdle()
        assertEquals(0, calls)
        vm.updateQuery("kotlin")
        assertTrue(vm.state.value.content is SearchContent.Loading)
        advanceTimeBy(399)
        runCurrent()
        assertEquals(0, calls)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, calls)
        assertTrue(vm.state.value.content is SearchContent.Results)
    }

    @Test fun editAt399RestartsDebounceAndHidesOldResultsImmediately() = runTest(dispatcher) {
        val calls = mutableListOf<String>()
        val vm = model(RepositorySearching { calls += it; listOf(repository()) })
        vm.updateQuery("first")
        advanceUntilIdle()
        assertTrue(vm.state.value.content is SearchContent.Results)
        vm.updateQuery("second")
        assertTrue(vm.state.value.content is SearchContent.Loading)
        advanceTimeBy(399)
        vm.updateQuery("third")
        advanceTimeBy(399)
        runCurrent()
        assertEquals(listOf("first"), calls)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf("first", "third"), calls)
    }

    @Test fun newEditCancelsInFlightBeforeNextDebounceFinishes() = runTest(dispatcher) {
        var canceled = false
        val vm = model(RepositorySearching {
            try { awaitCancellation() } finally { canceled = true }
        })
        vm.updateQuery("first")
        advanceTimeBy(400)
        runCurrent()
        vm.updateQuery("second")
        runCurrent()
        assertTrue(canceled)
        assertEquals(SearchContent.Loading("second"), vm.state.value.content)
        vm.updateQuery("")
        advanceUntilIdle()
        assertEquals(SearchContent.Idle, vm.state.value.content)
    }

    @Test fun clearDuringDebounceNeverCallsRepository() = runTest(dispatcher) {
        var calls = 0
        val vm = model(RepositorySearching { calls++; emptyList() })
        vm.updateQuery("query")
        advanceTimeBy(100)
        vm.updateQuery("")
        advanceUntilIdle()
        assertEquals(0, calls)
        assertEquals(SearchContent.Idle, vm.state.value.content)
    }

    @Test fun lateSuccessOrFailureCannotOverwriteNewGeneration() = runTest(dispatcher) {
        for (failLate in listOf(false, true)) {
            lateinit var old: Continuation<List<Repository>>
            val vm = model(RepositorySearching {
                if (it == "old") suspendCoroutine { old = it } else listOf(repository(2))
            })
            vm.updateQuery("old")
            advanceTimeBy(400)
            runCurrent()
            vm.updateQuery("new")
            advanceTimeBy(400)
            runCurrent()
            if (failLate) old.resumeWithException(RepositorySearchException(SearchError.Server))
            else old.resume(listOf(repository(1)))
            runCurrent()
            assertEquals(listOf(repository(2)), (vm.state.value.content as SearchContent.Results).repositories)
        }
    }

    @Test fun clearRejectsLateNonCooperativeResponse() = runTest(dispatcher) {
        lateinit var old: Continuation<List<Repository>>
        val vm = model(RepositorySearching { suspendCoroutine { old = it } })
        vm.updateQuery("query")
        advanceTimeBy(400)
        runCurrent()
        vm.updateQuery("")
        old.resume(listOf(repository()))
        runCurrent()
        assertEquals(SearchContent.Idle, vm.state.value.content)
    }

    @Test fun currentDependencyCancellationReturnsIdleWithoutVisibleError() = runTest(dispatcher) {
        val vm = model(RepositorySearching { throw CancellationException("dependency canceled") })
        vm.updateQuery("query")
        advanceUntilIdle()
        assertEquals(SearchContent.Idle, vm.state.value.content)
    }

    @Test fun failureRetryAndEmptySuccessUseCurrentQueryAndDebounce() = runTest(dispatcher) {
        var calls = 0
        val vm = model(RepositorySearching {
            if (++calls == 1) throw RepositorySearchException(SearchError.Connectivity)
            emptyList()
        })
        vm.updateQuery("query")
        advanceUntilIdle()
        assertTrue(vm.canRetry())
        vm.retry()
        assertEquals(1, calls)
        advanceTimeBy(399)
        runCurrent()
        assertEquals(1, calls)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(SearchContent.Empty("query"), vm.state.value.content)
        assertEquals(2, calls)
    }

    @Test fun rateLimitExpiryOnlyEnablesManualRetry() = runTest(dispatcher) {
        var calls = 0
        val deadline = time.now + 1_500
        val vm = model(RepositorySearching {
            calls++
            throw RepositorySearchException(SearchError.RateLimited, deadline)
        })
        vm.updateQuery("query")
        advanceUntilIdle()
        assertFalse(vm.canRetry())
        vm.retry()
        advanceUntilIdle()
        assertEquals(1, calls)
        time.now = deadline - 1
        assertFalse(vm.canRetry())
        time.now = deadline
        assertTrue(vm.canRetry())
        assertEquals(1, calls)
        vm.retry()
        advanceUntilIdle()
        assertEquals(2, calls)
    }

    @Test fun newGenerationCanUseCachedResult() = runTest(dispatcher) {
        var calls = 0
        val vm = model(CachedRepositorySearchRepository(RepositorySearching {
            calls++
            listOf(repository())
        }, SearchResultCache(time)))
        vm.updateQuery("query")
        advanceUntilIdle()
        vm.updateQuery("QUERY")
        advanceUntilIdle()
        assertEquals(1, calls)
        assertEquals("QUERY", (vm.state.value.content as SearchContent.Results).query)
    }

    @Test fun savedDetailRestoresSnapshotWithoutNetworkAndBackCanResolveSearch() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val first = model(savedState = saved)
        first.updateQuery("query")
        advanceUntilIdle()
        val selected = repository().copy(description = null, language = "")
        first.select(selected)
        val restoredSaved = SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })
        var calls = 0
        val restored = model(RepositorySearching { calls++; listOf(repository()) }, restoredSaved)
        advanceUntilIdle()
        assertEquals(selected, restored.state.value.selected)
        assertEquals("query", restored.state.value.queryText)
        assertEquals(0, calls)
        assertTrue(restoredSaved.keys().none { it.contains("results") })
        restored.closeDetail()
        advanceUntilIdle()
        assertNull(restored.state.value.selected)
        assertEquals(1, calls)
    }

    @Test fun restoredSearchResolvesOnceAndBackInProcessDoesNotFetch() = runTest(dispatcher) {
        var calls = 0
        val vm = model(RepositorySearching { calls++; listOf(repository()) }, SavedStateHandle(mapOf("query" to "query")))
        advanceUntilIdle()
        assertEquals(1, calls)
        vm.select(repository())
        vm.closeDetail()
        advanceUntilIdle()
        assertEquals(1, calls)
        assertTrue(vm.state.value.content is SearchContent.Results)
    }

    @Test fun clearingViewModelCancelsRequest() = runTest(dispatcher) {
        var canceled = false
        val vm = model(RepositorySearching {
            try { awaitCancellation() } finally { canceled = true }
        })
        vm.updateQuery("query")
        advanceTimeBy(400)
        runCurrent()
        stores.single().clear()
        runCurrent()
        assertTrue(canceled)
    }

    @Test fun unexpectedErrorsAreReportedAndVisible() = runTest(dispatcher) {
        val error = IllegalStateException("unexpected")
        var reported: Throwable? = null
        val vm = model(RepositorySearching { throw error }, unexpected = { reported = it })
        vm.updateQuery("query")
        advanceUntilIdle()
        assertSame(error, reported)
        assertEquals(SearchContent.Failed("query", SearchError.Unexpected), vm.state.value.content)
    }
}
