package com.forge.bridge.data.remote.api

import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.delay
import java.io.IOException
import kotlin.math.min
import kotlin.random.Random

/**
 * Retry helper with exponential backoff and jitter. Retries on:
 *  - IOException (network)
 *  - 408 / 425 / 429 / 5xx HTTP responses
 *
 * Honors `maxAttempts` and caps individual sleeps at `maxBackoffMillis`.
 */
object HttpRetry {

    suspend fun <T> withRetry(
        maxAttempts: Int = 3,
        baseDelayMillis: Long = 500,
        maxBackoffMillis: Long = 8_000,
        block: suspend (attempt: Int) -> T
    ): T {
        var lastErr: Throwable? = null
        for (attempt in 1..maxAttempts) {
            try {
                return block(attempt)
            } catch (t: Throwable) {
                lastErr = t
                if (attempt == maxAttempts || !isRetryable(t)) throw t
                val backoff = computeBackoff(attempt, baseDelayMillis, maxBackoffMillis)
                delay(backoff)
            }
        }
        throw lastErr ?: IllegalStateException("retry failed without an error")
    }

    fun isRetryable(t: Throwable): Boolean {
        if (t is IOException) return true
        if (t is ResponseException) {
            val code = t.response.status.value
            return code == 408 || code == 425 || code == 429 || code in 500..599
        }
        return false
    }

    fun computeBackoff(attempt: Int, base: Long, cap: Long): Long {
        // Decorrelated-ish exponential backoff with jitter.
        val expo = base * (1L shl (attempt - 1).coerceAtMost(10))
        val capped = min(expo, cap)
        val jitter = Random.nextLong(0, capped / 2 + 1)
        return capped / 2 + jitter
    }
}
