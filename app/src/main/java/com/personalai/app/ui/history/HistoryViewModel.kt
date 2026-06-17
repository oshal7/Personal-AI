package com.personalai.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalai.app.data.db.ChatSessionEntity
import com.personalai.app.data.repository.ChatRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(private val chatRepository: ChatRepository) : ViewModel() {

    val sessions: StateFlow<List<ChatSessionEntity>> = chatRepository.observeSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch { chatRepository.deleteSession(sessionId) }
    }
}
