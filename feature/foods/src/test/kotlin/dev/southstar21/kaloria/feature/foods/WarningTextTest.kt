package dev.southstar21.kaloria.feature.foods

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.southstar21.kaloria.core.scanner.label.WarningKind
import dev.southstar21.kaloria.feature.foods.editor.warningTextRes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WarningTextTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `every label-scan warning has a string that takes its args`() {
        WarningKind.entries.forEach { kind ->
            val res = warningTextRes(kind) ?: error("No string for $kind")
            // As many args as the scanner's own text takes, so none is left unfilled.
            val args = List(Regex("%s").findAll(kind.english).count()) { "‹$it›" }
            val text = context.getString(res, *args.toTypedArray())

            assertFalse("$kind leaves a placeholder: $text", '%' in text)
            assertEquals("$kind drops an arg: $text", args.size, args.count { it in text })
        }
    }

    @Test
    fun `in English each string reads as the scanner's own text`() {
        WarningKind.entries.forEach { kind ->
            val args = List(Regex("%s").findAll(kind.english).count()) { "x$it" }
            assertEquals(kind.english.format(*args.toTypedArray()), context.getString(warningTextRes(kind)!!, *args.toTypedArray()))
        }
    }
}
