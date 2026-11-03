package dev.southstar21.kaloria.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.Clock
import java.time.LocalDate

/**
 * Wakes the widget just after local midnight so it starts the new day at zero. An inexact window
 * needs no exact-alarm permission and lets the system batch it with other wake-ups.
 */
internal object NewDayAlarm {
    const val ACTION = "dev.southstar21.kaloria.widget.NEW_DAY"
    private const val WINDOW_MS = 5 * 60 * 1000L

    fun schedule(context: Context, clock: Clock) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val midnight = LocalDate.now(clock).plusDays(1).atStartOfDay(clock.zone).toInstant().toEpochMilli()
        val intent = Intent(context, KaloriaWidgetReceiver::class.java).setAction(ACTION)
        val pending = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        // Same PendingIntent each time, so rescheduling replaces rather than adds.
        alarms.setWindow(AlarmManager.RTC, midnight, WINDOW_MS, pending)
    }
}
