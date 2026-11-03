package dev.southstar21.kaloria.core.datastore

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
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

data class Session(val serverUrl: String, val userId: String, val email: String, val token: String)

data class SyncInfo(
    val lastSyncAt: Long? = null,
    val lastError: String? = null,
)

private val Context.sessionStore by preferencesDataStore("session")

/**
 * The signed-in PocketBase session and per-collection sync cursors. The auth token is encrypted
 * with an AES-GCM key that never leaves the Android Keystore.
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
            Session(server ?: return@map null, user ?: return@map null, email.orEmpty(), token?.let(::decrypt) ?: return@map null)
        }
        .flowOn(Dispatchers.Default)

    /** Last used server, kept after sign-out so the field stays filled. */
    val serverUrl: Flow<String?> = store.data.map { it[SERVER] }.distinctUntilChanged()

    // Cursor and token writes don't change these; don't re-emit for them.
    val syncInfo: Flow<SyncInfo> = store.data.map { SyncInfo(it[LAST_SYNC], it[LAST_ERROR]) }.distinctUntilChanged()

    suspend fun current(): Session? = session.first()

    /** Whether a session is stored, without decrypting its token. */
    suspend fun isSignedIn(): Boolean = store.data.first()[TOKEN] != null

    suspend fun save(session: Session) = store.edit {
        it[SERVER] = session.serverUrl
        it[USER] = session.userId
        it[EMAIL] = session.email
        it[TOKEN] = encrypt(session.token)
        it.remove(LAST_ERROR)
    }

    suspend fun updateToken(token: String) = store.edit { it[TOKEN] = encrypt(token) }

    /** Signs out and forgets the cursors, so the next sign-in pulls everything again. */
    suspend fun clear() = store.edit { prefs ->
        val server = prefs[SERVER]
        prefs.clear()
        server?.let { prefs[SERVER] = it }
    }

    suspend fun cursor(collection: String): String? = store.data.first()[cursorKey(collection)]

    suspend fun setCursor(collection: String, value: String) = store.edit { it[cursorKey(collection)] = value }

    suspend fun recordSuccess(at: Long) = store.edit {
        it[LAST_SYNC] = at
        it.remove(LAST_ERROR)
    }

    suspend fun recordError(message: String) = store.edit { it[LAST_ERROR] = message }

    private fun cursorKey(collection: String) = stringPreferencesKey("cursor_$collection")

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

    /** Null if the key was lost (app data restored to another device): the user signs in again. */
    private fun decrypt(encoded: String): String? = runCatching {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, IV_SIZE))
        String(cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE))
    }.getOrNull()

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "kaloria_session"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        val SERVER = stringPreferencesKey("server_url")
        val USER = stringPreferencesKey("user_id")
        val EMAIL = stringPreferencesKey("email")
        val TOKEN = stringPreferencesKey("token")
        val LAST_SYNC = longPreferencesKey("last_sync_at")
        val LAST_ERROR = stringPreferencesKey("last_error")
    }
}
