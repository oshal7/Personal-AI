package com.personalai.app.ui.spaces

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalai.app.data.db.SpaceEntity
import com.personalai.app.data.repository.SpaceRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SpacesViewModel(private val spaceRepository: SpaceRepository) : ViewModel() {

    val spaces: StateFlow<List<SpaceEntity>> = spaceRepository.observeSpaces()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addSpace(name: String, skillFileName: String?, skillContent: String) {
        if (name.isBlank()) return
        viewModelScope.launch { spaceRepository.addSpace(name.trim(), skillFileName, skillContent) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { spaceRepository.deleteSpace(id) }
    }
}
