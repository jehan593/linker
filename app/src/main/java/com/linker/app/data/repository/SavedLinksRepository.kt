package com.linker.app.data.repository

import com.linker.app.data.db.dao.SavedLinkDao
import com.linker.app.data.db.entity.SavedLinkEntity
import kotlinx.coroutines.flow.Flow

class SavedLinksRepository(private val savedLinkDao: SavedLinkDao) {

    fun observeAll(): Flow<List<SavedLinkEntity>> = savedLinkDao.observeAll()

    /** Same exact-match check as [save], so this agrees whether saving would bump or insert. */
    suspend fun isSaved(url: String): Boolean = savedLinkDao.findByUrl(url.trim()) != null

    /**
     * Saving a URL that's already saved (exact match after trimming) bumps its timestamp instead
     * of inserting a duplicate — re-saving a link keeps it "recently saved" without cluttering
     * the list with copies.
     */
    suspend fun save(url: String, savedAtMillis: Long = System.currentTimeMillis()) {
        val trimmed = url.trim()
        val existing = savedLinkDao.findByUrl(trimmed)
        if (existing != null) {
            savedLinkDao.update(existing.copy(savedAtMillis = savedAtMillis))
        } else {
            savedLinkDao.insert(SavedLinkEntity(url = trimmed, savedAtMillis = savedAtMillis))
        }
    }

    /** Edits the URL in place; keeps the original save time. */
    suspend fun updateUrl(link: SavedLinkEntity, newUrl: String) {
        savedLinkDao.update(link.copy(url = newUrl.trim()))
    }

    suspend fun delete(link: SavedLinkEntity) {
        savedLinkDao.delete(link)
    }
}
