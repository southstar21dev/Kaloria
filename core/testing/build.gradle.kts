plugins {
    alias(libs.plugins.kaloria.android.library)
    alias(libs.plugins.kaloria.android.compose)
}

dependencies {
    api(projects.core.designsystem)
    api(libs.junit)
    api(libs.robolectric)
    api(libs.androidx.test.core)
    api(libs.androidx.compose.ui.test.junit4)
    api(libs.roborazzi)
    api(libs.roborazzi.compose)
    api(libs.roborazzi.junit.rule)
}
