package io.strlght.estee.ui.transcribe

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.instanceOf
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.strlght.estee.R
import io.strlght.estee.data.InferenceParams
import io.strlght.estee.data.SettingsRepository
import io.strlght.estee.testing.TestJson
import io.strlght.estee.testing.audioPayload
import io.strlght.estee.testing.fakeContext
import io.strlght.estee.testing.whisperApi
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

private val TRANSCRIPT_OK = """{"text":"  hello world  "}"""

private fun formValue(body: String, name: String): String? {
    val part = Regex("name=\"$name\"[^\r\n]*\r?\n(?:[^\r\n]*\r?\n)*?\r?\n([^\r\n]*)")
        .find(body) ?: return null
    return part.groupValues[1]
}

@OptIn(ExperimentalCoroutinesApi::class)
class TranscriptionViewModelTest : DescribeSpec({

    lateinit var context: Context
    lateinit var resolver: ContentResolver
    lateinit var settings: SettingsRepository
    lateinit var server: MockWebServer
    lateinit var uri: Uri
    lateinit var ioDispatcher: CoroutineDispatcher
    var audioBytes: ByteArray = ByteArray(0)

    fun newViewModel(endpoint: String = server.url("/inference").toString()): TranscriptionViewModel {
        every { settings.serverUrl } returns flowOf(endpoint)
        return TranscriptionViewModel(context, settings, whisperApi(server), TestJson, ioDispatcher)
    }

    beforeAny {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        ioDispatcher = UnconfinedTestDispatcher()
        audioBytes = audioPayload(1000)
        server = MockWebServer()
        server.start()
        context = fakeContext()
        every { context.cacheDir } returns Files.createTempDirectory("estee-cache").toFile()
        resolver = mockk()
        every { context.contentResolver } returns resolver
        every { resolver.query(any(), any(), any(), any(), any()) } returns null
        every { resolver.getType(any()) } returns "audio/mp4"
        every { resolver.openInputStream(any()) } answers { ByteArrayInputStream(audioBytes) }
        uri = mockk { every { lastPathSegment } returns "song.m4a" }
        settings = mockk(relaxed = true)
        every { settings.inferenceParams } returns flowOf(InferenceParams())
    }

    afterAny {
        server.shutdown()
        Dispatchers.resetMain()
    }

    describe("endpoint validation") {

        it("fails when no endpoint is configured") {
            runTest {
                val vm = newViewModel(endpoint = "")

                vm.handleIncomingFile(uri)
                val state = vm.uiState.first { it is TranscriptionUiState.Failure }

                (state as TranscriptionUiState.Failure).message shouldBe
                    "res#${R.string.error_server_url_missing}"
                server.requestCount shouldBe 0
                vm.clear()
            }
        }

        it("fails when the endpoint is malformed") {
            runTest {
                val vm = newViewModel(endpoint = "inference only")

                vm.handleIncomingFile(uri)
                val state = vm.uiState.first { it is TranscriptionUiState.Failure }

                (state as TranscriptionUiState.Failure).message shouldBe
                    "res#${R.string.error_server_url_missing}"
                server.requestCount shouldBe 0
                vm.clear()
            }
        }
    }

    describe("successful transcription") {

        it("resolves attachment, uploads and reports the transcript") {
            runTest {
                server.enqueue(MockResponse().setBody(TRANSCRIPT_OK))
                val vm = newViewModel()

                vm.handleIncomingFile(uri)
                val state = vm.uiState.first { it is TranscriptionUiState.Success }

                state as TranscriptionUiState.Success
                state.text shouldBe "hello world"
                state.attachment.displayName shouldBe "song.m4a"
                state.attachment.mimeType shouldBe "audio/mp4"
                server.requestCount shouldBe 1
                vm.clear()
            }
        }

        it("trims the transcript text") {
            runTest {
                server.enqueue(MockResponse().setBody("""{"text":"  padded text  "}"""))
                val vm = newViewModel()

                vm.handleIncomingFile(uri)
                val state = vm.uiState.first { it is TranscriptionUiState.Success }

                (state as TranscriptionUiState.Success).text shouldBe "padded text"
                vm.clear()
            }
        }

        it("fails when the response carries no text") {
            runTest {
                server.enqueue(MockResponse().setBody("""{"text":null}"""))
                val vm = newViewModel()

                vm.handleIncomingFile(uri)
                val state = vm.uiState.first { it is TranscriptionUiState.Failure }

                (state as TranscriptionUiState.Failure).message shouldBe
                    "res#${R.string.error_empty_transcription}"
                vm.clear()
            }
        }

        it("sends configured inference params as form parts") {
            runTest {
                every { settings.inferenceParams } returns flowOf(InferenceParams(language = "pl", bestOf = 3))
                server.enqueue(MockResponse().setBody(TRANSCRIPT_OK))
                val vm = newViewModel()

                vm.handleIncomingFile(uri)
                vm.uiState.first { it is TranscriptionUiState.Success }

                val body = server.takeRequest().body.readUtf8()
                formValue(body, "language") shouldBe "pl"
                formValue(body, "best_of") shouldBe "3"
                formValue(body, "response_format") shouldBe "json"
                body shouldNotContain "name=\"temperature\""
                vm.clear()
            }
        }
    }

    describe("server errors") {

        it("surfaces error body detail on server failure") {
            runTest {
                server.enqueue(MockResponse().setResponseCode(500).setBody("model not loaded"))
                val vm = newViewModel()

                vm.handleIncomingFile(uri)
                val state = vm.uiState.first { it is TranscriptionUiState.Failure }

                val message = (state as TranscriptionUiState.Failure).message
                message shouldBe
                    "res#${R.string.error_with_detail}:res#${R.string.error_server}:500,model not loaded"
                vm.clear()
            }
        }

        it("maps bad requests without a body") {
            runTest {
                server.enqueue(MockResponse().setResponseCode(400).setBody(""))
                val vm = newViewModel()

                vm.handleIncomingFile(uri)
                val state = vm.uiState.first { it is TranscriptionUiState.Failure }

                (state as TranscriptionUiState.Failure).message shouldBe
                    "res#${R.string.error_server_bad_request}:400"
                vm.clear()
            }
        }

        it("maps unparseable success bodies as bad responses") {
            runTest {
                server.enqueue(MockResponse().setBody("<html>gateway</html>"))
                val vm = newViewModel()

                vm.handleIncomingFile(uri)
                val state = vm.uiState.first { it is TranscriptionUiState.Failure }

                (state as TranscriptionUiState.Failure).message shouldBe
                    "res#${R.string.error_bad_response}"
                vm.clear()
            }
        }

        it("maps connection refused to could not connect") {
            runTest {
                val vm = newViewModel(endpoint = "http://127.0.0.1:1/inference")

                vm.handleIncomingFile(uri)
                val state = vm.uiState.first { it is TranscriptionUiState.Failure }

                (state as TranscriptionUiState.Failure).message shouldBe
                    "res#${R.string.error_could_not_connect}"
                vm.clear()
            }
        }
    }

    describe("file size limit") {

        it("rejects oversized files before touching the server") {
            runTest {
                val cursor = mockk<Cursor>(relaxed = true)
                every { cursor.moveToFirst() } returns true
                every { cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME) } returns 0
                every { cursor.getColumnIndex(OpenableColumns.SIZE) } returns 1
                every { cursor.isNull(any()) } returns false
                every { cursor.getString(0) } returns "huge.wav"
                every { cursor.getLong(1) } returns 501L * 1024 * 1024
                every { resolver.query(any(), any(), any(), any(), any()) } returns cursor

                val vm = newViewModel()

                vm.handleIncomingFile(uri)
                val state = vm.uiState.first { it is TranscriptionUiState.Failure }

                state as TranscriptionUiState.Failure
                state.message shouldBe "res#${R.string.error_file_too_large}:500"
                state.attachment?.displayName shouldBe "huge.wav"
                server.requestCount shouldBe 0
                coVerify(exactly = 0) { resolver.openInputStream(any()) }
                vm.clear()
            }
        }
    }

    describe("phase progression") {

        it("walks uploading with progress to transcribing to success") {
            runTest {
                audioBytes = audioPayload(128 * 1024)
                server.enqueue(
                    MockResponse()
                        .setBody(TRANSCRIPT_OK)
                        .setBodyDelay(200, TimeUnit.MILLISECONDS),
                )
                val vm = newViewModel()
                val seen = CopyOnWriteArrayList<TranscriptionUiState>()
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    vm.uiState.collect { seen.add(it) }
                }

                vm.handleIncomingFile(uri)
                val deadline = System.currentTimeMillis() + 10_000L
                while (seen.none { it is TranscriptionUiState.Success } &&
                    System.currentTimeMillis() < deadline
                ) {
                    Thread.sleep(5)
                }

                val working = seen.filterIsInstance<TranscriptionUiState.Working>()
                working.map { it.phase } shouldContain TranscriptionPhase.UPLOADING
                working.map { it.phase } shouldContain TranscriptionPhase.TRANSCRIBING
                working.any {
                    it.phase == TranscriptionPhase.UPLOADING && it.uploadProgress == 0.5f
                } shouldBe true
                seen.last() shouldBe instanceOf<TranscriptionUiState.Success>()
                vm.clear()
            }
        }
    }

    describe("retry") {

        it("reuses the staged file for a second attempt") {
            runTest {
                server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
                server.enqueue(MockResponse().setBody(TRANSCRIPT_OK))
                val vm = newViewModel()

                vm.handleIncomingFile(uri)
                vm.uiState.first { it is TranscriptionUiState.Failure }
                vm.retry()
                val state = vm.uiState.first { it is TranscriptionUiState.Success }

                state as TranscriptionUiState.Success
                server.requestCount shouldBe 2
                coVerify(exactly = 1) { resolver.openInputStream(any()) }
                vm.clear()
            }
        }
    }

    describe("run with new parameters") {

        it("re-runs with one-shot overrides and reuses the staged file") {
            runTest {
                every { settings.inferenceParams } returns flowOf(InferenceParams(language = "en"))
                server.enqueue(MockResponse().setBody(TRANSCRIPT_OK))
                val vm = newViewModel()

                vm.handleIncomingFile(uri)
                vm.uiState.first { it is TranscriptionUiState.Success }
                formValue(server.takeRequest().body.readUtf8(), "language") shouldBe "en"

                server.enqueue(MockResponse().setBody(TRANSCRIPT_OK))
                vm.rerunWithParams(InferenceParams(language = "pl", bestOf = 5))
                vm.uiState.first { it is TranscriptionUiState.Success }
                val body = server.takeRequest().body.readUtf8()
                formValue(body, "language") shouldBe "pl"
                formValue(body, "best_of") shouldBe "5"

                server.requestCount shouldBe 2
                coVerify(exactly = 1) { resolver.openInputStream(any()) }
                coVerify(exactly = 0) { settings.setInferenceParams(any()) }
                vm.clear()
            }
        }

        it("re-stages from the uri when the staged file vanished") {
            runTest {
                server.enqueue(MockResponse().setBody(TRANSCRIPT_OK))
                val vm = newViewModel()

                vm.handleIncomingFile(uri)
                vm.uiState.first { it is TranscriptionUiState.Success }
                server.takeRequest()

                val staged = (vm.uiState.value as TranscriptionUiState.Success)
                    .attachment.stagedFile
                checkNotNull(staged).delete()

                server.enqueue(MockResponse().setBody(TRANSCRIPT_OK))
                vm.rerunWithParams(InferenceParams(translate = true))
                vm.uiState.first { it is TranscriptionUiState.Success }
                formValue(server.takeRequest().body.readUtf8(), "translate") shouldBe "true"

                coVerify(exactly = 2) { resolver.openInputStream(any()) }
                coVerify(exactly = 0) { settings.setInferenceParams(any()) }
                vm.clear()
            }
        }

        it("does nothing when there is no previous submission") {
            runTest {
                val vm = newViewModel()

                vm.rerunWithParams(InferenceParams(language = "pl"))

                server.requestCount shouldBe 0
                vm.uiState.value shouldBe TranscriptionUiState.Empty
            }
        }

        it("re-runs from a failure with overrides and reuses the staged file") {
            runTest {
                every { settings.inferenceParams } returns flowOf(InferenceParams(language = "en"))
                server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
                val vm = newViewModel()

                vm.handleIncomingFile(uri)
                vm.uiState.first { it is TranscriptionUiState.Failure }
                formValue(server.takeRequest().body.readUtf8(), "language") shouldBe "en"

                server.enqueue(MockResponse().setBody(TRANSCRIPT_OK))
                vm.rerunWithParams(InferenceParams(language = "de"))
                vm.uiState.first { it is TranscriptionUiState.Success }
                formValue(server.takeRequest().body.readUtf8(), "language") shouldBe "de"

                server.requestCount shouldBe 2
                coVerify(exactly = 1) { resolver.openInputStream(any()) }
                vm.clear()
            }
        }

        it("retry after a failed re-run repeats the same overrides") {
            runTest {
                every { settings.inferenceParams } returns flowOf(InferenceParams(language = "en"))
                server.enqueue(MockResponse().setBody(TRANSCRIPT_OK))
                val vm = newViewModel()

                vm.handleIncomingFile(uri)
                vm.uiState.first { it is TranscriptionUiState.Success }
                formValue(server.takeRequest().body.readUtf8(), "language") shouldBe "en"

                server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
                vm.rerunWithParams(InferenceParams(language = "pl"))
                vm.uiState.first { it is TranscriptionUiState.Failure }
                formValue(server.takeRequest().body.readUtf8(), "language") shouldBe "pl"

                server.enqueue(MockResponse().setBody(TRANSCRIPT_OK))
                vm.retry()
                vm.uiState.first { it is TranscriptionUiState.Success }
                formValue(server.takeRequest().body.readUtf8(), "language") shouldBe "pl"

                server.requestCount shouldBe 3
                coVerify(exactly = 1) { resolver.openInputStream(any()) }
                coVerify(exactly = 0) { settings.setInferenceParams(any()) }
                vm.clear()
            }
        }

        it("is ignored while a transcription is in progress") {
            runTest {
                server.enqueue(
                    MockResponse()
                        .setBody(TRANSCRIPT_OK)
                        .setBodyDelay(200, TimeUnit.MILLISECONDS),
                )
                val vm = newViewModel()

                vm.handleIncomingFile(uri)
                vm.uiState.first { it is TranscriptionUiState.Working }

                vm.rerunWithParams(InferenceParams(language = "pl"))

                vm.uiState.first { it is TranscriptionUiState.Success }
                server.requestCount shouldBe 1
                vm.clear()
            }
        }

        it("sends no param parts for a fully cleared override") {
            runTest {
                every { settings.inferenceParams } returns flowOf(InferenceParams(language = "en"))
                server.enqueue(MockResponse().setBody(TRANSCRIPT_OK))
                val vm = newViewModel()

                vm.handleIncomingFile(uri)
                vm.uiState.first { it is TranscriptionUiState.Success }
                formValue(server.takeRequest().body.readUtf8(), "language") shouldBe "en"

                server.enqueue(MockResponse().setBody(TRANSCRIPT_OK))
                vm.rerunWithParams(InferenceParams())
                vm.uiState.first { it is TranscriptionUiState.Success }
                server.takeRequest().body.readUtf8() shouldNotContain "name=\"language\""

                vm.clear()
            }
        }
    }

    describe("rerun sheet visibility") {

        it("open and close toggle the sheet flag") {
            runTest {
                val vm = newViewModel()

                vm.openRerunSheet()
                vm.showRerunSheet.value shouldBe true

                vm.closeRerunSheet()
                vm.showRerunSheet.value shouldBe false
            }
        }

        it("starting any run or clearing closes an open sheet") {
            runTest {
                val vm = newViewModel()

                vm.openRerunSheet()
                vm.handleIncomingFile(uri)
                vm.showRerunSheet.value shouldBe false

                server.enqueue(MockResponse().setBody(TRANSCRIPT_OK))
                vm.uiState.first { it is TranscriptionUiState.Success }

                vm.openRerunSheet()
                server.enqueue(MockResponse().setBody(TRANSCRIPT_OK))
                vm.rerunWithParams(InferenceParams(language = "pl"))
                vm.showRerunSheet.value shouldBe false

                vm.uiState.first { it is TranscriptionUiState.Success }
                vm.openRerunSheet()
                vm.clear()
                vm.showRerunSheet.value shouldBe false
            }
        }
    }

    describe("cancel and clear") {

        it("cancel resets state to empty") {
            runTest {
                server.enqueue(
                    MockResponse()
                        .setBody(TRANSCRIPT_OK)
                        .setBodyDelay(200, TimeUnit.MILLISECONDS),
                )
                val vm = newViewModel()

                vm.handleIncomingFile(uri)
                vm.uiState.first { it is TranscriptionUiState.Working }

                vm.cancel()

                vm.uiState.value shouldBe TranscriptionUiState.Empty
                vm.clear()
            }
        }

        it("clear resets state and forgets the submission") {
            runTest {
                server.enqueue(MockResponse().setBody(TRANSCRIPT_OK))
                val vm = newViewModel()

                vm.handleIncomingFile(uri)
                vm.uiState.first { it is TranscriptionUiState.Success }

                vm.clear()
                vm.retry()
                Thread.sleep(200)

                vm.uiState.value shouldBe TranscriptionUiState.Empty
                server.requestCount shouldBe 1
            }
        }
    }
})
