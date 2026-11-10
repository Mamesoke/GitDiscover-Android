package com.mamesoke.gitdiscover.presentation.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mamesoke.gitdiscover.R
import com.mamesoke.gitdiscover.domain.model.Repository
import java.text.NumberFormat

@Composable
fun RepositoryDetailScreen(
    repository: Repository,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(
                    repository.fullName, style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    repository.description ?: stringResource(R.string.no_description),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("description-value")
                )
                HorizontalDivider()
                DetailField(stringResource(R.string.owner), repository.ownerLogin, "owner-value")
                DetailField(stringResource(R.string.language),
                    repository.language ?: stringResource(R.string.unknown_language), "language-value")
                DetailField(stringResource(R.string.stars),
                    NumberFormat.getIntegerInstance().format(repository.stars), "stars-value")
                DetailField(stringResource(R.string.forks),
                    NumberFormat.getIntegerInstance().format(repository.forks), "forks-value")
            }
        }
        Button(onClick = { onOpenLink(repository.htmlUrl) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.open_github))
        }
    }
}

@Composable
private fun DetailField(label: String, value: String, tag: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(value, modifier = Modifier.testTag(tag), style = MaterialTheme.typography.bodyLarge)
    }
}
