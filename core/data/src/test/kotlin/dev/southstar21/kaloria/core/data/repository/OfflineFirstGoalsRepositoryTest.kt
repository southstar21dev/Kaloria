package dev.southstar21.kaloria.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.southstar21.kaloria.core.data.sync.SyncRequester
import dev.southstar21.kaloria.core.data.widget.WidgetUpdater
import dev.southstar21.kaloria.core.database.KaloriaDatabase
import dev.southstar21.kaloria.core.model.Goals
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OfflineFirstGoalsRepositoryTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), KaloriaDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    private var syncRequests = 0
    private var widgetUpdates = 0

    private val repository = OfflineFirstGoalsRepository(
        dao = db.goalsDao(),
        clock = Clock.fixed(Instant.parse("2026-10-30T12:00:00Z"), ZoneOffset.UTC),
        sync = SyncRequester { syncRequests++ },
        widget = WidgetUpdater { widgetUpdates++ },
    )

    @After fun close() = db.close()

    @Test
    fun `no goals row means empty goals`() = runTest {
        assertEquals(Goals(), repository.goals.first())
    }

    @Test
    fun `two saves keep one row with the latest goals`() = runTest {
        repository.save(Goals(energyKcal = 1800))
        val id = db.goalsDao().current()!!.id

        repository.save(Goals(energyKcal = 2200, steps = 9000))

        assertEquals(Goals(energyKcal = 2200, steps = 9000), repository.goals.first())
        assertEquals(id, db.goalsDao().current()!!.id)
        assertEquals(listOf(id), db.goalsDao().dirty().map { it.id })
        assertEquals(2, syncRequests)
        assertEquals(2, widgetUpdates)
    }
}
