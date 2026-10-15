package io.strlght.estee.data

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeMonotonicallyIncreasing
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSingleElement
import io.kotest.matchers.shouldBe
import okio.Buffer
import java.io.File
import kotlin.math.roundToInt

private fun tempFile(data: ByteArray): File =
    File.createTempFile("estee-progress", ".bin").apply {
        deleteOnExit()
        writeBytes(data)
    }

private fun bytes(size: Int): ByteArray = ByteArray(size) { (it % 251).toByte() }

private fun write(file: File): List<Float> {
    val reports = mutableListOf<Float>()
    ProgressRequestBody(file) { reports.add(it) }.writeTo(Buffer())
    return reports
}

private fun List<Float>.shouldBeWholePercentSteps() {
    distinct() shouldBe this
    shouldBeMonotonicallyIncreasing()
    all { it > 0f && it <= 1f } shouldBe true
    all { (it * 100).roundToInt() / 100f == it } shouldBe true
    last() shouldBe 1.0f
}

class ProgressRequestBodyTest : StringSpec({

    "file contents reach the sink byte for byte" {
        val data = bytes(100_000)
        val reports = mutableListOf<Float>()
        val sink = Buffer()

        ProgressRequestBody(tempFile(data)) { reports.add(it) }.writeTo(sink)

        sink.readByteArray() shouldBe data
    }

    "empty file writes nothing and never reports progress" {
        write(tempFile(ByteArray(0))) shouldBe emptyList()
    }

    "small file reports completion exactly once" {
        write(tempFile(bytes(100))) shouldHaveSingleElement 1.0f
    }

    "power-of-two upload hits the halfway mark exactly" {
        val reports = write(tempFile(bytes(128 * 1024)))

        reports.shouldBeWholePercentSteps()
        reports shouldContain 0.5f
    }

    "ragged upload still ends at exactly one" {
        write(tempFile(bytes(100_000))).shouldBeWholePercentSteps()
    }
})
