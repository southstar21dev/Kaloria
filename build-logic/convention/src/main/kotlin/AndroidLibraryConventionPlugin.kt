import com.android.build.api.dsl.LibraryExtension
import dev.southstar21.kaloria.configureKotlinAndroid
import dev.southstar21.kaloria.lib
import dev.southstar21.kaloria.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        apply(plugin = "com.android.library")
        extensions.configure<LibraryExtension> {
            configureKotlinAndroid(this)
            // Namespace follows the module path: :core:database -> dev.southstar21.kaloria.core.database
            namespace = "dev.southstar21.kaloria" + path.replace(':', '.').replace('-', '_')
        }
        dependencies {
            "testImplementation"(libs.lib("junit"))
            "testImplementation"(libs.lib("kotlinx-coroutines-test"))
        }
    }
}
