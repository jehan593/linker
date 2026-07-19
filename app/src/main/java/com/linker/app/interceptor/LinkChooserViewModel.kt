package com.linker.app.interceptor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linker.app.data.repository.BrowserListItem
import com.linker.app.data.repository.BrowserPrefsRepository
import com.linker.app.data.repository.NotesnookRepository
import com.linker.app.data.repository.SavedLinksRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class LinkChooserViewModel(
    initialUrl: String,
    private val browserPrefsRepository: BrowserPrefsRepository,
    private val savedLinksRepository: SavedLinksRepository,
    private val notesnookRepository: NotesnookRepository
) : ViewModel() {

    var editableUrl by mutableStateOf(initialUrl)
        private set

    var browsers by mutableStateOf<List<BrowserListItem>>(emptyList())
        private set

    var loadingBrowsers by mutableStateOf(true)
        private set

    // Reflects whether editableUrl is already a saved link (pre-existing or just saved this
    // session) — not just "was the save button tapped" — so the chooser's bookmark icon can look
    // "already saved, tap to bump timestamp" even for a URL that arrived already saved.
    var isAlreadySaved by mutableStateOf(false)
        private set

    var isSending by mutableStateOf(false)
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

    fun saveLink() {
        viewModelScope.launch {
            savedLinksRepository.save(editableUrl)
            isAlreadySaved = true
        }
    }

    // Cancels any in-flight check so a burst of edits can't have an earlier (now-stale) lookup
    // overwrite the result of a later one.
    private fun checkAlreadySaved(url: String) {
        savedCheckJob?.cancel()
        savedCheckJob = viewModelScope.launch {
            isAlreadySaved = savedLinksRepository.isSaved(url)
        }
    }

    fun sendToNotesnook() {
        val url = editableUrl
        if (url.isBlank()) {
            _toastMessages.tryEmit("Nothing to send")
            return
        }
        viewModelScope.launch {
            isSending = true
            val result = notesnookRepository.sendLink(url)
            isSending = false
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
