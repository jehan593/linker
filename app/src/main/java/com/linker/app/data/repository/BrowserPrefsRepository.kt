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
 * Merges the live "what's installed" signal ([InstalledBrowsersRepository]) with the user's stored
 * per-browser overrides ([BrowserPrefDao]) into a single ordered list. A browser that has never
 * been interacted with has no DB row yet — it gets a default (visible, system label) and a
 * synthesized order index appended after every persisted one, computed fresh on every merge so
 * newly installed browsers consistently land at the end until the user reorders them.
 */
class BrowserPrefsRepository(
    private val browserPrefDao: BrowserPrefDao,
    private val installedBrowsersRepository: InstalledBrowsersRepository
) {

    // Bumped by the manual refresh button (ManageBrowsersScreen) so a re-scan can be forced even
    // when no pref has changed — e.g. the user installed or removed a browser while this screen
    // was already open and wants that reflected without waiting for the next natural re-emission.
    private val refreshTrigger = MutableStateFlow(0)

    /** Full list (including hidden entries) for the manage/settings screen, updates as prefs change. */
    fun observeManageList(): Flow<List<BrowserListItem>> =
        combine(browserPrefDao.observeAll(), refreshTrigger) { prefs, _ -> prefs }
            .map { prefs -> merge(installedBrowsersRepository.getBrowsers(forceRefresh = true), prefs) }

    fun refreshInstalledBrowsers() {
        refreshTrigger.value++
    }

    /** One-shot, hidden-filtered list for the link chooser — doesn't need to be reactive. */
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
     * Hiding or renaming one browser used to only materialize *that* browser's own row, leaving
     * its still-unpersisted siblings to get a fresh alphabetically-derived orderIndex next merge —
     * and since that synthesis starts counting from just after the *persisted* max, promoting one
     * sibling to a real row could shift where the counter starts and silently reshuffle everyone
     * else's relative order. [applyOrder] below fixes that by locking in the whole currently-shown
     * order first, so every hide/rename is order-neutral for the browsers it doesn't touch.
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
     * Persists every item's position in [orderedList] as its orderIndex — used both for a
     * drag-and-drop drop (see ManageBrowsersScreen) and, above, before any single hide/rename so
     * the full current order becomes permanent rather than partially synthesized.
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
