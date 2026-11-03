package dev.southstar21.kaloria.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.southstar21.kaloria.core.data.sync.SyncRequester
import dev.southstar21.kaloria.core.data.widget.WidgetUpdater
import dev.southstar21.kaloria.core.database.KaloriaDatabase
import dev.southstar21.kaloria.core.model.AmountUnit
import dev.southstar21.kaloria.core.model.LogEntry
import dev.southstar21.kaloria.core.model.Nutrients
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OfflineFirstDiaryRepositoryTest {
    private val zone = ZoneId.of("Europe/Budapest") // UTC+1 in late October
    private val now = Instant.parse("2026-10-30T12:00:00Z")
    private val clock = Clock.fixed(now, zone)

    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), KaloriaDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    private var syncRequests = 0
    private var widgetUpdates = 0

    private val repository = OfflineFirstDiaryRepository(
        logDao = db.logEntryDao(),
        weightDao = db.weightDao(),
        clock = clock,
        sync = SyncRequester { syncRequests++ },
        widget = WidgetUpdater { widgetUpdates++ },
    )

    @After fun close() = db.close()

    private fun local(text: String): Instant = LocalDateTime.parse(text).atZone(zone).toInstant()

    private fun entry(id: String, at: Instant) = LogEntry(
        id = id,
        foodId = null,
        foodName = "Oats",
        brand = null,
        amount = 50.0,
        unit = AmountUnit.Gram,
        nutrientsPer100 = Nutrients(energyKcal = 380.0),
        loggedAt = at,
    )

    @Test
    fun `saving an entry stores it and asks for a sync and a widget update`() = runTest {
        repository.saveEntry(entry("a", local("2026-10-30T08:00")))

        assertEquals(1, syncRequests)
        assertEquals(1, widgetUpdates)
        assertEquals(listOf("a"), repository.entries(LocalDate.of(2026, 10, 30)).first().map { it.id })
        // Stamped with the clock's time, and dirty until pushed.
        val row = db.logEntryDao().get("a")!!
        assertEquals(now.toEpochMilli(), row.updatedAt)
        assertEquals(true, row.dirty)
    }

    @Test
    fun `deleting an entry hides it and asks for a sync and a widget update`() = runTest {
        repository.saveEntry(entry("a", local("2026-10-30T08:00")))

        repository.deleteEntry("a")

        assertEquals(2, syncRequests)
        assertEquals(2, widgetUpdates)
        assertNull(repository.entry("a"))
        assertEquals(emptyList<LogEntry>(), repository.entries(LocalDate.of(2026, 10, 30)).first())
    }

    @Test
    fun `entries of a day follow the clock's zone`() = runTest {
        repository.saveEntry(entry("late", local("2026-10-29T23:30"))) // 22:30 UTC
        repository.saveEntry(entry("early", local("2026-10-30T00:10"))) // 23:10 UTC, still the 29th in UTC

        assertEquals(listOf("late"), repository.entries(LocalDate.of(2026, 10, 29)).first().map { it.id })
        assertEquals(listOf("early"), repository.entries(LocalDate.of(2026, 10, 30)).first().map { it.id })
        assertEquals(listOf("late", "early"), repository.entries(LocalDate.of(2026, 10, 29), LocalDate.of(2026, 10, 30)).first().map { it.id })
    }

    @Test
    fun `logged days are local dates in the clock's zone`() = runTest {
        repository.saveEntry(entry("a", local("2026-10-29T23:30"))) // 22:30 UTC on the 29th
        repository.saveEntry(entry("b", local("2026-10-30T00:10"))) // 23:10 UTC on the 29th
        repository.saveEntry(entry("c", local("2026-10-30T00:20"))) // same day as b
        repository.saveEntry(entry("d", local("2026-10-01T12:00")))
        repository.saveEntry(entry("gone", local("2026-10-15T12:00")))
        repository.deleteEntry("gone")

        assertEquals(
            setOf(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 29), LocalDate.of(2026, 10, 30)),
            repository.loggedDays().first(),
        )
    }

    @Test
    fun `weights ask for a sync but not a widget update`() = runTest {
        repository.addWeight(80.5, local("2026-10-30T07:00"))

        assertEquals(1, syncRequests)
        assertEquals(0, widgetUpdates)
        val latest = repository.latestWeight().first()!!
        assertEquals(80.5, latest.kilograms, 0.0)

        repository.deleteWeight(latest.id)

        assertEquals(2, syncRequests)
        assertNull(repository.latestWeight().first())
    }
}
