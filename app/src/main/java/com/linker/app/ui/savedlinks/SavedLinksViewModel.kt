package com.linker.app.ui.savedlinks

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linker.app.data.db.entity.SavedLinkEntity
import com.linker.app.data.repository.NotesnookRepository
import com.linker.app.data.repository.SavedLinksRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SavedLinksViewModel(
    private val savedLinksRepository: SavedLinksRepository,
    private val notesnookRepository: NotesnookRepository
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")

    var searchText by mutableStateOf("")
        private set

    /** Case-insensitive substring match over the URL text — simple and fast enough for a personal list. */
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

    private val _toastMessages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toastMessages: SharedFlow<String> = _toastMessages.asSharedFlow()

    fun sendToNotesnook(link: SavedLinkEntity) {
        viewModelScope.launch {
            val result = notesnookRepository.sendLink(link.url)
            _toastMessages.tryEmit(
                when {
                    result.isSuccess -> "Sent to Notesnook"
                    result.exceptionOrNull() is IllegalStateException -> "Set a Notesnook API key in settings first"
                    else -> "Send failed"
                }
            )
        }
    }
}
