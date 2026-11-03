plugins {
    alias(libs.plugins.kaloria.android.library)
    alias(libs.plugins.kaloria.android.compose)
}

dependencies {
    api(projects.core.designsystem)
    api(projects.core.model)
    implementation(libs.androidx.compose.material.icons.extended)
}
