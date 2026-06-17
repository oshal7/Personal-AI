package com.personalai.app.data.repository

import com.personalai.app.data.db.ChatMessageDao
import com.personalai.app.data.db.ChatMessageEntity
import com.personalai.app.data.db.MessageRole
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** In-memory stand-in for the Room DAO so repository logic can be tested without a database. */
private class FakeChatMessageDao : ChatMessageDao {
    private val nextId = AtomicLong(1)
    private val state = MutableStateFlow<List<ChatMessageEntity>>(emptyList())

    override fun observeAll(): Flow<List<ChatMessageEntity>> = state

    override suspend fun insert(message: ChatMessageEntity): Long {
        val id = nextId.getAndIncrement()
        state.value = (state.value + message.copy(id = id)).sortedBy { it.timestampMillis }
        return id
    }
}

class ChatRepositoryTest {

    @Test
    fun `addMessage persists content and role`() = runTest {
        val repository = ChatRepository(FakeChatMessageDao())

        repository.addMessage(MessageRole.USER, "what's 2 plus 2?")
        repository.addMessage(MessageRole.ASSISTANT, "4")

        val messages = repository.observeMessages().first()
        assertEquals(2, messages.size)
        assertEquals(MessageRole.USER, messages[0].role)
        assertEquals("what's 2 plus 2?", messages[0].content)
        assertEquals(MessageRole.ASSISTANT, messages[1].role)
        assertEquals("4", messages[1].content)
    }

    @Test
    fun `messages stay ordered by timestamp regardless of insert order`() = runTest {
        val dao = FakeChatMessageDao()
        val repository = ChatRepository(dao)

        dao.insert(ChatMessageEntity(role = MessageRole.USER, content = "third", timestampMillis = 300))
        dao.insert(ChatMessageEntity(role = MessageRole.USER, content = "first", timestampMillis = 100))
        dao.insert(ChatMessageEntity(role = MessageRole.USER, content = "second", timestampMillis = 200))

        val ordered = repository.observeMessages().first().map { it.content }
        assertEquals(listOf("first", "second", "third"), ordered)
    }

    @Test
    fun `stress - thousands of rapid inserts all land without loss or duplicate ids`() = runTest {
        val repository = ChatRepository(FakeChatMessageDao())
        val messageCount = 5_000

        repeat(messageCount) { i ->
            val role = if (i % 2 == 0) MessageRole.USER else MessageRole.ASSISTANT
            repository.addMessage(role, "message #$i")
        }

        val messages = repository.observeMessages().first()
        assertEquals(messageCount, messages.size)
        assertEquals(messageCount, messages.map { it.id }.toSet().size)
        assertTrue(messages.zipWithNext().all { (a, b) -> a.timestampMillis <= b.timestampMillis })
    }
}
