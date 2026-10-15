package io.strlght.estee.ui.params

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.strlght.estee.R
import io.strlght.estee.data.InferenceParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class ParamFieldError(val messageRes: Int) {
    NOT_INTEGER(R.string.params_error_integer),
    NOT_NUMBER(R.string.params_error_number),
}

data class ParamBuildOutcome(
    val params: InferenceParams,
    val errors: Map<String, ParamFieldError>,
)

fun buildInferenceParams(values: Map<String, String>): ParamBuildOutcome {
    val errors = mutableMapOf<String, ParamFieldError>()

    fun text(name: String): String? = values[name]?.trim()?.takeIf { it.isNotEmpty() }

    fun int(name: String): Int? = text(name)?.let { raw ->
        raw.toIntOrNull() ?: run {
            errors[name] = ParamFieldError.NOT_INTEGER
            null
        }
    }

    fun decimal(name: String): Double? = text(name)?.let { raw ->
        raw.replace(',', '.').toDoubleOrNull() ?: run {
            errors[name] = ParamFieldError.NOT_NUMBER
            null
        }
    }

    fun bool(name: String): Boolean? = text(name)?.toBoolean()

    val params = InferenceParams(
        language = text("language"),
        translate = bool("translate"),
        detectLanguage = bool("detect_language"),
        prompt = text("prompt"),
        carryInitialPrompt = bool("carry_initial_prompt"),
        temperature = decimal("temperature"),
        temperatureInc = decimal("temperature_inc"),
        bestOf = int("best_of"),
        beamSize = int("beam_size"),
        maxLen = int("max_len"),
        maxContext = int("max_context"),
        audioCtx = int("audio_ctx"),
        splitOnWord = bool("split_on_word"),
        suppressNst = bool("suppress_nst"),
        noTimestamps = bool("no_timestamps"),
        tokenTimestamps = bool("token_timestamps"),
        wordThold = decimal("word_thold"),
        entropyThold = decimal("entropy_thold"),
        logprobThold = decimal("logprob_thold"),
        noSpeechThold = decimal("no_speech_thold"),
        offsetT = int("offset_t"),
        offsetN = int("offset_n"),
        duration = int("duration"),
        vad = bool("vad"),
        vadThreshold = decimal("vad_threshold"),
        vadMinSpeechDurationMs = int("vad_min_speech_duration_ms"),
        vadMinSilenceDurationMs = int("vad_min_silence_duration_ms"),
        vadMaxSpeechDurationS = decimal("vad_max_speech_duration_s"),
        vadSpeechPadMs = int("vad_speech_pad_ms"),
        vadSamplesOverlap = decimal("vad_samples_overlap"),
        diarize = bool("diarize"),
        tinydiarize = bool("tinydiarize"),
        debugMode = bool("debug_mode"),
        noLanguageProbabilities = bool("no_language_probabilities"),
    )
    return ParamBuildOutcome(params = params, errors = errors)
}

data class ParamsEditorUiState(
    val values: Map<String, String> = emptyMap(),
    val errors: Map<String, ParamFieldError> = emptyMap(),
)

class ParamsEditorState(initialValues: Map<String, String>) {

    private val _state = MutableStateFlow(ParamsEditorUiState(values = initialValues))
    val state: StateFlow<ParamsEditorUiState> = _state.asStateFlow()

    fun onParamChange(name: String, value: String) {
        _state.update {
            it.copy(
                values = if (value.isBlank()) it.values - name else it.values + (name to value),
                errors = it.errors - name,
            )
        }
    }

    fun reset(initialValues: Map<String, String>) {
        _state.update { it.copy(values = initialValues, errors = emptyMap()) }
    }

    fun build(): ParamBuildOutcome {
        val outcome = buildInferenceParams(_state.value.values)
        _state.update { it.copy(errors = outcome.errors) }
        return outcome
    }
}

@Composable
fun rememberParamsEditorState(
    key: Any?,
    initialValues: Map<String, String>,
): ParamsEditorState = remember(key) { ParamsEditorState(initialValues) }
