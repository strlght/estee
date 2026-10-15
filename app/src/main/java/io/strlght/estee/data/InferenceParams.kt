package io.strlght.estee.data

import io.strlght.estee.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
data class InferenceParams(
    @SerialName("language") val language: String? = null,
    @SerialName("translate") val translate: Boolean? = null,
    @SerialName("detect_language") val detectLanguage: Boolean? = null,
    @SerialName("prompt") val prompt: String? = null,
    @SerialName("carry_initial_prompt") val carryInitialPrompt: Boolean? = null,
    @SerialName("temperature") val temperature: Double? = null,
    @SerialName("temperature_inc") val temperatureInc: Double? = null,
    @SerialName("best_of") val bestOf: Int? = null,
    @SerialName("beam_size") val beamSize: Int? = null,
    @SerialName("max_len") val maxLen: Int? = null,
    @SerialName("max_context") val maxContext: Int? = null,
    @SerialName("audio_ctx") val audioCtx: Int? = null,
    @SerialName("split_on_word") val splitOnWord: Boolean? = null,
    @SerialName("suppress_nst") val suppressNst: Boolean? = null,
    @SerialName("no_timestamps") val noTimestamps: Boolean? = null,
    @SerialName("token_timestamps") val tokenTimestamps: Boolean? = null,
    @SerialName("word_thold") val wordThold: Double? = null,
    @SerialName("entropy_thold") val entropyThold: Double? = null,
    @SerialName("logprob_thold") val logprobThold: Double? = null,
    @SerialName("no_speech_thold") val noSpeechThold: Double? = null,
    @SerialName("offset_t") val offsetT: Int? = null,
    @SerialName("offset_n") val offsetN: Int? = null,
    @SerialName("duration") val duration: Int? = null,
    @SerialName("vad") val vad: Boolean? = null,
    @SerialName("vad_threshold") val vadThreshold: Double? = null,
    @SerialName("vad_min_speech_duration_ms") val vadMinSpeechDurationMs: Int? = null,
    @SerialName("vad_min_silence_duration_ms") val vadMinSilenceDurationMs: Int? = null,
    @SerialName("vad_max_speech_duration_s") val vadMaxSpeechDurationS: Double? = null,
    @SerialName("vad_speech_pad_ms") val vadSpeechPadMs: Int? = null,
    @SerialName("vad_samples_overlap") val vadSamplesOverlap: Double? = null,
    @SerialName("diarize") val diarize: Boolean? = null,
    @SerialName("tinydiarize") val tinydiarize: Boolean? = null,
    @SerialName("debug_mode") val debugMode: Boolean? = null,
    @SerialName("no_language_probabilities") val noLanguageProbabilities: Boolean? = null,
)

internal val ParamsJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

private val TEXT_PLAIN = "text/plain".toMediaType()

fun InferenceParams.toFormParts(): Map<String, RequestBody> =
    ParamsJson.encodeToJsonElement(this).jsonObject
        .filterNot { (_, value) -> value is JsonNull }
        .mapValues { (_, value) -> value.jsonPrimitive.content.toRequestBody(TEXT_PLAIN) }

fun InferenceParams.toEditableValues(): Map<String, String> =
    ParamsJson.encodeToJsonElement(this).jsonObject
        .filterNot { (_, value) -> value is JsonNull }
        .mapValues { (_, value) -> value.jsonPrimitive.content }

fun InferenceParams.overrideCount(): Int =
    ParamsJson.encodeToJsonElement(this).jsonObject.count { (_, value) -> value !is JsonNull }

enum class ParamKind {
    INTEGER,
    DECIMAL,
    BOOLEAN,
    TEXT,
}

data class ParamSpec(
    val name: String,
    val kind: ParamKind,
    val default: String,
)

data class ParamGroup(
    val titleRes: Int,
    val descriptionRes: Int,
    val specs: List<ParamSpec>,
)

object ParamSpecs {

    private fun bool(name: String, default: String = "false") = ParamSpec(name, ParamKind.BOOLEAN, default)
    private fun int(name: String, default: String) = ParamSpec(name, ParamKind.INTEGER, default)
    private fun decimal(name: String, default: String) = ParamSpec(name, ParamKind.DECIMAL, default)
    private fun text(name: String, default: String) = ParamSpec(name, ParamKind.TEXT, default)

    val GROUPS: List<ParamGroup> = listOf(
        ParamGroup(
            titleRes = R.string.params_group_language,
            descriptionRes = R.string.params_group_language_description,
            specs = listOf(
                text("language", "en"),
                bool("translate"),
                bool("detect_language"),
                text("prompt", "(empty)"),
                bool("carry_initial_prompt"),
            ),
        ),
        ParamGroup(
            titleRes = R.string.params_group_decoding,
            descriptionRes = R.string.params_group_decoding_description,
            specs = listOf(
                decimal("temperature", "0.0"),
                decimal("temperature_inc", "0.2"),
                int("best_of", "2"),
                int("beam_size", "-1"),
                int("max_len", "0"),
                int("max_context", "-1"),
                int("audio_ctx", "0"),
                bool("split_on_word"),
                bool("suppress_nst"),
                bool("no_timestamps"),
                bool("token_timestamps", "auto"),
            ),
        ),
        ParamGroup(
            titleRes = R.string.params_group_thresholds,
            descriptionRes = R.string.params_group_thresholds_description,
            specs = listOf(
                decimal("word_thold", "0.01"),
                decimal("entropy_thold", "2.40"),
                decimal("logprob_thold", "-1.00"),
                decimal("no_speech_thold", "0.60"),
            ),
        ),
        ParamGroup(
            titleRes = R.string.params_group_range,
            descriptionRes = R.string.params_group_range_description,
            specs = listOf(
                int("offset_t", "0"),
                int("offset_n", "0"),
                int("duration", "0"),
            ),
        ),
        ParamGroup(
            titleRes = R.string.params_group_vad,
            descriptionRes = R.string.params_group_vad_description,
            specs = listOf(
                bool("vad"),
                decimal("vad_threshold", "0.50"),
                int("vad_min_speech_duration_ms", "250"),
                int("vad_min_silence_duration_ms", "100"),
                decimal("vad_max_speech_duration_s", "no limit"),
                int("vad_speech_pad_ms", "30"),
                decimal("vad_samples_overlap", "0.10"),
            ),
        ),
        ParamGroup(
            titleRes = R.string.params_group_misc,
            descriptionRes = R.string.params_group_misc_description,
            specs = listOf(
                bool("diarize"),
                bool("tinydiarize"),
                bool("debug_mode"),
                bool("no_language_probabilities"),
            ),
        ),
    )
}
