package com.linker.app.interceptor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linker.app.data.repository.BrowserListItem
import com.linker.app.data.repository.BrowserPrefsRepository
import com.linker.app.data.repository.SavedLinksRepository
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

    var justSaved by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            browsers = browserPrefsRepository.loadVisibleBrowsers()
            loadingBrowsers = false
        }
    }

    fun onUrlEdited(newUrl: String) {
        editableUrl = newUrl
        justSaved = false
    }

    fun saveLink() {
        viewModelScope.launch {
            savedLinksRepository.save(editableUrl)
            justSaved = true
        }
    }
}
