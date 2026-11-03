plugins {
    alias(libs.plugins.kaloria.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "dev.southstar21.kaloria.baselineprofile"
    defaultConfig {
        // Numbers from an emulator are only good for comparing runs, but the profile itself is fine.
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
    }
    targetProjectPath = ":app"
}

baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
