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
 * Enumerates every app that can resolve a bare https URL — the same signal Android itself uses to
 * decide what counts as a "browser" candidate. A short TTL cache avoids re-querying PackageManager
 * (which loads an icon per app) on every recomposition of the chooser screen; a newly installed
 * browser just takes up to [CACHE_TTL_MILLIS] to show up, which is an acceptable trade.
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
