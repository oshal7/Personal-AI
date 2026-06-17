package com.personalai.app.ui.modeldownload

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalai.app.data.repository.DownloadProgress
import com.personalai.app.data.repository.ModelRepository
import com.personalai.app.domain.model.ModelInfo
import com.personalai.app.domain.model.ModelRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class DownloadUiState {
    data object Idle : DownloadUiState()
    data class Downloading(val percent: Int) : DownloadUiState()
    data object Done : DownloadUiState()
    data class Error(val message: String) : DownloadUiState()
}

class ModelDownloadViewModel(private val modelRepository: ModelRepository) : ViewModel() {

    val model: ModelInfo = ModelRegistry.default

    private val _uiState = MutableStateFlow<DownloadUiState>(
        if (modelRepository.isDownloaded(model)) DownloadUiState.Done else DownloadUiState.Idle
    )
    val uiState: StateFlow<DownloadUiState> = _uiState.asStateFlow()

    fun startDownload() {
        if (_uiState.value is DownloadUiState.Downloading) return
        viewModelScope.launch {
            modelRepository.download(model).collect { progress ->
                _uiState.value = when (progress) {
                    is DownloadProgress.InProgress -> {
                        val percent = if (progress.totalBytes > 0) {
                            (progress.bytesRead * 100 / progress.totalBytes).toInt().coerceIn(0, 100)
                        } else 0
                        DownloadUiState.Downloading(percent)
                    }
                    is DownloadProgress.Complete -> DownloadUiState.Done
                    is DownloadProgress.Failed -> DownloadUiState.Error(progress.message)
                }
            }
        }
    }
}
