import java.util.Properties

plugins {
    alias(libs.plugins.kaloria.android.library)
    alias(libs.plugins.kaloria.hilt)
}

// Debug builds talk to a PocketBase on this machine. The emulator reaches it at 10.0.2.2; a real
// phone needs the machine's LAN address, set as pocketbase.debugUrl in local.properties.
val debugServerUrl: String = Properties().run {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
    getProperty("pocketbase.debugUrl") ?: "http://10.0.2.2:8090"
}

android {
    buildFeatures.buildConfig = true
    buildTypes {
        // Release talks to the production server. Both can be changed in Settings.
        getByName("debug") { buildConfigField("String", "DEFAULT_SERVER_URL", "\"$debugServerUrl\"") }
        getByName("release") { buildConfigField("String", "DEFAULT_SERVER_URL", "\"https://apikaloria.southstar21.dev\"") }
    }
}

dependencies {
    api(projects.core.data)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(projects.core.network)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.ktor.serialization.kotlinx.json)
}
