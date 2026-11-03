package dev.southstar21.kaloria.core.scanner

import org.junit.Assert.assertArrayEquals
import org.junit.Test

/**
 * A 1280 x 720 landscape buffer (as phone sensors deliver) holding a 100 x 50 rect at (200, 100).
 * Expected edges were worked out by turning the picture by hand.
 */
class UprightEdgesTest {
    private val w = 1280
    private val h = 720

    @Test
    fun `no rotation leaves it as is`() = assertArrayEquals(intArrayOf(200, 100, 300, 150), uprightEdges(200, 100, 300, 150, 0, w, h))

    @Test
    fun `a quarter turn clockwise, the usual portrait case`() = // The buffer's top edge becomes the upright right side: x' = h - y, y' = x.
        assertArrayEquals(intArrayOf(570, 200, 620, 300), uprightEdges(200, 100, 300, 150, 90, w, h))

    @Test
    fun `a half turn`() = assertArrayEquals(intArrayOf(980, 570, 1080, 620), uprightEdges(200, 100, 300, 150, 180, w, h))

    @Test
    fun `three quarter turns`() = // x' = y, y' = w - x.
        assertArrayEquals(intArrayOf(100, 980, 150, 1080), uprightEdges(200, 100, 300, 150, 270, w, h))

    @Test
    fun `the whole buffer stays the whole upright frame`() = assertArrayEquals(intArrayOf(0, 0, h, w), uprightEdges(0, 0, w, h, 90, w, h))
}
