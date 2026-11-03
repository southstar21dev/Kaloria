plugins {
    alias(libs.plugins.kaloria.android.feature)
}

dependencies {
    implementation(projects.core.data)
    implementation(projects.core.ui)
    // FoodCatalog is a concrete class over the OFF/USDA data sources; the ViewModel test feeds them a mock engine.
    testImplementation(projects.core.network)
    testImplementation(libs.ktor.client.mock)
}
