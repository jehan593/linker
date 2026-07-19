package com.linker.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.linker.app.data.db.entity.SavedLinkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedLinkDao {
    @Query("SELECT * FROM saved_links ORDER BY savedAtMillis DESC")
    fun observeAll(): Flow<List<SavedLinkEntity>>

    @Insert
    suspend fun insert(link: SavedLinkEntity): Long

    @Delete
    suspend fun delete(link: SavedLinkEntity)
}
