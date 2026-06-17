package com.personalai.app.ui.chat

import android.content.Context
import com.personalai.app.data.db.ChatMessageDao
import com.personalai.app.data.db.ChatMessageEntity
import com.personalai.app.data.db.ChatSessionDao
import com.personalai.app.data.db.ChatSessionEntity
import com.personalai.app.data.db.MessageRole
import com.personalai.app.data.db.TaskDao
import com.personalai.app.data.db.TaskEntity
import com.personalai.app.data.repository.ChatRepository
import com.personalai.app.data.repository.ModelRepository
import com.personalai.app.data.repository.TaskRepository
import com.personalai.app.voice.SpeechInputManager
import com.personalai.llama.LlamaSession
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

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

private class FakeTaskDao : TaskDao {
    private val nextId = AtomicLong(1)
    private val state = MutableStateFlow<List<TaskEntity>>(emptyList())

    override fun observeAll(): Flow<List<TaskEntity>> = state

    override suspend fun insert(task: TaskEntity): Long {
        val id = nextId.getAndIncrement()
        state.value = state.value + task.copy(id = id)
        return id
    }

    override suspend fun setDone(id: Long, isDone: Boolean) {
        state.value = state.value.map { if (it.id == id) it.copy(isDone = isDone) else it }
    }

    override suspend fun delete(id: Long) {
        state.value = state.value.filterNot { it.id == id }
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

    private fun fakeChatRepository() = ChatRepository(FakeChatMessageDao(), FakeChatSessionDao())

    private fun fakeTaskRepository() = TaskRepository(FakeTaskDao())

    private fun fakeSpeechInputManager() = SpeechInputManager(mockk(relaxed = true))

    private fun viewModel(
        chatRepository: ChatRepository = fakeChatRepository(),
        llamaSession: LlamaSession = FakeLlamaSession(),
        taskRepository: TaskRepository = fakeTaskRepository(),
        initialSessionId: Long = 0L,
    ) = ChatViewModel(chatRepository, fakeModelRepository(), llamaSession, taskRepository, fakeSpeechInputManager(), initialSessionId)

    @Test
    fun `model load is not skipped when native backend is still initializing at ViewModel creation`() = runTest {
        val llamaSession = FakeLlamaSession()
        val viewModel = viewModel(llamaSession = llamaSession)

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
        viewModel(llamaSession = llamaSession)

        llamaSession.moveTo(LlamaSession.State.Error(RuntimeException("native init failed")))

        assertEquals(0, llamaSession.loadModelCalls)
    }

    @Test
    fun `send button stays disabled until the model finishes loading`() = runTest {
        val llamaSession = FakeLlamaSession()
        val chatRepository = fakeChatRepository()
        val viewModel = viewModel(chatRepository = chatRepository, llamaSession = llamaSession)
        viewModel.onInputChange("hello")

        viewModel.sendMessage()
        assertEquals(0L, viewModel.sessionId.value)

        llamaSession.moveTo(LlamaSession.State.Initialized)
        viewModel.onInputChange("hello")
        viewModel.sendMessage()

        val messages = viewModel.messages.first()
        assertEquals(listOf("hello" to MessageRole.USER, "ok" to MessageRole.ASSISTANT), messages.map { it.content to it.role })
    }

    @Test
    fun `sending the first message lazily creates a session instead of reusing the sentinel`() = runTest {
        val llamaSession = FakeLlamaSession()
        val viewModel = viewModel(llamaSession = llamaSession, initialSessionId = 0L)
        llamaSession.moveTo(LlamaSession.State.Initialized)

        assertEquals(0L, viewModel.sessionId.value)
        viewModel.onInputChange("first message")
        viewModel.sendMessage()

        assertTrue(viewModel.sessionId.value != 0L)
    }

    @Test
    fun `a task-like message surfaces a suggestion that is only saved on explicit confirmation`() = runTest {
        val llamaSession = FakeLlamaSession()
        val taskRepository = fakeTaskRepository()
        val viewModel = viewModel(llamaSession = llamaSession, taskRepository = taskRepository)
        llamaSession.moveTo(LlamaSession.State.Initialized)

        viewModel.onInputChange("remind me to call the dentist")
        viewModel.sendMessage()

        assertEquals("Call the dentist", viewModel.taskSuggestion.value)
        assertTrue(taskRepository.observeTasks().first().isEmpty())

        viewModel.confirmTaskSuggestion()

        assertNull(viewModel.taskSuggestion.value)
        assertEquals(listOf("Call the dentist"), taskRepository.observeTasks().first().map { it.title })
    }

    @Test
    fun `dismissing a task suggestion never saves it`() = runTest {
        val llamaSession = FakeLlamaSession()
        val taskRepository = fakeTaskRepository()
        val viewModel = viewModel(llamaSession = llamaSession, taskRepository = taskRepository)
        llamaSession.moveTo(LlamaSession.State.Initialized)

        viewModel.onInputChange("i need to buy milk")
        viewModel.sendMessage()
        viewModel.dismissTaskSuggestion()

        assertNull(viewModel.taskSuggestion.value)
        assertTrue(taskRepository.observeTasks().first().isEmpty())
    }
}
