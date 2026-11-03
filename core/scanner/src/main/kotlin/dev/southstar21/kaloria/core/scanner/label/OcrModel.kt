package dev.southstar21.kaloria.core.scanner.label

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** A point in image pixels. */
data class Pt(val x: Float, val y: Float) {
    operator fun plus(o: Pt) = Pt(x + o.x, y + o.y)
    operator fun minus(o: Pt) = Pt(x - o.x, y - o.y)
    operator fun times(k: Float) = Pt(x * k, y * k)
    infix fun dot(o: Pt) = x * o.x + y * o.y
    val length: Float get() = kotlin.math.sqrt(x * x + y * y)
}

/** A recognised word and its corners, in the same order as [OcrLine.corners]. */
data class OcrWord(val text: String, val corners: List<Pt>) {
    val center: Pt get() = (corners[0] + corners[1] + corners[2] + corners[3]) * 0.25f
}

/**
 * A recognised line of text: its axis-aligned box in the image (pixels), the angle the text runs
 * at (degrees, clockwise), its corners in reading order (top-left, top-right, bottom-right,
 * bottom-left of the text) and its words, as ML Kit reports them.
 */
data class OcrLine(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val angle: Float = 0f,
    val corners: List<Pt> = listOf(Pt(left.toFloat(), top.toFloat()), Pt(right.toFloat(), top.toFloat()), Pt(right.toFloat(), bottom.toFloat()), Pt(left.toFloat(), bottom.toFloat())),
    val words: List<OcrWord> = emptyList(),
) {
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}

/** Table rows as text, read left to right; see [groupRows]. */
fun rowsOf(lines: List<OcrLine>): List<String> = groupRows(lines).map { row -> row.joinToString("  ") { it.text } }

/**
 * Rebuilds the label's table rows. OCR often returns the nutrient names and the value columns as
 * separate blocks, so lines are matched up by position: lines whose vertical centres are within
 * half a line height of each other are one row, sorted left to right; rows run top to bottom.
 *
 * A photo is rarely held perfectly level, and across a table's width a few degrees of tilt shift a
 * value by more than a row, pairing it with the wrong nutrient. So positions are first rotated by
 * the text's median angle, which puts each row back on one horizontal line.
 */
fun groupRows(lines: List<OcrLine>): List<List<OcrLine>> {
    if (lines.isEmpty()) return emptyList()
    val tilt = medianAngle(lines) * PI / 180
    val c = cos(tilt)
    val s = sin(tilt)
    val upright = lines.map { line ->
        val width = (line.right - line.left).toFloat()
        val boxHeight = (line.bottom - line.top).toFloat()
        // A tilted line's box also spans the rise across its width; take that back out.
        val height = ((boxHeight - width * abs(s)) / c).toFloat().coerceIn(boxHeight / 4, boxHeight)
        Upright(
            line,
            x = (line.centerX * c + line.centerY * s).toFloat(),
            y = (-line.centerX * s + line.centerY * c).toFloat(),
            height = height,
        )
    }
    val rows = mutableListOf<MutableList<Upright>>()
    for (line in upright.sortedBy { it.y }) {
        val row = rows.lastOrNull()
        // Against the row's middle so far, not its first line, so one line set a little high
        // doesn't pull the row away from the rest.
        if (row != null && abs(line.y - row.sumOf { it.y.toDouble() }.toFloat() / row.size) <= maxOf(row.maxOf { it.height }, line.height) / 2) {
            row.add(line)
        } else {
            rows.add(mutableListOf(line))
        }
    }
    return rows.map { row -> row.sortedBy { it.x }.map { it.line } }
}

internal fun medianAngle(lines: List<OcrLine>): Float = lines.map { it.angle }.sorted()[lines.size / 2]

private class Upright(val line: OcrLine, val x: Float, val y: Float, val height: Float)
