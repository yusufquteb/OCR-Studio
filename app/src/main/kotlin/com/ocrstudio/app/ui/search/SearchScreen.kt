package com.ocrstudio.app.ui.search

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ocrstudio.app.R
import com.ocrstudio.core.database.dao.PageSearchHit
import com.ocrstudio.core.ui.components.EmptyState

@Composable
fun SearchScreen(viewModel: SearchViewModel = hiltViewModel()) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.search_title)) }) }) { padding ->
        androidx.compose.foundation.layout.Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                label = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { androidx.compose.material3.Icon(Icons.Filled.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth()
            )

            if (query.isNotBlank() && results.isEmpty()) {
                Text(stringResource(R.string.search_no_results), modifier = Modifier.padding(top = 16.dp))
            }

            LazyColumn {
                items(results, key = { "${it.jobId}_${it.pageNumber}" }) { hit -> SearchResultItem(hit) }
            }
        }
    }
}

@Composable
private fun SearchResultItem(hit: PageSearchHit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        androidx.compose.foundation.layout.Column(Modifier.padding(12.dp)) {
            Text("Page ${hit.pageNumber}", style = MaterialTheme.typography.labelMedium)
            Text(
                hit.correctedText.take(200),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3
            )
        }
    }
}
