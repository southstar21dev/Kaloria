plugins {
    alias(libs.plugins.kaloria.android.library)
    alias(libs.plugins.kaloria.hilt)
}

dependencies {
    api(projects.core.model)
    api(libs.androidx.health.connect)
    implementation(libs.kotlinx.coroutines.android)
}
