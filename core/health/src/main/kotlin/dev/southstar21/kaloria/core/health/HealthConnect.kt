package dev.southstar21.kaloria.core.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import dev.southstar21.kaloria.core.model.HealthAccess
import kotlin.coroutines.cancellation.CancellationException

/**
 * The Health Connect client, created the first time Health Connect is available. Unlike a plain
 * `lazy`, a miss isn't remembered: installing or updating Health Connect works without a restart.
 */
internal class HealthConnect(private val context: Context) {
    @Volatile private var client: HealthConnectClient? = null

    fun status(): Int = HealthConnectClient.getSdkStatus(context)

    fun clientOrNull(): HealthConnectClient? = client ?: synchronized(this) {
        client ?: if (status() == HealthConnectClient.SDK_AVAILABLE) HealthConnectClient.getOrCreate(context).also { client = it } else null
    }

    /** Whether all of [permissions] are granted; Health Connect missing or failing counts as no. */
    suspend fun access(permissions: Set<String>): HealthAccess = when (status()) {
        HealthConnectClient.SDK_AVAILABLE -> {
            val granted = clientOrNull()?.let { c -> orDefault(emptySet()) { c.permissionController.getGrantedPermissions() } }.orEmpty()
            if (granted.containsAll(permissions)) HealthAccess.Granted else HealthAccess.NeedsPermission
        }

        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthAccess.NotInstalled

        else -> HealthAccess.Unsupported
    }
}

/** Runs [block]; a failure gives [default], but cancellation still cancels. */
internal inline fun <T> orDefault(default: T, block: () -> T): T = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    default
}
