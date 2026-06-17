package com.personalai.llama

import android.content.Context
import com.personalai.llama.internal.LlamaEngineImpl

/** Entry point for obtaining the app-wide [LlamaSession] singleton. */
object LlamaBridge {
    fun getSession(context: Context): LlamaSession = LlamaEngineImpl.getInstance(context)
}
