package dev.southstar21.kaloria.core.network

import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RetryTest {
    @Test
    fun `a dropped connection is retried until it works`() = runTest {
        var calls = 0
        val result = retryingTransient {
            calls++
            if (calls < 3) throw IOException("connection reset") else "ok"
        }
        assertEquals("ok", result)
        assertEquals(3, calls)
    }

    @Test
    fun `by default it tries once and retries three times before reporting the failure`() = runTest {
        var calls = 0
        try {
            retryingTransient {
                calls++
                throw IOException("down")
            }
        } catch (_: IOException) {
        }
        assertEquals(4, calls)
    }

    @Test
    fun `a real error is not retried`() = runTest {
        var calls = 0
        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                retryingTransient(delaysMillis = listOf(1, 1)) {
                    calls++
                    error("bad data")
                }
            }
        }
        assertEquals(1, calls)
    }
}
