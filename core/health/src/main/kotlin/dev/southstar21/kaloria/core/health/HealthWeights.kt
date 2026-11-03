package dev.southstar21.kaloria.core.health

import android.content.Context
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Mass
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.southstar21.kaloria.core.model.HealthAccess
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** A weigh-in as Health Connect has it. [fromKaloria] marks the ones this app wrote. */
data class HealthWeight(val id: String, val kilograms: Double, val time: Instant, val fromKaloria: Boolean)

/** A weigh-in to write: [id] is Kaloria's own id, so writing it again updates rather than duplicates. */
data class WeightToWrite(val id: String, val kilograms: Double, val time: Instant, val version: Long)

/**
 * Weight in Health Connect, which Samsung Health (and other apps) read from and write to. Kaloria's
 * weigh-ins go in tagged with Kaloria's own ids; everyone else's come back out.
 */
@Singleton
class HealthWeights @Inject constructor(@ApplicationContext private val context: Context) {

    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getWritePermission(WeightRecord::class),
    )

    /** Feed to rememberLauncherForActivityResult to ask for weight access. */
    fun permissionContract(): ActivityResultContract<Set<String>, Set<String>> = PermissionController.createRequestPermissionResultContract()

    private val healthConnect = HealthConnect(context)
    private val client: HealthConnectClient? get() = healthConnect.clientOrNull()

    /** Same states as for steps: granted, needs permission, Health Connect missing or outdated. */
    suspend fun access(): HealthAccess = healthConnect.access(permissions)

    suspend fun read(from: Instant, to: Instant): List<HealthWeight> {
        val c = client ?: return emptyList()
        val own = context.packageName
        val all = mutableListOf<HealthWeight>()
        var page: String? = null
        do {
            val response = c.readRecords(
                ReadRecordsRequest(WeightRecord::class, TimeRangeFilter.between(from, to), pageSize = 1000, pageToken = page),
            )
            response.records.mapTo(all) {
                HealthWeight(it.metadata.id, it.weight.inKilograms, it.time, fromKaloria = it.metadata.dataOrigin.packageName == own)
            }
            page = response.pageToken
        } while (page != null)
        return all
    }

    /** Inserts or updates by Kaloria id; Health Connect skips any whose [WeightToWrite.version] it already has. */
    suspend fun write(weights: List<WeightToWrite>) {
        val c = client ?: return
        weights.chunked(CHUNK).forEach { chunk ->
            c.insertRecords(
                chunk.map {
                    WeightRecord(
                        time = it.time,
                        zoneOffset = ZoneId.systemDefault().rules.getOffset(it.time),
                        weight = Mass.kilograms(it.kilograms),
                        metadata = Metadata.manualEntry(clientRecordId = it.id, clientRecordVersion = it.version),
                    )
                },
            )
        }
    }

    /** Deletes weigh-ins Kaloria wrote, by Kaloria id. Other apps' records can't be deleted from here. */
    suspend fun delete(kaloriaIds: List<String>) {
        val c = client ?: return
        kaloriaIds.chunked(CHUNK).forEach { c.deleteRecords(WeightRecord::class, emptyList(), it) }
    }

    private companion object {
        const val CHUNK = 100
    }
}
