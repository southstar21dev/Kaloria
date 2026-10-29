package dev.southstar21.kaloria.core.health

import android.content.Context
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.Period
import javax.inject.Inject
import javax.inject.Singleton

enum class StepsAccess { Granted, NeedsPermission, NotInstalled, Unsupported }

/**
 * Step counts from Health Connect (Samsung Health and Google Fit write there). Only counts are
 * read; Kaloria never turns steps into calories.
 */
@Singleton
class StepsRepository @Inject constructor(@ApplicationContext private val context: Context) {

    val permissions: Set<String> = setOf(HealthPermission.getReadPermission(StepsRecord::class))

    /** Feed to rememberLauncherForActivityResult to ask for step access. */
    fun permissionContract(): ActivityResultContract<Set<String>, Set<String>> =
        PermissionController.createRequestPermissionResultContract()

    private val client: HealthConnectClient? by lazy {
        if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) HealthConnectClient.getOrCreate(context) else null
    }

    suspend fun access(): StepsAccess = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> {
            val granted = runCatching { client!!.permissionController.getGrantedPermissions() }.getOrDefault(emptySet())
            if (granted.containsAll(permissions)) StepsAccess.Granted else StepsAccess.NeedsPermission
        }
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> StepsAccess.NotInstalled
        else -> StepsAccess.Unsupported
    }

    /** Steps per local day, deduplicated by Health Connect across apps and devices. */
    suspend fun stepsPerDay(from: LocalDate, toInclusive: LocalDate): Map<LocalDate, Long> {
        val c = client ?: return emptyMap()
        if (access() != StepsAccess.Granted) return emptyMap()
        return runCatching {
            c.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(from.atStartOfDay(), toInclusive.plusDays(1).atStartOfDay()),
                    timeRangeSlicer = Period.ofDays(1),
                ),
            ).associate { bucket -> bucket.startTime.toLocalDate() to (bucket.result[StepsRecord.COUNT_TOTAL] ?: 0L) }
        }.getOrDefault(emptyMap())
    }

    suspend fun steps(day: LocalDate): Long? = stepsPerDay(day, day)[day]
}
