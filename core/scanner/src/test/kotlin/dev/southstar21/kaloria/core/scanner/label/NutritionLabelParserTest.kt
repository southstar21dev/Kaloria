package dev.southstar21.kaloria.core.scanner.label

import dev.southstar21.kaloria.core.model.AmountUnit
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionLabelParserTest {

    private fun parse(text: String) = NutritionLabelParser.parse(text.trimIndent().lines())

    private fun LabelExtraction.assertNoWarningsExceptLessThan() = assertTrue(warnings.toString(), warnings.all { "less than" in it.message })

    @Test
    fun `hungarian single column`() {
        val r = parse(
            """
            Tápérték 100 g termékben
            Energia 1338 kJ / 320 kcal
            Zsír 24 g
            ebből telített zsírsavak 16 g
            Szénhidrát 2,0 g
            ebből cukrok 2,0 g
            Fehérje 24 g
            Só 1,8 g
            """,
        )
        with(r.nutrients) {
            assertEquals(320.0, energyKcal!!, 0.0)
            assertEquals(24.0, fat!!, 0.0)
            assertEquals(16.0, saturatedFat!!, 0.0)
            assertEquals(2.0, carbohydrates!!, 0.0)
            assertEquals(2.0, sugars!!, 0.0)
            assertEquals(24.0, protein!!, 0.0)
            assertEquals(1.8, salt!!, 0.0)
        }
        assertEquals(AmountUnit.Gram, r.unit)
        r.assertNoWarningsExceptLessThan()
    }

    @Test
    fun `hungarian two columns with ocr digit confusions takes the per 100 g column`() {
        val r = parse(
            """
            100 g-ban   1 adagban (30 g)
            Energia 1544 kJ/369 kcal  463 kJ/111 kcal
            Zsír l3 g  3,9 g
            - amelyből telített zsírsavak 1,5 g  0,5 g
            Szenhidrat 52 g  16 g
            - amelyből cukrok 23 g  6,9 g
            Rost 4,8 g  1,4 g
            Feherje 7,3 g  2,2 g
            Só O,5 g  0,15 g
            """,
        )
        with(r.nutrients) {
            assertEquals(369.0, energyKcal!!, 0.0)
            assertEquals(13.0, fat!!, 0.0)
            assertEquals(1.5, saturatedFat!!, 0.0)
            assertEquals(52.0, carbohydrates!!, 0.0)
            assertEquals(23.0, sugars!!, 0.0)
            assertEquals(4.8, fiber!!, 0.0)
            assertEquals(7.3, protein!!, 0.0)
            assertEquals(0.5, salt!!, 0.0)
        }
        r.assertNoWarningsExceptLessThan()
    }

    @Test
    fun `names and values recognised as separate blocks are rejoined into rows`() {
        val names = listOf("Energia", "Zsír", "Szénhidrát", "Fehérje", "Só")
        val values = listOf("1338 kJ / 320 kcal", "24 g", "2 g", "24 g", "1,8 g")
        val lines = names.mapIndexed { i, t -> OcrLine(t, 10, 100 + i * 40, 120, 130 + i * 40) } +
            values.mapIndexed { i, t -> OcrLine(t, 300, 102 + i * 40, 480, 128 + i * 40) }
        val rows = rowsOf(lines.shuffled())
        assertEquals("Energia  1338 kJ / 320 kcal", rows.first())
        val r = NutritionLabelParser.parse(rows)
        assertEquals(320.0, r.nutrients.energyKcal!!, 0.0)
        assertEquals(1.8, r.nutrients.salt!!, 0.0)
    }

    @Test
    fun `a tilted photo still pairs each value with its own nutrient`() {
        // Rows 40 px apart, values 600 px to the right, the photo turned 5 degrees clockwise: the
        // value column drops ~52 px, more than a row, so without straightening every value would
        // land one row down.
        val angle = 5.0
        val rad = Math.toRadians(angle)
        fun line(text: String, x: Int, y: Int, width: Int): OcrLine {
            val cx = (x + width / 2) * cos(rad) - y * sin(rad)
            val cy = (x + width / 2) * sin(rad) + y * cos(rad)
            val halfW = (width * cos(rad) + 26 * sin(rad)) / 2
            val halfH = (width * sin(rad) + 26 * cos(rad)) / 2
            return OcrLine(text, (cx - halfW).toInt(), (cy - halfH).toInt(), (cx + halfW).toInt(), (cy + halfH).toInt(), angle.toFloat())
        }
        val names = listOf("Energia", "Zsír", "Szénhidrát", "Fehérje", "Só")
        val values = listOf("1338 kJ / 320 kcal", "24 g", "2 g", "23 g", "1,8 g")
        val lines = names.mapIndexed { i, t -> line(t, 100, 300 + i * 40, 160) } +
            values.mapIndexed { i, t -> line(t, 700, 300 + i * 40, 180) }
        val rows = rowsOf(lines.shuffled())
        assertEquals(listOf("Energia  1338 kJ / 320 kcal", "Zsír  24 g", "Szénhidrát  2 g", "Fehérje  23 g", "Só  1,8 g"), rows)
    }

    @Test
    fun `a g read as 9 is put back, but not on the energy row or a real unit`() {
        val rows = NutritionLabelParser.fixMisreadGrams(
            listOf("Energia 1339 kJ / 319 kcal", "Zsír 44 g 13 g", "telített 179 5,1 g", "Szénhidrát 1,2 g 0,4 9", "cukrok 0,8 g 0,29", "Fehérje 239 6,9 g", "Só 4,3 g 1,39"),
        )
        assertEquals(
            listOf("Energia 1339 kJ / 319 kcal", "Zsír 44 g 13 g", "telített 17 g 5,1 g", "Szénhidrát 1,2 g 0,4 g", "cukrok 0,8 g 0,2 g", "Fehérje 23 g 6,9 g", "Só 4,3 g 1,3 g"),
            rows,
        )
    }

    @Test
    fun `a wrapped name takes the value beside its first line`() {
        val r = parse(
            """
            Zsír 44 g 13 g
            ebből 17 g 5,1 g
            telített zsírsavak
            Szénhidrát 1,2 g 0,4 g
            ebből 0,8 g 0,2 g
            cukrok
            Fehérje 23 g 6,9 g
            """,
        )
        with(r.nutrients) {
            assertEquals(17.0, saturatedFat!!, 0.0)
            assertEquals(0.8, sugars!!, 0.0)
            assertEquals(23.0, protein!!, 0.0)
        }
    }

    @Test
    fun `names a letter or two off still count, short words don't`() {
        assertTrue(NutritionLabelParser.isNutrientRow("Energja 180 kJ"))
        assertTrue(NutritionLabelParser.isNutrientRow("Feheie 0 g"))
        assertTrue(NutritionLabelParser.isNutrientRow("Szenhidrat"))
        assertTrue(!NutritionLabelParser.isNutrientRow("Fehér csomagolás"))
        assertTrue(!NutritionLabelParser.isNutrientRow("Tárolás 0-25 °C"))
    }

    @Test
    fun `english with less than`() {
        val r = parse(
            """
            Typical values per 100g
            Energy 2252kJ/539kcal
            Fat 30.9g
            of which saturates 10.6g
            Carbohydrate 57.5g
            of which sugars 56.3g
            Fibre <0.5g
            Protein 6.3g
            Salt 0.107g
            """,
        )
        with(r.nutrients) {
            assertEquals(539.0, energyKcal!!, 0.0)
            assertEquals(10.6, saturatedFat!!, 0.0)
            assertEquals(56.3, sugars!!, 0.0)
            assertEquals(0.5, fiber!!, 0.0)
            assertEquals(0.107, salt!!, 0.0)
        }
        assertTrue(r.warnings.any { it.field == "fiber" && "less than" in it.message })
    }

    @Test
    fun `turkish`() {
        val r = parse(
            """
            Besin Değerleri 100 g
            Enerji 1641 kJ / 392 kcal
            Yağ 15 g
            Doymuş Yağ 7,2 g
            Karbonhidrat 58 g
            Şekerler 31 g
            Lif 2,1 g
            Protein 4,9 g
            Tuz 0,45 g
            """,
        )
        with(r.nutrients) {
            assertEquals(392.0, energyKcal!!, 0.0)
            assertEquals(15.0, fat!!, 0.0)
            assertEquals(7.2, saturatedFat!!, 0.0)
            assertEquals(58.0, carbohydrates!!, 0.0)
            assertEquals(31.0, sugars!!, 0.0)
            assertEquals(2.1, fiber!!, 0.0)
            assertEquals(4.9, protein!!, 0.0)
            assertEquals(0.45, salt!!, 0.0)
        }
        r.assertNoWarningsExceptLessThan()
    }

    @Test
    fun `azerbaijani with q for grams`() {
        val r = parse(
            """
            100 q məhsulda
            Enerji dəyəri 1590 kJ / 380 kcal
            Yağlar 20 q
            o cümlədən doymuş yağ turşuları 9 q
            Karbohidratlar 45 q
            o cümlədən şəkər 30 q
            Zülallar 5 q
            Duz 0,3 q
            """,
        )
        with(r.nutrients) {
            assertEquals(380.0, energyKcal!!, 0.0)
            assertEquals(20.0, fat!!, 0.0)
            assertEquals(9.0, saturatedFat!!, 0.0)
            assertEquals(45.0, carbohydrates!!, 0.0)
            assertEquals(30.0, sugars!!, 0.0)
            assertEquals(5.0, protein!!, 0.0)
            assertEquals(0.3, salt!!, 0.0)
        }
        r.assertNoWarningsExceptLessThan()
    }

    @Test
    fun `kj only energy is converted with a warning`() {
        val r = parse(
            """
            Energia 1338 kJ
            Zsír 24 g
            Szénhidrát 2 g
            Fehérje 24 g
            """,
        )
        assertEquals(319.8, r.nutrients.energyKcal!!, 0.1)
        assertTrue(r.warnings.any { it.field == "energy" && "kJ" in it.message })
    }

    @Test
    fun `drinks are detected as per 100 ml`() {
        val r = parse(
            """
            100 ml-ben
            Energia 180 kJ / 42 kcal
            Zsír 0 g
            Szénhidrát 10,6 g
            ebből cukrok 10,6 g
            Fehérje 0 g
            Só 0 g
            """,
        )
        assertEquals(AmountUnit.Millilitre, r.unit)
        assertEquals(10.6, r.nutrients.sugars!!, 0.0)
    }

    @Test
    fun `a misread energy digit is flagged`() {
        // 4·24 + 4·2 + 9·24 = 320, so 820 kcal must be a misread.
        val r = parse(
            """
            Energia 3431 kJ / 820 kcal
            Zsír 24 g
            Szénhidrát 2 g
            Fehérje 24 g
            """,
        )
        assertTrue(r.warnings.any { it.field == "energy" && "macros" in it.message })
    }

    @Test
    fun `missing rows stay unknown`() {
        val r = parse("Energia 1338 kJ / 320 kcal")
        assertNull(r.nutrients.protein)
        assertTrue(r.warnings.any { it.field == "protein" })
    }
}
