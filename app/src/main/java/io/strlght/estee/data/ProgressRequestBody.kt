package io.strlght.estee.data

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.Buffer
import okio.source
import java.io.File

class ProgressRequestBody(
    private val file: File,
    private val contentType: MediaType? = null,
    private val onProgress: (fraction: Float) -> Unit,
) : RequestBody() {

    override fun contentType(): MediaType? = contentType

    override fun contentLength(): Long = file.length()

    override fun writeTo(sink: BufferedSink) {
        file.source().use { source ->
            val buffer = Buffer()
            val length = file.length()
            var total = 0L
            var lastReportedPercent = -1

            while (true) {
                val read = source.read(buffer, SEGMENT_SIZE)
                if (read == -1L) break
                sink.write(buffer, read)
                total += read

                if (length > 0L) {
                    val percent = ((total.toFloat() / length) * 100)
                        .coerceAtMost(100f)
                        .toInt()
                    if (percent != lastReportedPercent) {
                        lastReportedPercent = percent
                        onProgress(percent / 100f)
                    }
                } else {
                    onProgress(-1f)
                }
            }
        }
    }

    private companion object {
        const val SEGMENT_SIZE = 64L * 1024L
    }
}
