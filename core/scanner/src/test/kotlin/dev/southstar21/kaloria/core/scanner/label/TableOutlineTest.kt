package dev.southstar21.kaloria.core.scanner.label

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TableOutlineTest {

    /** A flat label: names at x 100 (sub-rows indented), values at x 600, rows 40 px apart. */
    private val flatTable: List<Triple<String, Float, Float>> = listOf(
        Triple("Tápérték 100 g termékben", 100f, 260f),
        Triple("Energia", 100f, 300f), Triple("1338 kJ / 320 kcal", 600f, 300f),
        Triple("Zsír", 100f, 340f), Triple("24 g", 600f, 340f),
        Triple("ebből telített zsírsavak", 130f, 380f), Triple("16 g", 600f, 380f),
        Triple("Szénhidrát", 100f, 420f), Triple("2,0 g", 600f, 420f),
        Triple("ebből cukrok", 130f, 460f), Triple("2,0 g", 600f, 460f),
        Triple("Fehérje", 100f, 500f), Triple("24 g", 600f, 500f),
        Triple("Só", 100f, 540f), Triple("1,8 g", 600f, 540f),
    )

    /** The table as photographed through [map], each line's corners mapped from the flat label. */
    private fun photographed(map: (Pt) -> Pt): List<OcrLine> = flatTable.map { (text, x, y) ->
        val width = text.length * 14f
        val corners = listOf(Pt(x, y - 13), Pt(x + width, y - 13), Pt(x + width, y + 13), Pt(x, y + 13)).map(map)
        val angle = Math.toDegrees(atan2((corners[1].y - corners[0].y).toDouble(), (corners[1].x - corners[0].x).toDouble())).toFloat()
        OcrLine(
            text,
            corners.minOf { it.x }.toInt(),
            corners.minOf { it.y }.toInt(),
            corners.maxOf { it.x }.toInt(),
            corners.maxOf { it.y }.toInt(),
            angle,
            corners,
        )
    }

    private fun Quad.contains(p: Pt): Boolean {
        val ring = listOf(topLeft, topRight, bottomRight, bottomLeft)
        return ring.indices.all { i ->
            val a = ring[i]
            val b = ring[(i + 1) % 4]
            (b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x) >= 0
        }
    }

    private fun degrees(from: Pt, to: Pt) = Math.toDegrees(atan2((to.y - from.y).toDouble(), (to.x - from.x).toDouble()))

    @Test
    fun `a rotated table is outlined along its rows, with every line inside`() {
        val rad = Math.toRadians(8.0)
        val lines = photographed { p -> Pt((p.x * cos(rad) - p.y * sin(rad)).toFloat() + 200, (p.x * sin(rad) + p.y * cos(rad)).toFloat()) }
        val quad = TableOutline.find(lines)!!
        assertEquals(8.0, degrees(quad.topLeft, quad.topRight), 0.5)
        assertEquals(8.0, degrees(quad.bottomLeft, quad.bottomRight), 0.5)
        // The indented sub-rows don't tilt the left edge.
        assertEquals(98.0, degrees(quad.topLeft, quad.bottomLeft), 0.5)
        lines.flatMap { it.corners }.forEach { assertTrue("$it outside $quad", quad.contains(it)) }
    }

    @Test
    fun `a label photographed from below narrows toward the top, and the outline follows`() {
        // A keystone: the further up, the more the label is squeezed toward x = 400.
        val keystone = { p: Pt -> Pt(400 + (p.x - 400) * (0.75f + p.y / 1200f), p.y) }
        val lines = photographed(keystone)
        val quad = TableOutline.find(lines)!!
        assertTrue(
            "top ${quad.topRight.x - quad.topLeft.x} should be narrower than bottom ${quad.bottomRight.x - quad.bottomLeft.x}",
            quad.topRight.x - quad.topLeft.x < quad.bottomRight.x - quad.bottomLeft.x - 20,
        )
        // The left edge leans the way the names' column does.
        val names = lines.filter { it.text == "Energia" || it.text == "Só" }
        assertEquals(degrees(names[0].corners[0], names[1].corners[0]), degrees(quad.topLeft, quad.bottomLeft), 1.5)
        lines.flatMap { it.corners }.forEach { assertTrue("$it outside $quad", quad.contains(it)) }
    }

    @Test
    fun `a real tilted label photo is outlined with its column headings, and flattens into readable rows`() {
        val quad = TableOutline.find(SalamiLabel.photo)!!
        fun centre(text: String) = SalamiLabel.photo.first { it.text == text }.let { Pt(it.centerX, it.centerY) }
        listOf("100 g", "30 g", "Tápérték", "Energia", "Só", "1,39").forEach { assertTrue("$it outside $quad", quad.contains(centre(it))) }
        assertTrue(!quad.contains(centre("Pick Téliszalámi")))

        // Flatten the lines the way the photo is warped, then read them back as rows.
        val w = quad.width
        val h = quad.height
        val toFlat = homography(listOf(quad.topLeft, quad.topRight, quad.bottomRight, quad.bottomLeft), listOf(Pt(0f, 0f), Pt(w, 0f), Pt(w, h), Pt(0f, h)))
        val flat = SalamiLabel.photo.filter { line -> line.corners.all { quad.contains(it) } }.map { line ->
            val c = line.corners.map(toFlat)
            OcrLine(line.text, c.minOf { it.x }.toInt(), c.minOf { it.y }.toInt(), c.maxOf { it.x }.toInt(), c.maxOf { it.y }.toInt(), 0f, c)
        }
        val read = NutritionLabelParser.parse(rowsOf(flat)).nutrients
        assertEquals(494.0, read.energyKcal!!, 0.0)
        assertEquals(44.0, read.fat!!, 0.0)
        assertEquals(17.0, read.saturatedFat!!, 0.0)
        assertEquals(1.2, read.carbohydrates!!, 0.0)
        assertEquals(0.8, read.sugars!!, 0.0)
        assertEquals(23.0, read.protein!!, 0.0)
        assertEquals(4.3, read.salt!!, 0.0)
    }

    /** The perspective transform taking [from]'s four points to [to]'s. */
    private fun homography(from: List<Pt>, to: List<Pt>): (Pt) -> Pt {
        val a = Array(8) { DoubleArray(9) }
        for (i in 0 until 4) {
            val (x, y) = from[i].x.toDouble() to from[i].y.toDouble()
            val (u, v) = to[i].x.toDouble() to to[i].y.toDouble()
            a[2 * i] = doubleArrayOf(x, y, 1.0, 0.0, 0.0, 0.0, -u * x, -u * y, u)
            a[2 * i + 1] = doubleArrayOf(0.0, 0.0, 0.0, x, y, 1.0, -v * x, -v * y, v)
        }
        for (col in 0 until 8) {
            val pivot = (col until 8).maxBy { kotlin.math.abs(a[it][col]) }
            a[col] = a[pivot].also { a[pivot] = a[col] }
            for (r in 0 until 8) {
                if (r != col) {
                    val f = a[r][col] / a[col][col]
                    for (k in col until 9) a[r][k] -= f * a[col][k]
                }
            }
        }
        val m = DoubleArray(8) { a[it][8] / a[it][it] }
        return { p ->
            val d = m[6] * p.x + m[7] * p.y + 1
            Pt(((m[0] * p.x + m[1] * p.y + m[2]) / d).toFloat(), ((m[3] * p.x + m[4] * p.y + m[5]) / d).toFloat())
        }
    }

    @Test
    fun `text without a nutrition table has no outline`() {
        val lines = listOf("Best before", "12.11.2026", "Made in Hungary").mapIndexed { i, t -> OcrLine(t, 10, 100 + i * 40, 200, 126 + i * 40) }
        assertNull(TableOutline.find(lines))
    }
}
