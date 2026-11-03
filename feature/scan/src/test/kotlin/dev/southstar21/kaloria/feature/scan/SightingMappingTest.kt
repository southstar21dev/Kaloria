package dev.southstar21.kaloria.feature.scan

import androidx.compose.ui.unit.IntSize
import dev.southstar21.kaloria.core.scanner.BarcodeSighting
import dev.southstar21.kaloria.feature.scan.camera.toView
import org.junit.Assert.assertEquals
import org.junit.Test

class SightingMappingTest {
    private val screen = IntSize(1080, 2340)

    @Test
    fun `the visible area maps onto the whole view`() {
        val whole = BarcodeSighting("x", 0f, 0f, 1f, 1f).toView(screen)
        assertEquals(0f, whole.left, 0.01f)
        assertEquals(0f, whole.top, 0.01f)
        assertEquals(1080f, whole.right, 0.01f)
        assertEquals(2340f, whole.bottom, 0.01f)
    }

    @Test
    fun `a code keeps its place and proportions on screen`() {
        val r = BarcodeSighting("x", 0.25f, 0.4f, 0.75f, 0.5f).toView(screen)
        assertEquals(270f, r.left, 0.01f)
        assertEquals(936f, r.top, 0.01f)
        assertEquals(540f, r.width, 0.01f)
        assertEquals(234f, r.height, 0.01f)
    }
}
