package com.personalai.app.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalai.app.data.db.TaskEntity
import com.personalai.app.data.repository.TaskRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TasksViewModel(private val taskRepository: TaskRepository) : ViewModel() {

    val tasks: StateFlow<List<TaskEntity>> = taskRepository.observeTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setDone(id: Long, isDone: Boolean) {
        viewModelScope.launch { taskRepository.setDone(id, isDone) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { taskRepository.delete(id) }
    }
}
