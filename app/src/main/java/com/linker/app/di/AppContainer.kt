package com.linker.app.di

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.linker.app.data.browser.InstalledBrowsersRepository
import com.linker.app.data.db.AppDatabase
import com.linker.app.data.repository.BrowserPrefsRepository
import com.linker.app.data.repository.NotesnookRepository
import com.linker.app.data.repository.SavedLinksRepository

private val Context.dataStore by preferencesDataStore(name = "linker_settings")

interface AppContainer {
    val database: AppDatabase
    val installedBrowsersRepository: InstalledBrowsersRepository
    val browserPrefsRepository: BrowserPrefsRepository
    val savedLinksRepository: SavedLinksRepository
    val notesnookRepository: NotesnookRepository
}

class DefaultAppContainer(private val context: Context) : AppContainer {

    override val database: AppDatabase by lazy {
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .fallbackToDestructiveMigration()
            .build()
    }

    override val installedBrowsersRepository: InstalledBrowsersRepository by lazy {
        InstalledBrowsersRepository(context.packageManager, context.packageName)
    }

    override val browserPrefsRepository: BrowserPrefsRepository by lazy {
        BrowserPrefsRepository(database.browserPrefDao(), installedBrowsersRepository)
    }

    override val savedLinksRepository: SavedLinksRepository by lazy {
        SavedLinksRepository(database.savedLinkDao())
    }

    override val notesnookRepository: NotesnookRepository by lazy {
        NotesnookRepository(context.dataStore)
    }
}
