package dev.southstar21.kaloria.core.sync

import android.content.Context
import android.security.NetworkSecurityPolicy
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.southstar21.kaloria.core.data.widget.WidgetUpdater
import dev.southstar21.kaloria.core.database.dao.LocalDataDao
import dev.southstar21.kaloria.core.datastore.Session
import dev.southstar21.kaloria.core.datastore.SessionStore
import dev.southstar21.kaloria.core.datastore.SyncInfo
import dev.southstar21.kaloria.core.datastore.SyncedAccount
import dev.southstar21.kaloria.core.network.PocketBaseAuthException
import dev.southstar21.kaloria.core.network.PocketBaseClient
import dev.southstar21.kaloria.core.network.UnexpectedResponseException
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map

data class AccountState(
    val session: Session?,
    val serverUrl: String,
    val sync: SyncInfo,
    /** Set while a sign-in waits for the user to say what happens to this phone's data. */
    val pendingSwitch: PendingSwitch? = null,
)

/**
 * A sign-in to [toEmail] while this phone holds data synced with another account, [fromEmail] (null
 * when it was synced before the app kept track of which). Nothing is saved or synced until the
 * user picks a [SwitchChoice] or cancels.
 */
data class PendingSwitch(val fromEmail: String?, val toEmail: String)

enum class SwitchChoice {
    /** Keep this phone's data and upload all of it to the new account. */
    Upload,

    /** Delete this phone's data and download the new account's instead. */
    Replace,
}

enum class SignInResult { SignedIn, NeedsChoice }

class SignInException(val reason: Reason, cause: Throwable? = null) : Exception(reason.name, cause) {
    enum class Reason { NoServer, InvalidAddress, CleartextNotAllowed, Unreachable, NotPocketBase, WrongCredentials, TooManyAttempts, ServerError }
}

@Singleton
class AccountRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessions: SessionStore,
    private val client: PocketBaseClient,
    private val scheduler: SyncScheduler,
    private val lock: SyncLock,
    private val localData: LocalDataDao,
    private val widget: WidgetUpdater,
) {
    /** Empty in release builds: there is no server to suggest, the user enters their own. */
    val defaultServerUrl: String = BuildConfig.DEFAULT_SERVER_URL

    private class Pending(val session: Session, val switch: PendingSwitch)

    private val pending = MutableStateFlow<Pending?>(null)

    val state: Flow<AccountState> = combine(sessions.session, sessions.serverUrl, sessions.syncInfo, pending.map { it?.switch }) { s, url, info, switch ->
        AccountState(s, url ?: defaultServerUrl, info, switch)
    }

    val isSyncing: Flow<Boolean> = scheduler.isSyncing

    /**
     * Signs in, unless the local data belongs to a different account: then nothing is saved and
     * [AccountState.pendingSwitch] asks the user first ([SignInResult.NeedsChoice]).
     *
     * @throws SignInException when signing in fails.
     */
    suspend fun signIn(serverUrl: String, email: String, password: String): SignInResult {
        cancelSwitch()
        val url = checkedServerUrl(serverUrl)
        signInStep { client.checkHealth(url) }
        val auth = signInStep { client.authWithPassword(url, email, password) }
        val session = Session(url, auth.record.id, auth.record.email.ifEmpty { email.trim() }, auth.token)
        val switch = lock.withLock {
            val plan = planLocalData(
                synced = sessions.syncedAccount(),
                signingIn = SyncedAccount(session.serverUrl, session.userId, session.email),
                hasData = localData.hasData(),
                hasSyncedRows = localData.hasSyncedRows(),
            )
            when (plan) {
                LocalDataPlan.Keep -> sessions.save(session)

                LocalDataPlan.Reupload -> {
                    localData.markAllDirty()
                    sessions.save(session)
                }

                LocalDataPlan.Ask -> return@withLock PendingSwitch(sessions.syncedAccount()?.email?.ifEmpty { null }, session.email)
            }
            null
        }
        if (switch != null) {
            pending.value = Pending(session, switch)
            return SignInResult.NeedsChoice
        }
        startSyncing()
        return SignInResult.SignedIn
    }

    /** Finishes the sign-in waiting in [AccountState.pendingSwitch], doing [choice] with the local data first. */
    suspend fun resolveSwitch(choice: SwitchChoice) {
        // Taken at once, so a second tap can't apply the choice twice.
        val waiting = pending.getAndUpdate { null } ?: return
        lock.withLock {
            when (choice) {
                SwitchChoice.Upload -> localData.markAllDirty()

                SwitchChoice.Replace -> {
                    localData.deleteAll()
                    forgetHealthImports()
                }
            }
            sessions.save(waiting.session)
        }
        if (choice == SwitchChoice.Replace) widget.requestUpdate()
        startSyncing()
    }

    /** Drops the sign-in waiting in [AccountState.pendingSwitch]; nothing changes. */
    fun cancelSwitch() {
        pending.value = null
    }

    /** Local data stays on the device; it's simply no longer synced. */
    suspend fun signOut() {
        cancelSwitch()
        scheduler.cancelAll()
        // Cancelling only asks a running sync to stop; wait for it, so it can't write after the clear.
        lock.withLock { sessions.clear() }
    }

    fun syncNow() = scheduler.syncNow()

    /**
     * Called at app start for signed-in users: keeps the periodic sync alive and catches up soon,
     * after the first frame rather than competing with it.
     */
    fun onAppStart() {
        scheduler.schedulePeriodic()
        scheduler.requestSync()
    }

    private fun startSyncing() {
        scheduler.schedulePeriodic()
        scheduler.syncNow()
    }

    /**
     * Health Connect sync remembers which weigh-ins this phone imported (it may delete those when
     * they vanish there) and what it exported. With the weigh-ins deleted, that's about rows that
     * no longer exist; it starts over and imports again.
     */
    private fun forgetHealthImports() {
        // Written at once, not in the background: the pull that follows must not see the old ids.
        context.getSharedPreferences(HEALTH_SYNC_PREFS, Context.MODE_PRIVATE).edit(commit = true) { clear() }
    }

    private fun checkedServerUrl(input: String): String {
        val url = normalizedServerUrl(input)
        val host = url?.let(::hostOf)
        val reason = when {
            url == null -> SignInException.Reason.NoServer

            host == null -> SignInException.Reason.InvalidAddress

            // Release builds allow HTTPS only; say so rather than "can't reach".
            url.startsWith("http://", ignoreCase = true) && !NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted(host) ->
                SignInException.Reason.CleartextNotAllowed

            else -> return url
        }
        throw SignInException(reason)
    }

    private fun hostOf(url: String): String? = try {
        URI(url).host?.removeSurrounding("[", "]")
    } catch (_: URISyntaxException) {
        null
    }

    /** Runs one step of signing in, turning its failures into a [SignInException] that says what went wrong. */
    private suspend fun <T> signInStep(block: suspend () -> T): T {
        val failure: Exception = try {
            return block()
        } catch (e: PocketBaseAuthException) {
            e
        } catch (e: IOException) {
            e
        } catch (e: UnexpectedResponseException) {
            e
        } catch (e: ResponseException) {
            e
        }
        val reason = when {
            failure is PocketBaseAuthException -> SignInException.Reason.WrongCredentials

            failure is IOException -> SignInException.Reason.Unreachable

            failure !is ResponseException -> SignInException.Reason.NotPocketBase

            failure.response.status == HttpStatusCode.TooManyRequests -> SignInException.Reason.TooManyAttempts

            failure.response.status.value >= 500 -> SignInException.Reason.ServerError

            // Something answered, but not the way PocketBase does (no health or auth endpoint).
            else -> SignInException.Reason.NotPocketBase
        }
        throw SignInException(reason, failure)
    }

    private companion object {
        /** HealthWeightSync's SharedPreferences, in :core:data. */
        const val HEALTH_SYNC_PREFS = "health_weight_sync"
    }
}

/** The server address as typed, without trailing slashes and with https:// when no scheme is given; null if blank. */
internal fun normalizedServerUrl(input: String): String? = input.trim().trimEnd('/').takeIf { it.isNotEmpty() }
    ?.let { if ("://" in it) it else "https://$it" }

internal enum class LocalDataPlan {
    /** Sync as usual: the data already belongs to this account, or there's nothing to decide. */
    Keep,

    /** Same account, but the server doesn't have the data (another server, or this one was reset): upload it all. */
    Reupload,

    /** Another account's data: ask the user. */
    Ask,
}

/**
 * What to do with the local data when [signingIn] signs in, given the account it was last synced
 * with. The email tells accounts apart across servers; a new user id for the same email on the same
 * server means the server was reset.
 */
internal fun planLocalData(synced: SyncedAccount?, signingIn: SyncedAccount, hasData: Boolean, hasSyncedRows: Boolean): LocalDataPlan = when {
    !hasData -> LocalDataPlan.Keep

    // Never synced, so the data was made on this phone and is already queued for upload. Synced rows
    // with no record of the account mean an install from before it was kept: someone's, unknown.
    synced == null -> if (hasSyncedRows) LocalDataPlan.Ask else LocalDataPlan.Keep

    sameServer(synced.serverUrl, signingIn.serverUrl) && synced.userId == signingIn.userId -> LocalDataPlan.Keep

    synced.email.isNotEmpty() && synced.email.equals(signingIn.email, ignoreCase = true) -> LocalDataPlan.Reupload

    else -> LocalDataPlan.Ask
}

private fun sameServer(a: String, b: String) = normalizedServerUrl(a).equals(normalizedServerUrl(b), ignoreCase = true)
