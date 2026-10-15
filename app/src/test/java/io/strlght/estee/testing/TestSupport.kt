package io.strlght.estee.testing

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.strlght.estee.data.WhisperApi
import io.strlght.estee.di.AppModule
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

val TestJson: Json = AppModule.provideJson()

fun fakeContext(): Context = mockk {
    every { getString(any()) } answers { "res#${firstArg<Int>()}" }
    every { getString(any(), *anyVararg()) } answers {
        val formatArgs = args.drop(1).flatMap { if (it is Array<*>) it.toList() else listOf(it) }
        if (formatArgs.isEmpty()) "res#${firstArg<Int>()}"
        else "res#${firstArg<Int>()}:${formatArgs.joinToString(",")}"
    }
}

fun whisperApi(server: MockWebServer): WhisperApi =
    Retrofit.Builder()
        .baseUrl(server.url("/"))
        .client(OkHttpClient())
        .addConverterFactory(TestJson.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(WhisperApi::class.java)

fun audioPayload(size: Int): ByteArray = ByteArray(size) { (it % 251).toByte() }
