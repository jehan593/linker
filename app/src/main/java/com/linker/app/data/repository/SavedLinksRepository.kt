package com.linker.app.data.repository

import com.linker.app.data.db.dao.SavedLinkDao
import com.linker.app.data.db.entity.SavedLinkEntity
import kotlinx.coroutines.flow.Flow

class SavedLinksRepository(private val savedLinkDao: SavedLinkDao) {

    fun observeAll(): Flow<List<SavedLinkEntity>> = savedLinkDao.observeAll()

    suspend fun save(url: String, savedAtMillis: Long = System.currentTimeMillis()) {
        savedLinkDao.insert(SavedLinkEntity(url = url, savedAtMillis = savedAtMillis))
    }

    suspend fun delete(link: SavedLinkEntity) {
        savedLinkDao.delete(link)
    }
}
