package io.strlght.estee.data

import kotlinx.serialization.Serializable

@Serializable
data class WhisperResponse(
    val transcription: String? = null,
    val text: String? = null,
    val segments: List<WhisperSegment>? = null,
    val language: String? = null,
)

@Serializable
data class WhisperSegment(
    val id: Int = 0,
    val seek: Double = 0.0,
    val start: Double = 0.0,
    val end: Double = 0.0,
    val text: String = "",
)
