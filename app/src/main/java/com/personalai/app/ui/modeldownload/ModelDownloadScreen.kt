package com.personalai.app.ui.modeldownload

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
        val state = uiState
        val isError = state is DownloadUiState.Error

        LoaderAvatar(
            percent = (state as? DownloadUiState.Downloading)?.percent,
            isIndeterminate = state is DownloadUiState.Done,
            isError = isError,
        )

        Text(
            text = stringResource(R.string.model_download_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 28.dp),
        )
        Text(
            stringResource(R.string.model_download_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp, bottom = 28.dp),
        )

        when (state) {
            is DownloadUiState.Idle -> {
                val sizeGb = viewModel.model.approxSizeBytes / 1_000_000_000.0
                Button(onClick = viewModel::startDownload, modifier = Modifier.fillMaxWidth(0.7f)) {
                    Text(stringResource(R.string.model_download_button, "%.1f GB".format(sizeGb)))
                }
            }
            is DownloadUiState.Downloading -> {
                Text(
                    stringResource(R.string.model_download_progress, state.percent),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
            is DownloadUiState.Done -> {
                Text(stringResource(R.string.model_loading), style = MaterialTheme.typography.titleMedium)
            }
            is DownloadUiState.Error -> {
                Text(
                    stringResource(R.string.model_download_error, state.message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Button(onClick = viewModel::startDownload, modifier = Modifier.padding(top = 16.dp).fillMaxWidth(0.7f)) {
                    Text(stringResource(R.string.model_download_retry))
                }
            }
        }
    }
}

/**
 * A breathing circular avatar that doubles as a progress ring: determinate while a download
 * percent is known, indeterminate while the model is being loaded into memory, static (and
 * recolored) on error.
 */
@Composable
private fun LoaderAvatar(percent: Int?, isIndeterminate: Boolean, isError: Boolean) {
    val transition = rememberInfiniteTransition(label = "loaderPulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scale",
    )

    val containerColor = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
    val iconColor = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.primary

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(140.dp)) {
        if (!isError) {
            if (isIndeterminate) {
                CircularProgressIndicator(
                    modifier = Modifier.size(140.dp),
                    strokeWidth = 4.dp,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            } else {
                CircularProgressIndicator(
                    progress = { (percent ?: 0) / 100f },
                    modifier = Modifier.size(140.dp),
                    strokeWidth = 4.dp,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }

        Box(
            modifier = Modifier
                .size(104.dp)
                .graphicsLayer {
                    if (!isError) {
                        scaleX = pulse
                        scaleY = pulse
                    }
                }
                .background(containerColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isError) Icons.Filled.ErrorOutline else Icons.Filled.SmartToy,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(48.dp),
            )
        }
    }
}
