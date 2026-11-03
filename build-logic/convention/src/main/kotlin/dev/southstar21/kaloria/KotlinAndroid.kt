package dev.southstar21.kaloria

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

/** The Java/JVM level every module compiles for. Change it here only. */
val KALORIA_JAVA: JavaVersion = JavaVersion.VERSION_17
internal val KALORIA_JVM_TARGET: JvmTarget = JvmTarget.JVM_17

/** Shared Android settings for every application and library module. */
internal fun Project.configureKotlinAndroid(extension: CommonExtension) {
    extension.apply {
        compileSdk = libs.version("compileSdk").toInt()
        defaultConfig.minSdk = libs.version("minSdk").toInt()
        compileOptions.sourceCompatibility = KALORIA_JAVA
        compileOptions.targetCompatibility = KALORIA_JAVA
        testOptions.unitTests.isIncludeAndroidResources = true
        // Robolectric screenshot tests render full screens; the default heap runs out.
        testOptions.unitTests.all { it.maxHeapSize = "3g" }
    }
    configureKotlin()
    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions.optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi")
    }
}

internal fun Project.configureKotlin() {
    // Modules without tests yet (datastore, …) shouldn't fail `test`.
    tasks.withType<Test>().configureEach {
        failOnNoDiscoveredTests.set(false)
        // Robolectric reaches into JDK internals, which JDK 17+ only allows when opened explicitly.
        jvmArgs(
            "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
            "--add-opens=java.base/java.io=ALL-UNNAMED",
            "--add-opens=java.base/java.lang=ALL-UNNAMED",
        )
    }
    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(KALORIA_JVM_TARGET)
        }
    }
}
