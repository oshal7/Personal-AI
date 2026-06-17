package com.personalai.app.data.repository

import com.personalai.app.data.db.ChatMessageDao
import com.personalai.app.data.db.ChatMessageEntity
import com.personalai.app.data.db.MessageRole
import kotlinx.coroutines.flow.Flow

class ChatRepository(private val dao: ChatMessageDao) {

    fun observeMessages(): Flow<List<ChatMessageEntity>> = dao.observeAll()

    suspend fun addMessage(role: MessageRole, content: String) {
        dao.insert(ChatMessageEntity(role = role, content = content, timestampMillis = System.currentTimeMillis()))
    }
}
