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

    /** Called once a drag reorder gesture is dropped, with the full list in its new order. */
    fun persistOrder(orderedList: List<BrowserListItem>) {
        viewModelScope.launch { browserPrefsRepository.applyOrder(orderedList) }
    }

    /** Forces a fresh PackageManager scan — see the refresh button in ManageBrowsersScreen. */
    fun refresh() {
        browserPrefsRepository.refreshInstalledBrowsers()
    }
}
