package dev.southstar21.kaloria.core.datastore

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * DataStore does real file I/O on its own threads, so these use runBlocking rather than virtual time.
 * The session DataStore is a process-wide singleton, so each test starts by signing out.
 */
@RunWith(RobolectricTestRunner::class)
class SessionStoreTest {
    private lateinit var store: SessionStore

    private val session = Session(serverUrl = "https://pb.example.com", userId = "user1", email = "a@example.com", token = "secret-token")

    @Before
    fun setUp() {
        FakeAndroidKeyStore.install()
        store = SessionStore(ApplicationProvider.getApplicationContext<Context>())
        runBlocking { store.clear() }
    }

    @Test
    fun `save then current round-trips the session, token included`() = runBlocking {
        assertNull(store.current())
        assertFalse(store.isSignedIn())

        store.save(session)

        assertEquals(session, store.current())
        assertTrue(store.isSignedIn())
        assertEquals(session.serverUrl, store.serverUrl.first())
    }

    @Test
    fun `the token survives a new store instance using the same key`() = runBlocking {
        store.save(session)

        val again = SessionStore(ApplicationProvider.getApplicationContext<Context>())

        assertEquals(session, again.current())
    }

    @Test
    fun `updateToken replaces only the token`() = runBlocking {
        store.save(session)

        store.updateToken("new-token")

        assertEquals(session.copy(token = "new-token"), store.current())
    }

    @Test
    fun `a token that can't be decrypted means signed out, not a crash`() = runBlocking {
        store.save(session)
        FakeAndroidKeyStore.keys.clear() // e.g. app data restored onto another phone

        val restored = SessionStore(ApplicationProvider.getApplicationContext<Context>())

        assertNull(restored.current())
        // The encrypted token is still stored; only decrypting it fails.
        assertTrue(restored.isSignedIn())
    }

    @Test
    fun `clear signs out and forgets cursors but keeps the server`() = runBlocking {
        store.save(session)
        store.setCursor("foods", "2026-10-29 00:00:00.000Z")
        store.recordSuccess(123L)

        store.clear()

        assertNull(store.current())
        assertFalse(store.isSignedIn())
        assertNull(store.cursor("foods"))
        assertEquals(SyncInfo(), store.syncInfo.first())
        assertEquals(session.serverUrl, store.serverUrl.first())
    }

    @Test
    fun `cursors are kept per collection`() = runBlocking {
        store.setCursor("foods", "a")
        store.setCursor("log_entries", "b")

        assertEquals("a", store.cursor("foods"))
        assertEquals("b", store.cursor("log_entries"))
        assertNull(store.cursor("goals"))
    }

    @Test
    fun `recordError then recordSuccess, and save clears the error`() = runBlocking {
        store.recordError("offline")
        assertEquals(SyncInfo(lastError = "offline"), store.syncInfo.first())

        store.recordSuccess(10L)
        assertEquals(SyncInfo(lastSyncAt = 10L), store.syncInfo.first())

        store.recordError("401")
        store.save(session)
        assertEquals(SyncInfo(lastSyncAt = 10L), store.syncInfo.first())
    }

    @Test
    fun `syncInfo doesn't re-emit for cursor writes`() = runBlocking {
        val seen = CopyOnWriteArrayList<SyncInfo>()
        val collector = launch(Dispatchers.Default) { store.syncInfo.collect { seen += it } }
        withTimeout(5_000) { while (seen.isEmpty()) kotlinx.coroutines.delay(10) }

        store.setCursor("foods", "c1")
        store.setCursor("foods", "c2")
        store.updateToken("t")
        // Give the collector time to see those writes, if it were going to.
        kotlinx.coroutines.delay(300)
        store.recordSuccess(99L)
        withTimeout(5_000) { while (seen.size < 2) kotlinx.coroutines.delay(10) }
        kotlinx.coroutines.delay(100)
        collector.cancel()

        assertEquals(listOf(SyncInfo(), SyncInfo(lastSyncAt = 99L)), seen.toList())
    }
}
