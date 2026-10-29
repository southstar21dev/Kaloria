package dev.southstar21.kaloria.core.sync

import dev.southstar21.kaloria.core.datastore.Session
import dev.southstar21.kaloria.core.datastore.SessionStore
import dev.southstar21.kaloria.core.datastore.SyncInfo
import dev.southstar21.kaloria.core.network.PocketBaseClient
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

data class AccountState(val session: Session?, val serverUrl: String, val sync: SyncInfo)

@Singleton
class AccountRepository @Inject constructor(
    private val sessions: SessionStore,
    private val client: PocketBaseClient,
    private val scheduler: SyncScheduler,
) {
    val defaultServerUrl: String = BuildConfig.DEFAULT_SERVER_URL

    val state: Flow<AccountState> = sessions.session.combineWith(sessions.serverUrl, sessions.syncInfo) { s, url, info ->
        AccountState(s, url ?: defaultServerUrl, info)
    }

    val isSyncing: Flow<Boolean> = scheduler.isSyncing

    /** @throws Exception with a user-facing message when sign-in fails. */
    suspend fun signIn(serverUrl: String, email: String, password: String) {
        val url = serverUrl.trim().trimEnd('/').let { if ("://" in it) it else "https://$it" }
        if (!client.isReachable(url)) throw IllegalStateException("Can't reach $url")
        val auth = client.authWithPassword(url, email, password)
        sessions.save(Session(url, auth.record.id, auth.record.email.ifEmpty { email.trim() }, auth.token))
        scheduler.schedulePeriodic()
        scheduler.syncNow()
    }

    /** Local data stays on the device; it's simply no longer synced. */
    suspend fun signOut() {
        scheduler.cancelAll()
        sessions.clear()
    }

    fun syncNow() = scheduler.syncNow()

    /** Called at app start: keep the periodic sync alive for signed-in users. */
    suspend fun onAppStart() {
        if (sessions.current() != null) {
            scheduler.schedulePeriodic()
            scheduler.syncNow()
        }
    }
}

private fun <A, B, C, R> Flow<A>.combineWith(b: Flow<B>, c: Flow<C>, transform: suspend (A, B, C) -> R): Flow<R> =
    kotlinx.coroutines.flow.combine(this, b, c, transform)

