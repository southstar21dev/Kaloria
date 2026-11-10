plugins {
    alias(libs.plugins.kaloria.android.feature)
}

dependencies {
    implementation(projects.core.data)
    implementation(projects.core.ui)
    // The ViewModel test builds a real HealthWeightSync over stand-ins for its database and Health Connect.
    testImplementation(projects.core.database)
    testImplementation(projects.core.health)
}
