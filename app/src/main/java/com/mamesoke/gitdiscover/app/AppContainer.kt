package com.mamesoke.gitdiscover.app

import android.util.Log
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mamesoke.gitdiscover.data.cache.SearchResultCache
import com.mamesoke.gitdiscover.domain.time.TimeProvider
import com.mamesoke.gitdiscover.data.network.GitHubApiClient
import com.mamesoke.gitdiscover.data.network.OkHttpTransport
import com.mamesoke.gitdiscover.data.network.RateLimitGate
import com.mamesoke.gitdiscover.data.repository.CachedRepositorySearchRepository
import com.mamesoke.gitdiscover.domain.search.QueryRules
import com.mamesoke.gitdiscover.domain.usecase.SearchRepositoriesUseCase
import com.mamesoke.gitdiscover.presentation.search.SearchViewModel
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

class AppContainer {
    private val time = TimeProvider(System::currentTimeMillis)
    private val client = OkHttpClient.Builder()
        .retryOnConnectionFailure(false)
        .followRedirects(false)
        .followSslRedirects(false)
        .cache(null)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()
    private val repository = CachedRepositorySearchRepository(
        GitHubApiClient(OkHttpTransport(client), RateLimitGate(time)),
        SearchResultCache(time)
    )
    private val search = SearchRepositoriesUseCase(repository, QueryRules())

    val searchViewModelFactory = viewModelFactory {
        initializer {
            SearchViewModel(search, createSavedStateHandle(), time) {
                Log.e("GitDiscover", "Unexpected search failure", it)
            }
        }
    }
}
