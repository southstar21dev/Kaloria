package dev.southstar21.kaloria.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
        assertEquals("0.33", 0.333.toInputText())
    }
}
