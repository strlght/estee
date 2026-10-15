package io.strlght.estee.ui.settings

import android.content.Context
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.strlght.estee.R
import io.strlght.estee.data.InferenceParams
import io.strlght.estee.data.SettingsRepository
import io.strlght.estee.testing.TestJson
import io.strlght.estee.testing.fakeContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class ParamsViewModelTest : DescribeSpec({

    lateinit var context: Context
    lateinit var settings: SettingsRepository

    beforeAny {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context = fakeContext()
        settings = mockk(relaxed = true)
        every { settings.inferenceParams } returns flowOf(InferenceParams())
        coEvery { settings.setInferenceParams(any()) } returns Unit
    }

    afterAny {
        Dispatchers.resetMain()
    }

    describe("loading") {

        it("hydrates editable values from stored params") {
            every { settings.inferenceParams } returns
                flowOf(InferenceParams(language = "en", temperature = 0.5, bestOf = -1, vad = true))

            val vm = ParamsViewModel(context, settings, TestJson)

            vm.uiState.value.values shouldContainExactly mapOf(
                "language" to "en",
                "temperature" to "0.5",
                "best_of" to "-1",
                "vad" to "true",
            )
        }
    }

    describe("onParamChange") {

        it("blank value removes the entry") {
            every { settings.inferenceParams } returns flowOf(InferenceParams(language = "en"))

            val vm = ParamsViewModel(context, settings, TestJson)
            vm.onParamChange("language", "   ")

            vm.uiState.value.values shouldNotContainKey "language"
        }

        it("editing clears the pending error for that field") {
            val vm = ParamsViewModel(context, settings, TestJson)
            vm.onParamChange("best_of", "abc")
            vm.save()
            vm.uiState.value.errors.containsKey("best_of") shouldBe true

            vm.onParamChange("best_of", "2")

            vm.uiState.value.errors shouldNotContainKey "best_of"
        }
    }

    describe("validation on save") {

        it("rejects non-integer values without persisting") {
            val vm = ParamsViewModel(context, settings, TestJson)
            vm.onParamChange("best_of", "abc")
            vm.onParamChange("beam_size", "1.5")
            vm.save()

            vm.uiState.value.errors shouldContainExactly mapOf(
                "best_of" to "res#${R.string.params_error_integer}",
                "beam_size" to "res#${R.string.params_error_integer}",
            )
            vm.uiState.value.saved shouldBe false
            coVerify(exactly = 0) { settings.setInferenceParams(any()) }
        }

        it("rejects non-numeric decimals without persisting") {
            val vm = ParamsViewModel(context, settings, TestJson)
            vm.onParamChange("word_thold", "loud")
            vm.save()

            vm.uiState.value.errors shouldContainExactly mapOf(
                "word_thold" to "res#${R.string.params_error_number}",
            )
            coVerify(exactly = 0) { settings.setInferenceParams(any()) }
        }
    }

    describe("saving valid values") {

        it("parses types including comma decimals") {
            runTest {
                val captured = slot<InferenceParams>()
                coEvery { settings.setInferenceParams(capture(captured)) } returns Unit

                val vm = ParamsViewModel(context, settings, TestJson)
                var callbackFired = false
                vm.onParamChange("language", " fr ")
                vm.onParamChange("temperature", "0,5")
                vm.onParamChange("best_of", "4")
                vm.onParamChange("translate", "true")
                vm.onParamChange("vad", "false")
                vm.save { callbackFired = true }

                captured.captured shouldBe InferenceParams(
                    language = "fr",
                    temperature = 0.5,
                    bestOf = 4,
                    translate = true,
                    vad = false,
                )
                callbackFired shouldBe true
                vm.uiState.value.saved shouldBe true
            }
        }

        it("round-trips an empty form as default params") {
            runTest {
                val captured = slot<InferenceParams>()
                coEvery { settings.setInferenceParams(capture(captured)) } returns Unit

                val vm = ParamsViewModel(context, settings, TestJson)
                vm.save()

                captured.captured shouldBe InferenceParams()
            }
        }
    }

    describe("resetAll") {

        it("clears values and the saved flag after a successful save") {
            every { settings.inferenceParams } returns flowOf(InferenceParams(language = "en"))

            val vm = ParamsViewModel(context, settings, TestJson)
            vm.save()
            vm.uiState.value.saved shouldBe true

            vm.resetAll()

            vm.uiState.value.values shouldBe emptyMap()
            vm.uiState.value.saved shouldBe false
        }

        it("clears pending validation errors") {
            val vm = ParamsViewModel(context, settings, TestJson)
            vm.onParamChange("beam_size", "bad")
            vm.save()
            vm.uiState.value.errors.containsKey("beam_size") shouldBe true

            vm.resetAll()

            vm.uiState.value.errors shouldBe emptyMap()
        }
    }
})
