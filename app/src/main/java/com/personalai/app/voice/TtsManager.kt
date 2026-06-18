package com.personalai.app.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Wraps the system [TextToSpeech] engine for on-device speech output; never touches the network itself. */
class TtsManager(context: Context) {

    sealed class State {
        data object Idle : State()
        data object Speaking : State()
        data class Error(val message: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    @Volatile
    private var ready = false

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (!ready) _state.value = State.Error("Text-to-speech isn't available on this device")
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _state.value = State.Speaking
            }

            override fun onDone(utteranceId: String?) {
                _state.value = State.Idle
            }

            @Deprecated("Required override of the deprecated single-arg onError")
            override fun onError(utteranceId: String?) {
                _state.value = State.Error("Speech playback failed")
            }
        })
    }

    fun speak(text: String) {
        if (!ready || text.isBlank()) return
        tts.language = Locale.getDefault()
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }

    fun stop() {
        tts.stop()
        _state.value = State.Idle
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
