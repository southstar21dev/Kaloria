package dev.southstar21.kaloria.core.health

import android.content.Context
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Mass
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.southstar21.kaloria.core.model.HealthAccess
import java.time.Duration
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
interface HealthWeights {
    /** What weight sync needs; [access] is granted once all of these are. */
    val permissions: Set<String>

    /** Asked for along with [permissions] but not needed: reading further back than Health Connect's default. */
    val optionalPermissions: Set<String>

    /** Feed to rememberLauncherForActivityResult to ask for weight access. */
    fun permissionContract(): ActivityResultContract<Set<String>, Set<String>>

    /** Same states as for steps: granted, needs permission, Health Connect missing or outdated. */
    suspend fun access(): HealthAccess

    /**
     * Whether other apps' readings of any age can be read. Without it, Health Connect only returns
     * those from up to [DEFAULT_HISTORY] before Kaloria was first granted access.
     */
    suspend fun canReadHistory(): Boolean

    suspend fun read(from: Instant, to: Instant): List<HealthWeight>

    /** Inserts or updates by Kaloria id; Health Connect skips any whose [WeightToWrite.version] it already has. */
    suspend fun write(weights: List<WeightToWrite>)

    /** Deletes weigh-ins Kaloria wrote, by Kaloria id. Other apps' records can't be deleted from here. */
    suspend fun delete(kaloriaIds: List<String>)

    companion object {
        /** How far back before the first grant Health Connect reads without the history permission. */
        val DEFAULT_HISTORY: Duration = Duration.ofDays(30)
    }
}

@Singleton
internal class HealthConnectWeights @Inject constructor(@ApplicationContext private val context: Context) : HealthWeights {

    override val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getWritePermission(WeightRecord::class),
    )

    // Only offered where this Health Connect supports it; the request would fail otherwise.
    override val optionalPermissions: Set<String>
        get() = if (healthConnect.supports(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY)) setOf(HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY) else emptySet()

    override fun permissionContract(): ActivityResultContract<Set<String>, Set<String>> = PermissionController.createRequestPermissionResultContract()

    private val healthConnect = HealthConnect(context)
    private val client: HealthConnectClient? get() = healthConnect.clientOrNull()

    override suspend fun access(): HealthAccess = healthConnect.access(permissions)

    override suspend fun canReadHistory(): Boolean = healthConnect.supports(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY) &&
        HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY in healthConnect.granted()

    override suspend fun read(from: Instant, to: Instant): List<HealthWeight> {
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

    override suspend fun write(weights: List<WeightToWrite>) {
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

    override suspend fun delete(kaloriaIds: List<String>) {
        val c = client ?: return
        kaloriaIds.chunked(CHUNK).forEach { c.deleteRecords(WeightRecord::class, emptyList(), it) }
    }

    private companion object {
        const val CHUNK = 100
    }
}
