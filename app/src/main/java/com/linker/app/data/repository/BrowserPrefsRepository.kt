package com.linker.app.data.repository

import android.graphics.drawable.Drawable
import com.linker.app.data.browser.BrowserInfo
import com.linker.app.data.browser.InstalledBrowsersRepository
import com.linker.app.data.db.dao.BrowserPrefDao
import com.linker.app.data.db.entity.BrowserPrefEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class BrowserListItem(
    val packageName: String,
    val systemLabel: String,
    val displayLabel: String,
    val icon: Drawable,
    val hidden: Boolean,
    val orderIndex: Int
)

/**
 * Merges what's installed ([InstalledBrowsersRepository]) with the user's stored per-browser
 * settings (hidden state, custom name, order) into one ordered list. Browsers the user has never
 * touched get default settings and land at the end; newly installed ones follow the same path.
 */
class BrowserPrefsRepository(
    private val browserPrefDao: BrowserPrefDao,
    private val installedBrowsersRepository: InstalledBrowsersRepository
) {

    // Refresh button forces a re-scan even when no pref changed (e.g. a browser was installed
    // while the manage screen was already open).
    private val refreshTrigger = MutableStateFlow(0)

    /** Full list (hidden and visible) for the manage screen; updates as prefs change. */
    fun observeManageList(): Flow<List<BrowserListItem>> =
        combine(browserPrefDao.observeAll(), refreshTrigger) { prefs, _ -> prefs }
            .map { prefs -> merge(installedBrowsersRepository.getBrowsers(forceRefresh = true), prefs) }

    fun refreshInstalledBrowsers() {
        refreshTrigger.value++
    }

    /** One-shot, hidden-filtered list for the link chooser — no reactivity needed. */
    suspend fun loadVisibleBrowsers(): List<BrowserListItem> {
        val prefs = browserPrefDao.observeAll().first()
        return merge(installedBrowsersRepository.getBrowsers(), prefs).filterNot { it.hidden }
    }

    private fun merge(browsers: List<BrowserInfo>, prefs: List<BrowserPrefEntity>): List<BrowserListItem> {
        val prefsByPackage = prefs.associateBy { it.packageName }
        var nextOrder = (prefs.maxOfOrNull { it.orderIndex } ?: -1) + 1
        return browsers.map { info ->
            val pref = prefsByPackage[info.packageName]
            val orderIndex = pref?.orderIndex ?: nextOrder++
            BrowserListItem(
                packageName = info.packageName,
                systemLabel = info.systemLabel,
                displayLabel = pref?.customLabel?.takeIf { it.isNotBlank() } ?: info.systemLabel,
                icon = info.icon,
                hidden = pref?.hidden ?: false,
                orderIndex = orderIndex
            )
        }.sortedBy { it.orderIndex }
    }

    /**
     * Locks in the full current order before any hide/rename, so touching one browser never
     * silently reshuffles the ones it doesn't touch.
     */
    suspend fun setHidden(currentList: List<BrowserListItem>, packageName: String, hidden: Boolean) {
        applyOrder(currentList)
        val pref = browserPrefDao.observeAll().first().first { it.packageName == packageName }
        browserPrefDao.upsert(pref.copy(hidden = hidden))
    }

    suspend fun setCustomLabel(currentList: List<BrowserListItem>, packageName: String, label: String?) {
        applyOrder(currentList)
        val pref = browserPrefDao.observeAll().first().first { it.packageName == packageName }
        browserPrefDao.upsert(pref.copy(customLabel = label?.takeIf { it.isNotBlank() }))
    }

    /**
     * Saves the current order as each item's orderIndex — used after a drag-and-drop
     * reorder, and by hide/rename above.
     */
    suspend fun applyOrder(orderedList: List<BrowserListItem>) {
        val prefsByPackage = browserPrefDao.observeAll().first().associateBy { it.packageName }
        val updated = orderedList.mapIndexed { index, item ->
            (prefsByPackage[item.packageName] ?: BrowserPrefEntity(packageName = item.packageName))
                .copy(orderIndex = index)
        }
        browserPrefDao.upsertAll(updated)
    }
}
