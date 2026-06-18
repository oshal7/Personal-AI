package com.personalai.llama

import com.personalai.llama.LlamaSession.State
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Public API for running a local GGUF model via llama.cpp. A single instance manages
 * the lifecycle of one loaded model at a time (load -> chat -> unload).
 */
interface LlamaSession {

    val state: StateFlow<State>

    /** Loads a GGUF model from [pathToModel]. Throws [UnsupportedModelException] on failure. */
    suspend fun loadModel(pathToModel: String)

    /** Sets the system prompt. Must be called once, immediately after [loadModel]. */
    suspend fun setSystemPrompt(systemPrompt: String)

    /** Sends a user message and streams the assistant's reply token by token. */
    fun sendUserPrompt(message: String, maxTokens: Int = DEFAULT_MAX_TOKENS): Flow<String>

    /**
     * Cancels an in-flight [sendUserPrompt] generation after the current token finishes.
     * The model stays loaded and ready for the next prompt. No-op if nothing is generating.
     */
    fun stopGeneration()

    /** Unloads the current model and frees native resources, or clears an [State.Error]. */
    fun cleanUp()

    /** Releases the native backend entirely. Call when the session is no longer needed. */
    fun destroy()

    sealed class State {
        data object Uninitialized : State()
        data object Initializing : State()
        data object Initialized : State()
        data object LoadingModel : State()
        data object UnloadingModel : State()
        data object ModelReady : State()
        data object ProcessingSystemPrompt : State()
        data object ProcessingUserPrompt : State()
        data object Generating : State()
        data class Error(val exception: Exception) : State()
    }

    companion object {
        const val DEFAULT_MAX_TOKENS = 512
    }
}

val State.isBusy: Boolean
    get() = this is State.Initializing ||
        this is State.LoadingModel ||
        this is State.UnloadingModel ||
        this is State.ProcessingSystemPrompt ||
        this is State.ProcessingUserPrompt ||
        this is State.Generating

class UnsupportedModelException : Exception()
