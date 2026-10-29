package dev.southstar21.kaloria.core.sync

import dev.southstar21.kaloria.core.datastore.Session
import dev.southstar21.kaloria.core.network.PocketBaseClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A row with an amount; stands in for any synced table. */
private data class Row(val id: String, val amount: Int, val updatedAt: Long, val deleted: Boolean = false, val dirty: Boolean = true)

private class FakeTable : SyncTable<Row> {
    val rows = linkedMapOf<String, Row>()
    override val collection = "water_entries"
    override suspend fun dirty() = rows.values.filter { it.dirty }
    override suspend fun byIds(ids: List<String>) = ids.mapNotNull(rows::get)
    override suspend fun saveRemote(rows: List<Row>) = rows.forEach { this.rows[it.id] = it }
    override suspend fun markClean(row: Row) {
        rows[row.id]?.takeIf { it.updatedAt == row.updatedAt }?.let { rows[row.id] = it.copy(dirty = false) }
    }
    override fun id(row: Row) = row.id
    override fun updatedAt(row: Row) = row.updatedAt
    override fun toJson(row: Row) = buildJsonObject {
        put("millilitres", row.amount)
        put("clientUpdatedAt", row.updatedAt)
        put("deleted", row.deleted)
    }
    override fun fromJson(json: JsonObject) = Row(
        id = json["id"]!!.jsonPrimitive.content,
        amount = json["millilitres"]!!.jsonPrimitive.int,
        updatedAt = json["clientUpdatedAt"]!!.jsonPrimitive.long,
        deleted = json["deleted"]?.jsonPrimitive?.content == "true",
        dirty = false,
    )

    fun write(id: String, amount: Int, at: Long) { rows[id] = Row(id, amount, at) }
}

/** Just enough of PocketBase: PATCH/POST/list with `updated` cursors and the 409 LWW hook. */
private class FakePocketBase {
    val records = linkedMapOf<String, JsonObject>()
    private var clock = 0

    private fun timestamp() = "2026-10-29 00:00:%02d.000Z".format(++clock)

    fun handle(scope: MockRequestHandleScope, request: HttpRequestData) = with(scope) {
        val segments = request.url.encodedPath.split('/').filter { it.isNotEmpty() }
        val id = segments.getOrNull(4)
        fun body() = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
        fun json(text: String, status: HttpStatusCode = HttpStatusCode.OK) =
            respond(text, status, headersOf(HttpHeaders.ContentType, "application/json"))
        when {
            request.method == HttpMethod.Patch -> {
                val existing = records[id] ?: return@with json("""{"status":404}""", HttpStatusCode.NotFound)
                val incoming = body()
                if (incoming["clientUpdatedAt"]!!.jsonPrimitive.long < existing["clientUpdatedAt"]!!.jsonPrimitive.long) {
                    return@with json("""{"status":409}""", HttpStatusCode.Conflict)
                }
                records[id!!] = JsonObject(existing + incoming + ("updated" to JsonPrimitive(timestamp())))
                json("{}")
            }
            request.method == HttpMethod.Post -> {
                val incoming = body()
                val newId = incoming["id"]!!.jsonPrimitive.content
                records[newId] = JsonObject(incoming + ("updated" to JsonPrimitive(timestamp())))
                json("{}")
            }
            else -> {
                val since = request.url.parameters["filter"]?.substringAfter("'")?.substringBefore("'")
                val items = records.values.filter { since == null || it["updated"]!!.jsonPrimitive.content >= since }
                    .sortedBy { it["updated"]!!.jsonPrimitive.content }
                json("""{"page":1,"totalPages":1,"items":${Json.encodeToString(kotlinx.serialization.json.JsonArray(items))}}""")
            }
        }
    }
}

private class MemoryCursors : CursorStore {
    val map = mutableMapOf<String, String>()
    override suspend fun cursor(collection: String) = map[collection]
    override suspend fun setCursor(collection: String, value: String) { map[collection] = value }
}

class SyncEngineTest {
    private val server = FakePocketBase()
    private val http = HttpClient(MockEngine { server.handle(this, it) }) {
        install(ContentNegotiation) { json() }
        expectSuccess = true
    }
    private val session = Session("http://pb", "owner123456789a", "me@example.com", "token")

    private fun device() = FakeTable().let { table -> table to SyncEngine(PocketBaseClient(http), MemoryCursors(), listOf(table)) }

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
}
