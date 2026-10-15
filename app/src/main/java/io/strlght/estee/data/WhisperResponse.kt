package io.strlght.estee.data

import kotlinx.serialization.Serializable

@Serializable
data class WhisperResponse(
    val text: String? = null,
)
