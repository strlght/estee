package io.strlght.estee.ui.settings

import android.content.Context
import app.cash.turbine.test
import io.kotest.core.spec.style.DescribeSpec
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
class SettingsViewModelTest : DescribeSpec({

    lateinit var context: Context
    lateinit var settings: SettingsRepository

    beforeAny {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context = fakeContext()
        settings = mockk(relaxed = true)
    }

    afterAny {
        Dispatchers.resetMain()
    }

    describe("initial state") {

        it("loads the stored url once and ignores later emissions") {
            every { settings.serverUrl } returns flowOf("http://first", "http://second")

            val vm = SettingsViewModel(context, settings, TestJson)

            vm.uiState.value.serverUrl shouldBe "http://first"
        }
    }

    describe("onUrlChange") {

        it("typing clears error and saved flag") {
            runTest {
                every { settings.serverUrl } returns flowOf("http://old")
                coEvery { settings.setServerUrl(any()) } returns Unit

                val vm = SettingsViewModel(context, settings, TestJson)
                vm.onUrlChange("http://good")
                vm.save()
                vm.uiState.value.saved shouldBe true

                vm.onUrlChange("whisper://bad")
                vm.save()
                vm.uiState.value.urlError shouldBe "res#${R.string.error_url_invalid}"

                vm.onUrlChange("dirty")

                vm.uiState.value.serverUrl shouldBe "dirty"
                vm.uiState.value.urlError shouldBe null
                vm.uiState.value.saved shouldBe false
            }
        }
    }

    describe("save") {

        it("rejects blank url") {
            every { settings.serverUrl } returns flowOf("")

            val vm = SettingsViewModel(context, settings, TestJson)
            vm.onUrlChange("   ")
            vm.save()

            vm.uiState.value.urlError shouldBe "res#${R.string.error_url_empty}"
            vm.uiState.value.saved shouldBe false
            coVerify(exactly = 0) { settings.setServerUrl(any()) }
        }

        it("rejects malformed url") {
            every { settings.serverUrl } returns flowOf("")

            val vm = SettingsViewModel(context, settings, TestJson)
            vm.onUrlChange("whisper://nope")
            vm.save()

            vm.uiState.value.urlError shouldBe "res#${R.string.error_url_invalid}"
            coVerify(exactly = 0) { settings.setServerUrl(any()) }
        }

        it("trims and strips trailing slash before persisting") {
            runTest {
                every { settings.serverUrl } returns flowOf("")
                val captured = slot<String>()
                coEvery { settings.setServerUrl(capture(captured)) } returns Unit

                val vm = SettingsViewModel(context, settings, TestJson)
                var callbackFired = false
                vm.onUrlChange("  http://host:8080/inference/ ")
                vm.save { callbackFired = true }

                captured.captured shouldBe "http://host:8080/inference"
                vm.uiState.value.serverUrl shouldBe "http://host:8080/inference"
                vm.uiState.value.urlError shouldBe null
                vm.uiState.value.saved shouldBe true
                callbackFired shouldBe true
            }
        }
    }

    describe("consumeSaved") {

        it("resets the saved flag") {
            runTest {
                every { settings.serverUrl } returns flowOf("")
                coEvery { settings.setServerUrl(any()) } returns Unit

                val vm = SettingsViewModel(context, settings, TestJson)
                vm.onUrlChange("http://host/inference")
                vm.save()
                vm.consumeSaved()

                vm.uiState.value.saved shouldBe false
            }
        }
    }

    describe("overrideCount") {

        it("projects the stored inference params") {
            every { settings.serverUrl } returns flowOf("")
            every { settings.inferenceParams } returns
                flowOf(InferenceParams(language = "en", bestOf = 3, vad = true))

            val vm = SettingsViewModel(context, settings, TestJson)

            vm.overrideCount.test {
                expectMostRecentItem() shouldBe 3
            }
        }
    }
})
