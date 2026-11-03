package dev.southstar21.kaloria.core.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ReadConfirmerTest {
    private val frame = 33_000_000L
    private val ean13 = "5991234567890"
    private val ean8 = "59912345"

    /** The frame (1-based) on which a run of identical reads is first accepted. */
    private fun ReadConfirmer.acceptedAfter(value: String, short: Boolean): Int = (1..20).first { read(value, short, it * frame) }

    @Test fun `a normal code needs three reads`() {
        assertEquals(3, ReadConfirmer().acceptedAfter(ean13, short = false))
    }

    @Test fun `a short code needs five reads`() {
        assertEquals(5, ReadConfirmer().acceptedAfter(ean8, short = true))
    }

    @Test fun `a different code in between starts over`() {
        val c = ReadConfirmer()
        c.read(ean13, false, 0)
        c.read(ean13, false, frame)
        assertFalse(c.read("5991234567883", false, 2 * frame))
        assertFalse(c.read(ean13, false, 3 * frame))
        assertFalse(c.read(ean13, false, 4 * frame))
        assert(c.read(ean13, false, 5 * frame))
    }

    @Test fun `reads too far apart don't count together`() {
        val c = ReadConfirmer()
        c.read(ean13, false, 0)
        c.read(ean13, false, frame)
        assertFalse(c.read(ean13, false, 2_000_000_000L))
    }
}
