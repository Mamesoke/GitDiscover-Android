package com.mamesoke.gitdiscover.presentation.search

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mamesoke.gitdiscover.R
import com.mamesoke.gitdiscover.domain.model.Repository
import com.mamesoke.gitdiscover.domain.repository.SearchError
import java.text.NumberFormat
import kotlin.math.ceil

@Composable
fun SearchScreen(
    state: SearchUiState,
    nowMillis: Long,
    onQueryChange: (String) -> Unit,
    onRetry: () -> Unit,
    onSelect: (Repository) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState()
) {
    val keyboard = LocalSoftwareKeyboardController.current
    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.queryText,
            onValueChange = onQueryChange,
            label = { Text(stringResource(R.string.search_placeholder)) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = { onQueryChange("") }, enabled = state.queryText.isNotEmpty()) {
                    Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear_search))
                }
            },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Search
            ),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("search-field")
        )
        when (val content = state.content) {
            SearchContent.Idle -> Status(
                stringResource(R.string.idle_title), stringResource(R.string.idle_message), "idle-state"
            )
            is SearchContent.Loading -> {
                Column(
                    Modifier.fillMaxSize().padding(24.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.searching), Modifier.padding(top = 16.dp))
                }
            }
            is SearchContent.Empty -> Status(
                stringResource(R.string.empty_title), content.query, "empty-state"
            )
            is SearchContent.Results -> {
                LazyColumn(state = listState, modifier = Modifier.testTag("results-list")) {
                    items(content.repositories, key = { it.id }) { repository ->
                        RepositoryRow(repository) {
                            keyboard?.hide()
                            onSelect(repository)
                        }
                        HorizontalDivider()
                    }
                }
            }
            is SearchContent.Failed -> {
                val remaining = content.retryAtMillis?.let {
                    ceil((it.toDouble() - nowMillis) / 1000).toInt().coerceAtLeast(0)
                } ?: 0
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite }.testTag("error-state"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
                ) {
                    Text(stringResource(R.string.error_title), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(content.error.messageResource()))
                    if (remaining > 0) Text(pluralStringResource(R.plurals.retry_countdown, remaining, remaining))
                    OutlinedButton(onClick = onRetry, enabled = remaining == 0) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
        }
    }
}

@Composable
private fun Status(title: String, message: String, tag: String) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RepositoryRow(repository: Repository, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp)
            .testTag("repository-${repository.id}"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(repository.fullName, style = MaterialTheme.typography.titleMedium)
        repository.description?.takeIf { it.isNotEmpty() }?.let {
            Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                repository.language ?: stringResource(R.string.unknown_language),
                Modifier.weight(1f), style = MaterialTheme.typography.bodySmall
            )
            val formatted = NumberFormat.getIntegerInstance().format(repository.stars)
            val label = stringResource(R.string.stars_count, formatted)
            Row(Modifier.semantics(mergeDescendants = true) { contentDescription = label }) {
                Icon(Icons.Default.Star, null, Modifier.size(16.dp))
                Text(formatted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@StringRes
internal fun SearchError.messageResource(): Int = when (this) {
    SearchError.InvalidRequest -> R.string.error_request
    SearchError.InvalidResponse, SearchError.Decoding -> R.string.error_response
    SearchError.AccessDenied -> R.string.error_access_denied
    SearchError.Server -> R.string.error_server
    SearchError.Connectivity -> R.string.error_connectivity
    SearchError.RateLimited -> R.string.error_rate_limit
    SearchError.Unexpected -> R.string.error_unexpected
}
