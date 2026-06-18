package com.personalai.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A named context with an optional attached skill file whose content is injected into chats started inside it. */
@Entity(tableName = "spaces")
data class SpaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val skillFileName: String?,
    val skillContent: String,
    val createdAtMillis: Long,
)
