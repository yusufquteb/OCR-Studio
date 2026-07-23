package com.ocrstudio.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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

@Composable
fun SettingsScreen(
    onOpenModels: () -> Unit,
    onOpenExport: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) }) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            SettingsNavCard(
                icon = Icons.Filled.CloudDownload,
                title = stringResource(R.string.settings_models_ai_title),
                subtitle = stringResource(R.string.settings_models_ai_subtitle),
                onClick = onOpenModels
            )
            SettingsNavCard(
                icon = Icons.Filled.Upload,
                title = stringResource(R.string.settings_export_title),
                subtitle = stringResource(R.string.settings_export_subtitle),
                onClick = onOpenExport,
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
            )

            Text("${stringResource(R.string.settings_default_batch_size)}: ${settings.defaultBatchSize}", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = settings.defaultBatchSize.toFloat(),
                onValueChange = { viewModel.setDefaultBatchSize(it.toInt()) },
                valueRange = 10f..50f,
                steps = 7
            )

            Text("${stringResource(R.string.settings_default_dpi)}: ${settings.defaultDpi}", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = settings.defaultDpi.toFloat(),
                onValueChange = { viewModel.setDefaultDpi(it.toInt()) },
                valueRange = 300f..600f,
                steps = 5
            )

            SettingsRow(stringResource(R.string.settings_keep_images), settings.keepImagesByDefault, viewModel::setKeepImagesByDefault)
            SettingsRow(stringResource(R.string.settings_battery_constraint), settings.batteryConstraintOnly, viewModel::setBatteryConstraintOnly)

            Button(onClick = viewModel::clearCache, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Text(stringResource(R.string.settings_clear_cache))
            }
        }
    }
}

@Composable
private fun SettingsNavCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row {
                Icon(icon, contentDescription = null, modifier = Modifier.padding(end = 16.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
private fun SettingsRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
