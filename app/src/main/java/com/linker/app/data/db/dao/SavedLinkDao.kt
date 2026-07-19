package com.linker.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.linker.app.data.db.entity.SavedLinkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedLinkDao {
    @Query("SELECT * FROM saved_links ORDER BY savedAtMillis DESC")
    fun observeAll(): Flow<List<SavedLinkEntity>>

    @Query("SELECT * FROM saved_links WHERE url = :url LIMIT 1")
    suspend fun findByUrl(url: String): SavedLinkEntity?

    @Insert
    suspend fun insert(link: SavedLinkEntity): Long

    @Update
    suspend fun update(link: SavedLinkEntity)

    @Delete
    suspend fun delete(link: SavedLinkEntity)
}
