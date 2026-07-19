package com.linker.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.linker.app.data.remote.NotesnookApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class NotesnookSettings(val apiKey: String?, val tagId: String?)

class NotesnookRepository(private val dataStore: DataStore<Preferences>) {
    companion object {
        val NOTESNOOK_API_KEY = stringPreferencesKey("notesnook_api_key")
        val NOTESNOOK_TAG_ID = stringPreferencesKey("notesnook_tag_id")
    }

    val settingsFlow: Flow<NotesnookSettings> = dataStore.data.map {
        NotesnookSettings(it[NOTESNOOK_API_KEY], it[NOTESNOOK_TAG_ID])
    }

    suspend fun saveSettings(apiKey: String, tagId: String) {
        dataStore.edit {
            if (apiKey.isBlank()) it.remove(NOTESNOOK_API_KEY) else it[NOTESNOOK_API_KEY] = apiKey
            if (tagId.isBlank()) it.remove(NOTESNOOK_TAG_ID) else it[NOTESNOOK_TAG_ID] = tagId
        }
    }

    suspend fun sendLink(url: String): Result<Unit> {
        val settings = settingsFlow.first()
        val apiKey = settings.apiKey
        if (apiKey.isNullOrBlank()) {
            return Result.failure(IllegalStateException("No API key set"))
        }
        return NotesnookApi.sendLink(apiKey, url, settings.tagId)
    }
}
