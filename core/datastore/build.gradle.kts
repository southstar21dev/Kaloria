plugins {
    alias(libs.plugins.kaloria.android.library)
    alias(libs.plugins.kaloria.hilt)
}

dependencies {
    api(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
