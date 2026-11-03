plugins {
    alias(libs.plugins.kaloria.android.library)
    alias(libs.plugins.kaloria.android.room)
    alias(libs.plugins.kaloria.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    // MigrationTestHelper reads the exported schemas from the test assets; under Robolectric those
    // are the unit test's assets.
    sourceSets.getByName("test").assets.directories.add("$projectDir/schemas")
}

dependencies {
    api(projects.core.model)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
}
