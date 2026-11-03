import dev.southstar21.kaloria.lib
import dev.southstar21.kaloria.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

/** A :feature:* module: Compose UI, Hilt ViewModels and a Navigation 3 entry. */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        apply(plugin = "kaloria.android.library")
        apply(plugin = "kaloria.android.compose")
        apply(plugin = "kaloria.hilt")
        apply(plugin = "org.jetbrains.kotlin.plugin.serialization")
        apply(plugin = "io.github.takahirom.roborazzi")

        dependencies {
            "implementation"(project(":core:designsystem"))
            "implementation"(project(":core:model"))
            "implementation"(libs.lib("androidx-compose-material3"))
            "implementation"(libs.lib("androidx-compose-material-icons-extended"))
            "implementation"(libs.lib("androidx-activity-compose"))
            "implementation"(libs.lib("androidx-lifecycle-runtime-compose"))
            "implementation"(libs.lib("androidx-lifecycle-viewmodel-compose"))
            "implementation"(libs.lib("androidx-hilt-lifecycle-viewmodel-compose"))
            "implementation"(libs.lib("androidx-navigation3-runtime"))
            "implementation"(libs.lib("kotlinx-serialization-json"))
            "testImplementation"(libs.lib("turbine"))
            "testImplementation"(project(":core:testing"))
            "debugImplementation"(libs.lib("androidx-compose-ui-test-manifest"))
        }
    }
}
