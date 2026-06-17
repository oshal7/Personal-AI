package com.personalai.app.ui.modeldownload

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.personalai.app.R

@Composable
fun ModelDownloadScreen(viewModel: ModelDownloadViewModel, onModelReady: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState) {
        if (uiState is DownloadUiState.Done) onModelReady()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.model_download_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.model_download_description),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
        )

        when (val state = uiState) {
            is DownloadUiState.Idle -> {
                val sizeGb = viewModel.model.approxSizeBytes / 1_000_000_000.0
                Button(onClick = viewModel::startDownload) {
                    Text(stringResource(R.string.model_download_button, "%.1f GB".format(sizeGb)))
                }
            }
            is DownloadUiState.Downloading -> {
                LinearProgressIndicator(
                    progress = { state.percent / 100f },
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Text(stringResource(R.string.model_download_progress, state.percent))
            }
            is DownloadUiState.Done -> {
                Text(stringResource(R.string.model_loading))
            }
            is DownloadUiState.Error -> {
                Text(stringResource(R.string.model_download_error, state.message))
                Button(onClick = viewModel::startDownload, modifier = Modifier.padding(top = 12.dp)) {
                    Text(stringResource(R.string.model_download_retry))
                }
            }
        }
    }
}
