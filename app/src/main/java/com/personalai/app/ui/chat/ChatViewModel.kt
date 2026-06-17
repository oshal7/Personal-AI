package com.personalai.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalai.app.data.db.ChatMessageEntity
import com.personalai.app.data.db.MessageRole
import com.personalai.app.data.repository.ChatRepository
import com.personalai.app.data.repository.ModelRepository
import com.personalai.app.data.repository.TaskRepository
import com.personalai.app.domain.model.ModelRegistry
import com.personalai.app.domain.tools.TaskSuggestionDetector
import com.personalai.app.voice.SpeechInputManager
import com.personalai.llama.LlamaSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val SYSTEM_PROMPT =
    "You are a helpful, concise personal assistant running entirely offline on the user's phone."

/** A [sessionId] of 0 means "new, unsaved chat" — no row is created until the first message is sent. */
class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val modelRepository: ModelRepository,
    private val llamaSession: LlamaSession,
    private val taskRepository: TaskRepository,
    private val speechInputManager: SpeechInputManager,
    initialSessionId: Long = 0L,
) : ViewModel() {

    private val _sessionId = MutableStateFlow(initialSessionId)
    val sessionId: StateFlow<Long> = _sessionId.asStateFlow()

    val messages: StateFlow<List<ChatMessageEntity>> = _sessionId
        .flatMapLatest { id -> chatRepository.observeMessages(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val sessionState: StateFlow<LlamaSession.State> = llamaSession.state

    private val _streamingReply = MutableStateFlow("")
    val streamingReply: StateFlow<String> = _streamingReply.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _taskSuggestion = MutableStateFlow<String?>(null)
    val taskSuggestion: StateFlow<String?> = _taskSuggestion.asStateFlow()

    val voiceState: StateFlow<SpeechInputManager.State> = speechInputManager.state

    init {
        viewModelScope.launch {
            // The native backend finishes initializing asynchronously, so wait for it to
            // leave Uninitialized/Initializing instead of checking the state once at a
            // potentially-too-early instant (which left ModelReady unreachable).
            val readyToLoad = llamaSession.state.first {
                it !is LlamaSession.State.Uninitialized && it !is LlamaSession.State.Initializing
            }
            if (readyToLoad is LlamaSession.State.Initialized) {
                val modelFile = modelRepository.localFile(ModelRegistry.default)
                llamaSession.loadModel(modelFile.absolutePath)
                llamaSession.setSystemPrompt(SYSTEM_PROMPT)
            }
        }

        viewModelScope.launch {
            speechInputManager.state.collect { state ->
                when (state) {
                    is SpeechInputManager.State.PartialResult -> _inputText.value = state.text
                    is SpeechInputManager.State.FinalResult -> {
                        _inputText.value = state.text
                        speechInputManager.resetToIdle()
                    }
                    else -> Unit
                }
            }
        }
    }

    fun onInputChange(text: String) {
        _inputText.value = text
    }

    fun startVoiceInput() = speechInputManager.startListening()

    fun stopVoiceInput() {
        speechInputManager.stopListening()
        speechInputManager.resetToIdle()
    }

    fun dismissTaskSuggestion() {
        _taskSuggestion.value = null
    }

    /** Only ever called after an explicit user tap — tasks are never saved silently. */
    fun confirmTaskSuggestion() {
        val suggestion = _taskSuggestion.value ?: return
        _taskSuggestion.value = null
        viewModelScope.launch {
            taskRepository.addTask(suggestion, _sessionId.value)
        }
    }

    private suspend fun ensureSessionId(): Long {
        if (_sessionId.value == 0L) {
            _sessionId.value = chatRepository.createSession()
        }
        return _sessionId.value
    }

    fun sendMessage() {
        val text = _inputText.value.trim()
        if (text.isEmpty() || sessionState.value !is LlamaSession.State.ModelReady) return

        _inputText.value = ""
        viewModelScope.launch {
            val id = ensureSessionId()
            chatRepository.addMessage(id, MessageRole.USER, text)

            TaskSuggestionDetector.detect(text)?.let { suggestion -> _taskSuggestion.value = suggestion }

            val builder = StringBuilder()
            llamaSession.sendUserPrompt(text).collect { token ->
                builder.append(token)
                _streamingReply.value = builder.toString()
            }

            val reply = builder.toString()
            _streamingReply.value = ""
            if (reply.isNotBlank()) {
                chatRepository.addMessage(id, MessageRole.ASSISTANT, reply)
            }
        }
    }
}
