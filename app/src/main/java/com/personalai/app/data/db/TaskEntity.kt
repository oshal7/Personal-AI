package com.personalai.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val sourceSessionId: Long,
    val isDone: Boolean = false,
    val createdAtMillis: Long,
)
