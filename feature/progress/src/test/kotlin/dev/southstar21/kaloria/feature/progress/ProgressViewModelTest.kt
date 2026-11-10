package dev.southstar21.kaloria.feature.progress

import android.content.Context
import androidx.activity.result.contract.ActivityResultContract
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import dev.southstar21.kaloria.core.data.health.HealthWeightSync
import dev.southstar21.kaloria.core.data.health.StepsSource
import dev.southstar21.kaloria.core.data.repository.DiaryRepository
import dev.southstar21.kaloria.core.data.repository.GoalsRepository
import dev.southstar21.kaloria.core.database.dao.WeightDao
import dev.southstar21.kaloria.core.health.HealthWeights
import dev.southstar21.kaloria.core.model.Goals
import dev.southstar21.kaloria.core.model.HealthAccess
import dev.southstar21.kaloria.core.model.LogEntry
import dev.southstar21.kaloria.core.model.WeightEntry
import java.lang.reflect.Proxy
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProgressViewModelTest {
    private val zone = ZoneId.of("Europe/Budapest")
    private val today = LocalDate.of(2026, 10, 30)
    private val clock = TickingClock(LocalDateTime.of(2026, 10, 30, 23, 59).atZone(zone).toInstant(), zone)
    private val main = UnconfinedTestDispatcher()

    private val steps = RecordingSteps()

    /** Health Connect says yes, then every read and write fails: a round that can't complete. */
    private val weightSync = HealthWeightSync(
        context = ApplicationProvider.getApplicationContext<Context>(),
        dao = failing(WeightDao::class.java),
        health = failing(HealthWeights::class.java) { method -> if (method == "access") HealthAccess.Granted else null },
        clock = clock,
        sync = {},
        io = main,
    )

    private lateinit var viewModel: ProgressViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(main)
        viewModel = ProgressViewModel(EmptyDiary(), StaticGoals(), steps, weightSync, clock, compute = main)
    }

    @After
    fun tearDown() {
        ViewModelStore().apply { put("progress", viewModel) }.clear()
        Dispatchers.resetMain()
    }

    private suspend fun TestScope.awaitState(predicate: (ProgressUiState) -> Boolean): ProgressUiState {
        backgroundScope.launch { viewModel.state.collect {} }
        return viewModel.state.first { it != null && predicate(it) }!!
    }

    @Test
    fun `midnight with the screen open moves the range on to the new day`() = runTest {
        awaitState { it.today == today }

        clock.now = LocalDateTime.of(2026, 10, 31, 0, 1).atZone(zone).toInstant()
        viewModel.onTimeChange()

        val state = awaitState { it.today == today.plusDays(1) }
        assertEquals(today.plusDays(1), state.today)
        assertEquals(today.plusDays(1), steps.ranges.last().second)
    }

    @Test
    fun `ticks within the same day don't ask Health Connect again`() = runTest {
        viewModel.refreshSteps().join()
        val calls = steps.ranges.size

        viewModel.onTimeChange()

        assertEquals(calls, steps.ranges.size)
    }

    @Test
    fun `a weight sync that fails is reported`() = runTest {
        assertFalse(viewModel.weightSyncFailed.value)

        weightSync.sync()

        assertTrue(viewModel.weightSyncFailed.value)
    }
}

/** A stand-in for [type] whose methods fail, except those [answer] gives a value for. */
private fun <T> failing(type: Class<T>, answer: (String) -> Any? = { null }): T = type.cast(
    Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ ->
        answer(method.name) ?: throw IllegalStateException("${method.name} unavailable in this test")
    },
)

private class TickingClock(var now: Instant, private val zone: ZoneId) : Clock() {
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = TickingClock(now, zone)
    override fun instant(): Instant = now
}

private class RecordingSteps : StepsSource {
    val ranges = mutableListOf<Pair<LocalDate, LocalDate>>()

    override val permissions: Set<String> = emptySet()
    override fun permissionContract(): ActivityResultContract<Set<String>, Set<String>> = throw UnsupportedOperationException()
    override suspend fun access() = HealthAccess.Granted
    override suspend fun stepsPerDay(from: LocalDate, toInclusive: LocalDate): Map<LocalDate, Long> {
        ranges += from to toInclusive
        return emptyMap()
    }
    override suspend fun steps(day: LocalDate): Long? = null
}

private class EmptyDiary : DiaryRepository {
    override fun entries(day: LocalDate): Flow<List<LogEntry>> = MutableStateFlow(emptyList())
    override fun entries(from: LocalDate, toInclusive: LocalDate): Flow<List<LogEntry>> = MutableStateFlow(emptyList())
    override suspend fun entry(id: String): LogEntry? = null
    override suspend fun saveEntry(entry: LogEntry) = Unit
    override suspend fun deleteEntry(id: String) = Unit
    override fun loggedDays(): Flow<Set<LocalDate>> = MutableStateFlow(emptySet())
    override fun weights(from: LocalDate, toInclusive: LocalDate): Flow<List<WeightEntry>> = MutableStateFlow(emptyList())
    override fun latestWeight(): Flow<WeightEntry?> = MutableStateFlow(null)
    override suspend fun addWeight(kilograms: Double, at: Instant) = Unit
    override suspend fun deleteWeight(id: String) = Unit
}

private class StaticGoals : GoalsRepository {
    override val goals = MutableStateFlow(Goals())
    override suspend fun save(goals: Goals) = Unit
}
