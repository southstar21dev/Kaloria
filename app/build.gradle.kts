plugins {
    alias(libs.plugins.kaloria.android.application)
    alias(libs.plugins.kaloria.android.compose)
    alias(libs.plugins.kaloria.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "dev.southstar21.kaloria"

    defaultConfig {
        applicationId = "dev.southstar21.kaloria"
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            // Until a release keystore exists, sign with the debug key so the optimised build can be
            // installed for testing. Play uploads need a real key (keystore.properties).
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    testOptions.unitTests.all { it.maxHeapSize = "3g" }
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(projects.core.model)
    implementation(projects.core.data)
    implementation(projects.core.sync)
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
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(projects.core.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
