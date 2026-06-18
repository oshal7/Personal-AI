package com.personalai.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Denormalized so the History list can render title/preview/time without a join against
 * chat_messages — [lastMessagePreview] and [updatedAtMillis] are refreshed by the repository
 * every time a message is added to the session.
 */
@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val lastMessagePreview: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val spaceId: Long? = null,
)
