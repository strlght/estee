package io.strlght.estee.util

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException

class CoroutinesTest : StringSpec({

    "captures exception into failure result" {
        val error = IllegalStateException("boom")
        runSuspendCatching { throw error } shouldBe Result.failure(error)
    }

    "rethrows cancellation instead of capturing it" {
        shouldThrow<CancellationException> {
            runSuspendCatching { throw CancellationException("cancelled") }
        }
    }
})
