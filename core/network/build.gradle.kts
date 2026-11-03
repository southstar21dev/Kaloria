import dev.southstar21.kaloria.localProperty

plugins {
    alias(libs.plugins.kaloria.android.library)
    alias(libs.plugins.kaloria.hilt)
    alias(libs.plugins.kotlin.serialization)
}

// The USDA key comes from local.properties (usda.apiKey) or the USDA_API_KEY env var in CI, and
// never from the repo. DEMO_KEY works but is limited to ~30 requests an hour.
val usdaApiKey: String = localProperty("usda.apiKey") ?: System.getenv("USDA_API_KEY") ?: "DEMO_KEY"

android {
    buildFeatures.buildConfig = true
    defaultConfig {
        buildConfigField("String", "USDA_API_KEY", "\"$usdaApiKey\"")
    }
}

dependencies {
    api(projects.core.model)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.ktor.client.mock)
}
