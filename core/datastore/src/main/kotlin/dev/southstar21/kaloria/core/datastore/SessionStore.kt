package dev.southstar21.kaloria.core.datastore

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class Session(val serverUrl: String, val userId: String, val email: String, val token: String)

/**
 * How the last sync went. [rejected]: rows the server refused, which stay unsynced. [stale]: local
 * edits the server refused as older than its copy, which replaced them.
 */
data class SyncInfo(
    val lastSyncAt: Long? = null,
    val lastError: String? = null,
    val rejected: Int = 0,
    val stale: Int = 0,
)

/** The account this phone's local data belongs to: the one it was last signed in to. */
data class SyncedAccount(val serverUrl: String, val userId: String, val email: String)

private val Context.sessionStore by preferencesDataStore("session")

/**
 * The signed-in PocketBase session and per-collection sync cursors. The auth token is encrypted
 * with an AES-GCM key that never leaves the Android Keystore.
 *
 * The writes a sync run makes take the user id it started with and are dropped if another account
 * (or nobody) is signed in by then, so a run finishing after sign-out can't leave a token or cursors
 * behind for the next sign-in.
 */
@Singleton
class SessionStore @Inject constructor(@ApplicationContext context: Context) {
    private val store: DataStore<Preferences> = context.sessionStore

    // Sync writes cursors and timestamps to this store constantly; only decrypt when the session
    // itself changed, and never on the main thread.
    val session: Flow<Session?> = store.data
        .map { prefs -> listOf(prefs[SERVER], prefs[USER], prefs[EMAIL], prefs[TOKEN]) }
        .distinctUntilChanged()
        .map { (server, user, email, token) ->
            val plain = (token?.let(::decrypt) as? Token.Plain)?.value ?: return@map null
            Session(server ?: return@map null, user ?: return@map null, email.orEmpty(), plain)
        }
        .flowOn(Dispatchers.Default)

    /** Last used server, kept after sign-out so the field stays filled. */
    val serverUrl: Flow<String?> = store.data.map { it[SERVER] }.distinctUntilChanged()

    // Cursor and token writes don't change these; don't re-emit for them.
    val syncInfo: Flow<SyncInfo> = store.data
        .map { SyncInfo(it[LAST_SYNC], it[LAST_ERROR], it[REJECTED] ?: 0, it[STALE] ?: 0) }
        .distinctUntilChanged()

    /**
     * The signed-in session, or null. A token this phone can't decrypt (app data moved to another
     * phone, whose Keystore doesn't have the key) signs out for good, cursors included, so the next
     * sign-in starts clean instead of trusting cursors for data that isn't here.
     */
    suspend fun current(): Session? {
        val prefs = store.data.first()
        val encrypted = prefs[TOKEN] ?: return null
        val token = withContext(Dispatchers.Default) { decrypt(encrypted) }
        if (token == Token.Lost) store.edit { if (it[TOKEN] == encrypted) signOut(it, error = KEY_LOST_MESSAGE) }
        // Unavailable: the Keystore may answer next time, so stay signed in.
        return (token as? Token.Plain)?.let { prefs.session(it.value) }
    }

    suspend fun isSignedIn(): Boolean = current() != null

    /**
     * The account the local data was last synced with, kept after sign-out. Installs from before this
     * was stored fall back to the signed-in account.
     */
    suspend fun syncedAccount(): SyncedAccount? = store.data.first().syncedAccount()

    /**
     * Signs in, and makes [session]'s account the owner of the local data. Cursors start over: the
     * ones stored belong to an earlier sign-in and may not match what this phone holds.
     */
    suspend fun save(session: Session) = store.edit {
        it.removeCursors()
        it[SERVER] = session.serverUrl
        it[USER] = session.userId
        it[EMAIL] = session.email
        it[TOKEN] = encrypt(session.token)
        it[SYNCED_SERVER] = session.serverUrl
        it[SYNCED_USER] = session.userId
        it[SYNCED_EMAIL] = session.email
        it.remove(LAST_ERROR)
        it.remove(REJECTED)
        it.remove(STALE)
    }

    suspend fun updateToken(userId: String, token: String) = store.edit { if (it[USER] == userId) it[TOKEN] = encrypt(token) }

    /**
     * Signs out and forgets the cursors, so the next sign-in pulls everything again. The server and
     * the account the local data belongs to are kept; so is [error], to tell the user why.
     */
    suspend fun clear(error: String? = null) = store.edit { signOut(it, error) }

    /** [clear], unless someone other than [userId] has signed in meanwhile. */
    suspend fun expire(userId: String, error: String) = store.edit { if (it[USER] == userId) signOut(it, error) }

    suspend fun cursor(collection: String): String? = store.data.first()[cursorKey(collection)]

    suspend fun setCursor(userId: String, collection: String, value: String) = store.edit {
        if (it[USER] == userId) it[cursorKey(collection)] = value
    }

    suspend fun recordSuccess(userId: String, at: Long, rejected: Int = 0, stale: Int = 0) = store.edit {
        if (it[USER] != userId) return@edit
        it[LAST_SYNC] = at
        it.remove(LAST_ERROR)
        it[REJECTED] = rejected
        it[STALE] = stale
    }

    suspend fun recordError(userId: String, message: String) = store.edit { if (it[USER] == userId) it[LAST_ERROR] = message }

    private fun signOut(prefs: MutablePreferences, error: String?) {
        val synced = prefs.syncedAccount()
        val server = prefs[SERVER]
        prefs.clear()
        server?.let { prefs[SERVER] = it }
        synced?.let {
            prefs[SYNCED_SERVER] = it.serverUrl
            prefs[SYNCED_USER] = it.userId
            prefs[SYNCED_EMAIL] = it.email
        }
        error?.let { prefs[LAST_ERROR] = it }
    }

    private fun Preferences.session(token: String): Session? {
        val server = this[SERVER]
        val user = this[USER]
        return if (server != null && user != null) Session(server, user, this[EMAIL].orEmpty(), token) else null
    }

    private fun Preferences.syncedAccount(): SyncedAccount? {
        val server = this[SYNCED_SERVER] ?: this[SERVER].takeIf { this[USER] != null }
        val user = this[SYNCED_USER] ?: this[USER]
        val email = this[SYNCED_EMAIL] ?: this[EMAIL]
        return if (server != null && user != null) SyncedAccount(server, user, email.orEmpty()) else null
    }

    private fun MutablePreferences.removeCursors() {
        asMap().keys.filter { it.name.startsWith(CURSOR_PREFIX) }.forEach { remove(it) }
    }

    private fun cursorKey(collection: String) = stringPreferencesKey("$CURSOR_PREFIX$collection")

    /** Looked up once: each Keystore lookup is a binder call to the keystore service. */
    @Volatile private var cachedKey: SecretKey? = null

    private fun key(): SecretKey = cachedKey ?: loadOrCreateKey().also { cachedKey = it }

    private fun loadOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
        }.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.iv + cipher.doFinal(plain.toByteArray())
        return Base64.encodeToString(sealed, Base64.NO_WRAP)
    }

    private sealed interface Token {
        data class Plain(val value: String) : Token

        /** Encrypted with a key this phone doesn't have (app data restored onto another phone). */
        data object Lost : Token

        /** The Keystore failed for some other reason, which may pass. */
        data object Unavailable : Token
    }

    @Suppress("SwallowedException") // the outcome is the answer; there's nothing more to report
    private fun decrypt(encoded: String): Token = try {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, IV_SIZE))
        Token.Plain(String(cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE)))
    } catch (e: AEADBadTagException) {
        Token.Lost
    } catch (e: KeyPermanentlyInvalidatedException) {
        Token.Lost
    } catch (e: IllegalArgumentException) {
        Token.Lost // not valid Base64, or too short to hold an IV
    } catch (e: GeneralSecurityException) {
        Token.Unavailable
    } catch (e: java.security.ProviderException) {
        Token.Unavailable
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "kaloria_session"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val CURSOR_PREFIX = "cursor_"
        const val KEY_LOST_MESSAGE = "Sign in again on this phone."
        val SERVER = stringPreferencesKey("server_url")
        val USER = stringPreferencesKey("user_id")
        val EMAIL = stringPreferencesKey("email")
        val TOKEN = stringPreferencesKey("token")
        val SYNCED_SERVER = stringPreferencesKey("synced_server_url")
        val SYNCED_USER = stringPreferencesKey("synced_user_id")
        val SYNCED_EMAIL = stringPreferencesKey("synced_email")
        val LAST_SYNC = longPreferencesKey("last_sync_at")
        val LAST_ERROR = stringPreferencesKey("last_error")
        val REJECTED = intPreferencesKey("last_sync_rejected")
        val STALE = intPreferencesKey("last_sync_stale")
    }
}
