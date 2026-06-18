package com.personalai.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.personalai.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val models by viewModel.models.collectAsState()
    val isSwitching by viewModel.isSwitching.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Text(
                stringResource(R.string.settings_models_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Text(
                stringResource(R.string.settings_models_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            if (isSwitching) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.settings_model_switching))
                }
            }
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                items(models, key = { it.model.id }) { item ->
                    ModelRow(
                        item = item,
                        switchDisabled = isSwitching,
                        onDownload = { viewModel.download(item.model) },
                        onDelete = { viewModel.delete(item.model) },
                        onUse = { viewModel.setActive(item.model) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelRow(
    item: ModelUiItem,
    switchDisabled: Boolean,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onUse: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(item.model.displayName, style = MaterialTheme.typography.titleSmall)
                if (item.isActive) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = stringResource(R.string.settings_model_active),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                stringResource(R.string.settings_model_size, item.model.approxSizeBytes / 1_000_000_000.0),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (item.downloadPercent != null) {
                LinearProgressIndicator(
                    progress = { item.downloadPercent / 100f },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Text(
                    stringResource(R.string.settings_model_downloading, item.downloadPercent),
                    style = MaterialTheme.typography.bodySmall,
                )
            } else if (item.downloadError != null) {
                Text(
                    stringResource(R.string.settings_model_error, item.downloadError),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                when {
                    item.isActive -> Unit
                    item.isDownloaded -> {
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.settings_model_delete))
                        }
                        Button(onClick = onUse, enabled = !switchDisabled) {
                            Text(stringResource(R.string.settings_model_use))
                        }
                    }
                    item.downloadPercent != null -> Unit
                    else -> {
                        OutlinedButton(onClick = onDownload) {
                            Text(stringResource(R.string.settings_model_download))
                        }
                    }
                }
            }
        }
    }
}
