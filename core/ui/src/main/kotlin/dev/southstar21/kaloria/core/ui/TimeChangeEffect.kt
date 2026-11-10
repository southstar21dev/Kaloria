package dev.southstar21.kaloria.core.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleStartEffect

/**
 * Calls [onChange] while the screen is in front whenever the date may have moved on: each minute
 * (the system's time tick), and when the time, date or zone is changed. Once more on start, for
 * anything missed while stopped. Screens use it so "today" turns over at midnight with the app
 * open, without waiting for the next resume.
 */
@Composable
fun TimeChangeEffect(onChange: () -> Unit) {
    val context = LocalContext.current
    val currentOnChange by rememberUpdatedState(onChange)
    LifecycleStartEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = currentOnChange()
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        // System broadcasts still arrive with RECEIVER_NOT_EXPORTED; other apps can't send these.
        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        currentOnChange()
        onStopOrDispose { context.unregisterReceiver(receiver) }
    }
}
