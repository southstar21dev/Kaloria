package dev.southstar21.kaloria.core.scanner.label

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** The nutrition table's corners in the photo: top-left, top-right, bottom-right, bottom-left. */
data class Quad(val topLeft: Pt, val topRight: Pt, val bottomRight: Pt, val bottomLeft: Pt) {
    val width: Float get() = ((topRight - topLeft).length + (bottomRight - bottomLeft).length) / 2
    val height: Float get() = ((bottomLeft - topLeft).length + (bottomRight - topRight).length) / 2
}

/**
 * Finds the nutrition table in a photo from the text alone, so it can be cropped and flattened
 * before it's read. No edge detection: the table's rows are the lines starting with a nutrient name
 * (plus the values beside them), and the text gives the geometry. The top and bottom rows set the
 * top and bottom edges' directions, the left-aligned names set the left edge's; a camera held at
 * an angle makes the side edges lean in mirror image, so the right edge mirrors the left. Each edge
 * is then pushed out until every line of the table is inside, plus a margin.
 */
object TableOutline {

    /** Fewest nutrient rows that make a table; fewer and the photo is probably something else. */
    private const val MIN_ROWS = 3

    /** A value cell as OCR reads it: "24 g", "<0,5 g", "13g", "179", "5,1 9". */
    private val value = Regex("""[<≤]?\s*\d+(?:[.,]\d+)?\s*[\p{L}µ%]{0,4}(?:\s\d)?""")

    /** Edges further than this from the text's own angle are a bad fit; fall back to a rectangle. */
    private const val MAX_LEAN_DEG = 25.0

    fun find(lines: List<OcrLine>): Quad? = fit(lines)?.quad

    /** The table's outline and the lines inside it (the rows and their headings). */
    class Fit(val quad: Quad, val lines: List<OcrLine>, val lineHeight: Float)

    fun fit(lines: List<OcrLine>): Fit? {
        val rows = groupRows(lines)
        val nutrientRows = rows.indices.filter { i -> NutritionLabelParser.isNutrientRow(rows[i].joinToString(" ") { it.text }) }
        if (nutrientRows.size < MIN_ROWS) return null
        val lineHeight = lines.map { lineHeight(it) }.sorted()[lines.size / 2]

        // The table runs from the first nutrient row to the last, with the header above it ("Per
        // 100 g", "Tápérték") when that sits close.
        var first = nutrientRows.first()
        val last = nutrientRows.last()
        repeat(2) {
            val above = rows.getOrNull(first - 1) ?: return@repeat
            if (gap(above, rows[first]) < lineHeight * 2.5f) first--
        }
        val table = rows.subList(first, last + 1)

        val tilt = medianAngle(table.flatten()) * PI / 180
        val across = Pt(cos(tilt).toFloat(), sin(tilt).toFloat())
        val down = Pt(-across.y, across.x)

        val left = leftEdge(table.flatMap { row -> listOf(row.first().corners[0], row.first().corners[3]) }, down, lineHeight) ?: down
        // The last column's values line up at their ends, starts or middles (right-aligned, left-
        // aligned or centred); whichever lines up best gives the right edge. Only values count, not
        // text printed beside the table. Failing that, a camera held at an angle leans the sides in
        // mirror image, so mirror the left edge.
        val right = rightEdge(table.mapNotNull { row -> row.lastOrNull { value.matches(it.text.trim()) } }, down, lineHeight)
            ?: rotate(down, -(angleOf(left) - angleOf(down)))

        // The column headings ("100 g", "30 g") sit above the table's first row, and in a tilted
        // photo they can fall into rows of their own: take in any line just above the table and
        // across from it.
        val tableLines = table.flatten().toSet()
        val spanStart = tableLines.minOf { it.corners[0] dot across }
        val spanEnd = tableLines.maxOf { it.corners[1] dot across }
        val topY = tableLines.minOf { line -> line.corners.minOf { it dot down } }
        val headings = lines.filter { line ->
            line !in tableLines && (
                Pt(line.centerX, line.centerY).let { c ->
                    (c dot across) in spanStart..spanEnd && (c dot down) in (topY - lineHeight * 2.5f)..topY
                }
                )
        }
        // Rows that curve (a can seen from above) put the last values below the last name: take in
        // values carrying on down under the table, each within a few lines of the one before.
        val below = mutableListOf<OcrLine>()
        var bottomY = tableLines.maxOf { line -> line.corners.maxOf { it dot down } }
        while (true) {
            val next = lines.filter { line ->
                line !in tableLines && line !in headings && line !in below && line.text.any(Char::isDigit) &&
                    Pt(line.centerX, line.centerY).let { c -> (c dot across) in spanStart..spanEnd && (c dot down) in bottomY..(bottomY + lineHeight * 3f) }
            }
            if (next.isEmpty()) break
            below += next
            bottomY = next.maxOf { line -> line.corners.maxOf { it dot down } }
        }
        val outlined = (tableLines + headings + below).toList()
        val points = outlined.flatMap { it.corners }

        // Seen at an angle, the rows fan out: the text's direction changes steadily from the top of
        // the table to the bottom. Fitted over all its longer lines, that gives the top and bottom
        // edges, where a single short row ("Só") would be too few pixels to trust.
        val fan = rowFan(outlined, down)
        val top = fan?.invoke(points.minOf { it dot down }) ?: across
        val bottom = fan?.invoke(points.maxOf { it dot down }) ?: across

        val fits = listOf(top to across, bottom to across, left to down, right to down).all { (d, expected) -> angleBetween(d, expected) < MAX_LEAN_DEG }
        val (t, b, l, r) = if (fits) listOf(top, bottom, left, right) else listOf(across, across, down, down)

        val margin = lineHeight * 0.8f
        val topEdge = pushOut(perp(t) * -1f, points, margin)
        val bottomEdge = pushOut(perp(b), points, margin)
        val leftEdge = pushOut(perp(l), points, margin)
        val rightEdge = pushOut(perp(r) * -1f, points, margin)
        val quad = Quad(
            topLeft = intersect(topEdge, leftEdge) ?: return null,
            topRight = intersect(topEdge, rightEdge) ?: return null,
            bottomRight = intersect(bottomEdge, rightEdge) ?: return null,
            bottomLeft = intersect(bottomEdge, leftEdge) ?: return null,
        ).takeIf { it.width > lineHeight * 3 && it.height > lineHeight * 2 } ?: return null
        return Fit(quad, outlined, lineHeight)
    }

    /** A line as {p : normal · p = offset}, with [normal] pointing out of the table. */
    private class Edge(val normal: Pt, val offset: Float)

    private fun pushOut(normal: Pt, points: List<Pt>, margin: Float) = Edge(normal, points.maxOf { it dot normal } + margin)

    private fun intersect(a: Edge, b: Edge): Pt? {
        val det = a.normal.x * b.normal.y - a.normal.y * b.normal.x
        if (abs(det) < 1e-3f) return null
        return Pt(
            (a.offset * b.normal.y - a.normal.y * b.offset) / det,
            (a.normal.x * b.offset - a.offset * b.normal.x) / det,
        )
    }

    /**
     * The direction rows run at, as it changes down the table: a weighted straight-line fit of the
     * longer lines' angles against how far down they sit. Null without enough lines to fit.
     */
    private fun rowFan(lines: List<OcrLine>, down: Pt): ((Float) -> Pt)? {
        val long = lines.filter { line -> (line.corners[1] - line.corners[0]).length > lineHeight(line) * 2.5f }
        if (long.size < MIN_ROWS) return null
        val samples = long.map { line ->
            val d = line.corners[1] - line.corners[0]
            Triple((Pt(line.centerX, line.centerY) dot down).toDouble(), atan2(d.y.toDouble(), d.x.toDouble()), d.length.toDouble())
        }
        val weight = samples.sumOf { it.third }
        val meanT = samples.sumOf { it.first * it.third } / weight
        val meanA = samples.sumOf { it.second * it.third } / weight
        val varT = samples.sumOf { (it.first - meanT).let { d -> d * d } * it.third }
        val slope = if (varT > 0) samples.sumOf { (it.first - meanT) * (it.second - meanA) * it.third } / varT else 0.0
        return { t -> (meanA + slope * (t - meanT)).let { a -> Pt(cos(a).toFloat(), sin(a).toFloat()) } }
    }

    /**
     * The left edge through the rows' first words. Sub-rows ("of which sugars") are often
     * indented and would tilt the fit, so it's fitted again without the points right of the first.
     */
    private fun leftEdge(starts: List<Pt>, down: Pt, lineHeight: Float): Pt? {
        val rough = principalAxis(starts, down) ?: return null
        val mean = starts.fold(Pt(0f, 0f)) { a, p -> a + p } * (1f / starts.size)
        val rightward = perp(rough) * -1f
        val onEdge = starts.filter { ((it - mean) dot rightward) <= lineHeight * 0.3f }
        return principalAxis(onEdge, down) ?: rough
    }

    /**
     * The right edge along the rows' last values: their right ends, left ends or middles, whichever
     * line up best (none further than about half a line off), or null when none do.
     */
    private fun rightEdge(lasts: List<OcrLine>, down: Pt, lineHeight: Float): Pt? {
        if (lasts.size < MIN_ROWS) return null
        val candidates = listOf(
            lasts.flatMap { listOf(it.corners[1], it.corners[2]) },
            lasts.flatMap { listOf(it.corners[0], it.corners[3]) },
            lasts.flatMap { listOf((it.corners[0] + it.corners[1]) * 0.5f, (it.corners[3] + it.corners[2]) * 0.5f) },
        )
        return candidates.mapNotNull { points ->
            val axis = principalAxis(points, down) ?: return@mapNotNull null
            val mean = points.fold(Pt(0f, 0f)) { a, p -> a + p } * (1f / points.size)
            val spread = points.maxOf { abs((it - mean) dot perp(axis)) }
            (axis to spread).takeIf { spread < lineHeight * 0.6f }
        }.minByOrNull { it.second }?.first
    }

    /** The direction the points line up along, oriented like [like]; null for too few points. */
    private fun principalAxis(points: List<Pt>, like: Pt): Pt? {
        if (points.size < 2 * MIN_ROWS) return null
        val mean = points.fold(Pt(0f, 0f)) { a, p -> a + p } * (1f / points.size)
        var xx = 0f
        var xy = 0f
        var yy = 0f
        for (p in points) {
            val d = p - mean
            xx += d.x * d.x
            xy += d.x * d.y
            yy += d.y * d.y
        }
        val theta = 0.5 * atan2(2.0 * xy, (xx - yy).toDouble())
        val axis = Pt(cos(theta).toFloat(), sin(theta).toFloat())
        return if (axis dot like < 0) axis * -1f else axis
    }

    private fun gap(above: List<OcrLine>, below: List<OcrLine>) = below.minOf { it.centerY } - above.maxOf { it.centerY }

    private fun lineHeight(line: OcrLine) = (line.corners[3] - line.corners[0]).length

    /** Perpendicular, a quarter turn clockwise on screen (y points down). */
    private fun perp(d: Pt) = Pt(-d.y, d.x)

    private fun angleOf(d: Pt) = atan2(d.y.toDouble(), d.x.toDouble())

    private fun rotate(d: Pt, radians: Double) = Pt((d.x * cos(radians) - d.y * sin(radians)).toFloat(), (d.x * sin(radians) + d.y * cos(radians)).toFloat())

    private fun angleBetween(a: Pt, b: Pt): Double = acos(((a dot b) / (a.length * b.length)).toDouble().coerceIn(-1.0, 1.0)) * 180 / PI
}
