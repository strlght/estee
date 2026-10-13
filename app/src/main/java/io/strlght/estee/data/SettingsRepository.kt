package io.strlght.estee.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    val serverUrl: Flow<String> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_SERVER_URL] ?: "" }

    suspend fun setServerUrl(url: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_SERVER_URL] = url.trim()
        }
    }

    private companion object {
        val KEY_SERVER_URL = stringPreferencesKey("server_url")
    }
}
