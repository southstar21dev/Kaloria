package dev.southstar21.kaloria.widget

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate

/** Intent action that opens the barcode scanner directly. */
const val ACTION_SCAN_BARCODE = "dev.southstar21.kaloria.action.SCAN_BARCODE"
const val ACTION_SEARCH_FOOD = "dev.southstar21.kaloria.action.SEARCH_FOOD"

/**
 * A widget shortcut waiting for the app to open it. Only [ShortcutActivity] posts here, and other
 * apps can't start that, so another app can't open the scanner or search over the user's screen.
 */
object WidgetShortcuts {
    private val pendingAction = MutableStateFlow<String?>(null)

    /** The action of a shortcut not yet opened, if any. */
    val pending: StateFlow<String?> = pendingAction.asStateFlow()

    internal fun post(action: String) {
        pendingAction.value = action
    }

    /** Takes the waiting shortcut's action, so it's opened once. */
    fun take(): String? = pendingAction.getAndUpdate { null }
}

/**
 * Where the widget's buttons lead: hands the shortcut over and brings the app to the front. It's
 * not exported (the launcher activity has to be), and shows nothing before finishing.
 */
class ShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.action?.takeIf { it == ACTION_SCAN_BARCODE || it == ACTION_SEARCH_FOOD }?.let(WidgetShortcuts::post)
        // Brings back the running app rather than recreating it, or starts it if it isn't running.
        packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
        finish()
    }
}
