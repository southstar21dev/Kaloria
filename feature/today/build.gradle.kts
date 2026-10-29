plugins {
    alias(libs.plugins.kaloria.android.feature)
}

dependencies {
    implementation(projects.core.data)
    implementation(projects.core.ui)
    implementation(projects.core.health)
}
