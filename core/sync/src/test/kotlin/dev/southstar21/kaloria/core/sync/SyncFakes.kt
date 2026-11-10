package dev.southstar21.kaloria.core.sync

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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/** A row with an amount; stands in for any synced table. */
internal data class Row(val id: String, val amount: Int, val updatedAt: Long, val deleted: Boolean = false, val dirty: Boolean = true)

internal class FakeTable : SyncTable<Row> {
    val rows = linkedMapOf<String, Row>()
    override val collection = "water_entries"
    override suspend fun dirty() = rows.values.filter { it.dirty }
    override suspend fun saveIfNewer(rows: List<Row>): Int {
        val newer = rows.filter { r -> this.rows[r.id]?.let { r.updatedAt > it.updatedAt } ?: true }
        newer.forEach { this.rows[it.id] = it }
        return newer.size
    }
    override suspend fun markClean(rows: List<Row>) = rows.forEach { row ->
        this.rows[row.id]?.takeIf { it.updatedAt == row.updatedAt }?.let { this.rows[row.id] = it.copy(dirty = false) }
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

    fun write(id: String, amount: Int, at: Long) {
        rows[id] = Row(id, amount, at)
    }

    /** What an account switch that keeps the data does: everything is queued for upload again. */
    fun markAllDirty() = rows.replaceAll { _, row -> row.copy(dirty = true) }
}

/**
 * Just enough of PocketBase: PATCH/POST, GET by id, and a list sorted by `updated,id` that
 * understands the two filters the client sends, with the 409 last-write-wins hook.
 */
internal class FakePocketBase(private val pageSize: Int = 200) {
    val records = linkedMapOf<String, JsonObject>()
    val methods = mutableListOf<HttpMethod>()

    /** Called after each list page is served, with the page's number from 1. */
    var afterPage: (Int) -> Unit = {}

    /** While set, every write gets the same `updated`, as writes within one millisecond would. */
    var frozenClock = false

    private var clock = 0L
    private var pagesServed = 0

    fun timestamp(): String {
        if (!frozenClock || clock == 0L) clock++
        return LocalDateTime.of(2026, 10, 29, 0, 0).plusSeconds(clock).format(FORMAT) + "Z"
    }

    /** Another device writing [id] directly on the server. */
    fun write(id: String, amount: Int, at: Long) {
        records[id] = buildJsonObject {
            put("id", id)
            put("millilitres", amount)
            put("clientUpdatedAt", at)
            put("deleted", false)
            put("updated", timestamp())
        }
    }

    fun handle(scope: MockRequestHandleScope, request: HttpRequestData) = with(scope) {
        val segments = request.url.encodedPath.split('/').filter { it.isNotEmpty() }
        val id = segments.getOrNull(4)
        fun body() = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
        fun json(text: String, status: HttpStatusCode = HttpStatusCode.OK) = respond(text, status, headersOf(HttpHeaders.ContentType, "application/json"))
        methods += request.method
        when {
            request.url.encodedPath.endsWith("auth-refresh") -> json("""{"token":"refreshed","record":{"id":"owner123456789a"}}""")

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
                // Like PocketBase: ids must be 15 chars of [a-z0-9] and not taken.
                if (newId in records || !Regex("[a-z0-9]{15}").matches(newId)) return@with json("""{"status":400}""", HttpStatusCode.BadRequest)
                records[newId] = JsonObject(incoming + ("updated" to JsonPrimitive(timestamp())))
                json("{}")
            }

            id != null -> records[id]?.let { json(it.toString()) } ?: json("""{"status":404}""", HttpStatusCode.NotFound)

            else -> {
                val filter = request.url.parameters["filter"]
                val perPage = minOf(request.url.parameters["perPage"]?.toInt() ?: pageSize, pageSize)
                val items = records.values.filter { matches(filter, it) }
                    .sortedWith(compareBy<JsonObject>({ it.text("updated") }, { it.text("id") }))
                    .take(perPage)
                val page = json("""{"page":1,"items":${Json.encodeToString(JsonArray(items))}}""")
                afterPage(++pagesServed)
                page
            }
        }
    }

    private fun JsonObject.text(key: String) = this[key]!!.jsonPrimitive.content

    private fun matches(filter: String?, record: JsonObject): Boolean {
        if (filter == null) return true
        val updated = record.text("updated")
        SINCE.matchEntire(filter)?.let { return updated >= it.groupValues[1] }
        AFTER.matchEntire(filter)?.let {
            val (u, u2, i) = it.destructured
            check(u == u2)
            return updated > u || (updated == u && record.text("id") > i)
        }
        error("Unexpected filter: $filter")
    }

    fun client() = HttpClient(MockEngine { handle(this, it) }) {
        install(ContentNegotiation) { json() }
        expectSuccess = true
    }

    private companion object {
        val FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
        val SINCE = Regex("""updated >= '([^']+)'""")
        val AFTER = Regex("""\(updated > '([^']+)' \|\| \(updated = '([^']+)' && id > '([^']+)'\)\)""")
    }
}

internal class MemoryCursors : CursorStore {
    val map = mutableMapOf<String, String>()
    override suspend fun cursor(collection: String) = map[collection]
    override suspend fun setCursor(collection: String, value: String) {
        map[collection] = value
    }
}

/** Ids in PocketBase's format: 15 chars of [a-z0-9]. */
internal fun id(n: Int) = n.toString().padStart(15, 'a')
