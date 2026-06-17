package com.personalai.app.data.repository

import com.personalai.app.data.db.TaskDao
import com.personalai.app.data.db.TaskEntity
import kotlinx.coroutines.flow.Flow

class TaskRepository(private val dao: TaskDao) {

    fun observeTasks(): Flow<List<TaskEntity>> = dao.observeAll()

    suspend fun addTask(title: String, sourceSessionId: Long) {
        dao.insert(
            TaskEntity(
                title = title,
                sourceSessionId = sourceSessionId,
                createdAtMillis = System.currentTimeMillis(),
            )
        )
    }

    suspend fun setDone(id: Long, isDone: Boolean) = dao.setDone(id, isDone)

    suspend fun delete(id: Long) = dao.delete(id)
}
