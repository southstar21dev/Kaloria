import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import dev.southstar21.kaloria.lib
import dev.southstar21.kaloria.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        apply(plugin = "org.jetbrains.kotlin.plugin.compose")
        val android: CommonExtension = extensions.findByType(ApplicationExtension::class.java)
            ?: extensions.getByType(LibraryExtension::class.java)
        android.buildFeatures.compose = true

        extensions.configure(ComposeCompilerGradlePluginExtension::class.java) {
            stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("compose_compiler_config.conf"))
        }

        dependencies {
            val bom = libs.lib("androidx-compose-bom")
            "implementation"(platform(bom))
            "implementation"(libs.lib("androidx-compose-ui"))
            "implementation"(libs.lib("androidx-compose-ui-tooling-preview"))
            "debugImplementation"(libs.lib("androidx-compose-ui-tooling"))
            "testImplementation"(platform(bom))
        }
    }
}
