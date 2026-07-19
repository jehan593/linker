package com.linker.app.ui.savedlinks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linker.app.data.db.entity.SavedLinkEntity
import com.linker.app.data.repository.SavedLinksRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SavedLinksViewModel(
    private val savedLinksRepository: SavedLinksRepository
) : ViewModel() {

    val links: StateFlow<List<SavedLinkEntity>> = savedLinksRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(link: SavedLinkEntity) {
        viewModelScope.launch { savedLinksRepository.delete(link) }
    }
}
