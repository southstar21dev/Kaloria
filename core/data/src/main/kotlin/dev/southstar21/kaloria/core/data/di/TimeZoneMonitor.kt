package dev.southstar21.kaloria.core.data.di

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.DateTimeException
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

/** The phone's time zone now and after every change, for day boundaries that have to move with it. */
fun interface TimeZoneMonitor {
    fun zone(): Flow<ZoneId>
}

internal class BroadcastTimeZoneMonitor @Inject constructor(@ApplicationContext private val context: Context) : TimeZoneMonitor {
    override fun zone(): Flow<ZoneId> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                // The extra is the new zone even if the process default hasn't caught up yet.
                trySend(intent.getStringExtra(Intent.EXTRA_TIMEZONE)?.toZoneOrNull() ?: ZoneId.systemDefault())
            }
        }
        // Registered before the first value, so a change in between isn't missed.
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_TIMEZONE_CHANGED), Context.RECEIVER_NOT_EXPORTED)
        trySend(ZoneId.systemDefault())
        awaitClose { context.unregisterReceiver(receiver) }
    }.distinctUntilChanged().conflate()
}

private fun String.toZoneOrNull(): ZoneId? = try {
    ZoneId.of(this)
} catch (_: DateTimeException) {
    null
}
