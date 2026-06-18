package com.personalai.app

import android.app.Application
import com.personalai.app.data.db.AppDatabase
import com.personalai.app.data.repository.ChatRepository
import com.personalai.app.data.repository.ModelRepository
import com.personalai.app.data.repository.TaskRepository
import com.personalai.app.voice.SpeechInputManager
import com.personalai.app.voice.TtsManager
import com.personalai.llama.LlamaBridge

class PersonalAiApplication : Application() {

    val database by lazy { AppDatabase.create(this) }
    val chatRepository by lazy { ChatRepository(database.chatMessageDao(), database.chatSessionDao()) }
    val taskRepository by lazy { TaskRepository(database.taskDao()) }
    val modelRepository by lazy { ModelRepository(applicationContext) }
    val llamaSession by lazy { LlamaBridge.getSession(applicationContext) }
    val speechInputManager by lazy { SpeechInputManager(applicationContext) }
    val ttsManager by lazy { TtsManager(applicationContext) }
}
