package io.strlght.estee.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
) {

    val serverUrl: Flow<String> = dataStore.data
        .map { preferences -> preferences[KEY_SERVER_URL] ?: "" }

    suspend fun setServerUrl(url: String) {
        dataStore.edit { preferences ->
            preferences[KEY_SERVER_URL] = url.trim()
        }
    }

    val inferenceParams: Flow<InferenceParams> = dataStore.data
        .map { preferences ->
            val stored = preferences[KEY_INFERENCE_PARAMS]
            if (stored.isNullOrBlank()) {
                InferenceParams()
            } else {
                runCatching { json.decodeFromString<InferenceParams>(stored) }
                    .getOrElse { InferenceParams() }
            }
        }

    suspend fun setInferenceParams(params: InferenceParams) {
        dataStore.edit { preferences ->
            if (params == InferenceParams()) {
                preferences.remove(KEY_INFERENCE_PARAMS)
            } else {
                preferences[KEY_INFERENCE_PARAMS] = json.encodeToString(params)
            }
        }
    }

    private companion object {
        val KEY_SERVER_URL = stringPreferencesKey("server_url")
        val KEY_INFERENCE_PARAMS = stringPreferencesKey("inference_params")
    }
}
