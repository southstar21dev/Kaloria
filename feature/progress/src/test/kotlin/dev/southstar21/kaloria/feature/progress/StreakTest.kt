package dev.southstar21.kaloria.feature.progress

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreakTest {
    private val today = LocalDate.of(2026, 10, 29)
    private fun days(vararg back: Long) = back.map { today.minusDays(it) }.toSet()

    @Test
    fun `counts back from today when today is logged`() {
        val s = Streak.of(days(0, 1, 2, 4), today)
        assertEquals(3, s.current)
        assertTrue(s.loggedToday)
    }

    @Test
    fun `an unlogged today keeps yesterday's streak alive`() {
        val s = Streak.of(days(1, 2, 3), today)
        assertEquals(3, s.current)
        assertFalse(s.loggedToday)
    }

    @Test
    fun `a missed yesterday ends the streak`() {
        assertEquals(0, Streak.of(days(2, 3, 4), today).current)
    }

    @Test
    fun `best is the longest run ever and total counts every logged day`() {
        val s = Streak.of(days(0, 1, 5, 6, 7, 8, 20), today)
        assertEquals(2, s.current)
        assertEquals(4, s.best)
        assertEquals(7, s.totalDays)
    }

    @Test
    fun `future days are ignored`() {
        val s = Streak.of(days(0) + today.plusDays(1), today)
        assertEquals(1, s.current)
        assertEquals(1, s.totalDays)
    }

    @Test
    fun `nothing logged`() {
        assertEquals(Streak(0, 0, 0, false), Streak.of(emptySet(), today))
    }
}
