package dev.southstar21.kaloria

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import dev.southstar21.kaloria.core.sync.AccountRepository
import dev.southstar21.kaloria.widget.WidgetRefresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class KaloriaApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var account: AccountRepository
    @Inject lateinit var widgetRefresher: WidgetRefresher

    /** Work that outlives any screen: the start-up sync, widget refreshes. */
    val appScope = CoroutineScope(SupervisorJob())

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        appScope.launch { account.onAppStart() }
        widgetRefresher.start(appScope)
    }
}
