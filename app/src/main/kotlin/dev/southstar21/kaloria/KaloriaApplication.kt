package dev.southstar21.kaloria

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import dev.southstar21.kaloria.core.datastore.SessionStore
import dev.southstar21.kaloria.core.sync.AccountRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class KaloriaApplication :
    Application(),
    Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    // Lazy: building it creates the HTTP client and opens WorkManager's database, which must not
    // happen on the main thread while the first frame is waiting.
    @Inject lateinit var account: dagger.Lazy<AccountRepository>

    @Inject lateinit var sessions: dagger.Lazy<SessionStore>

    /** Work that outlives any screen: the start-up sync. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // Signed-out users never need the HTTP client or WorkManager at start.
        appScope.launch { if (sessions.get().isSignedIn()) account.get().onAppStart() }
    }
}
