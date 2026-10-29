plugins {
    alias(libs.plugins.kaloria.android.library)
    alias(libs.plugins.kaloria.hilt)
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.database)
    implementation(projects.core.network)
    api(projects.core.datastore)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.turbine)
}
