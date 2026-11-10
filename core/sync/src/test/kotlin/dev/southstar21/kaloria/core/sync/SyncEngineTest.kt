package dev.southstar21.kaloria.core.sync

import dev.southstar21.kaloria.core.datastore.Session
import dev.southstar21.kaloria.core.network.PocketBaseClient
import io.ktor.client.HttpClient
import io.ktor.http.HttpMethod
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncEngineTest {
    private val server = FakePocketBase()
    private val http = server.client()
    private val session = Session("http://pb", "owner123456789a", "me@example.com", "token")

    private fun device(cursors: MemoryCursors = MemoryCursors(), client: HttpClient = http) = FakeTable().let { table -> table to SyncEngine(PocketBaseClient(client), cursors, listOf(table)) }

    @Test
    fun `dirty rows are pushed with the owner and then clean`() = runTest {
        val (table, engine) = device()
        table.write("aaaaaaaaaaaaaaa", 250, at = 1_000)
        val report = engine.sync(session)
        assertEquals(1, report.pushed)
        assertEquals("owner123456789a", server.records["aaaaaaaaaaaaaaa"]!!["owner"]!!.jsonPrimitive.content)
        assertFalse(table.rows.getValue("aaaaaaaaaaaaaaa").dirty)
    }

    @Test
    fun `two devices converge and the newer edit wins in both directions`() = runTest {
        val (phone, phoneSync) = device()
        val (tablet, tabletSync) = device()

        phone.write("aaaaaaaaaaaaaaa", 250, at = 1_000)
        phoneSync.sync(session)
        tabletSync.sync(session)
        assertEquals(250, tablet.rows.getValue("aaaaaaaaaaaaaaa").amount)

        // Tablet edits later; phone's own later sync must take it.
        tablet.write("aaaaaaaaaaaaaaa", 500, at = 2_000)
        tabletSync.sync(session)
        phoneSync.sync(session)
        assertEquals(500, phone.rows.getValue("aaaaaaaaaaaaaaa").amount)

        // Phone was offline and edited at a time *before* the tablet's edit: stale, rejected, replaced.
        phone.write("aaaaaaaaaaaaaaa", 330, at = 1_500)
        val report = phoneSync.sync(session)
        assertEquals(1, report.rejectedAsStale)
        assertEquals(500, phone.rows.getValue("aaaaaaaaaaaaaaa").amount)
        assertFalse(phone.rows.getValue("aaaaaaaaaaaaaaa").dirty)
        assertEquals(500, server.records["aaaaaaaaaaaaaaa"]!!["millilitres"]!!.jsonPrimitive.int)
    }

    @Test
    fun `soft deletes travel like edits`() = runTest {
        val (phone, phoneSync) = device()
        val (tablet, tabletSync) = device()
        phone.write("bbbbbbbbbbbbbbb", 250, at = 1_000)
        phoneSync.sync(session)
        tabletSync.sync(session)

        phone.rows["bbbbbbbbbbbbbbb"] = phone.rows.getValue("bbbbbbbbbbbbbbb").copy(deleted = true, updatedAt = 2_000, dirty = true)
        phoneSync.sync(session)
        tabletSync.sync(session)
        assertTrue(tablet.rows.getValue("bbbbbbbbbbbbbbb").deleted)
    }

    @Test
    fun `a local edit newer than the pulled copy is kept`() = runTest {
        val (phone, phoneSync) = device()
        phone.write("ccccccccccccccc", 250, at = 1_000)
        phoneSync.sync(session)
        // Edited again locally after the push; the pull of our own older copy must not undo it.
        phone.write("ccccccccccccccc", 400, at = 3_000)
        phoneSync.sync(session)
        assertEquals(400, phone.rows.getValue("ccccccccccccccc").amount)
        assertEquals(400, server.records["ccccccccccccccc"]!!["millilitres"]!!.jsonPrimitive.int)
    }

    @Test
    fun `a row the server refuses is skipped and the rest still sync`() = runTest {
        val (table, engine) = device()
        table.write("hc:not-a-valid-id", 80, at = 1_000)
        table.write("ddddddddddddddd", 250, at = 1_000)
        val report = engine.sync(session)
        assertEquals(1, report.rejected)
        assertEquals(1, report.pushed)
        assertTrue(table.rows.getValue("hc:not-a-valid-id").dirty)
        assertFalse(table.rows.getValue("ddddddddddddddd").dirty)
    }

    @Test
    fun `the first sync creates rows directly and later syncs update them`() = runTest {
        val (table, engine) = device()
        table.write("eeeeeeeeeeeeeee", 250, at = 1_000)
        engine.sync(session)
        assertEquals(listOf(HttpMethod.Post, HttpMethod.Get), server.methods)

        server.methods.clear()
        table.write("eeeeeeeeeeeeeee", 300, at = 2_000)
        engine.sync(session)
        assertEquals(listOf(HttpMethod.Patch, HttpMethod.Get), server.methods)
    }

    @Test
    fun `a first sync against existing records falls back to updating them`() = runTest {
        val (phone, phoneSync) = device()
        phone.write("fffffffffffffff", 250, at = 1_000)
        phoneSync.sync(session)
        // A second device (no cursor yet) that already has the same row, edited later.
        val (tablet, tabletSync) = device()
        tablet.write("fffffffffffffff", 400, at = 2_000)
        val report = tabletSync.sync(session)
        assertEquals(1, report.pushed)
        assertEquals(400, server.records["fffffffffffffff"]!!["millilitres"]!!.jsonPrimitive.int)
    }

    @Test
    fun `a first pull over several pages gets every record, even one that changes meanwhile`() = runTest {
        repeat(450) { server.write(id(it), it, at = 1_000) }
        // Another device edits a record from the first page while the second is being read: it
        // moves to the end of the list. Paging by number would then skip a record.
        server.afterPage = { page -> if (page == 1) server.write(id(10), 999, at = 2_000) }
        val (phone, phoneSync) = device()

        phoneSync.sync(session)

        assertEquals(450, phone.rows.size)
        assertEquals(999, phone.rows.getValue(id(10)).amount)
    }

    @Test
    fun `records sharing a timestamp across a page boundary are all pulled`() = runTest {
        server.frozenClock = true
        repeat(250) { server.write(id(it), it, at = 1_000) }
        server.frozenClock = false
        val (phone, phoneSync) = device()

        phoneSync.sync(session)

        assertEquals(250, phone.rows.size)
    }

    @Test
    fun `an edit refused as older is replaced even when the cursor has passed the server copy`() = runTest {
        val (tablet, tabletSync) = device()
        tablet.write(id(1), 500, at = 2_000)
        tabletSync.sync(session)
        val (phone, phoneSync) = device()
        phoneSync.sync(session)
        // More changes elsewhere move the phone's cursor well past the record.
        repeat(5) { server.write(id(100 + it), it, at = 3_000) }
        phoneSync.sync(session)

        // The phone's clock is behind: its edit is stamped before the copy it edits.
        phone.write(id(1), 330, at = 1_500)
        val report = phoneSync.sync(session)

        assertEquals(1, report.rejectedAsStale)
        assertEquals(500, phone.rows.getValue(id(1)).amount)
        assertFalse(phone.rows.getValue(id(1)).dirty)
        assertFalse(phoneSync.hasPending())
    }

    @Test
    fun `rows that fail are left for the next run instead of every round`() = runTest {
        val (table, engine) = device()
        table.write("hc:not-a-valid-id", 80, at = 1_000)
        table.write(id(1), 250, at = 1_000)

        val report = engine.syncUntilSettled(session, maxRounds = 3)

        assertEquals(1, report.rejected)
        assertEquals(1, report.pushed)
        // One attempt for the refused row (create, then update) and one pull: a single round.
        assertEquals(1, server.methods.count { it == HttpMethod.Patch })
        assertEquals(1, server.methods.count { it == HttpMethod.Get })
        assertTrue(table.rows.getValue("hc:not-a-valid-id").dirty)
    }

    @Test
    fun `a row written while a run is going is pushed in its next round`() = runTest {
        val (table, engine) = device()
        table.write("hc:not-a-valid-id", 80, at = 1_000)
        var edited = false
        server.afterPage = {
            if (!edited) {
                edited = true
                table.write(id(2), 300, at = 2_000)
            }
        }

        val report = engine.syncUntilSettled(session, maxRounds = 3)

        assertEquals(1, report.pushed)
        assertFalse(table.rows.getValue(id(2)).dirty)
    }

    @Test
    fun `after switching to another server every row is uploaded again`() = runTest {
        val (phone, phoneSync) = device()
        repeat(3) { phone.write(id(it), it, at = 1_000) }
        phoneSync.sync(session)
        assertTrue(phone.rows.values.none { it.dirty })

        // Same account on a fresh server: the sign-in marks everything dirty and starts the cursors over.
        val newServer = FakePocketBase()
        phone.markAllDirty()
        val moved = SyncEngine(PocketBaseClient(newServer.client()), MemoryCursors(), listOf(phone))
        val report = moved.sync(session.copy(serverUrl = "http://pb2"))

        assertEquals(3, report.pushed)
        assertEquals(setOf(id(0), id(1), id(2)), newServer.records.keys)
        assertTrue(phone.rows.values.none { it.dirty })
    }
}
