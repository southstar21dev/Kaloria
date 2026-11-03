import dev.southstar21.kaloria.KALORIA_JAVA
import dev.southstar21.kaloria.configureKotlin
import dev.southstar21.kaloria.lib
import dev.southstar21.kaloria.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** Plain Kotlin module with no Android dependency, e.g. :core:model. */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        apply(plugin = "org.jetbrains.kotlin.jvm")
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = KALORIA_JAVA
            targetCompatibility = KALORIA_JAVA
        }
        configureKotlin()
        dependencies {
            "testImplementation"(libs.lib("junit"))
        }
    }
}
