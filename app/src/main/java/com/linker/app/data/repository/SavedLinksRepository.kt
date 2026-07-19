package com.linker.app.data.repository

import com.linker.app.data.db.dao.SavedLinkDao
import com.linker.app.data.db.entity.SavedLinkEntity
import kotlinx.coroutines.flow.Flow

class SavedLinksRepository(private val savedLinkDao: SavedLinkDao) {

    fun observeAll(): Flow<List<SavedLinkEntity>> = savedLinkDao.observeAll()

    /**
     * Saving a URL that's already saved (exact match, after trimming) bumps its existing row's
     * timestamp instead of inserting a duplicate — repeatedly saving the same link is meant to
     * keep it "recently saved" and bring it back to the top, not clutter the list with copies of
     * itself. Comparison is a plain string match (no scheme/host normalization) so behavior stays
     * simple and predictable: two URLs are "the same" only if their text is identical.
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

    /** Edits an existing saved link's URL text in place — keeps its original savedAtMillis, since
     *  correcting a link isn't the same event as (re-)saving it. */
    suspend fun updateUrl(link: SavedLinkEntity, newUrl: String) {
        savedLinkDao.update(link.copy(url = newUrl.trim()))
    }

    suspend fun delete(link: SavedLinkEntity) {
        savedLinkDao.delete(link)
    }
}
