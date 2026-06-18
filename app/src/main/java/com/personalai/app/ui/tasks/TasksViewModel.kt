package com.personalai.app.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalai.app.data.db.TaskEntity
import com.personalai.app.data.repository.TaskRepository
import com.personalai.app.reminder.AlarmScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TasksViewModel(
    private val taskRepository: TaskRepository,
    private val alarmScheduler: AlarmScheduler,
) : ViewModel() {

    val tasks: StateFlow<List<TaskEntity>> = taskRepository.observeTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun canScheduleExactAlarms(): Boolean = alarmScheduler.canScheduleExactAlarms()

    fun addTask(title: String, reminderAtMillis: Long?) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val id = taskRepository.addTask(title.trim(), sourceSessionId = 0L, reminderAtMillis = reminderAtMillis)
            reminderAtMillis?.let { alarmScheduler.schedule(id, title.trim(), it) }
        }
    }

    fun setDone(id: Long, isDone: Boolean) {
        viewModelScope.launch {
            taskRepository.setDone(id, isDone)
            if (isDone) alarmScheduler.cancel(id)
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            alarmScheduler.cancel(id)
            taskRepository.delete(id)
        }
    }
}
