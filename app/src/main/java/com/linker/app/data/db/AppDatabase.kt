package com.linker.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.linker.app.data.db.dao.BrowserPrefDao
import com.linker.app.data.db.dao.SavedLinkDao
import com.linker.app.data.db.entity.BrowserPrefEntity
import com.linker.app.data.db.entity.SavedLinkEntity

@Database(
    entities = [SavedLinkEntity::class, BrowserPrefEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun savedLinkDao(): SavedLinkDao
    abstract fun browserPrefDao(): BrowserPrefDao

    companion object {
        const val DATABASE_NAME = "linker.db"
    }
}
