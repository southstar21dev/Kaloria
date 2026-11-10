package dev.southstar21.kaloria.core.sync

import dev.southstar21.kaloria.core.datastore.Session
import dev.southstar21.kaloria.core.network.PocketBaseClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The sync worker's body: what each kind of failure records and whether WorkManager retries. */
class SyncRunnerTest {
    private class FakeSessions(var session: Session?) : SyncSessions {
        val cursors = MemoryCursors()
        var error: String? = null
        var success: SyncReport? = null
        override suspend fun current() = session
        override suspend fun updateToken(userId: String, token: String) {
            session = session?.copy(token = token)
        }
        override fun cursors(userId: String): CursorStore = cursors
        override suspend fun recordSuccess(userId: String, at: Long, report: SyncReport) {
            success = report
            error = null
        }
        override suspend fun recordError(userId: String, message: String) {
            error = message
        }
        override suspend fun expire(userId: String, message: String) {
            session = null
            error = message
        }
    }

    private val sessions = FakeSessions(Session("http://pb", "owner123456789a", "me@example.com", "token"))
    private val table = FakeTable()
    private var pulled = 0
    private var signedOut = 0

    private fun runner(http: HttpClient, lock: SyncLock = SyncLock()) = SyncRunner(
        sessions = sessions,
        client = PocketBaseClient(http),
        tables = listOf(table),
        lock = lock,
        now = { 42L },
        onPulled = { pulled++ },
        onSignedOut = { signedOut++ },
    )

    private fun answering(status: HttpStatusCode, body: String = "{}", contentType: String = "application/json") = HttpClient(
        MockEngine { respond(body, status, headersOf(HttpHeaders.ContentType, contentType)) },
    ) {
        install(ContentNegotiation) { json() }
        expectSuccess = true
    }

    @Test
    fun `an expired session signs out and keeps saying why`() = runTest {
        val outcome = runner(answering(HttpStatusCode.Unauthorized)).run()

        assertEquals(SyncRunner.Outcome.Failure, outcome)
        assertNull(sessions.session)
        assertEquals("Session expired. Sign in again.", sessions.error)
        assertEquals(1, signedOut)
    }

    @Test
    fun `an unreachable server is retried`() = runTest {
        val down = HttpClient(MockEngine { throw IOException("connection refused") })

        assertEquals(SyncRunner.Outcome.Retry, runner(down).run())
        assertEquals("Server unreachable", sessions.error)
    }

    @Test
    fun `a captive portal page is retried, not a silent failure`() = runTest {
        val portal = answering(HttpStatusCode.OK, "<html>Sign in to the Wi-Fi</html>", contentType = "text/html")

        assertEquals(SyncRunner.Outcome.Retry, runner(portal).run())
        assertEquals("Unexpected server response", sessions.error)
    }

    @Test
    fun `a server error is retried`() = runTest {
        assertEquals(SyncRunner.Outcome.Retry, runner(answering(HttpStatusCode.InternalServerError)).run())
        assertEquals("Server error 500", sessions.error)
    }

    @Test
    fun `refused rows are recorded, not reported as a clean sync`() = runTest {
        val server = FakePocketBase()
        table.write("hc:not-a-valid-id", 80, at = 1_000)
        table.write(id(1), 250, at = 1_000)
        server.write(id(2), 100, at = 1_000)

        assertEquals(SyncRunner.Outcome.Success, runner(server.client()).run())

        assertEquals(1, sessions.success?.rejected)
        assertEquals(1, sessions.success?.pushed)
        assertEquals(1, pulled)
        assertEquals("refreshed", sessions.session?.token)
    }

    @Test
    fun `nothing happens when signed out`() = runTest {
        sessions.session = null
        var requests = 0
        val http = HttpClient(
            MockEngine {
                requests++
                respond("{}")
            },
        )

        assertEquals(SyncRunner.Outcome.Success, runner(http).run())
        assertEquals(0, requests)
    }

    @Test
    fun `runs never overlap`() = runTest {
        val server = FakePocketBase()
        val arrived = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val inFlight = AtomicInteger()
        val overlapped = AtomicBoolean()
        val http = HttpClient(
            MockEngine { request ->
                if (inFlight.incrementAndGet() > 1) overlapped.set(true)
                if (request.url.encodedPath.endsWith("auth-refresh") && !gate.isCompleted) {
                    arrived.complete(Unit)
                    gate.await()
                }
                server.handle(this, request).also { inFlight.decrementAndGet() }
            },
        ) {
            install(ContentNegotiation) { json() }
            expectSuccess = true
        }
        val lock = SyncLock()
        val first = async { runner(http, lock).run() }
        val second = async { runner(http, lock).run() }
        // The first run is held mid-request; give the second real time to collide with it.
        arrived.await()
        withContext(Dispatchers.Default) { delay(200) }
        gate.complete(Unit)

        assertEquals(SyncRunner.Outcome.Success, first.await())
        assertEquals(SyncRunner.Outcome.Success, second.await())
        assertFalse(overlapped.get())
        assertTrue(server.methods.isNotEmpty())
    }
}
