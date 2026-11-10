plugins {
    alias(libs.plugins.kaloria.android.library)
    alias(libs.plugins.kaloria.android.compose)
}

dependencies {
    api(projects.core.designsystem)
    api(projects.core.model)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)

    testImplementation(projects.core.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
