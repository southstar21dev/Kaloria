package dev.southstar21.kaloria.core.ui

import java.time.LocalTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FormatTest {
    @Test
    fun `decimal input accepts comma and dot`() {
        assertEquals(12.5, "12,5".parseDecimal()!!, 0.0)
        assertEquals(12.5, " 12.5 ".parseDecimal()!!, 0.0)
        assertNull("".parseDecimal())
        assertNull("1,2,3".parseDecimal())
    }

    @Test
    fun `input text drops trailing zeros`() {
        assertEquals("100", 100.0.toInputText())
        assertEquals("12.5", 12.5.toInputText())
        assertEquals("0.333", 0.333.toInputText())
    }

    @Test
    fun `input text keeps small values instead of rounding them to two decimals`() {
        assertEquals("0.035", 0.035.toInputText())
        assertEquals("0.004", 0.004.toInputText())
        assertEquals("12.004", 12.004.toInputText())
        // A kJ conversion has endless digits; six significant ones are plenty to read.
        assertEquals("239.006", (1000 / 4.184).toInputText())
        assertEquals(0.035, 0.035.toInputText().parseDecimal()!!, 0.0)
    }

    @Test
    fun `times follow the 12 or 24 hour setting in the locale's own way`() {
        val time = LocalTime.of(14, 5)
        assertEquals("14:05", time.format(timeFormatter(Locale.UK, is24Hour = true)))
        assertEquals("2:05 PM", time.format(timeFormatter(Locale.US, is24Hour = false)).replace(' ', ' '))
        assertEquals("14:05", time.format(timeFormatter(Locale.US, is24Hour = true)))
        assertEquals("14:05", time.format(timeFormatter(Locale.forLanguageTag("hu-HU"), is24Hour = true)))
    }
}
