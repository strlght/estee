package io.strlght.estee.ui.transcribe

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.strlght.estee.R
import io.strlght.estee.data.InferenceParams
import io.strlght.estee.data.ProgressRequestBody
import io.strlght.estee.data.SettingsRepository
import io.strlght.estee.data.WhisperApi
import io.strlght.estee.data.toEditableValues
import io.strlght.estee.data.toFormParts
import io.strlght.estee.di.IoDispatcher
import io.strlght.estee.util.runSuspendCatching
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

data class AudioAttachment(
    val uri: Uri,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long?,
    val stagedFile: File? = null,
)

enum class TranscriptionPhase {
    PREPARING,
    UPLOADING,
    TRANSCRIBING,
}

sealed interface TranscriptionUiState {

    data object Empty : TranscriptionUiState

    data class Working(
        val attachment: AudioAttachment,
        val phase: TranscriptionPhase,
        val uploadProgress: Float?,
        val elapsedMs: Long,
    ) : TranscriptionUiState

    data class Success(
        val attachment: AudioAttachment,
        val text: String,
    ) : TranscriptionUiState

    data class Failure(
        val attachment: AudioAttachment?,
        val message: String,
    ) : TranscriptionUiState
}

@HiltViewModel
class TranscriptionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val api: WhisperApi,
    private val json: Json,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow<TranscriptionUiState>(TranscriptionUiState.Empty)
    val uiState: StateFlow<TranscriptionUiState> = _uiState

    private val _showRerunSheet = MutableStateFlow(false)
    val showRerunSheet: StateFlow<Boolean> = _showRerunSheet.asStateFlow()

    private val _lastSubmittedUri = MutableStateFlow<Uri?>(null)
    private val _lastOverride = MutableStateFlow<InferenceParams?>(null)

    val endpointUrl: StateFlow<String> = settings.serverUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val endpointConfigured: StateFlow<Boolean> = settings.serverUrl
        .map { it.isNotBlank() && it.toHttpUrlOrNull() != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val editableParams: StateFlow<Map<String, String>> = settings.inferenceParams
        .map { it.toEditableValues(json) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val rerunSeed: StateFlow<Map<String, String>> =
        combine(_lastOverride, settings.inferenceParams) { override, stored ->
            (override ?: stored).toEditableValues(json)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private var transcriptionJob: Job? = null

    fun openRerunSheet() {
        _showRerunSheet.value = true
    }

    fun closeRerunSheet() {
        _showRerunSheet.value = false
    }

    fun handleIncomingFile(uri: Uri) {
        _showRerunSheet.value = false
        _lastSubmittedUri.value = uri
        _lastOverride.value = null
        startTranscription(uri)
    }

    fun retry() {
        _showRerunSheet.value = false
        val failure = _uiState.value as? TranscriptionUiState.Failure
        val attachment = failure?.attachment
        val staged = attachment?.stagedFile?.takeIf { it.exists() && it.length() > 0L }

        transcriptionJob?.cancel()
        if (attachment != null && staged != null) {
            transcriptionJob = viewModelScope.launch(ioDispatcher) {
                uploadWithStagedFile(attachment.copy(stagedFile = staged), _lastOverride.value)
            }
        } else {
            _lastSubmittedUri.value?.let { startTranscription(it, _lastOverride.value) }
        }
    }

    fun rerunWithParams(params: InferenceParams) {
        _showRerunSheet.value = false
        val attachment = when (val current = _uiState.value) {
            is TranscriptionUiState.Success -> current.attachment
            is TranscriptionUiState.Failure -> current.attachment
            else -> null
        } ?: return

        _lastOverride.value = params
        transcriptionJob?.cancel()
        val staged = attachment.stagedFile?.takeIf { it.exists() && it.length() > 0L }
        if (staged != null) {
            transcriptionJob = viewModelScope.launch(ioDispatcher) {
                uploadWithStagedFile(attachment.copy(stagedFile = staged), params)
            }
        } else {
            startTranscription(attachment.uri, params)
        }
    }

    fun cancel() {
        _showRerunSheet.value = false
        transcriptionJob?.cancel()
        transcriptionJob = null
        _uiState.value = TranscriptionUiState.Empty
    }

    fun clear() {
        _showRerunSheet.value = false
        transcriptionJob?.cancel()
        transcriptionJob = null
        _lastSubmittedUri.value = null
        _lastOverride.value = null
        _uiState.value = TranscriptionUiState.Empty
    }

    private fun startTranscription(uri: Uri, paramsOverride: InferenceParams? = null) {
        transcriptionJob?.cancel()
        transcriptionJob = viewModelScope.launch(ioDispatcher) {
            val resolver = context.contentResolver

            val attachment = runSuspendCatching {
                withContext(ioDispatcher) { resolveAttachment(resolver, uri) }
            }.getOrElse { error ->
                _uiState.value = TranscriptionUiState.Failure(null, describe(error))
                return@launch
            }

            if ((attachment.sizeBytes ?: 0L) > MAX_UPLOAD_BYTES) {
                _uiState.value = TranscriptionUiState.Failure(
                    attachment,
                    getString(R.string.error_file_too_large, MAX_UPLOAD_BYTES / (1024 * 1024)),
                )
                return@launch
            }

            _uiState.value = TranscriptionUiState.Working(
                attachment = attachment,
                phase = TranscriptionPhase.PREPARING,
                uploadProgress = null,
                elapsedMs = 0L,
            )

            val staged = runSuspendCatching {
                withContext(ioDispatcher) { stageInCache(resolver, attachment) }
            }.getOrElse { error ->
                _uiState.value = TranscriptionUiState.Failure(attachment, describe(error))
                return@launch
            }

            uploadWithStagedFile(attachment.copy(stagedFile = staged), paramsOverride)
        }
    }

    private suspend fun uploadWithStagedFile(
        attachment: AudioAttachment,
        paramsOverride: InferenceParams? = null,
    ) {
        val staged = attachment.stagedFile ?: return

        val endpoint = settings.serverUrl.first().trim()
        if (endpoint.toHttpUrlOrNull() == null) {
            _uiState.value = TranscriptionUiState.Failure(
                attachment,
                getString(R.string.error_server_url_missing),
            )
            return
        }

        _uiState.value = TranscriptionUiState.Working(
            attachment = attachment,
            phase = TranscriptionPhase.UPLOADING,
            uploadProgress = 0f,
            elapsedMs = 0L,
        )

        coroutineScope {
            val startedAt = SystemClock.elapsedRealtime()
            val timer = launch {
                while (isActive) {
                    delay(500L)
                    _uiState.update { state ->
                        if (state is TranscriptionUiState.Working) {
                            state.copy(elapsedMs = SystemClock.elapsedRealtime() - startedAt)
                        } else {
                            state
                        }
                    }
                }
            }
            try {
                runSuspendCatching { transcribe(endpoint, attachment, staged, paramsOverride) }
                    .onFailure { error ->
                        _uiState.value = TranscriptionUiState.Failure(attachment, describe(error))
                    }
            } finally {
                timer.cancel()
            }
        }
    }

    private suspend fun transcribe(
        endpoint: String,
        attachment: AudioAttachment,
        staged: File,
        paramsOverride: InferenceParams? = null,
    ) {
        val body = ProgressRequestBody(
            file = staged,
            contentType = attachment.mimeType?.toMediaTypeOrNull(),
        ) { fraction ->
            _uiState.update { state ->
                when {
                    state !is TranscriptionUiState.Working -> state
                    fraction >= 1f -> state.copy(
                        phase = TranscriptionPhase.TRANSCRIBING,
                    )
                    state.phase == TranscriptionPhase.UPLOADING ->
                        state.copy(uploadProgress = if (fraction < 0f) null else fraction)
                    else -> state
                }
            }
        }

        val filePart = MultipartBody.Part.createFormData(
            "file",
            attachment.displayName,
            body,
        )
        val responseFormat = "json".toRequestBody("text/plain".toMediaTypeOrNull())
        val overrideParts = (paramsOverride ?: settings.inferenceParams.first()).toFormParts(json)

        val response = api.transcribe(endpoint, filePart, responseFormat, overrideParts)
        if (response.isSuccessful) {
            val text = response.body()?.text?.trim().orEmpty()
            if (text.isEmpty()) {
                _uiState.value = TranscriptionUiState.Failure(
                    attachment,
                    getString(R.string.error_empty_transcription),
                )
            } else {
                _uiState.value = TranscriptionUiState.Success(
                    attachment = attachment,
                    text = text,
                )
            }
        } else {
            val serverMessage = response
                .errorBody()
                ?.string()
                ?.takeIf { it.isNotBlank() }
                ?.take(400)
            val reason = if (response.code() == HttpURLConnection.HTTP_BAD_REQUEST) {
                getString(R.string.error_server_bad_request, response.code())
            } else {
                getString(R.string.error_server, response.code())
            }
            _uiState.value = TranscriptionUiState.Failure(
                attachment,
                serverMessage?.let { getString(R.string.error_with_detail, reason, it) } ?: reason,
            )
        }
    }

    private fun stageInCache(resolver: ContentResolver, attachment: AudioAttachment): File {
        val uploadsDir = File(context.cacheDir, "uploads")
            .apply { mkdirs() }

        val safeName = attachment.displayName
            .filterNot { it == '/' || it == '\\' || it == '\u0000' }
            .ifBlank { "audio" }
            .take(80)
        val target = File(uploadsDir, "${System.currentTimeMillis()}_$safeName")

        resolver.openInputStream(attachment.uri).use { input ->
            requireNotNull(input) { getString(R.string.error_unable_to_open_file) }
            target.outputStream().use { output ->
                input.copyTo(output, bufferSize = 64 * 1024)
            }
        }
        require(target.length() > 0L) { getString(R.string.error_shared_file_empty) }

        uploadsDir.listFiles()?.filter { it != target }?.forEach { it.delete() }

        return target
    }

    private fun resolveAttachment(resolver: ContentResolver, uri: Uri): AudioAttachment {
        var displayName = uri.lastPathSegment ?: "audio"
        var sizeBytes: Long? = null

        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                        displayName = cursor.getString(nameIndex)
                    }
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                        sizeBytes = cursor.getLong(sizeIndex)
                    }
                }
            }

        return AudioAttachment(
            uri = uri,
            displayName = displayName,
            mimeType = resolver.getType(uri),
            sizeBytes = sizeBytes,
        )
    }

    private fun describe(error: Throwable): String = when (error) {
        is SecurityException -> getString(R.string.error_lost_access)
        is UnknownHostException -> getString(R.string.error_unknown_host)
        is ConnectException -> getString(R.string.error_could_not_connect)
        is SocketTimeoutException -> getString(R.string.error_timeout)
        is SerializationException -> getString(R.string.error_bad_response)
        is IOException -> getString(R.string.error_network, error.message)
        else -> error.message ?: getString(R.string.error_unexpected, error.javaClass.simpleName)
    }

    private fun getString(resId: Int, vararg formatArgs: Any?): String =
        context.getString(resId, *formatArgs)

    private companion object {
        const val MAX_UPLOAD_BYTES = 500L * 1024 * 1024
    }
}
