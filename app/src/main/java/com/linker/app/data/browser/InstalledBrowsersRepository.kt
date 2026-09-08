package com.linker.app.data.browser

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class BrowserInfo(
    val packageName: String,
    val systemLabel: String,
    val icon: Drawable
)

/**
 * Finds every app that resolves a bare https URL — the same signal Android uses when deciding
 * what counts as a browser. A short-lived cache avoids re-loading every app's icon on each
 * recomposition of the chooser; a newly installed browser shows up within [CACHE_TTL_MILLIS].
 */
class InstalledBrowsersRepository(private val packageManager: PackageManager, private val selfPackage: String) {

    private val mutex = Mutex()
    private var cache: List<BrowserInfo>? = null
    private var cachedAtMillis: Long = 0L

    suspend fun getBrowsers(forceRefresh: Boolean = false): List<BrowserInfo> = mutex.withLock {
        val cached = cache
        val now = System.currentTimeMillis()
        if (!forceRefresh && cached != null && now - cachedAtMillis < CACHE_TTL_MILLIS) {
            return@withLock cached
        }
        val fresh = queryBrowsers()
        cache = fresh
        cachedAtMillis = now
        fresh
    }

    private suspend fun queryBrowsers(): List<BrowserInfo> = withContext(Dispatchers.Default) {
        val probeIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
        packageManager.queryIntentActivities(probeIntent, PackageManager.MATCH_ALL)
            .asSequence()
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != selfPackage }
            .map { appInfo ->
                BrowserInfo(
                    packageName = appInfo.packageName,
                    systemLabel = appInfo.loadLabel(packageManager).toString(),
                    icon = appInfo.loadIcon(packageManager)
                )
            }
            .sortedBy { it.systemLabel.lowercase() }
            .toList()
    }

    companion object {
        private const val CACHE_TTL_MILLIS = 5 * 60 * 1000L
    }
}
