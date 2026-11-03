package dev.southstar21.kaloria.feature.scan

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import dev.southstar21.kaloria.core.designsystem.component.GlassOverCamera
import dev.southstar21.kaloria.core.scanner.label.LiveLabel
import dev.southstar21.kaloria.core.scanner.label.Pt
import dev.southstar21.kaloria.core.testing.screenshot
import dev.southstar21.kaloria.feature.scan.label.LiveFlatCard
import dev.southstar21.kaloria.feature.scan.label.LiveOutline
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The live label scan over the camera: a table found on a can, outlined with its surface grid
 * curving with the can, and its flattened preview in the card below.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class LiveLabelScreenshotTest {
    @get:Rule val compose = createComposeRule()

    /** A table on a can seen from above: rows sag in the middle, the sides lean in. */
    private fun canPoint(u: Float, v: Float): Pt {
        val x = 0.14f + 0.72f * u + (0.5f - u) * 0.06f * v
        val y = 0.36f + 0.24f * v + 0.05f * (1 - (2 * u - 1) * (2 * u - 1))
        return Pt(x, y)
    }

    private fun live(): LiveLabel {
        val steps = 16
        val outline = buildList {
            for (i in 0 until steps) add(canPoint(i / 16f, 0f))
            for (i in 0 until steps) add(canPoint(1f, i / 16f))
            for (i in steps downTo 1) add(canPoint(i / 16f, 1f))
            for (i in steps downTo 1) add(canPoint(0f, i / 16f))
        }
        val grid = listOf(0.25f, 0.5f, 0.75f).map { u -> (0..steps).map { canPoint(u, it / 16f) } } +
            listOf(0.2f, 0.4f, 0.6f, 0.8f).map { v -> (0..steps).map { canPoint(it / 16f, v) } }
        val flat = Bitmap.createBitmap(640, 300, Bitmap.Config.ARGB_8888).apply {
            val canvas = Canvas(this)
            canvas.drawColor(android.graphics.Color.WHITE)
            val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 26f }
            val rule = Paint().apply { strokeWidth = 2f }
            listOf("Energia" to "180 kJ / 42 kcal", "Zsír" to "0 g", "Szénhidrát" to "10,6 g", "amelyből cukrok" to "10,6 g", "Fehérje" to "0 g", "Só" to "0,02 g")
                .forEachIndexed { i, (name, value) ->
                    val y = 52f + i * 42f
                    canvas.drawText(name, 24f, y, ink)
                    canvas.drawText(value, 610f - ink.measureText(value), y, ink)
                    canvas.drawLine(16f, y + 12, 624f, y + 12, rule)
                }
        }
        return LiveLabel(outline, grid, flat)
    }

    @Test fun liveCan() {
        val label = live()
        compose.screenshot("label_live_can") {
            GlassOverCamera {
                Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF6A6E75), Color(0xFF1C1E22))))) {
                    // The can's label where the camera sees it.
                    LiveOutline(label)
                    Box(Modifier.align(Alignment.BottomCenter).padding(16.dp).padding(bottom = 120.dp)) { LiveFlatCard(label.flat) }
                }
            }
        }
    }
}
