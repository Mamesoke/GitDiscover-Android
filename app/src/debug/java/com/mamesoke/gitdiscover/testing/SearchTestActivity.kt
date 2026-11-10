package com.mamesoke.gitdiscover.testing

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mamesoke.gitdiscover.app.GitDiscoverApp
import com.mamesoke.gitdiscover.domain.model.Repository
import com.mamesoke.gitdiscover.domain.repository.RepositorySearchException
import com.mamesoke.gitdiscover.domain.repository.RepositorySearching
import com.mamesoke.gitdiscover.domain.repository.SearchError
import com.mamesoke.gitdiscover.domain.search.QueryRules
import com.mamesoke.gitdiscover.domain.time.TimeProvider
import com.mamesoke.gitdiscover.domain.usecase.SearchRepositoriesUseCase
import com.mamesoke.gitdiscover.presentation.search.SearchViewModel
import com.mamesoke.gitdiscover.ui.theme.GitDiscoverTheme

class SearchTestActivity : ComponentActivity() {
    var lastOpenedUrl: String? = null
        private set
    var searchCalls = 0
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val scenario = FixtureScenario.valueOf(checkNotNull(intent.getStringExtra("scenario")))
        val repository = RepositorySearching {
            searchCalls++
            when {
                scenario == FixtureScenario.Empty -> emptyList()
                scenario == FixtureScenario.Offline && searchCalls == 1 ->
                    throw RepositorySearchException(SearchError.Connectivity)
                scenario == FixtureScenario.RateLimit ->
                    throw RepositorySearchException(SearchError.RateLimited, System.currentTimeMillis() + 60_000)
                else -> listOf(
                    Repository(1, "GitDiscover", "sample/GitDiscover", "sample",
                        if (scenario == FixtureScenario.MissingFields) null else "Discover public repositories.",
                        if (scenario == FixtureScenario.MissingFields) null else "Kotlin",
                        1234, 56, "https://github.com/sample/GitDiscover")
                )
            }
        }
        val factory = viewModelFactory {
            initializer {
                SearchViewModel(
                    SearchRepositoriesUseCase(repository, QueryRules()), createSavedStateHandle(),
                    TimeProvider(System::currentTimeMillis)
                ) { Log.e("GitDiscoverTest", "Unexpected fixture failure", it) }
            }
        }
        setContent {
            GitDiscoverTheme {
                GitDiscoverApp(viewModel(factory = factory), openLink = {
                    lastOpenedUrl = it
                    !intent.getBooleanExtra("missingBrowser", false)
                })
            }
        }
    }
}

enum class FixtureScenario { Results, MissingFields, Empty, Offline, RateLimit }
