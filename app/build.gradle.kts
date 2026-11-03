import dev.southstar21.kaloria.semanticVersion
import dev.southstar21.kaloria.versionCodeOf
import java.util.Properties

plugins {
    alias(libs.plugins.kaloria.android.application)
    alias(libs.plugins.kaloria.android.compose)
    alias(libs.plugins.kaloria.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.baselineprofile)
}

// The release workflow passes the version semantic-release chose; local builds work it out from git.
val appVersion: String = providers.gradleProperty("kaloriaVersion").orNull ?: semanticVersion()

// The release key: keystore.properties locally, environment variables in the release workflow.
// Without either, release builds are signed with the debug key (fine for your own phone).
val keystoreProperties = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}

fun signingValue(property: String, environmentVariable: String): String? = keystoreProperties.getProperty(property) ?: providers.environmentVariable(environmentVariable).orNull

val releaseKeystore: String? = signingValue("storeFile", "KALORIA_KEYSTORE_PATH")

android {
    namespace = "dev.southstar21.kaloria"

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = signingValue("storePassword", "KALORIA_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "KALORIA_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "KALORIA_KEY_PASSWORD")
            }
        }
    }

    defaultConfig {
        applicationId = "dev.southstar21.kaloria"
        versionCode = versionCodeOf(appVersion)
        versionName = appVersion
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            // Without a release key, sign with the debug key so the optimised build still installs.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    // Nearly all of the APK is ML Kit's native OCR and barcode engines, once per CPU type. Builds
    // make one APK with only arm64-v8a (every phone from the last decade, ~25 MB smaller) plus a
    // universal one for anything else (32-bit ARM, emulators).
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            isUniversalApk = true
        }
    }

    lint {
        // Lint the whole app at once from here, so cross-module checks (e.g. unused resources) work.
        checkDependencies = true
        // Existing findings; new code must be clean.
        baseline = file("lint-baseline.xml")
    }
}

base.archivesName = "kaloria-$appVersion"

// Prints the version the next build will use: ./gradlew -q :app:printVersion
tasks.register("printVersion") {
    val version = appVersion
    doLast { println(version) }
}

baselineProfile {
    // The profile is checked in (src/release/generated); regenerate with :app:generateBaselineProfile.
    automaticGenerationDuringBuild = false
    // Also lays out the dex so start-up code is read in one sequential chunk.
    dexLayoutOptimization = true
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(projects.core.model)
    implementation(projects.core.data)
    implementation(projects.core.sync)
    implementation(projects.core.ui)
    implementation(projects.feature.today)
    implementation(projects.feature.foodsearch)
    implementation(projects.feature.foods)
    implementation(projects.feature.settings)
    implementation(projects.feature.scan)
    implementation(projects.feature.progress)
    implementation(projects.widget)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.profileinstaller)
    baselineProfile(projects.baselineprofile)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(projects.core.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
