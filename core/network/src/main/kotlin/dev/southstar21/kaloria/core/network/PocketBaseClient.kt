package dev.southstar21.kaloria.core.network

import io.ktor.client.HttpClient
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.ContentConvertException
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.inject.Inject
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Serializable
data class PocketBaseAuth(val token: String, val record: PocketBaseUser)

@Serializable
data class PocketBaseUser(val id: String, val email: String = "")

@Serializable
internal data class PocketBaseList(val page: Int, val items: List<JsonObject>)

@Serializable
private data class PasswordAuth(val identity: String, val password: String)

/** The server said no to the credentials or session (401/403/400 on auth). */
class PocketBaseAuthException(message: String) : Exception(message)

/** The server holds a newer version of the record (the last-write-wins hook answered 409). */
class StaleRecordException : Exception("A newer version exists on the server")

/**
 * The answer wasn't the JSON PocketBase sends: e.g. a captive portal's or proxy's page. Usually
 * passes, so it's worth trying again later.
 */
class UnexpectedResponseException(cause: Throwable) : Exception("Unexpected server response", cause)

/** Minimal PocketBase REST client: password auth, change feed by `updated`, and upserts. */
class PocketBaseClient @Inject constructor(private val client: HttpClient) {

    suspend fun authWithPassword(baseUrl: String, email: String, password: String): PocketBaseAuth = authCall {
        client.post("${baseUrl.trimEnd('/')}/api/collections/users/auth-with-password") {
            contentType(ContentType.Application.Json)
            setBody(PasswordAuth(email.trim(), password))
        }.body()
    }

    /** Exchanges a still-valid token for a fresh one, extending the session. */
    suspend fun refresh(baseUrl: String, token: String): PocketBaseAuth = authCall {
        client.post("${baseUrl.trimEnd('/')}/api/collections/users/auth-refresh") {
            header(HttpHeaders.Authorization, token)
        }.body()
    }

    /**
     * Every record in [collection] changed at or after [since] (PocketBase datetime, or null for
     * all), oldest first. `>=` rather than `>`: re-reading one record is harmless, missing one
     * written in the same millisecond is not. The cursor is also moved back by [OVERLAP], so a
     * record stamped just before it but committed just after the last pull isn't missed.
     *
     * Pages follow on from the last record of the previous one (by `updated`, then `id`) rather than
     * by page number: a record changing while the pages are read moves to the end of the list, which
     * would shift every later record back a page and skip one.
     */
    suspend fun changesSince(baseUrl: String, token: String, collection: String, since: String?): List<JsonObject> {
        val all = mutableListOf<JsonObject>()
        var after: Pair<String, String>? = null
        do {
            val filter = after?.let { (updated, id) -> "(updated > '$updated' || (updated = '$updated' && id > '$id'))" }
                ?: since?.let { "updated >= '${pocketBaseTimeMinus(it, OVERLAP)}'" }
            val result: PocketBaseList = authCall {
                client.get("${baseUrl.trimEnd('/')}/api/collections/$collection/records") {
                    header(HttpHeaders.Authorization, token)
                    if (filter != null) parameter("filter", filter)
                    parameter("sort", "updated,id")
                    parameter("perPage", PAGE_SIZE)
                    // Counting every match on every page is the slowest part of a list; a short page is the end.
                    parameter("skipTotal", true)
                }.body()
            }
            all += result.items
            val last = result.items.lastOrNull()
            after = last?.let { (it.text("updated") ?: return@let null) to (it.text("id") ?: return@let null) }
        } while (result.items.size >= PAGE_SIZE && after != null)
        return all
    }

    /** The record [id], or null if the server has none this account may see. */
    suspend fun record(baseUrl: String, token: String, collection: String, id: String): JsonObject? = try {
        authCall {
            client.get("${baseUrl.trimEnd('/')}/api/collections/$collection/records/$id") {
                header(HttpHeaders.Authorization, token)
            }.body()
        }
    } catch (e: ClientRequestException) {
        if (e.response.status == HttpStatusCode.NotFound) null else throw e
    }

    /**
     * Creates or updates the record [id]. Throws [StaleRecordException] when the server already
     * has a newer version. [likelyNew] tries the create first, which saves a 404 round trip per row
     * when most rows have never been on the server (the first sync of a device that has data).
     */
    suspend fun upsert(
        baseUrl: String,
        token: String,
        collection: String,
        id: String,
        ownerId: String,
        fields: JsonObject,
        likelyNew: Boolean = false,
    ) {
        val url = "${baseUrl.trimEnd('/')}/api/collections/$collection/records"
        val body = JsonObject(fields + ("owner" to JsonPrimitive(ownerId)))
        suspend fun update() = authCall {
            client.patch("$url/$id") {
                header(HttpHeaders.Authorization, token)
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }
        suspend fun create() = authCall {
            client.post(url) {
                header(HttpHeaders.Authorization, token)
                contentType(ContentType.Application.Json)
                setBody(JsonObject(body + ("id" to JsonPrimitive(id))))
            }
        }
        try {
            if (likelyNew) {
                try {
                    create()
                    return
                } catch (e: ClientRequestException) {
                    // 400: the id is taken, so the record exists; anything else is a real failure.
                    if (e.response.status != HttpStatusCode.BadRequest) throw e
                }
            }
            try {
                update()
            } catch (e: ClientRequestException) {
                if (e.response.status == HttpStatusCode.NotFound && !likelyNew) create() else throw e
            }
        } catch (e: ClientRequestException) {
            if (e.response.status == HttpStatusCode.Conflict) throw StaleRecordException()
            throw e
        }
    }

    /**
     * Throws unless [baseUrl] answers like a PocketBase server: an IOException when it can't be
     * reached, [UnexpectedResponseException] or a ResponseException when something else answers.
     */
    suspend fun checkHealth(baseUrl: String) {
        val health: JsonObject = parsing { client.get("${baseUrl.trimEnd('/')}/api/health").body() }
        if (health["code"] == null && health["message"] == null) throw UnexpectedResponseException(IllegalStateException("Not a PocketBase health response"))
    }

    private suspend inline fun <T> authCall(block: () -> T): T = parsing {
        try {
            block()
        } catch (e: ClientRequestException) {
            if (e.response.status == HttpStatusCode.Unauthorized) throw PocketBaseAuthException("Session expired. Sign in again.")
            if (e.response.status == HttpStatusCode.BadRequest && e.response.call.request.url.encodedPath.endsWith("auth-with-password")) {
                throw PocketBaseAuthException("Wrong email or password.")
            }
            throw e
        }
    }

    /** A body that isn't PocketBase's JSON (a captive portal, a proxy's error page) becomes [UnexpectedResponseException]. */
    private suspend inline fun <T> parsing(block: () -> T): T = try {
        block()
    } catch (e: ContentConvertException) {
        throw UnexpectedResponseException(e)
    } catch (e: NoTransformationFoundException) {
        throw UnexpectedResponseException(e)
    } catch (e: SerializationException) {
        throw UnexpectedResponseException(e)
    }

    private fun JsonObject.text(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull

    private companion object {
        const val PAGE_SIZE = 200
        const val OVERLAP = 1_000L
    }
}

private val POCKETBASE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

/** A PocketBase datetime (`2026-10-29 08:15:00.123Z`) moved back by [millis]; unchanged if it isn't one. */
internal fun pocketBaseTimeMinus(value: String, millis: Long): String = try {
    POCKETBASE_TIME.format(LocalDateTime.parse(value.removeSuffix("Z"), POCKETBASE_TIME).minus(Duration.ofMillis(millis))) + "Z"
} catch (_: DateTimeParseException) {
    value
}
