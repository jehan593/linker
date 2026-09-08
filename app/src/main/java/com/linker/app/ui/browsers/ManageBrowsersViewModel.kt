package com.linker.app.ui.browsers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linker.app.data.repository.BrowserListItem
import com.linker.app.data.repository.BrowserPrefsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ManageBrowsersViewModel(
    private val browserPrefsRepository: BrowserPrefsRepository
) : ViewModel() {

    val browsers: StateFlow<List<BrowserListItem>> = browserPrefsRepository.observeManageList()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleHidden(browser: BrowserListItem) {
        viewModelScope.launch {
            browserPrefsRepository.setHidden(browsers.value, browser.packageName, !browser.hidden)
        }
    }

    fun rename(browser: BrowserListItem, newLabel: String) {
        viewModelScope.launch {
            browserPrefsRepository.setCustomLabel(browsers.value, browser.packageName, newLabel)
        }
    }

    fun resetLabel(browser: BrowserListItem) {
        viewModelScope.launch {
            browserPrefsRepository.setCustomLabel(browsers.value, browser.packageName, null)
        }
    }

    /** Saves the order after a drag is dropped. */
    fun persistOrder(orderedList: List<BrowserListItem>) {
        viewModelScope.launch { browserPrefsRepository.applyOrder(orderedList) }
    }

    /** Forces a fresh scan of installed browsers (manage screen refresh button). */
    fun refresh() {
        browserPrefsRepository.refreshInstalledBrowsers()
    }
}
