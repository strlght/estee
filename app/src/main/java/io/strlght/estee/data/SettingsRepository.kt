package io.strlght.estee.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    val serverUrl: Flow<String> = context.settingsDataStore.data
        .map { preferences -> preferences[KEY_SERVER_URL] ?: "" }

    suspend fun setServerUrl(url: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_SERVER_URL] = url.trim()
        }
    }

    val inferenceParams: Flow<InferenceParams> = context.settingsDataStore.data
        .map { preferences ->
            val stored = preferences[KEY_INFERENCE_PARAMS]
            if (stored.isNullOrBlank()) {
                InferenceParams()
            } else {
                runCatching { ParamsJson.decodeFromString<InferenceParams>(stored) }
                    .getOrElse { InferenceParams() }
            }
        }

    suspend fun setInferenceParams(params: InferenceParams) {
        context.settingsDataStore.edit { preferences ->
            if (params == InferenceParams()) {
                preferences.remove(KEY_INFERENCE_PARAMS)
            } else {
                preferences[KEY_INFERENCE_PARAMS] = ParamsJson.encodeToString(params)
            }
        }
    }

    private companion object {
        val KEY_SERVER_URL = stringPreferencesKey("server_url")
        val KEY_INFERENCE_PARAMS = stringPreferencesKey("inference_params")
    }
}
