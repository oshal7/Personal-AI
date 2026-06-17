package com.personalai.app

import android.app.Application
import com.personalai.app.data.db.AppDatabase
import com.personalai.app.data.repository.ChatRepository
import com.personalai.app.data.repository.ModelRepository
import com.personalai.llama.LlamaBridge

class PersonalAiApplication : Application() {

    val database by lazy { AppDatabase.create(this) }
    val chatRepository by lazy { ChatRepository(database.chatMessageDao()) }
    val modelRepository by lazy { ModelRepository(applicationContext) }
    val llamaSession by lazy { LlamaBridge.getSession(applicationContext) }
}
