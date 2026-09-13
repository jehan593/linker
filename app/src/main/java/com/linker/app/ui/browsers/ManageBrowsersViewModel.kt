package com.linker.app.ui.browsers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linker.app.data.repository.BrowserListItem
import com.linker.app.data.repository.BrowserPrefsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ManageBrowsersViewModel(
    private val browserPrefsRepository: BrowserPrefsRepository
) : ViewModel() {

    // The list starts empty until the first scan runs, so the screen shows a spinner (not the
    // "no browsers" empty state) while that first scan is in flight.
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val browsers: StateFlow<List<BrowserListItem>> = browserPrefsRepository.observeManageList()
        .onEach { _isLoading.value = false }
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
