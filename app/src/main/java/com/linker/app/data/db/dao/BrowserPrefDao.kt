package com.linker.app.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.linker.app.data.db.entity.BrowserPrefEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BrowserPrefDao {
    @Query("SELECT * FROM browser_prefs")
    fun observeAll(): Flow<List<BrowserPrefEntity>>

    @Query("SELECT MAX(orderIndex) FROM browser_prefs")
    suspend fun maxOrderIndex(): Int?

    @Upsert
    suspend fun upsert(pref: BrowserPrefEntity)

    @Upsert
    suspend fun upsertAll(prefs: List<BrowserPrefEntity>)
}
