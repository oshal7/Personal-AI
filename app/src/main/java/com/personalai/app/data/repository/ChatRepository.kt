package com.personalai.app.data.repository

import com.personalai.app.data.db.ChatMessageDao
import com.personalai.app.data.db.ChatMessageEntity
import com.personalai.app.data.db.ChatSessionDao
import com.personalai.app.data.db.ChatSessionEntity
import com.personalai.app.data.db.MessageRole
import kotlinx.coroutines.flow.Flow

private const val DEFAULT_TITLE = "New chat"
private const val TITLE_MAX_LENGTH = 40
private const val PREVIEW_MAX_LENGTH = 80

class ChatRepository(
    private val chatMessageDao: ChatMessageDao,
    private val chatSessionDao: ChatSessionDao,
) {

    fun observeSessions(): Flow<List<ChatSessionEntity>> = chatSessionDao.observeAll()

    fun observeMessages(sessionId: Long): Flow<List<ChatMessageEntity>> =
        chatMessageDao.observeForSession(sessionId)

    suspend fun createSession(): Long {
        val now = System.currentTimeMillis()
        return chatSessionDao.insert(
            ChatSessionEntity(
                title = DEFAULT_TITLE,
                lastMessagePreview = "",
                createdAtMillis = now,
                updatedAtMillis = now,
            )
        )
    }

    suspend fun addMessage(sessionId: Long, role: MessageRole, content: String) {
        chatMessageDao.insert(
            ChatMessageEntity(
                sessionId = sessionId,
                role = role,
                content = content,
                timestampMillis = System.currentTimeMillis(),
            )
        )

        val session = chatSessionDao.getById(sessionId)
        val title = if (role == MessageRole.USER && (session == null || session.title == DEFAULT_TITLE)) {
            truncate(content, TITLE_MAX_LENGTH)
        } else {
            session?.title ?: truncate(content, TITLE_MAX_LENGTH)
        }
        chatSessionDao.touch(sessionId, title, truncate(content, PREVIEW_MAX_LENGTH), System.currentTimeMillis())
    }

    suspend fun deleteSession(sessionId: Long) {
        chatMessageDao.deleteForSession(sessionId)
        chatSessionDao.deleteById(sessionId)
    }

    private fun truncate(text: String, maxLength: Int): String =
        if (text.length > maxLength) "${text.take(maxLength)}…" else text
}
