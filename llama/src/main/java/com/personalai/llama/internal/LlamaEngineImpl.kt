package com.personalai.llama.internal

import android.content.Context
import android.util.Log
import com.personalai.llama.LlamaSession
import com.personalai.llama.LlamaSession.State
import com.personalai.llama.UnsupportedModelException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * JNI-backed [LlamaSession]. All native calls run on a single-threaded dispatcher
 * because the underlying llama.cpp context is not safe for concurrent access.
 *
 * @see llama_jni.cpp for the native implementation.
 */
internal class LlamaEngineImpl private constructor() : LlamaSession {

    companion object {
        private const val TAG = "LlamaEngineImpl"

        @Volatile
        private var instance: LlamaSession? = null

        internal fun getInstance(context: Context): LlamaSession =
            instance ?: synchronized(this) {
                instance ?: LlamaEngineImpl().also { instance = it }
            }
    }

    private external fun init()
    private external fun load(modelPath: String): Int
    private external fun prepare(): Int
    private external fun processSystemPrompt(systemPrompt: String): Int
    private external fun processUserPrompt(userPrompt: String, maxTokens: Int): Int
    private external fun generateNextToken(): String?
    private external fun unload()
    private external fun shutdown()

    private val _state = MutableStateFlow<State>(State.Uninitialized)
    override val state: StateFlow<State> = _state.asStateFlow()

    @Volatile
    private var cancelGeneration = false

    @OptIn(ExperimentalCoroutinesApi::class)
    private val llamaDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val llamaScope = CoroutineScope(llamaDispatcher + SupervisorJob())

    init {
        llamaScope.launch {
            check(_state.value is State.Uninitialized) { "Already initialized" }
            _state.value = State.Initializing
            System.loadLibrary("personalai_llama")
            init()
            _state.value = State.Initialized
            Log.i(TAG, "Native llama backend ready")
        }
    }

    override suspend fun loadModel(pathToModel: String) = withContext(llamaDispatcher) {
        check(_state.value is State.Initialized) {
            "Cannot load model in state ${_state.value}"
        }
        try {
            File(pathToModel).let {
                require(it.exists() && it.isFile && it.canRead()) { "Model file not accessible: $pathToModel" }
            }
            _state.value = State.LoadingModel
            if (load(pathToModel) != 0) throw UnsupportedModelException()
            if (prepare() != 0) throw IOException("Failed to prepare inference context")
            cancelGeneration = false
            _state.value = State.ModelReady
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model: $pathToModel", e)
            _state.value = State.Error(e)
            throw e
        }
    }

    override suspend fun setSystemPrompt(systemPrompt: String) = withContext(llamaDispatcher) {
        check(_state.value is State.ModelReady) { "Cannot set system prompt in state ${_state.value}" }
        _state.value = State.ProcessingSystemPrompt
        if (processSystemPrompt(systemPrompt) != 0) {
            val e = RuntimeException("Failed to process system prompt")
            _state.value = State.Error(e)
            throw e
        }
        _state.value = State.ModelReady
    }

    override fun sendUserPrompt(message: String, maxTokens: Int): Flow<String> = flow {
        require(message.isNotBlank()) { "Cannot send an empty message" }
        check(_state.value is State.ModelReady) { "Cannot send a message in state ${_state.value}" }

        try {
            _state.value = State.ProcessingUserPrompt
            if (processUserPrompt(message, maxTokens) != 0) {
                Log.e(TAG, "Failed to process user prompt")
                _state.value = State.ModelReady
                return@flow
            }

            _state.value = State.Generating
            while (!cancelGeneration) {
                val token = generateNextToken() ?: break
                if (token.isNotEmpty()) emit(token)
            }
            _state.value = State.ModelReady
        } catch (e: CancellationException) {
            _state.value = State.ModelReady
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error during generation", e)
            _state.value = State.Error(e)
            throw e
        }
    }.flowOn(llamaDispatcher)

    override fun cleanUp() {
        cancelGeneration = true
        runBlocking(llamaDispatcher) {
            when (val s = _state.value) {
                is State.ModelReady -> {
                    _state.value = State.UnloadingModel
                    unload()
                    _state.value = State.Initialized
                }
                is State.Error -> _state.value = State.Initialized
                else -> Log.w(TAG, "cleanUp() called in state $s; ignoring")
            }
        }
    }

    override fun destroy() {
        cancelGeneration = true
        runBlocking(llamaDispatcher) {
            when (_state.value) {
                is State.Uninitialized -> {}
                is State.Initialized -> shutdown()
                else -> { unload(); shutdown() }
            }
        }
        llamaScope.cancel()
    }
}
