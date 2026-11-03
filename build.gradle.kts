plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.baselineprofile) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
}

// Static analysis over every module's sources: `./gradlew detekt`. Existing findings are in
// config/detekt/baseline.xml; new code must be clean. Rules: config/detekt/detekt.yml.
detekt {
    source.setFrom(
        fileTree(rootDir) {
            // Modules sit one or two levels down (app, core/data, build-logic/convention).
            include("*/src/main/kotlin/**/*.kt", "*/*/src/main/kotlin/**/*.kt")
            exclude("**/build/**")
        },
    )
    config.setFrom(files("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    baseline = file("config/detekt/baseline.xml")
    parallel = true
}

// Formatting: `./gradlew spotlessApply` fixes, `spotlessCheck` (CI) verifies. Rules: .editorconfig.
spotless {
    val ktlintVersion = libs.versions.ktlint.get()
    // Spotless doesn't pass .editorconfig's rule switches through reliably, so they're set here too.
    val ktlintOverrides = mapOf(
        "ktlint_standard_filename" to "disabled",
        "ktlint_standard_property-naming" to "disabled",
    )
    kotlin {
        // Modules sit one or two levels down (app, core/data, build-logic/convention).
        target("*/src/**/*.kt", "*/*/src/**/*.kt")
        targetExclude("**/build/**")
        ktlint(ktlintVersion).setEditorConfigPath("$rootDir/.editorconfig").editorConfigOverride(ktlintOverrides)
    }
    kotlinGradle {
        target("*.gradle.kts", "*/*.gradle.kts", "*/*/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint(ktlintVersion).setEditorConfigPath("$rootDir/.editorconfig").editorConfigOverride(ktlintOverrides)
    }
}
