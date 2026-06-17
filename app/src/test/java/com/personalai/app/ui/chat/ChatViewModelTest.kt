package com.personalai.app.ui.chat

import android.content.Context
import com.personalai.app.data.db.ChatMessageDao
import com.personalai.app.data.db.ChatMessageEntity
import com.personalai.app.data.db.MessageRole
import com.personalai.app.data.repository.ChatRepository
import com.personalai.app.data.repository.ModelRepository
import com.personalai.llama.LlamaSession
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.Dispatchers

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

/** Stands in for the JNI-backed session so the ViewModel's state-machine handling can be tested without native code. */
private class FakeLlamaSession : LlamaSession {
    private val _state = MutableStateFlow<LlamaSession.State>(LlamaSession.State.Uninitialized)
    override val state: StateFlow<LlamaSession.State> = _state.asStateFlow()

    var loadModelCalls = 0
    var systemPromptCalls = 0

    fun moveTo(newState: LlamaSession.State) {
        _state.value = newState
    }

    override suspend fun loadModel(pathToModel: String) {
        loadModelCalls++
        _state.value = LlamaSession.State.ModelReady
    }

    override suspend fun setSystemPrompt(systemPrompt: String) {
        systemPromptCalls++
    }

    override fun sendUserPrompt(message: String, maxTokens: Int): Flow<String> =
        kotlinx.coroutines.flow.flow { emit("ok") }

    override fun cleanUp() {}

    override fun destroy() {}
}

class ChatViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun fakeModelRepository(): ModelRepository {
        val context: Context = mockk(relaxed = true)
        every { context.filesDir } returns java.io.File(System.getProperty("java.io.tmpdir"), "chatvm-test-${System.nanoTime()}").apply { mkdirs() }
        return ModelRepository(context)
    }

    @Test
    fun `model load is not skipped when native backend is still initializing at ViewModel creation`() = runTest {
        val llamaSession = FakeLlamaSession()
        val viewModel = ChatViewModel(ChatRepository(FakeChatMessageDao()), fakeModelRepository(), llamaSession)

        // Simulates the real race: the native backend's async init hasn't completed yet
        // when the ViewModel is constructed.
        assertTrue(llamaSession.state.value is LlamaSession.State.Uninitialized)
        llamaSession.moveTo(LlamaSession.State.Initializing)
        llamaSession.moveTo(LlamaSession.State.Initialized)

        assertEquals(1, llamaSession.loadModelCalls)
        assertEquals(1, llamaSession.systemPromptCalls)
        assertTrue(viewModel.sessionState.value is LlamaSession.State.ModelReady)
    }

    @Test
    fun `model load is skipped when backend reports an error instead of Initialized`() = runTest {
        val llamaSession = FakeLlamaSession()
        ChatViewModel(ChatRepository(FakeChatMessageDao()), fakeModelRepository(), llamaSession)

        llamaSession.moveTo(LlamaSession.State.Error(RuntimeException("native init failed")))

        assertEquals(0, llamaSession.loadModelCalls)
    }

    @Test
    fun `send button stays disabled until the model finishes loading`() = runTest {
        val llamaSession = FakeLlamaSession()
        val chatRepository = ChatRepository(FakeChatMessageDao())
        val viewModel = ChatViewModel(chatRepository, fakeModelRepository(), llamaSession)
        viewModel.onInputChange("hello")

        viewModel.sendMessage()
        assertEquals(0, chatRepository.observeMessages().first().size)

        llamaSession.moveTo(LlamaSession.State.Initialized)
        viewModel.sendMessage()

        val messages = chatRepository.observeMessages().first()
        assertEquals(listOf("hello" to MessageRole.USER, "ok" to MessageRole.ASSISTANT), messages.map { it.content to it.role })
    }
}
