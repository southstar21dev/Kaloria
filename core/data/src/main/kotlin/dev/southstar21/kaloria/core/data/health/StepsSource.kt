package dev.southstar21.kaloria.core.data.health

import androidx.activity.result.contract.ActivityResultContract
import dev.southstar21.kaloria.core.health.StepsRepository
import dev.southstar21.kaloria.core.model.HealthAccess
import java.time.LocalDate
import javax.inject.Inject

/** Step counts for screens, so features don't depend on Health Connect directly (and tests can fake it). */
interface StepsSource {
    val permissions: Set<String>

    /** Feed to rememberLauncherForActivityResult to ask for step access. */
    fun permissionContract(): ActivityResultContract<Set<String>, Set<String>>

    suspend fun access(): HealthAccess

    /** Steps per local day from [from] to [toInclusive]; empty without access. */
    suspend fun stepsPerDay(from: LocalDate, toInclusive: LocalDate): Map<LocalDate, Long>

    suspend fun steps(day: LocalDate): Long?
}

internal class HealthConnectSteps @Inject constructor(private val steps: StepsRepository) : StepsSource {
    override val permissions get() = steps.permissions
    override fun permissionContract() = steps.permissionContract()
    override suspend fun access() = steps.access()
    override suspend fun stepsPerDay(from: LocalDate, toInclusive: LocalDate) = steps.stepsPerDay(from, toInclusive)
    override suspend fun steps(day: LocalDate) = steps.steps(day)
}
