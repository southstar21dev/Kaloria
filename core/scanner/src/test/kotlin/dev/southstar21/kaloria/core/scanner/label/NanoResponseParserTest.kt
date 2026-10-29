package dev.southstar21.kaloria.core.scanner.label

import dev.southstar21.kaloria.core.model.AmountUnit
import dev.southstar21.kaloria.core.model.Micronutrient
import org.junit.Assert.assertEquals
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
    fun `prose without json is rejected`() {
        assertNull(NanoResponseParser.parse("I can't read this label.", ocr))
    }
}
