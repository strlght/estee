package io.strlght.estee.data

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.PartMap
import retrofit2.http.Url

interface WhisperApi {

    @Multipart
    @POST
    suspend fun transcribe(
        @Url endpoint: String,
        @Part file: MultipartBody.Part,
        @Part("response_format") responseFormat: RequestBody,
        @PartMap params: Map<String, @JvmSuppressWildcards RequestBody>,
    ): Response<WhisperResponse>
}
