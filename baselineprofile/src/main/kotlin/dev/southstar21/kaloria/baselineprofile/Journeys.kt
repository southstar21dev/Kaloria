package dev.southstar21.kaloria.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.Until

/** The app under test, as the baseline profile plugin passes it (release builds have no suffix). */
val targetPackage: String
    get() = InstrumentationRegistry.getArguments().getString("targetAppId") ?: "dev.southstar21.kaloria"

/** Opens a dock tab. Its label can also be a screen title ("Today"), so take the lowest match. */
fun MacrobenchmarkScope.openTab(label: String) {
    device.wait(Until.hasObject(By.text(label)), 5_000)
    device.findObjects(By.text(label)).maxByOrNull { it.visibleBounds.bottom }?.click()
    device.waitForIdle()
}

/** Flings the screen's list down and back, so list and glass code paths get compiled. */
fun MacrobenchmarkScope.scrollScreen() {
    fling(Direction.DOWN)
    fling(Direction.UP)
}

/** Finds the list afresh each time: screens recompose as their data loads, replacing the node. */
private fun MacrobenchmarkScope.fling(direction: Direction) {
    repeat(3) {
        val list = device.findObject(By.scrollable(true)) ?: return
        try {
            list.setGestureMargin(device.displayWidth / 5)
            list.fling(direction)
            device.waitForIdle()
            return
        } catch (_: StaleObjectException) {
            device.waitForIdle()
        }
    }
}

/** Every top-level screen, the dock's add menu, food search and the food editor: what a typical session touches. */
fun MacrobenchmarkScope.browseApp() {
    device.wait(Until.hasObject(By.text("Today")), 10_000)
    scrollScreen()
    for (tab in listOf("Progress", "Foods", "Today")) {
        openTab(tab)
        scrollScreen()
    }
    searchAndOpenEditor()
}

/**
 * Add menu, then search: types a query, waits for results (online sources, so possibly none), then
 * opens the editor from "Create" (works offline too) and backs out to Today.
 */
private fun MacrobenchmarkScope.searchAndOpenEditor() {
    val add = device.findObject(By.desc("Add food")) ?: return
    add.click()
    if (!device.wait(Until.hasObject(By.text("Search foods")), 2_000)) return
    device.findObject(By.text("Search foods"))?.click()
    device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5_000)?.let { field ->
        field.text = "apple"
        device.wait(Until.hasObject(By.textStartsWith("Create")), 5_000)
        device.waitForIdle()
        scrollScreen()
        device.findObject(By.textStartsWith("Create"))?.let {
            it.click()
            device.wait(Until.hasObject(By.text("New food")), 5_000)
            device.waitForIdle()
            scrollScreen()
        }
    }
    backToToday()
}

/** Backs out of whatever is open (keyboard, editor, its discard prompt, search) until the dock shows. */
private fun MacrobenchmarkScope.backToToday() {
    repeat(5) {
        if (device.hasObject(By.desc("Add food"))) return
        device.findObject(By.text("Discard"))?.click() ?: device.pressBack()
        device.waitForIdle()
    }
}
