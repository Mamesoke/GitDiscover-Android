package com.mamesoke.gitdiscover.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mamesoke.gitdiscover.R
import com.mamesoke.gitdiscover.presentation.detail.RepositoryDetailScreen
import com.mamesoke.gitdiscover.presentation.search.SearchContent
import com.mamesoke.gitdiscover.presentation.search.SearchScreen
import com.mamesoke.gitdiscover.presentation.search.SearchViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitDiscoverApp(viewModel: SearchViewModel, openLink: (String) -> Boolean) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigation = rememberNavController()
    val startDestination = remember { state.selected?.let { "detail/${it.id}" } ?: "search" }
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val browserUnavailable = stringResource(R.string.browser_unavailable)
    val lifecycle = LocalLifecycleOwner.current
    val retryAt = (state.content as? SearchContent.Failed)?.retryAtMillis
    val now by produceState(System.currentTimeMillis(), retryAt, lifecycle) {
        if (retryAt != null) {
            lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    value = System.currentTimeMillis()
                    if (value >= retryAt) break
                    delay(1_000)
                }
            }
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        NavHost(navigation, startDestination = startDestination, modifier = Modifier.fillMaxSize()) {
            composable("search") {
                Scaffold(
                    topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
                    snackbarHost = { SnackbarHost(snackbar) }
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
                        SearchScreen(
                            state = state, nowMillis = now,
                            onQueryChange = viewModel::updateQuery, onRetry = viewModel::retry,
                            onSelect = {
                                viewModel.select(it)
                                navigation.navigate("detail/${it.id}") { launchSingleTop = true }
                            },
                            modifier = Modifier.widthIn(max = 840.dp),
                            listState = listState
                        )
                    }
                }
            }
            composable("detail/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                val selected = remember(entry) {
                    checkNotNull(state.selected) { "Detail requires a saved repository snapshot" }
                }
                check(selected.id == entry.arguments?.getLong("id"))
                val back = {
                    if (!navigation.popBackStack()) {
                        navigation.navigate("search") { popUpTo(navigation.graph.id) { inclusive = true } }
                    }
                    viewModel.closeDetail()
                }
                BackHandler(onBack = back)
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(selected.name) },
                            navigationIcon = {
                                IconButton(onClick = back) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                                }
                            }
                        )
                    },
                    snackbarHost = { SnackbarHost(snackbar) }
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                        RepositoryDetailScreen(selected, onOpenLink = {
                            if (!openLink(it)) scope.launch {
                                snackbar.showSnackbar(browserUnavailable)
                            }
                        }, modifier = Modifier.widthIn(max = 840.dp))
                    }
                }
            }
        }
    }
}
