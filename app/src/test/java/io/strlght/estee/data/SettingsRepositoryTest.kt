package io.strlght.estee.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.strlght.estee.testing.TestJson
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files

private fun testDataStore(scope: CoroutineScope): Pair<DataStore<Preferences>, SettingsRepository> {
    val dir = Files.createTempDirectory("estee-settings").toFile().apply { deleteOnExit() }
    val dataStore = PreferenceDataStoreFactory.create(scope = scope) {
        File(dir, "settings.preferences_pb")
    }
    return dataStore to SettingsRepository(dataStore, TestJson)
}

private val KEY_SERVER_URL = stringPreferencesKey("server_url")
private val KEY_INFERENCE_PARAMS = stringPreferencesKey("inference_params")

class SettingsRepositoryTest : BehaviorSpec({

    given("an empty store serving server url") {
        then("emits empty string") {
            runTest {
                val (_, repo) = testDataStore(backgroundScope)

                repo.serverUrl.first() shouldBe ""
            }
        }
    }

    given("a saved url with surrounding whitespace") {
        then("persists the trimmed value") {
            runTest {
                val (_, repo) = testDataStore(backgroundScope)

                repo.setServerUrl("  http://192.168.1.5:8080/inference  ")

                repo.serverUrl.first() shouldBe "http://192.168.1.5:8080/inference"
            }
        }
    }

    given("an empty store serving inference params") {
        then("emits default params") {
            runTest {
                val (_, repo) = testDataStore(backgroundScope)

                repo.inferenceParams.first() shouldBe InferenceParams()
            }
        }
    }

    given("saved non-default params") {
        then("round-trips through storage") {
            runTest {
                val (dataStore, repo) = testDataStore(backgroundScope)
                val params = InferenceParams(language = "ja", bestOf = 4, vad = true)

                repo.setInferenceParams(params)

                repo.inferenceParams.first() shouldBe params
                dataStore.data.first()[KEY_INFERENCE_PARAMS] shouldContain "best_of"
            }
        }
    }

    given("defaults saved over custom params") {
        then("removes the stored key") {
            runTest {
                val (dataStore, repo) = testDataStore(backgroundScope)

                repo.setInferenceParams(InferenceParams(language = "ko"))
                repo.setInferenceParams(InferenceParams())

                dataStore.data.first()[KEY_INFERENCE_PARAMS] shouldBe null
                repo.inferenceParams.first() shouldBe InferenceParams()
            }
        }
    }

    given("a corrupt stored payload") {
        then("falls back to default params") {
            runTest {
                val (dataStore, repo) = testDataStore(backgroundScope)
                dataStore.edit { it[KEY_INFERENCE_PARAMS] = "{not valid json" }

                repo.inferenceParams.first() shouldBe InferenceParams()
            }
        }
    }

    given("a blank stored payload") {
        then("falls back to default params") {
            runTest {
                val (dataStore, repo) = testDataStore(backgroundScope)
                dataStore.edit { it[KEY_INFERENCE_PARAMS] = "   " }

                repo.inferenceParams.first() shouldBe InferenceParams()
            }
        }
    }
})
