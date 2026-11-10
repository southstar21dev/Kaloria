package dev.southstar21.kaloria.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** How error statuses and non-JSON answers come out of the clients. */
class HttpErrorsTest {
    private var calls = 0

    private fun client(status: HttpStatusCode, body: String, contentType: String = "application/json") = HttpClient(
        MockEngine {
            calls++
            respond(body, status, headersOf(HttpHeaders.ContentType, contentType))
        },
    ) { kaloriaDefaults() }

    @Test
    fun `a barcode open food facts doesn't know is not found, not unavailable`() = runTest {
        // What the live API answers for a well-formed code it has no product for.
        val notFound = client(HttpStatusCode.NotFound, """{"code":"4006381333931","status":0,"status_verbose":"product not found"}""")
        assertNull(OpenFoodFactsDataSource(notFound).product("4006381333931"))
        assertEquals(1, calls)
    }

    @Test
    fun `rate limiting is not retried`() {
        val busy = client(HttpStatusCode.TooManyRequests, "{}")
        assertThrows(ClientRequestException::class.java) {
            runBlocking { OpenFoodFactsDataSource(busy).search("cola") }
        }
        assertEquals(1, calls)
    }

    @Test
    fun `a captive portal page is an unexpected response`() {
        val portal = client(HttpStatusCode.OK, "<html>Sign in to the Wi-Fi</html>", contentType = "text/html")
        assertThrows(UnexpectedResponseException::class.java) {
            runBlocking { PocketBaseClient(portal).changesSince("http://pb", "t", "foods", null) }
        }
        assertThrows(UnexpectedResponseException::class.java) {
            runBlocking { PocketBaseClient(portal).checkHealth("http://pb") }
        }
    }

    @Test
    fun `malformed json is an unexpected response`() {
        val broken = client(HttpStatusCode.OK, """{"page":1,"items":[{"id":""")
        assertThrows(UnexpectedResponseException::class.java) {
            runBlocking { PocketBaseClient(broken).changesSince("http://pb", "t", "foods", null) }
        }
    }

    @Test
    fun `a missing record is null`() = runTest {
        assertNull(PocketBaseClient(client(HttpStatusCode.NotFound, """{"status":404}""")).record("http://pb", "t", "foods", "aaaaaaaaaaaaaaa"))
    }

    @Test
    fun `wrong credentials and an expired session are auth errors`() {
        val wrong = assertThrows(PocketBaseAuthException::class.java) {
            runBlocking { PocketBaseClient(client(HttpStatusCode.BadRequest, """{"status":400}""")).authWithPassword("http://pb", "a@b.c", "x") }
        }
        assertEquals("Wrong email or password.", wrong.message)
        assertThrows(PocketBaseAuthException::class.java) {
            runBlocking { PocketBaseClient(client(HttpStatusCode.Unauthorized, """{"status":401}""")).refresh("http://pb", "t") }
        }
    }

    @Test
    fun `pull cursors are moved back by the overlap`() {
        assertEquals("2026-10-29 00:00:00.500Z", pocketBaseTimeMinus("2026-10-29 00:00:01.500Z", 1_000))
        assertEquals("2026-10-28 23:59:59.900Z", pocketBaseTimeMinus("2026-10-29 00:00:00.900Z", 1_000))
        assertEquals("not a date", pocketBaseTimeMinus("not a date", 1_000))
    }
}
