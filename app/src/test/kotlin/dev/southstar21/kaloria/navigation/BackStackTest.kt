package dev.southstar21.kaloria.navigation

import androidx.navigation3.runtime.NavKey
import dev.southstar21.kaloria.feature.foods.FoodEditorKey
import dev.southstar21.kaloria.feature.foods.FoodsKey
import dev.southstar21.kaloria.feature.foodsearch.FoodSearchKey
import dev.southstar21.kaloria.feature.scan.BarcodeScanKey
import dev.southstar21.kaloria.feature.scan.LabelScanKey
import dev.southstar21.kaloria.feature.settings.SettingsKey
import dev.southstar21.kaloria.feature.today.TodayKey
import org.junit.Assert.assertEquals
import org.junit.Test

class BackStackTest {
    private fun stack(vararg keys: NavKey) = mutableListOf(*keys)

    @Test
    fun `a second back from a screen already closing doesn't close the one beneath`() {
        val backStack = stack(TodayKey, SettingsKey)

        backStack.pop(SettingsKey)
        backStack.pop(SettingsKey)

        assertEquals(listOf(TodayKey), backStack)
    }

    @Test
    fun `the last screen is never closed`() {
        val backStack = stack(TodayKey)

        backStack.pop(TodayKey)
        backStack.back()

        assertEquals(listOf(TodayKey), backStack)
    }

    @Test
    fun `an editor's late done after a manual back leaves the screen beneath alone`() {
        val editor = FoodEditorKey(foodId = "oats")
        val backStack = stack(TodayKey, FoodsKey, editor)

        backStack.pop(editor) // back arrow
        backStack.pop(editor) // the save finishing afterwards

        assertEquals(listOf(TodayKey, FoodsKey), backStack)
    }

    @Test
    fun `a double tap opens one screen`() {
        val backStack = stack(TodayKey, FoodsKey)

        backStack.push(FoodEditorKey(foodId = "oats"))
        backStack.push(FoodEditorKey(foodId = "oats"))

        assertEquals(listOf(TodayKey, FoodsKey, FoodEditorKey(foodId = "oats")), backStack)
    }

    @Test
    fun `replacing only happens while the screen asking is on top`() {
        val scan = BarcodeScanKey(1)
        val backStack = stack(TodayKey, scan)

        backStack.replace(scan, LabelScanKey("599"))
        backStack.replace(scan, FoodEditorKey(barcode = "599"))

        assertEquals(listOf(TodayKey, LabelScanKey("599")), backStack)
    }

    @Test
    fun `a shortcut opens over Today`() {
        val backStack = stack(TodayKey, FoodsKey)

        backStack.openShortcut(FoodSearchKey())

        assertEquals(listOf(TodayKey, FoodSearchKey()), backStack)
    }

    @Test
    fun `a shortcut goes on top of an open editor instead of discarding it`() {
        val editor = FoodEditorKey(name = "Soup")
        val backStack = stack(TodayKey, FoodsKey, editor)

        backStack.openShortcut(BarcodeScanKey(1))

        assertEquals(listOf(TodayKey, FoodsKey, editor, BarcodeScanKey(1)), backStack)
    }

    @Test
    fun `a shortcut to the screen already open does nothing`() {
        val backStack = stack(TodayKey, FoodSearchKey())

        backStack.openShortcut(FoodSearchKey())

        assertEquals(listOf(TodayKey, FoodSearchKey()), backStack)
    }

    @Test
    fun `switching tabs keeps Today at the bottom`() {
        val backStack = stack(TodayKey, SettingsKey)

        backStack.navigateTopLevel(FoodsKey)

        assertEquals(listOf(TodayKey, FoodsKey), backStack)
    }
}
