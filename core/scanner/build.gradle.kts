plugins {
    alias(libs.plugins.kaloria.android.library)
    alias(libs.plugins.kaloria.hilt)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    api(projects.core.model)
    api(libs.androidx.camera.camera2)
    implementation(libs.mlkit.barcode)
    implementation(libs.mlkit.text)
    implementation(libs.mlkit.genai.prompt)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)
}
