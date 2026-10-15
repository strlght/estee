package io.strlght.estee.ui.params

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import io.strlght.estee.data.InferenceParams

class ParamsEditorStateTest : DescribeSpec({

    describe("onParamChange") {

        it("blank value removes the entry") {
            val editor = ParamsEditorState(mapOf("language" to "en"))

            editor.onParamChange("language", "   ")

            editor.state.value.values shouldNotContainKey "language"
        }

        it("editing clears the pending error for that field") {
            val editor = ParamsEditorState(emptyMap())
            editor.onParamChange("best_of", "abc")
            editor.build().errors shouldContainKey "best_of"

            editor.onParamChange("best_of", "3")

            editor.state.value.errors shouldNotContainKey "best_of"
        }
    }

    describe("build") {

        it("parses valid values and marks typed errors") {
            val editor = ParamsEditorState(
                mapOf("language" to " fr ", "temperature" to "0,5", "beam_size" to "x"),
            )

            val outcome = editor.build()

            outcome.params shouldBe InferenceParams(language = "fr", temperature = 0.5)
            outcome.errors shouldBe mapOf("beam_size" to ParamFieldError.NOT_INTEGER)
            editor.state.value.errors shouldBe outcome.errors
        }
    }

    describe("reset") {

        it("restores the given values and clears errors") {
            val editor = ParamsEditorState(mapOf("language" to "en"))
            editor.onParamChange("language", "de")
            editor.onParamChange("word_thold", "nope")
            editor.build().errors shouldContainKey "word_thold"

            editor.reset(mapOf("language" to "en"))

            editor.state.value.values shouldBe mapOf("language" to "en")
            editor.state.value.errors shouldBe emptyMap()
        }
    }
})
