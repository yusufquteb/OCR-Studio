package com.ocrstudio.app.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ocrstudio.app.R
import com.ocrstudio.core.database.dao.PageSearchHit
import com.ocrstudio.core.ui.components.EmptyState

@Composable
fun SearchScreen(viewModel: SearchViewModel = hiltViewModel()) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.search_title)) }) }) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                label = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSearch = { viewModel.onSearchSubmit() }
                ),
                modifier = Modifier.fillMaxWidth()
            )

            when {
                query.isBlank() && recentSearches.isEmpty() -> Unit
                query.isBlank() -> {
                    Text(
                        stringResource(R.string.search_recent_title),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    LazyColumn {
                        items(recentSearches, key = { it }) { recent ->
                            RecentSearchItem(recent, onClick = { viewModel.onQueryChange(recent) })
                        }
                    }
                }
                results.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Filled.SearchOff,
                        title = stringResource(R.string.search_no_results),
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
                else -> {
                    LazyColumn {
                        items(results, key = { "${it.jobId}_${it.pageNumber}" }) { hit -> SearchResultItem(hit) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentSearchItem(query: String, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onClick)) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            Text(query, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SearchResultItem(hit: PageSearchHit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text("Page ${hit.pageNumber}", style = MaterialTheme.typography.labelMedium)
            // Rtl LocalLayoutDirection so this multi-line snippet wraps and aligns from the
            // right like Arabic text should, not just gets the right bidi run order.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Text(
                    hit.correctedText.take(200),
                    style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
                    maxLines = 3
                )
            }
        }
    }
}
