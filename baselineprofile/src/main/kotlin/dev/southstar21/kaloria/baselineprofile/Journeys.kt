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

/** Every top-level screen, plus the dock's add menu: what a typical session touches. */
fun MacrobenchmarkScope.browseApp() {
    device.wait(Until.hasObject(By.text("Today")), 10_000)
    scrollScreen()
    for (tab in listOf("Progress", "Foods", "Today")) {
        openTab(tab)
        scrollScreen()
    }
    device.findObject(By.desc("Add food"))?.let {
        it.click()
        device.wait(Until.hasObject(By.text("Search foods")), 2_000)
        device.pressBack()
        device.waitForIdle()
    }
}
