package dev.southstar21.kaloria.core.scanner.label

/** A recognised line of text and where it sat in the image (pixels). */
data class OcrLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val centerY: Int get() = (top + bottom) / 2
    val height: Int get() = bottom - top
}

/**
 * Rebuilds the label's table rows. OCR often returns the nutrient names and the value columns as
 * separate blocks; lines whose vertical centres are within half a line height of each other are
 * one row, read left to right.
 */
fun rowsOf(lines: List<OcrLine>): List<String> {
    if (lines.isEmpty()) return emptyList()
    val rows = mutableListOf<MutableList<OcrLine>>()
    for (line in lines.sortedBy { it.centerY }) {
        val row = rows.lastOrNull()
        val anchor = row?.first()
        if (anchor != null && kotlin.math.abs(line.centerY - anchor.centerY) <= maxOf(anchor.height, line.height) / 2) {
            row.add(line)
        } else {
            rows.add(mutableListOf(line))
        }
    }
    return rows.map { row -> row.sortedBy { it.left }.joinToString("  ") { it.text } }
}
