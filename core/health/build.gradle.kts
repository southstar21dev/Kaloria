plugins {
    alias(libs.plugins.kaloria.android.library)
    alias(libs.plugins.kaloria.hilt)
}

dependencies {
    api(libs.androidx.health.connect)
    implementation(libs.kotlinx.coroutines.android)
}
