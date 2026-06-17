package com.personalai.app.ui.modeldownload

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalai.app.data.repository.DownloadProgress
import com.personalai.app.data.repository.ModelRepository
import com.personalai.app.domain.model.ModelInfo
import com.personalai.app.domain.model.ModelRegistry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed class DownloadUiState {
    data object Idle : DownloadUiState()
    data class Downloading(val percent: Int) : DownloadUiState()
    data object Done : DownloadUiState()
    data class Error(val message: String) : DownloadUiState()
}

/**
 * The download itself runs in `ModelDownloadService` so it survives the screen turning off or
 * this ViewModel/Activity being torn down; [startBackgroundDownload] just starts that service,
 * and [uiState] mirrors its progress via [ModelRepository.downloadState], which is shared with
 * (and kept current by) whichever component is actually running the download.
 */
class ModelDownloadViewModel(
    private val modelRepository: ModelRepository,
    private val startBackgroundDownload: () -> Unit,
) : ViewModel() {

    val model: ModelInfo = ModelRegistry.default

    val uiState: StateFlow<DownloadUiState> = modelRepository.downloadState
        .map { it.toUiState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), modelRepository.downloadState.value.toUiState())

    fun startDownload() {
        if (uiState.value is DownloadUiState.Downloading) return
        startBackgroundDownload()
    }

    private fun DownloadProgress?.toUiState(): DownloadUiState = when (this) {
        null -> if (modelRepository.isDownloaded(model)) DownloadUiState.Done else DownloadUiState.Idle
        is DownloadProgress.InProgress -> {
            val percent = if (totalBytes > 0) (bytesRead * 100 / totalBytes).toInt().coerceIn(0, 100) else 0
            DownloadUiState.Downloading(percent)
        }
        is DownloadProgress.Complete -> DownloadUiState.Done
        is DownloadProgress.Failed -> DownloadUiState.Error(message)
    }
}
