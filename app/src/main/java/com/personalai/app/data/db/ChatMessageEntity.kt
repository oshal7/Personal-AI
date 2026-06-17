package com.personalai.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MessageRole { USER, ASSISTANT }

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: MessageRole,
    val content: String,
    val timestampMillis: Long,
)
