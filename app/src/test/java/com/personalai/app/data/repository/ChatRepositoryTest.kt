package com.personalai.app.data.repository

import com.personalai.app.data.db.ChatMessageDao
import com.personalai.app.data.db.ChatMessageEntity
import com.personalai.app.data.db.ChatSessionDao
import com.personalai.app.data.db.ChatSessionEntity
import com.personalai.app.data.db.MessageRole
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** In-memory stand-in for the Room DAO so repository logic can be tested without a database. */
private class FakeChatMessageDao : ChatMessageDao {
    private val nextId = AtomicLong(1)
    private val state = MutableStateFlow<List<ChatMessageEntity>>(emptyList())

    override fun observeForSession(sessionId: Long): Flow<List<ChatMessageEntity>> =
        state.map { messages -> messages.filter { it.sessionId == sessionId } }

    override suspend fun insert(message: ChatMessageEntity): Long {
        val id = nextId.getAndIncrement()
        state.value = (state.value + message.copy(id = id)).sortedBy { it.timestampMillis }
        return id
    }

    override suspend fun deleteForSession(sessionId: Long) {
        state.value = state.value.filterNot { it.sessionId == sessionId }
    }

    override suspend fun deleteFromMessage(sessionId: Long, fromMessageId: Long) {
        state.value = state.value.filterNot { it.sessionId == sessionId && it.id >= fromMessageId }
    }
}

private class FakeChatSessionDao : ChatSessionDao {
    private val nextId = AtomicLong(1)
    private val state = MutableStateFlow<List<ChatSessionEntity>>(emptyList())

    override fun observeAll(): Flow<List<ChatSessionEntity>> = state.map { it.sortedByDescending { s -> s.updatedAtMillis } }

    override suspend fun getById(id: Long): ChatSessionEntity? = state.value.firstOrNull { it.id == id }

    override suspend fun insert(session: ChatSessionEntity): Long {
        val id = nextId.getAndIncrement()
        state.value = state.value + session.copy(id = id)
        return id
    }

    override suspend fun touch(id: Long, title: String, preview: String, updatedAtMillis: Long) {
        state.value = state.value.map {
            if (it.id == id) it.copy(title = title, lastMessagePreview = preview, updatedAtMillis = updatedAtMillis) else it
        }
    }

    override suspend fun delete(session: ChatSessionEntity) {
        state.value = state.value.filterNot { it.id == session.id }
    }

    override suspend fun deleteById(id: Long) {
        state.value = state.value.filterNot { it.id == id }
    }
}

class ChatRepositoryTest {

    private fun newRepository() = ChatRepository(FakeChatMessageDao(), FakeChatSessionDao())

    @Test
    fun `addMessage persists content and role`() = runTest {
        val repository = newRepository()
        val sessionId = repository.createSession()

        repository.addMessage(sessionId, MessageRole.USER, "what's 2 plus 2?")
        repository.addMessage(sessionId, MessageRole.ASSISTANT, "4")

        val messages = repository.observeMessages(sessionId).first()
        assertEquals(2, messages.size)
        assertEquals(MessageRole.USER, messages[0].role)
        assertEquals("what's 2 plus 2?", messages[0].content)
        assertEquals(MessageRole.ASSISTANT, messages[1].role)
        assertEquals("4", messages[1].content)
    }

    @Test
    fun `addMessage titles the session from the first user message`() = runTest {
        val repository = newRepository()
        val sessionId = repository.createSession()

        repository.addMessage(sessionId, MessageRole.USER, "what's the weather like")
        repository.addMessage(sessionId, MessageRole.ASSISTANT, "I can't check that offline")

        val session = repository.observeSessions().first().single()
        assertEquals("what's the weather like", session.title)
        assertEquals("I can't check that offline", session.lastMessagePreview)
    }

    @Test
    fun `messages from different sessions never mix`() = runTest {
        val repository = newRepository()
        val sessionA = repository.createSession()
        val sessionB = repository.createSession()

        repository.addMessage(sessionA, MessageRole.USER, "a-message")
        repository.addMessage(sessionB, MessageRole.USER, "b-message")

        assertEquals(listOf("a-message"), repository.observeMessages(sessionA).first().map { it.content })
        assertEquals(listOf("b-message"), repository.observeMessages(sessionB).first().map { it.content })
    }

    @Test
    fun `deleteSession removes both the session and its messages`() = runTest {
        val repository = newRepository()
        val sessionId = repository.createSession()
        repository.addMessage(sessionId, MessageRole.USER, "hello")

        repository.deleteSession(sessionId)

        assertTrue(repository.observeMessages(sessionId).first().isEmpty())
        assertNull(repository.observeSessions().first().firstOrNull { it.id == sessionId })
    }

    @Test
    fun `stress - thousands of rapid inserts all land without loss or duplicate ids`() = runTest {
        val repository = newRepository()
        val sessionId = repository.createSession()
        val messageCount = 5_000

        repeat(messageCount) { i ->
            val role = if (i % 2 == 0) MessageRole.USER else MessageRole.ASSISTANT
            repository.addMessage(sessionId, role, "message #$i")
        }

        val messages = repository.observeMessages(sessionId).first()
        assertEquals(messageCount, messages.size)
        assertEquals(messageCount, messages.map { it.id }.toSet().size)
        assertTrue(messages.zipWithNext().all { (a, b) -> a.timestampMillis <= b.timestampMillis })
    }
}
