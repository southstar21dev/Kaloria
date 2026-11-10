package dev.southstar21.kaloria.core.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    /** Reads [value] (null: nothing read) on frames [from] until [until], 33 ms apart; whether any was accepted. */
    private fun ReadConfirmer.frames(value: String?, from: Long, until: Long, inView: Set<String> = setOfNotNull(value)): Boolean {
        var accepted = false
        var t = from
        while (t < until) {
            accepted = read(value, short = false, nowNanos = t, inView = inView) || accepted
            t += frame
        }
        return accepted
    }

    @Test fun `a handled code still in view after a rescan isn't accepted again`() {
        val c = ReadConfirmer()
        assertEquals(3, c.acceptedAfter(ean13, short = false))
        c.ignoreUntilGone(ean13)
        // Ten seconds later (the sheet was open meanwhile, no frames read), the product still held up.
        assertFalse(c.frames(ean13, 10_000_000_000L, 13_000_000_000L))
    }

    @Test fun `a handled code is accepted again once it has been out of view for a second`() {
        val c = ReadConfirmer()
        c.ignoreUntilGone(ean13)
        assertFalse(c.frames(null, 0, 1_100_000_000L))
        assertTrue(c.frames(ean13, 1_100_000_000L, 1_300_000_000L))
    }

    @Test fun `a glimpse away isn't long enough to read a handled code again`() {
        val c = ReadConfirmer()
        c.ignoreUntilGone(ean13)
        assertFalse(c.frames(null, 0, 500_000_000L))
        assertFalse(c.frames(ean13, 500_000_000L, 600_000_000L))
        assertFalse(c.frames(null, 600_000_000L, 1_200_000_000L))
        assertFalse(c.frames(ean13, 1_200_000_000L, 1_400_000_000L))
    }

    @Test fun `another code is accepted while a handled one is ignored`() {
        val c = ReadConfirmer()
        c.ignoreUntilGone(ean13)
        assertTrue(c.frames(ean8, 0, 200_000_000L))
    }

    @Test fun `a handled code at the edge of the view still counts as in view`() {
        val c = ReadConfirmer()
        c.ignoreUntilGone(ean13)
        // Off centre: nothing read, but the code is still on screen.
        assertFalse(c.frames(null, 0, 2_000_000_000L, inView = setOf(ean13)))
        assertFalse(c.frames(ean13, 2_000_000_000L, 2_200_000_000L))
    }
}
