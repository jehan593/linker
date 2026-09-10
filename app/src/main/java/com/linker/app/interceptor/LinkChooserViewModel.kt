package com.linker.app.interceptor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linker.app.data.repository.BrowserListItem
import com.linker.app.data.repository.BrowserPrefsRepository
import com.linker.app.data.repository.SavedLinksRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class LinkChooserViewModel(
    initialUrl: String,
    private val browserPrefsRepository: BrowserPrefsRepository,
    private val savedLinksRepository: SavedLinksRepository
) : ViewModel() {

    var editableUrl by mutableStateOf(initialUrl)
        private set

    var browsers by mutableStateOf<List<BrowserListItem>>(emptyList())
        private set

    var loadingBrowsers by mutableStateOf(true)
        private set

    // True when the current URL is already saved (from before or just now), so the bookmark
    // icon flips to filled — "already saved, tap to refresh" instead of "save this".
    var isAlreadySaved by mutableStateOf(false)
        private set

    private val _toastMessages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toastMessages: SharedFlow<String> = _toastMessages.asSharedFlow()

    private var savedCheckJob: Job? = null

    init {
        viewModelScope.launch {
            browsers = browserPrefsRepository.loadVisibleBrowsers()
            loadingBrowsers = false
        }
        checkAlreadySaved(initialUrl)
    }

    fun onUrlEdited(newUrl: String) {
        editableUrl = newUrl
        checkAlreadySaved(newUrl)
    }

    fun toggleSaveLink() {
        viewModelScope.launch {
            if (isAlreadySaved) {
                savedLinksRepository.deleteByUrl(editableUrl)
                isAlreadySaved = false
            } else {
                savedLinksRepository.save(editableUrl)
                isAlreadySaved = true
            }
        }
    }

    // Cancels in-flight lookups so a burst of edits can't let a stale result win.
    private fun checkAlreadySaved(url: String) {
        savedCheckJob?.cancel()
        savedCheckJob = viewModelScope.launch {
            isAlreadySaved = savedLinksRepository.isSaved(url)
        }
    }
}
