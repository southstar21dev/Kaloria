plugins {
    alias(libs.plugins.kaloria.android.library)
    alias(libs.plugins.kaloria.android.compose)
}

dependencies {
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.material3)
    // Liquid Glass: backdrop capture, AGSL lens refraction, specular highlights (Apache-2.0).
    api(libs.backdrop)
    api(libs.kyant.shapes)
    implementation(libs.androidx.lifecycle.runtime.compose)
}
