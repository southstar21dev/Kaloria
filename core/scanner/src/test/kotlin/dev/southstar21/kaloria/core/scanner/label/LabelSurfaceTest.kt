package dev.southstar21.kaloria.core.scanner.label

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelSurfaceTest {

    /** A flat table, in mm on the label: (text, left, row) with rows 7 mm apart, letters 4 mm high. */
    private val table = listOf(
        Triple("Nutrition", 0.0, 0), Triple("per", 30.0, 0), Triple("100 ml", 50.0, 0),
        Triple("Energy", 0.0, 1), Triple("180 kJ / 42 kcal", 34.0, 1),
        Triple("Fat", 0.0, 2), Triple("0 g", 58.0, 2),
        Triple("of which saturates", 0.0, 3), Triple("0 g", 58.0, 3),
        Triple("Carbohydrate", 0.0, 4), Triple("10,6 g", 52.0, 4),
        Triple("of which sugars", 0.0, 5), Triple("10,6 g", 52.0, 5),
        Triple("Protein", 0.0, 6), Triple("0 g", 58.0, 6),
        Triple("Salt", 0.0, 7), Triple("0,02 g", 52.0, 7),
    )

    private val letter = 4.0
    private fun widthOf(text: String) = LabelSurface.emWidth(text) * letter

    /**
     * The table wrapped round a can of radius [radius] mm (centred on the table), photographed from
     * [distance] mm away and [above] mm above its middle, 12 px per mm at the label's distance.
     */
    private fun onCan(radius: Double, distance: Double = 220.0, above: Double = 60.0): List<OcrLine> {
        val middle = 36.0
        fun project(s: Double, y: Double): Pt {
            val angle = (s - middle) / radius
            val x = radius * sin(angle)
            val depth = distance - radius * cos(angle)
            val f = 12.0 * (distance - radius)
            return Pt((600 + f * x / depth).toFloat(), (500 + f * (y - above) / depth).toFloat())
        }
        return table.map { (text, left, row) ->
            val top = row * 7.0
            var cursor = left
            val words = text.split(" ").map { w ->
                val box = listOf(project(cursor, top), project(cursor + widthOf(w), top), project(cursor + widthOf(w), top + letter), project(cursor, top + letter))
                cursor += widthOf(w) + widthOf(" ")
                OcrWord(w, box)
            }
            val corners = listOf(words.first().corners[0], words.last().corners[1], words.last().corners[2], words.first().corners[3])
            val angle = Math.toDegrees(kotlin.math.atan2((corners[1].y - corners[0].y).toDouble(), (corners[1].x - corners[0].x).toDouble())).toFloat()
            OcrLine(text, corners.minOf { it.x }.toInt(), corners.minOf { it.y }.toInt(), corners.maxOf { it.x }.toInt(), corners.maxOf { it.y }.toInt(), angle, corners, words)
        }
    }

    /** Where photo point [p] lands on the flat table: [map] solved backwards by Newton's method. */
    private fun LabelSurface.flatOf(p: Pt): Pt {
        var u = 0.5f
        var v = 0.5f
        repeat(30) {
            val e = 1e-3f
            val here = map(u, v)
            val du = (map(u + e, v) - here) * (1 / e)
            val dv = (map(u, v + e) - here) * (1 / e)
            val r = p - here
            val det = du.x * dv.y - dv.x * du.y
            u += (r.x * dv.y - dv.x * r.y) / det
            v += (du.x * r.y - r.x * du.y) / det
        }
        return Pt(u, v)
    }

    private fun assertFlattens(lines: List<OcrLine>, surface: LabelSurface, rowTolerance: Double = 0.2, maxOffProportion: Double = 0.015) {
        val flat = lines.flatMap { it.words }.map { surface.flatOf(it.center) }
        val rows = table.flatMapIndexed { i, (text, _, row) -> text.split(" ").map { row } }
        val rowHeight = (flat.filterIndexed { i, _ -> rows[i] == 7 }.map { it.y }.average() - flat.filterIndexed { i, _ -> rows[i] == 0 }.map { it.y }.average()) / 7
        // Each row's words sit on one line of the flat table (to within a fifth of a row on a flat
        // label; round a can, near enough that the table reads as rows again).
        for (row in 0..7) {
            val ys = flat.filterIndexed { i, _ -> rows[i] == row }.map { it.y }
            assertTrue("row $row spreads ${(ys.max() - ys.min()) / rowHeight} rows", ys.max() - ys.min() < rowHeight * rowTolerance)
        }
        // And across, positions are back in proportion to the label: the value column starts at
        // the same place on every row, and the table's left and right halves keep their widths.
        val valueStarts = lines.filter { it.text.endsWith(" g") && it.words.size <= 2 }.map { surface.flatOf(it.words.first().corners[0]).x }
        val zeroG = lines.filter { it.text == "0 g" }.map { surface.flatOf(it.corners[0]).x }
        assertTrue("0 g column spread ${zeroG.max() - zeroG.min()}", zeroG.max() - zeroG.min() < 0.02f)
        val energy = surface.flatOf(lines.first { it.text == "Energy" }.corners[0]).x
        val kcalEnd = surface.flatOf(lines.first { it.text.endsWith("kcal") }.corners[1]).x
        val fatValue = surface.flatOf(lines.first { it.text == "0 g" }.corners[0]).x
        // On the label "0 g" starts at 58 of the 34..(34+widthOf) span; check its share of the row.
        val labelShare = (58.0 - 0.0) / (34.0 + widthOf("180 kJ / 42 kcal") - 0.0)
        assertEquals(labelShare, ((fatValue - energy) / (kcalEnd - energy)).toDouble(), 0.03)
        assertTrue(valueStarts.isNotEmpty())

        // Every word's left edge, flat, against where it starts on the label: one straight line,
        // or the can's edges are still squeezed.
        val starts = table.flatMap { (text, left, _) ->
            var cursor = left
            text.split(" ").map { w -> cursor.also { cursor += widthOf(w) + widthOf(" ") } }
        }
        val us = lines.flatMap { it.words }.map { surface.flatOf(it.corners[0]).x.toDouble() }
        val n = starts.size
        val ms = starts.average()
        val mu = us.average()
        val slope = starts.indices.sumOf { (starts[it] - ms) * (us[it] - mu) } / starts.sumOf { (it - ms) * (it - ms) }
        val worst = starts.indices.maxOf { abs(us[it] - (mu + slope * (starts[it] - ms))) }
        assertTrue("across, words are off proportion by up to $worst of the width ($n words)", worst < maxOffProportion)
    }

    @Test
    fun `a label round a can is fitted as curved, and flattens into straight rows in proportion`() {
        // A 330 ml can (33 mm radius) with the table wrapped some 60 degrees each way.
        val lines = onCan(radius = 33.0)
        val surface = LabelSurface.fit(lines)!!
        assertTrue(surface.isCurved)
        assertFlattens(lines, surface, rowTolerance = 0.3, maxOffProportion = 0.03)
    }

    @Test
    fun `a flat label is not bent or stretched`() {
        // A very large radius is a flat label in perspective.
        val lines = onCan(radius = 100_000.0, distance = 100_300.0)
        val surface = LabelSurface.fit(lines)!!
        assertFlattens(lines, surface)
        val corners = listOf(surface.map(0f, 0f), surface.map(1f, 0f), surface.map(1f, 1f), surface.map(0f, 1f))
        // The middle of the flat table is where the straight edges' diagonals cross.
        val mid = surface.map(0.5f, 0.5f)
        val (a, b) = corners[0] to corners[2]
        val (c, d) = corners[1] to corners[3]
        val t = ((c.x - a.x) * (d.y - c.y) - (c.y - a.y) * (d.x - c.x)) / ((b.x - a.x) * (d.y - c.y) - (b.y - a.y) * (d.x - c.x))
        val cross = a + (b - a) * t
        assertTrue("middle $mid vs diagonals $cross", abs(mid.x - cross.x) < 3 && abs(mid.y - cross.y) < 3)
        assertFalse(abs(surface.width / surface.height - 1f) < 0.01f)
    }

    @Test
    fun `a real can photo is found despite misread names, and its curved rows straighten`() {
        val lines = CanLabel.photo
        val fit = TableOutline.fit(lines)!!
        // "Energja" and "Feheie" still count as names, and the values that run on below the last
        // name, the rows curving down, stay in the table.
        assertTrue(fit.lines.map { it.text }.containsAll(listOf("Energja", "Feheie", "0,02 g", "Tápértéktartalom")))
        val surface = LabelSurface.fit(lines)!!
        assertTrue(surface.isCurved)
        val flat = lines.filter { it in fit.lines }.map { line ->
            val c = line.corners.map { surface.flatOf(it) }.map { Pt(it.x * 1000, it.y * 1000 * surface.height / surface.width) }
            OcrLine(line.text, c.minOf { it.x }.toInt(), c.minOf { it.y }.toInt(), c.maxOf { it.x }.toInt(), c.maxOf { it.y }.toInt(), 0f, c)
        }
        val rows = rowsOf(flat)
        // Straight, the middle rows pair each name with its own values (straight grouping in the
        // photo put them a row out).
        assertTrue(rows.toString(), rows.any { it.startsWith("Szénhidrat") && "10,6g" in it })
        assertTrue(rows.toString(), rows.any { it.startsWith("arnelyböl telitett") && it.endsWith("0g  0g") })
        assertTrue(rows.toString(), rows.any { it.startsWith("100 ml") && "330 ml" in it })
    }
}
