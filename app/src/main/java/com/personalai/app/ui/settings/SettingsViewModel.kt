package com.personalai.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalai.app.data.prefs.UserPreferences
import com.personalai.app.data.repository.DownloadProgress
import com.personalai.app.data.repository.ModelRepository
import com.personalai.app.domain.model.ModelInfo
import com.personalai.app.domain.model.ModelRegistry
import com.personalai.app.domain.model.SYSTEM_PROMPT
import com.personalai.llama.LlamaSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ModelUiItem(
    val model: ModelInfo,
    val isActive: Boolean,
    val isDownloaded: Boolean,
    val downloadPercent: Int?,
    val downloadError: String?,
)

/**
 * Drives the model list in Settings. The active model lives in [UserPreferences] and the actual
 * download in [ModelRepository]/`ModelDownloadService`; this just combines both into a list the
 * UI can render, plus the "switch active model" action which re-points the already-loaded
 * [LlamaSession] at a different GGUF file without restarting the app.
 */
class SettingsViewModel(
    private val modelRepository: ModelRepository,
    private val userPreferences: UserPreferences,
    private val llamaSession: LlamaSession,
    private val startDownload: (ModelInfo) -> Unit,
) : ViewModel() {

    private val refreshTrigger = MutableStateFlow(0)

    private val _isSwitching = MutableStateFlow(false)
    val isSwitching: StateFlow<Boolean> = _isSwitching.asStateFlow()

    val models: StateFlow<List<ModelUiItem>> = combine(
        userPreferences.activeModel,
        modelRepository.currentDownload,
        modelRepository.downloadState,
        refreshTrigger,
    ) { active, currentDownload, progress, _ ->
        ModelRegistry.all.map { model ->
            val isThisDownloading = currentDownload?.id == model.id
            val inProgress = (progress as? DownloadProgress.InProgress)?.takeIf { isThisDownloading }
            val failed = (progress as? DownloadProgress.Failed)?.takeIf { isThisDownloading }
            ModelUiItem(
                model = model,
                isActive = model.id == active.id,
                isDownloaded = modelRepository.isDownloaded(model),
                downloadPercent = inProgress?.let {
                    if (it.totalBytes > 0) (it.bytesRead * 100 / it.totalBytes).toInt().coerceIn(0, 100) else 0
                },
                downloadError = failed?.message,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun download(model: ModelInfo) {
        startDownload(model)
    }

    fun delete(model: ModelInfo) {
        viewModelScope.launch {
            if (userPreferences.activeModel.first().id == model.id) return@launch
            modelRepository.delete(model)
            refreshTrigger.value++
        }
    }

    /** Unloads the current model and loads [model] in its place, then persists it as the new default. */
    fun setActive(model: ModelInfo) {
        if (!modelRepository.isDownloaded(model) || _isSwitching.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isSwitching.value = true
            try {
                llamaSession.cleanUp()
                llamaSession.loadModel(modelRepository.localFile(model).absolutePath)
                llamaSession.setSystemPrompt(SYSTEM_PROMPT)
                userPreferences.setActiveModel(model)
            } catch (e: Exception) {
                // llamaSession.state already reflects the failure (State.Error); nothing more to do here.
            } finally {
                _isSwitching.value = false
            }
        }
    }
}
