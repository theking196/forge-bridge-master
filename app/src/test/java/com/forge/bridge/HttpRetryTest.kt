package com.forge.bridge

import com.forge.bridge.data.remote.api.HttpRetry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class HttpRetryTest {

    @Test
    fun `succeeds on first attempt`() = runTest {
        val result = HttpRetry.withRetry(maxAttempts = 3) { 42 }
        assertEquals(42, result)
    }

    @Test
    fun `retries on IOException then succeeds`() = runTest {
        var attempts = 0
        val result = HttpRetry.withRetry(maxAttempts = 3, baseDelayMillis = 1) {
            attempts++
            if (attempts < 3) throw IOException("transient") else "ok"
        }
        assertEquals("ok", result)
        assertEquals(3, attempts)
    }

    @Test(expected = IllegalStateException::class)
    fun `does not retry non-retryable error`() = runTest {
        HttpRetry.withRetry(maxAttempts = 3, baseDelayMillis = 1) {
            throw IllegalStateException("hard fail")
        }
    }

    @Test
    fun `gives up after max attempts`() = runTest {
        var attempts = 0
        try {
            HttpRetry.withRetry(maxAttempts = 2, baseDelayMillis = 1) {
                attempts++
                throw IOException("always fails")
            }
            fail("expected exception")
        } catch (e: IOException) {
            assertEquals(2, attempts)
        }
    }

    @Test
    fun `backoff is bounded by cap`() {
        val capped = HttpRetry.computeBackoff(20, base = 100, cap = 5_000)
        assertTrue("expected <= 5000, got $capped", capped <= 5_000)
    }
}
