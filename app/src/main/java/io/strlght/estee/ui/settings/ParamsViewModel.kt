package io.strlght.estee.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.strlght.estee.R
import io.strlght.estee.data.InferenceParams
import io.strlght.estee.data.SettingsRepository
import io.strlght.estee.data.toEditableValues
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ParamsUiState(
    val values: Map<String, String> = emptyMap(),
    val errors: Map<String, String> = emptyMap(),
    val saved: Boolean = false,
)

@HiltViewModel
class ParamsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ParamsUiState())
    val uiState: StateFlow<ParamsUiState> = _uiState.asStateFlow()

    private var loaded = false

    init {
        viewModelScope.launch {
            settings.inferenceParams.collect { stored ->
                if (!loaded) {
                    loaded = true
                    _uiState.update { it.copy(values = stored.toEditableValues()) }
                }
            }
        }
    }

    fun onParamChange(name: String, value: String) {
        _uiState.update {
            it.copy(
                values = if (value.isBlank()) it.values - name else it.values + (name to value),
                errors = it.errors - name,
                saved = false,
            )
        }
    }

    fun resetAll() {
        _uiState.update { it.copy(values = emptyMap(), errors = emptyMap(), saved = false) }
    }

    fun save(onSaved: () -> Unit = {}) {
        val errors = mutableMapOf<String, String>()
        val params = buildParams(_uiState.value.values, errors)
        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(errors = errors) }
            return
        }
        viewModelScope.launch {
            settings.setInferenceParams(params)
            _uiState.update { it.copy(saved = true) }
            onSaved()
        }
    }

    fun consumeSaved() {
        _uiState.update { it.copy(saved = false) }
    }

    private fun buildParams(values: Map<String, String>, errors: MutableMap<String, String>): InferenceParams {
        fun text(name: String): String? = values[name]?.trim()?.takeIf { it.isNotEmpty() }

        fun int(name: String): Int? = text(name)?.let { raw ->
            raw.toIntOrNull() ?: run {
                errors[name] = getString(R.string.params_error_integer)
                null
            }
        }

        fun decimal(name: String): Double? = text(name)?.let { raw ->
            raw.replace(',', '.').toDoubleOrNull() ?: run {
                errors[name] = getString(R.string.params_error_number)
                null
            }
        }

        fun bool(name: String): Boolean? = text(name)?.toBoolean()

        return InferenceParams(
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
    }

    private fun getString(resId: Int, vararg formatArgs: Any?): String =
        context.getString(resId, *formatArgs)
}
