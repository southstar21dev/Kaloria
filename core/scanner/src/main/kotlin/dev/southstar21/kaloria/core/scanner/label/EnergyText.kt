package dev.southstar21.kaloria.core.scanner.label

import dev.southstar21.kaloria.core.model.Nutrients
import kotlin.math.abs

/** An energy value as read: either unit may be missing. */
internal data class Energy(val kcal: Double?, val kj: Double?) {
    val isEmpty: Boolean get() = kcal == null && kj == null
    val isComplete: Boolean get() = kcal != null && kj != null
}

/** Reads the energy row of a normalised table, in the layouts labels print it. */
internal object EnergyText {
    private const val NUMBER = """\d+(?:[.,]\d+)?"""

    /**
     * kJ per 100 g runs into the thousands: "1338", "1 338", "1.338" or "1,338". It's never a
     * decimal below 10, so three digits after a separator are thousands.
     */
    private const val KJ_NUMBER = """\d{1,2}[ .,]\d{3}(?!\d)|$NUMBER"""

    private val kcal = Regex("""(?<![\d.,])($NUMBER)\s*k\s*c\s*a\s*l""")
    private val kj = Regex("""(?<![\d.,])($KJ_NUMBER)\s*k\s*j""")
    private val unit = Regex("""(?<!\p{L})(kj|kcal)(?!\p{L})""")

    /** "(kJ/kcal) 1574/372" or "1574/372 kJ/kcal": the units given once, the numbers in their order. */
    private val unitPair = Regex("""(?<!\p{L})(?:kj|kcal)\s*/\s*(?:kj|kcal)(?!\p{L})""")

    /** A number on the energy row; one followed by a mass ("100 g") or a share ("17 %") isn't energy. */
    private val bare = Regex("""(?<![\d.,])(\d{1,2}[.,]\d{3}(?!\d)|$NUMBER)(\s*(?:g|ml|q|%)(?!\p{L}))?""")
    private val separatedThousands = Regex("""\d{1,2}[ .,]\d{3}""")

    /** How far a kJ/kcal pair may stray from 4.184 and still be one: labels round both. */
    private const val PAIR_TOLERANCE = 0.06

    fun hasUnit(row: String): Boolean = unit.containsMatchIn(row)

    /** Whether kJ and kcal printed side by side are too far apart to be the same energy. */
    fun disagree(kcal: Double, kj: Double): Boolean = abs(kj / Nutrients.KJ_PER_KCAL - kcal) > maxOf(5.0, kcal * 0.05)

    /** The energy in [column] (0 is the first value column) of [text], the energy row and any it wrapped onto. */
    fun read(text: String, column: Int): Energy = inUnitOrder(text, column, unitPair.containsMatchIn(text))
        ?: paired(text, column)
        ?: inUnitOrder(text, column, true)
        ?: byRatio(text, column)
        ?: Energy(null, null)

    /** Each number next to its unit: "1338 kJ / 320 kcal". */
    private fun paired(text: String, column: Int): Energy? = Energy(
        kcal = kcal.findAll(text).elementAtOrNull(column)?.let { number(it.groupValues[1]) },
        kj = kj.findAll(text).elementAtOrNull(column)?.let { number(it.groupValues[1]) },
    ).takeUnless { it.isEmpty }

    /** Units named apart from the numbers ("Energia (kJ/kcal) 1574/372"): the numbers follow their order. */
    private fun inUnitOrder(text: String, column: Int, applies: Boolean): Energy? {
        val units = unit.findAll(text).map { it.value }.distinct().toList()
        val numbers = numbers(text)
        val first = column * units.size
        return if (!applies || units.isEmpty() || numbers.size < first + units.size) {
            null
        } else {
            fun at(name: String) = units.indexOf(name).takeIf { it >= 0 }?.let { numbers[first + it] }
            Energy(kcal = at("kcal"), kj = at("kj"))
        }
    }

    /** No units at all ("1574/372"): a pair whose ratio is kJ to kcal is read as one. */
    private fun byRatio(text: String, column: Int): Energy? = numbers(text).zipWithNext()
        .filter { (a, b) -> isKjKcal(a, b) || isKjKcal(b, a) }
        .getOrNull(column)
        ?.let { (a, b) -> Energy(kcal = minOf(a, b), kj = maxOf(a, b)) }

    private fun isKjKcal(kj: Double, kcal: Double) = kcal > 0 && abs(kj / kcal / Nutrients.KJ_PER_KCAL - 1) <= PAIR_TOLERANCE

    private fun numbers(text: String): List<Double> = bare.findAll(text).filter { it.groupValues[2].isEmpty() }.map { number(it.groupValues[1]) }.toList()

    private fun number(text: String): Double = if (separatedThousands.matches(text)) text.filter(Char::isDigit).toDouble() else text.toLabelNumber()
}
