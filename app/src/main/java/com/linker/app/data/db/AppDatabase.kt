package com.linker.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.linker.app.data.db.dao.BrowserPrefDao
import com.linker.app.data.db.dao.SavedLinkDao
import com.linker.app.data.db.entity.BrowserPrefEntity
import com.linker.app.data.db.entity.SavedLinkEntity

@Database(
    entities = [SavedLinkEntity::class, BrowserPrefEntity::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun savedLinkDao(): SavedLinkDao
    abstract fun browserPrefDao(): BrowserPrefDao

    companion object {
        const val DATABASE_NAME = "linker.db"

        /** v1 -> v2: track when a browser was hidden so the hidden section can order by recency. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE browser_prefs ADD COLUMN hiddenAtMillis INTEGER")
            }
        }
    }
}
