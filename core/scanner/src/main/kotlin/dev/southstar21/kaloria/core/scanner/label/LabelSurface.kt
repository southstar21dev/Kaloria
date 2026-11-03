package dev.southstar21.kaloria.core.scanner.label

import kotlin.math.abs
import kotlin.math.max

/**
 * Where the nutrition table lies in a photo, as a map from the flat table (u, v in 0..1, left to
 * right and top to bottom) to photo pixels, so it can be drawn flat. Fitted to the text itself,
 * for a table printed on a flat box or wrapped round a can or bottle:
 *
 * - [TableOutline] gives a four-sided outline: the straight side edges and the perspective of the
 *   whole (a perspective map of the unit square onto it).
 * - Inside that, rows that curve (text round a can, seen from above or below) are fitted from
 *   where each line's words sit: a row's height above its own anchor, as a quadratic across the
 *   table whose bend changes steadily from the top row to the bottom.
 * - Text round a can is squeezed toward the can's edges. Letters' widths, against what their
 *   characters would be on the flat (in the line's own height), show how squeezed each part is,
 *   and the flat table gives every part back its width.
 *
 * A flat table has no bend or squeeze, and the map is just the perspective one.
 */
class LabelSurface private constructor(
    private val quad: Quad,
    private val xa: Double,
    private val xb: Double,
    private val va: Double,
    private val vb: Double,
    private val bend: DoubleArray,
    private val profile: DoubleArray?,
    /** Natural size of the flat table in photo pixels. */
    val width: Float,
    val height: Float,
) {
    private val h = Homography.of(quad)

    /** Whether rows bend or are squeezed, so a single perspective map can't flatten it. */
    val isCurved: Boolean = profile != null || bend.any { abs(it) > 1e-4 }

    /** The photo point that lands at (u, v) of the flat table. */
    fun map(u: Float, v: Float): Pt {
        val x = xOf(u.toDouble())
        val vv = va + v * (vb - va)
        return h.map(x, vv + offset(x, vv))
    }

    /** The table's edge in the photo, clockwise from the top-left, [steps] points a side. */
    fun outline(steps: Int = 16): List<Pt> = buildList {
        for (i in 0 until steps) add(map(i / steps.toFloat(), 0f))
        for (i in 0 until steps) add(map(1f, i / steps.toFloat()))
        for (i in steps downTo 1) add(map(i / steps.toFloat(), 1f))
        for (i in steps downTo 1) add(map(0f, i / steps.toFloat()))
    }

    /** The same table in a photo [k] times the size. */
    fun scaled(k: Float) = LabelSurface(
        Quad(quad.topLeft * k, quad.topRight * k, quad.bottomRight * k, quad.bottomLeft * k),
        xa, xb, va, vb, bend, profile, width * k, height * k,
    )

    private fun offset(x: Double, v: Double) = (bend[0] + bend[1] * v) * x + (bend[2] + bend[3] * v) * x * x

    private fun xOf(u: Double): Double {
        val s = profile ?: return xa + u * (xb - xa)
        val n = s.size - 1
        var i = 0
        while (i < n - 1 && s[i + 1] < u) i++
        val t = if (s[i + 1] > s[i]) ((u - s[i]) / (s[i + 1] - s[i])).coerceIn(0.0, 1.0) else 0.0
        return xa + (i + t) / n * (xb - xa)
    }

    companion object {
        private const val PROFILE_STEPS = 64

        /** Rows narrower than this fraction of the widest show squeeze; above it, none is applied. */
        private const val FLAT_ENOUGH = 0.85

        fun fit(lines: List<OcrLine>): LabelSurface? {
            val table = TableOutline.fit(lines) ?: return null
            val h = Homography.of(table.quad)
            val inTable = table.lines

            // Each line's samples in the outline's own square: its words' centres and its ends.
            class Sample(val line: Int, val x: Double, val v: Double)
            val samples = inTable.flatMapIndexed { i, line ->
                val ends = listOf((line.corners[0] + line.corners[3]) * 0.5f, (line.corners[1] + line.corners[2]) * 0.5f)
                (line.words.map { it.center } + ends).map { p -> h.unmap(p).let { (x, v) -> Sample(i, x, v) } }
            }
            val lineHeights = inTable.map { line -> abs(h.unmap(line.corners[3]).second - h.unmap(line.corners[0]).second) }
            val rowHeight = lineHeights.sorted()[lineHeights.size / 2]

            // Bend: v = anchor + (p0 + p1·anchor)·x + (q0 + q1·anchor)·x², one anchor per row. Lines
            // on one row (a name and its values) share it: that's what shows how the rows' spacing
            // changes across a can. Fitted by turns (the bend for the anchors, then the anchors for
            // the bend), regrouping the lines into rows by where the bend puts them.
            fun offset(bend: DoubleArray, a: Double, x: Double) = (bend[0] + bend[1] * a) * x + (bend[2] + bend[3] * a) * x * x

            class Bend(val bend: DoubleArray, val rowOf: IntArray, val anchors: DoubleArray, val misfit: Double)

            fun fitBend(start: IntArray): Bend {
                var rowOf = start
                val rowCount = { rowOf.max() + 1 }
                var anchors = DoubleArray(rowCount()) { r -> samples.filter { rowOf[it.line] == r }.map { it.v }.average() }
                var bend = DoubleArray(4)
                repeat(3) { round ->
                    repeat(4) {
                        bend = leastSquares(
                            samples.map { s ->
                                val a = anchors[rowOf[s.line]]
                                val x2 = s.x * s.x
                                doubleArrayOf(s.x, a * s.x, x2, a * x2)
                            },
                            samples.map { it.v - anchors[rowOf[it.line]] },
                        ) ?: DoubleArray(4)
                        for (r in anchors.indices) {
                            val own = samples.filter { rowOf[it.line] == r }
                            if (own.isNotEmpty()) anchors[r] = own.map { s -> s.v - offset(bend, anchors[r], s.x) }.average()
                        }
                    }
                    if (round == 2) return@repeat
                    // Regroup: each line's own anchor under this bend, rows split where they jump.
                    val own = inTable.indices.map { i ->
                        val mine = samples.filter { it.line == i }
                        var a = anchors[rowOf[i]]
                        repeat(3) { a = mine.map { s -> s.v - offset(bend, a, s.x) }.average() }
                        i to a
                    }.sortedBy { it.second }
                    val regrouped = IntArray(inTable.size)
                    var row = 0
                    own.forEachIndexed { k, (i, a) ->
                        if (k > 0 && a - own[k - 1].second > rowHeight * 0.5) row++
                        regrouped[i] = row
                    }
                    rowOf = regrouped
                    anchors = DoubleArray(row + 1) { r -> own.filter { regrouped[it.first] == r }.map { it.second }.average() }
                }
                // How far the words sit from their rows, per degree of freedom left: more rows
                // always fit closer, so they have to earn it.
                val squares = samples.sumOf { s -> (s.v - anchors[rowOf[s.line]] - offset(bend, anchors[rowOf[s.line]], s.x)).let { it * it } }
                return Bend(bend, rowOf, anchors, squares / maxOf(1, samples.size - anchors.size - 4))
            }

            // Two starting points, the better fit kept: lines grouped into rows as they lie
            // straight, and every line on its own, so the bend comes only from how each line's
            // words climb or drop across it, which holds even where rows curve so much that
            // straight grouping pairs a name with the row above's values.
            val byRow = groupRows(inTable)
            val straight = IntArray(inTable.size) { i -> byRow.indexOfFirst { row -> inTable[i] in row } }
            val flatFit = Bend(DoubleArray(4), straight, DoubleArray(byRow.size) { r -> samples.filter { straight[it.line] == r }.map { it.v }.average() }, Double.MAX_VALUE)
            var best = flatFit
            if (samples.size - inTable.size >= 6) {
                best = listOf(fitBend(straight), fitBend(IntArray(inTable.size) { it })).minBy { it.misfit }
                // A bend that moves rows by more than half the table is a bad fit, not a can.
                if (samples.maxOf { s -> abs(offset(best.bend, best.anchors[best.rowOf[s.line]], s.x)) } > 0.5) best = flatFit
            }
            val bend = best.bend
            val rowOf = best.rowOf
            val anchors = best.anchors
            val lineAnchor = DoubleArray(inTable.size) { anchors[rowOf[it]] }

            // The flat table spans every line, plus a margin.
            val marginV = rowHeight * 0.8
            val va = inTable.indices.minOf { lineAnchor[it] - lineHeights[it] / 2 } - marginV
            val vb = inTable.indices.maxOf { lineAnchor[it] + lineHeights[it] / 2 } + marginV
            val marginX = table.lineHeight * 0.8 / table.quad.width
            val xs = inTable.flatMap { line -> line.corners.map { h.unmap(it).first } }
            val xa = xs.min() - marginX
            val xb = xs.max() + marginX

            // Squeeze: each word's width per character, against its height and what its characters
            // would measure, across the table.
            val squeeze = inTable.flatMap { it.words }.mapNotNull { word ->
                val em = emWidth(word.text).takeIf { it > 1.0 } ?: return@mapNotNull null
                val c = word.corners.map(h::unmap)
                val w = (abs(c[1].first - c[0].first) + abs(c[2].first - c[3].first)) / 2
                val hgt = (abs(c[3].second - c[0].second) + abs(c[2].second - c[1].second)) / 2
                if (hgt <= 0) return@mapNotNull null
                Triple(word.center.let { h.unmap(it).first }, w / (em * hgt), em)
            }
            var profile: DoubleArray? = null
            var stretch = 1.0
            if (squeeze.size >= 6 && squeeze.maxOf { it.first } - squeeze.minOf { it.first } > 0.4) {
                val coef = leastSquares(squeeze.map { (x, _, _) -> doubleArrayOf(1.0, x, x * x) }, squeeze.map { it.second }, squeeze.map { it.third })
                if (coef != null) {
                    val r = DoubleArray(PROFILE_STEPS + 1) { i ->
                        val x = xa + i * (xb - xa) / PROFILE_STEPS
                        coef[0] + coef[1] * x + coef[2] * x * x
                    }
                    val top = r.max()
                    if (top > 0 && r.min() / top < FLAT_ENOUGH) {
                        for (i in r.indices) r[i] = max(r[i], top * 0.3)
                        // Flat position grows by 1/r: squeezed parts take up more of the flat table.
                        val s = DoubleArray(PROFILE_STEPS + 1)
                        for (i in 1..PROFILE_STEPS) s[i] = s[i - 1] + (1 / r[i - 1] + 1 / r[i]) / 2
                        for (i in s.indices) s[i] /= s[PROFILE_STEPS]
                        profile = s
                        stretch = r.average { top / it }
                    }
                }
            }

            return LabelSurface(
                table.quad, xa, xb, va, vb, bend, profile,
                width = (table.quad.width * (xb - xa) * stretch).toFloat(),
                height = (table.quad.height * (vb - va)).toFloat(),
            )
        }

        /**
         * About how wide [text] is set in a plain sans-serif, in ems: so a word of digits and a word
         * of letters can be compared by their widths.
         */
        internal fun emWidth(text: String): Double = text.sumOf { c ->
            when {
                c in "iIlíj.,:;'!|" -> 0.28
                c == ' ' -> 0.28
                c in "mwMWŐœ" -> 0.85
                c.isUpperCase() -> 0.68
                c.isDigit() -> 0.56
                c in "frt" -> 0.36
                else -> 0.53
            }
        }

        private fun DoubleArray.average(f: (Double) -> Double) = sumOf(f) / size

        /** Weighted least squares; null if the system is singular. */
        private fun leastSquares(rows: List<DoubleArray>, targets: List<Double>, weights: List<Double>? = null): DoubleArray? {
            val n = rows.firstOrNull()?.size ?: return null
            val a = Array(n) { DoubleArray(n + 1) }
            rows.forEachIndexed { k, row ->
                val w = weights?.get(k) ?: 1.0
                for (i in 0 until n) {
                    for (j in 0 until n) a[i][j] += w * row[i] * row[j]
                    a[i][n] += w * row[i] * targets[k]
                }
            }
            for (i in 0 until n) a[i][i] += 1e-9
            return solve(a)
        }
    }
}

/** The perspective map of the unit square onto a quad (Heckbert's closed form), both ways. */
internal class Homography private constructor(private val m: DoubleArray) {
    private val inverse: DoubleArray = run {
        val (a, b, c, d, e) = m
        val f = m[5]
        val g = m[6]
        val hh = m[7]
        val det = a * (e - f * hh) - b * (d - f * g) + c * (d * hh - e * g)
        doubleArrayOf(
            (e - f * hh) / det, (c * hh - b) / det, (b * f - c * e) / det,
            (f * g - d) / det, (a - c * g) / det, (c * d - a * f) / det,
            (d * hh - e * g) / det, (b * g - a * hh) / det, (a * e - b * d) / det,
        )
    }

    fun map(u: Double, v: Double): Pt {
        val w = m[6] * u + m[7] * v + 1
        return Pt(((m[0] * u + m[1] * v + m[2]) / w).toFloat(), ((m[3] * u + m[4] * v + m[5]) / w).toFloat())
    }

    fun unmap(p: Pt): Pair<Double, Double> {
        val i = inverse
        val w = i[6] * p.x + i[7] * p.y + i[8]
        return (i[0] * p.x + i[1] * p.y + i[2]) / w to (i[3] * p.x + i[4] * p.y + i[5]) / w
    }

    companion object {
        fun of(q: Quad): Homography {
            val (x0, y0) = q.topLeft.x.toDouble() to q.topLeft.y.toDouble()
            val (x1, y1) = q.topRight.x.toDouble() to q.topRight.y.toDouble()
            val (x2, y2) = q.bottomRight.x.toDouble() to q.bottomRight.y.toDouble()
            val (x3, y3) = q.bottomLeft.x.toDouble() to q.bottomLeft.y.toDouble()
            val dx1 = x1 - x2
            val dx2 = x3 - x2
            val dx3 = x0 - x1 + x2 - x3
            val dy1 = y1 - y2
            val dy2 = y3 - y2
            val dy3 = y0 - y1 + y2 - y3
            val den = dx1 * dy2 - dx2 * dy1
            val g = if (abs(den) < 1e-12) 0.0 else (dx3 * dy2 - dx2 * dy3) / den
            val h = if (abs(den) < 1e-12) 0.0 else (dx1 * dy3 - dx3 * dy1) / den
            return Homography(doubleArrayOf(x1 - x0 + g * x1, x3 - x0 + h * x3, x0, y1 - y0 + g * y1, y3 - y0 + h * y3, y0, g, h))
        }
    }
}

/** Gauss-Jordan on an augmented n x (n+1) matrix; null if singular. */
private fun solve(a: Array<DoubleArray>): DoubleArray? {
    val n = a.size
    for (col in 0 until n) {
        val pivot = (col until n).maxBy { abs(a[it][col]) }
        if (abs(a[pivot][col]) < 1e-12) return null
        a[col] = a[pivot].also { a[pivot] = a[col] }
        for (r in 0 until n) {
            if (r != col) {
                val f = a[r][col] / a[col][col]
                for (k in col..n) a[r][k] -= f * a[col][k]
            }
        }
    }
    return DoubleArray(n) { a[it][n] / a[it][it] }
}
