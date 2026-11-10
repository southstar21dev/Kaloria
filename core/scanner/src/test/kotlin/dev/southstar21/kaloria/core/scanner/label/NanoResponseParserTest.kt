package dev.southstar21.kaloria.core.scanner.label

import dev.southstar21.kaloria.core.model.AmountUnit
import dev.southstar21.kaloria.core.model.Micronutrient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NanoResponseParserTest {
    private val ocr = """
        Energia 1338 kJ / 320 kcal
        Zsír 24 g
        ebből telített zsírsavak 16 g
        Szénhidrát 2,0 g
        Fehérje 24 g
        Só 1,8 g
    """.trimIndent()

    private fun LabelExtraction.warned(field: String, kind: WarningKind) = warnings.any { it.field == field && it.kind == kind }

    @Test
    fun `json wrapped in a code fence is parsed`() {
        val reply = """
            ```json
            {"per":"100g","energy_kcal":320,"energy_kj":1338,"fat_g":24,"saturated_fat_g":16,
             "carbohydrates_g":"2,0","sugars_g":null,"fiber_g":null,"protein_g":24,"salt_g":1.8,
             "micros":{"calcium":700,"unknown_thing":3},"name":"Trappista sajt","brand":"Coop"}
            ```
        """.trimIndent()
        val r = NanoResponseParser.parse(reply, ocr)!!
        assertEquals(ExtractionMethod.GeminiNano, r.method)
        assertEquals(2.0, r.nutrients.carbohydrates!!, 0.0)
        assertNull(r.nutrients.sugars)
        assertEquals(700.0, r.nutrients.micros[Micronutrient.Calcium]!!, 0.0)
        assertEquals(1, r.nutrients.micros.size)
        assertEquals("Coop", r.brand)
        assertEquals(AmountUnit.Gram, r.unit)
        assertTrue(r.warnings.none { it.message.startsWith("AI read") })
    }

    @Test
    fun `a value that disagrees with the ocr text is flagged`() {
        val reply = """{"per":"100g","energy_kcal":320,"fat_g":24,"carbohydrates_g":2,"protein_g":21,"salt_g":1.8}"""
        val r = NanoResponseParser.parse(reply, ocr)!!
        assertTrue(r.warnings.any { it.field == "protein" && it.message.startsWith("AI read 21") })
    }

    @Test
    fun `a value the model missed is taken from the text, flagged`() {
        val reply = """{"per":"100g","energy_kcal":320,"fat_g":24,"saturated_fat_g":null,"carbohydrates_g":2,"protein_g":24,"salt_g":1.8}"""
        val r = NanoResponseParser.parse(reply, ocr)!!
        assertEquals(16.0, r.nutrients.saturatedFat!!, 0.0)
        assertTrue(r.warnings.any { it.field == "saturatedFat" && "AI missed" in it.message })
    }

    @Test
    fun `a list of answers takes the first`() {
        val reply = "```json\n[{\"per\":\"100ml\",\"energy_kcal\":42,\"carbohydrates_g\":10.6}, {\"per\":\"100ml\",\"energy_kcal\":139,\"carbohydrates_g\":35}]\n```"
        val r = NanoResponseParser.parse(reply, ocr)!!
        assertEquals(42.0, r.nutrients.energyKcal!!, 0.0)
        assertEquals(10.6, r.nutrients.carbohydrates!!, 0.0)
    }

    @Test
    fun `prose without json is rejected`() {
        assertNull(NanoResponseParser.parse("I can't read this label.", ocr))
    }

    @Test
    fun `values written as strings are read`() {
        val reply = """{"per":"100g","energy_kcal":"320","fat_g":"24 g","saturated_fat_g":"16,0","carbohydrates_g":"<2","protein_g":" 24 ","salt_g":"1.8"}"""
        val r = NanoResponseParser.parse(reply, ocr)!!
        with(r.nutrients) {
            assertEquals(320.0, energyKcal!!, 0.0)
            assertEquals(24.0, fat!!, 0.0)
            assertEquals(16.0, saturatedFat!!, 0.0)
            assertEquals(2.0, carbohydrates!!, 0.0)
            assertEquals(24.0, protein!!, 0.0)
        }
        assertTrue(r.warned("carbohydrates", WarningKind.LessThan))
        assertEquals(1338.0 / 4.184, NanoResponseParser.parse("""{"energy_kj":"1 338"}""", ocr)!!.nutrients.energyKcal!!, 0.01)
    }

    @Test
    fun `a 0 where the text reads a value is a miss`() {
        val reply = """{"per":"100g","energy_kcal":320,"fat_g":0,"carbohydrates_g":2,"protein_g":24,"salt_g":1.8}"""
        val r = NanoResponseParser.parse(reply, ocr)!!
        assertEquals(24.0, r.nutrients.fat!!, 0.0)
        assertTrue(r.warned("fat", WarningKind.ModelMissed))
    }

    @Test
    fun `a 0 the text can't confirm is kept, flagged, and one it confirms isn't`() {
        val r = NanoResponseParser.parse("""{"energy_kcal":320,"fat_g":24,"carbohydrates_g":2,"protein_g":24,"fiber_g":0}""", ocr)!!
        assertEquals(0.0, r.nutrients.fiber!!, 0.0)
        assertTrue(r.warned("fiber", WarningKind.ModelZero))
        val drink = "Energia 180 kJ / 42 kcal\nZsír 0 g\nSzénhidrát 10,6 g\nFehérje 0 g"
        val d = NanoResponseParser.parse("""{"per":"100ml","energy_kcal":42,"fat_g":0,"carbohydrates_g":10.6,"protein_g":0}""", drink)!!
        assertEquals(0.0, d.nutrients.fat!!, 0.0)
        assertFalse(d.warnings.toString(), d.warned("fat", WarningKind.ModelZero) || d.warned("fat", WarningKind.ModelMissed))
    }

    @Test
    fun `a reply of nothing but nulls and zeros is not used`() {
        assertNull(NanoResponseParser.parse("""{"per":"100g","energy_kcal":0,"fat_g":0,"carbohydrates_g":0,"protein_g":0,"salt_g":null}""", ocr))
        assertNull(NanoResponseParser.parse("""{"per":"100g","energy_kcal":null}""", ocr))
    }

    @Test
    fun `kj only is converted, flagged`() {
        val r = NanoResponseParser.parse("""{"energy_kj":1338,"fat_g":24,"carbohydrates_g":2,"protein_g":24}""", ocr)!!
        assertEquals(319.8, r.nutrients.energyKcal!!, 0.1)
        assertTrue(r.warned("energy", WarningKind.KcalFromKj))
    }

    @Test
    fun `kj and kcal that disagree are flagged`() {
        val r = NanoResponseParser.parse("""{"energy_kcal":320,"energy_kj":3431,"fat_g":24,"carbohydrates_g":2,"protein_g":24}""", ocr)!!
        assertTrue(r.warned("energy", WarningKind.EnergyUnitsDisagree))
        val fine = NanoResponseParser.parse("""{"energy_kcal":320,"energy_kj":1338,"fat_g":24,"carbohydrates_g":2,"protein_g":24}""", ocr)!!
        assertFalse(fine.warned("energy", WarningKind.EnergyUnitsDisagree))
    }

    @Test
    fun `malformed or cut off json is rejected`() {
        assertNull(NanoResponseParser.parse("""{"per":"100g","energy_kcal":320,"fat_g":""", ocr))
        assertNull(NanoResponseParser.parse("""{"per":"100g" "energy_kcal" 320}""", ocr))
        assertNull(NanoResponseParser.parse("}{", ocr))
        assertNull(NanoResponseParser.parse("""["a", "b"]""", ocr))
    }

    @Test
    fun `impossible values are flagged by the checks`() {
        val r = NanoResponseParser.parse("""{"energy_kcal":1200,"fat_g":120,"carbohydrates_g":2,"protein_g":24}""", "")!!
        assertTrue(r.warned("energy", WarningKind.EnergyOver920))
        assertTrue(r.warned("fat", WarningKind.Over100Grams))
    }

    @Test
    fun `negative, infinite and not-a-number values count as missing`() {
        val reply = """{"energy_kcal":Infinity,"fat_g":-24,"carbohydrates_g":"Infinity","protein_g":1e400,"salt_g":"NaN","sugars_g":2}"""
        val r = NanoResponseParser.parse(reply, ocr)!!
        with(r.nutrients) {
            assertEquals(320.0, energyKcal!!, 0.0)
            assertEquals(24.0, fat!!, 0.0)
            assertEquals(2.0, carbohydrates!!, 0.0)
            assertEquals(24.0, protein!!, 0.0)
            assertEquals(1.8, salt!!, 0.0)
        }
        listOf("energy", "fat", "carbohydrates", "protein", "salt").forEach { assertTrue(it, r.warned(it, WarningKind.ModelMissed)) }
        assertTrue(r.nutrients.run { listOfNotNull(energyKcal, fat, carbohydrates, protein, salt) }.all { it.isFinite() })
    }

    @Test
    fun `micros are read from numbers and strings, and sodium the text reads joins them`() {
        val text = ocr + "\nNátrium 400 mg"
        val reply = """{"energy_kcal":320,"fat_g":24,"carbohydrates_g":2,"protein_g":24,"salt_g":1,"micros":{"calcium":"700","iron":"Infinity","potassium":-3,"vitamin_x":5}}"""
        val r = NanoResponseParser.parse(reply, text)!!
        assertEquals(mapOf(Micronutrient.Calcium to 700.0, Micronutrient.Sodium to 400.0), r.nutrients.micros)
        val own = NanoResponseParser.parse(reply.replace("\"calcium\"", "\"sodium\""), text)!!
        assertEquals(700.0, own.nutrients.micros[Micronutrient.Sodium]!!, 0.0)
    }

    @Test
    fun `what the text rules noted about a value they filled in comes along`() {
        val text = ocr + "\nRost <0,5 g"
        val r = NanoResponseParser.parse("""{"energy_kcal":320,"fat_g":24,"carbohydrates_g":2,"protein_g":24,"salt_g":1.8}""", text)!!
        assertEquals(0.5, r.nutrients.fiber!!, 0.0)
        assertTrue(r.warned("fiber", WarningKind.ModelMissed))
        assertTrue(r.warned("fiber", WarningKind.LessThan))
    }

    @Test
    fun `energy the text reads in the units-apart layout fills a gap`() {
        val r = NanoResponseParser.parse("""{"fat_g":24,"carbohydrates_g":2,"protein_g":24}""", "Energia (kJ/kcal) 1338/320\nZsír 24 g")!!
        assertEquals(320.0, r.nutrients.energyKcal!!, 0.0)
        assertTrue(r.warned("energy", WarningKind.ModelMissed))
    }
}
