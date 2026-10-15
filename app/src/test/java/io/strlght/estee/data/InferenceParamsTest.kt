package io.strlght.estee.data

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.maps.shouldContainAll
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.strlght.estee.testing.TestJson
import okhttp3.RequestBody
import okio.Buffer

private fun RequestBody.text(): String = Buffer().also { writeTo(it) }.readUtf8()

class InferenceParamsTest : StringSpec({

    "default params produce no form parts" {
        InferenceParams().toFormParts(TestJson) shouldBe emptyMap()
    }

    "default params produce no editable values" {
        InferenceParams().toEditableValues(TestJson) shouldBe emptyMap()
    }

    "default params have zero overrides" {
        InferenceParams().overrideCount(TestJson) shouldBe 0
    }

    "form parts use whisper wire names and primitive string values" {
        val params = InferenceParams(
            language = "en",
            translate = true,
            bestOf = 2,
            beamSize = -1,
            temperature = 0.2,
            temperatureInc = 0.2,
        )

        val parts = params.toFormParts(TestJson)

        parts shouldHaveSize 6
        parts.keys shouldBe setOf("language", "translate", "best_of", "beam_size", "temperature", "temperature_inc")
        parts.getValue("language").text() shouldBe "en"
        parts.getValue("translate").text() shouldBe "true"
        parts.getValue("best_of").text() shouldBe "2"
        parts.getValue("beam_size").text() shouldBe "-1"
        parts.getValue("temperature").text() shouldBe "0.2"
        parts.getValue("temperature_inc").text() shouldBe "0.2"
    }

    "false booleans are sent, only nulls are dropped" {
        val parts = InferenceParams(translate = false, detectLanguage = false).toFormParts(TestJson)

        parts shouldHaveSize 2
        parts.getValue("translate").text() shouldBe "false"
        parts.getValue("detect_language").text() shouldBe "false"
    }

    "editable values are raw strings keyed by wire names" {
        val params = InferenceParams(
            language = "fr",
            prompt = "hello world",
            vadThreshold = 0.5,
            vadMinSpeechDurationMs = 250,
            noTimestamps = true,
        )

        params.toEditableValues(TestJson) shouldContainAll mapOf(
            "language" to "fr",
            "prompt" to "hello world",
            "vad_threshold" to "0.5",
            "vad_min_speech_duration_ms" to "250",
            "no_timestamps" to "true",
        )
    }

    "override count matches non-null fields" {
        InferenceParams(language = "de", bestOf = 3, vad = false).overrideCount(TestJson) shouldBe 3
    }
})
