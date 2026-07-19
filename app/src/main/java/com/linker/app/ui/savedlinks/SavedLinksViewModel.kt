package com.linker.app.ui.savedlinks

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linker.app.data.db.entity.SavedLinkEntity
import com.linker.app.data.repository.SavedLinksRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SavedLinksViewModel(
    private val savedLinksRepository: SavedLinksRepository
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")

    var searchText by mutableStateOf("")
        private set

    /** Plain substring match (case-insensitive) over the URL text — a saved-links list is small
     *  enough that this doesn't need DB-level search, and a URL substring already covers matching
     *  by domain since the domain is just part of that string. */
    val links: StateFlow<List<SavedLinkEntity>> =
        combine(savedLinksRepository.observeAll(), searchQuery) { all, query ->
            if (query.isBlank()) all else all.filter { it.url.contains(query, ignoreCase = true) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onSearchChanged(text: String) {
        searchText = text
        searchQuery.value = text
    }

    fun edit(link: SavedLinkEntity, newUrl: String) {
        viewModelScope.launch { savedLinksRepository.updateUrl(link, newUrl) }
    }

    fun delete(link: SavedLinkEntity) {
        viewModelScope.launch { savedLinksRepository.delete(link) }
    }
}
