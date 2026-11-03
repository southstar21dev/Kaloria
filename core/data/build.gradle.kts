plugins {
    alias(libs.plugins.kaloria.android.library)
    alias(libs.plugins.kaloria.hilt)
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.database)
    implementation(projects.core.network)
    implementation(projects.core.health)
    api(projects.core.datastore)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    // In-memory Room for the repository tests (core:database keeps Room as an implementation detail).
    testImplementation(libs.androidx.room.runtime)
}
