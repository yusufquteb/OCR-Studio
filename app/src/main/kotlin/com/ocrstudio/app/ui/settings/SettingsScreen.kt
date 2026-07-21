package com.ocrstudio.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ocrstudio.app.R
import com.ocrstudio.core.common.OnlineModelInfo

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsState()
    val onlineConfig by viewModel.onlineCorrectionConfig.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) }) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
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

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            Text(stringResource(R.string.settings_online_correction_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.settings_online_correction_subtitle),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            SettingsRow(
                stringResource(R.string.settings_online_correction_enable),
                onlineConfig.enabled,
                viewModel::setOnlineCorrectionEnabled
            )

            var modelMenuExpanded by remember { mutableStateOf(false) }
            val selectedModel: OnlineModelInfo? = viewModel.onlineModels.find { it.id == onlineConfig.modelId }
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                TextButton(onClick = { modelMenuExpanded = true }) {
                    Text(
                        "${stringResource(R.string.settings_online_correction_model)}: " +
                            (selectedModel?.displayName ?: "—")
                    )
                }
                DropdownMenu(expanded = modelMenuExpanded, onDismissRequest = { modelMenuExpanded = false }) {
                    viewModel.onlineModels.forEach { model ->
                        DropdownMenuItem(
                            text = { Text("${model.provider.displayName} · ${model.displayName}") },
                            onClick = { modelMenuExpanded = false; viewModel.setOnlineModelId(model.id) }
                        )
                    }
                }
            }
            selectedModel?.let {
                Text(it.note, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
            }

            var apiKeyField by remember(onlineConfig.apiKey) { mutableStateOf(onlineConfig.apiKey) }
            OutlinedTextField(
                value = apiKeyField,
                onValueChange = { apiKeyField = it },
                label = { Text(stringResource(R.string.settings_online_correction_api_key)) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )
            Button(
                onClick = { viewModel.setOnlineApiKey(apiKeyField) },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.settings_online_correction_save)) }
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
