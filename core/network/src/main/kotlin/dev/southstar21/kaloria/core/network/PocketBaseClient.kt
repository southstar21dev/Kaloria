package dev.southstar21.kaloria.core.network

import io.ktor.client.HttpClient
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
import javax.inject.Inject
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

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
     * written in the same millisecond is not.
     */
    suspend fun changesSince(baseUrl: String, token: String, collection: String, since: String?): List<JsonObject> {
        val all = mutableListOf<JsonObject>()
        var page = 1
        do {
            val result: PocketBaseList = authCall {
                client.get("${baseUrl.trimEnd('/')}/api/collections/$collection/records") {
                    header(HttpHeaders.Authorization, token)
                    if (since != null) parameter("filter", "updated >= '$since'")
                    parameter("sort", "updated,id")
                    parameter("perPage", PAGE_SIZE)
                    parameter("page", page)
                    // Counting every match on every page is the slowest part of a list; a short page is the end.
                    parameter("skipTotal", true)
                }.body()
            }
            all += result.items
            page++
        } while (result.items.size >= PAGE_SIZE)
        return all
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

    suspend fun isReachable(baseUrl: String): Boolean = runCatching {
        client.get("${baseUrl.trimEnd('/')}/api/health")
        true
    }.onFailure { android.util.Log.w("PocketBase", "health check failed for $baseUrl", it) }.getOrDefault(false)

    private suspend inline fun <T> authCall(block: () -> T): T = try {
        block()
    } catch (e: ClientRequestException) {
        if (e.response.status == HttpStatusCode.Unauthorized) throw PocketBaseAuthException("Session expired. Sign in again.")
        if (e.response.status == HttpStatusCode.BadRequest && e.response.call.request.url.encodedPath.endsWith("auth-with-password")) {
            throw PocketBaseAuthException("Wrong email or password.")
        }
        throw e
    }

    private companion object {
        const val PAGE_SIZE = 200
    }
}
