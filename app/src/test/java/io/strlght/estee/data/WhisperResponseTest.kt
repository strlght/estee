package io.strlght.estee.data

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.strlght.estee.testing.TestJson
import kotlinx.serialization.SerializationException

class WhisperResponseTest : StringSpec({

    "parses whisper.cpp json payload and ignores extra fields" {
        val json = """
            {
              "text": "  hello from whisper  ",
              "timings": { "a": 1 },
              "model": "whisper-1",
              "unknown": true
            }
        """.trimIndent()

        val response = TestJson.decodeFromString<WhisperResponse>(json)

        response.text shouldBe "  hello from whisper  "
    }

    "parses bare object and coerces null text" {
        TestJson.decodeFromString<WhisperResponse>("{}") shouldBe WhisperResponse()
        TestJson.decodeFromString<WhisperResponse>("""{"text":null}""") shouldBe WhisperResponse()
    }

    "throws on structurally invalid payloads" {
        shouldThrow<SerializationException> {
            TestJson.decodeFromString<WhisperResponse>("""{"text":5}""")
        }
    }
})
