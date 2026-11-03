package dev.southstar21.kaloria.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records which code start-up and a walk through every tab run, so it's compiled ahead of time at
 * install instead of interpreted on first use. Run `./gradlew :app:generateBaselineProfile` with a
 * device or emulator connected, and commit the result.
 *
 * Only cold start goes into the startup profile, which decides what dex layout puts first. Putting
 * the whole walk there too would make it as big as the baseline profile and undo that.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startup() = rule.collect(packageName = targetPackage, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
    }

    @Test
    fun browse() = rule.collect(packageName = targetPackage, includeInStartupProfile = false) {
        pressHome()
        startActivityAndWait()
        browseApp()
    }
}
