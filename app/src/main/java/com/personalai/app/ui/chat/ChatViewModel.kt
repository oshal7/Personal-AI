package com.personalai.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalai.app.data.db.ChatMessageEntity
import com.personalai.app.data.db.MessageRole
import com.personalai.app.data.repository.ChatRepository
import com.personalai.app.data.repository.ModelRepository
import com.personalai.app.domain.model.ModelRegistry
import com.personalai.llama.LlamaSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val SYSTEM_PROMPT =
    "You are a helpful, concise personal assistant running entirely offline on the user's phone."

class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val modelRepository: ModelRepository,
    private val llamaSession: LlamaSession,
) : ViewModel() {

    val messages: StateFlow<List<ChatMessageEntity>> = chatRepository.observeMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val sessionState: StateFlow<LlamaSession.State> = llamaSession.state

    private val _streamingReply = MutableStateFlow("")
    val streamingReply: StateFlow<String> = _streamingReply.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    init {
        viewModelScope.launch {
            if (llamaSession.state.value is LlamaSession.State.Initialized) {
                val modelFile = modelRepository.localFile(ModelRegistry.default)
                llamaSession.loadModel(modelFile.absolutePath)
                llamaSession.setSystemPrompt(SYSTEM_PROMPT)
            }
        }
    }

    fun onInputChange(text: String) {
        _inputText.value = text
    }

    fun sendMessage() {
        val text = _inputText.value.trim()
        if (text.isEmpty() || sessionState.value !is LlamaSession.State.ModelReady) return

        _inputText.value = ""
        viewModelScope.launch {
            chatRepository.addMessage(MessageRole.USER, text)

            val builder = StringBuilder()
            llamaSession.sendUserPrompt(text).collect { token ->
                builder.append(token)
                _streamingReply.value = builder.toString()
            }

            val reply = builder.toString()
            _streamingReply.value = ""
            if (reply.isNotBlank()) {
                chatRepository.addMessage(MessageRole.ASSISTANT, reply)
            }
        }
    }
}
