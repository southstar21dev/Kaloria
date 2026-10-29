import com.android.build.api.dsl.ApplicationExtension
import dev.southstar21.kaloria.configureKotlinAndroid
import dev.southstar21.kaloria.libs
import dev.southstar21.kaloria.version
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        apply(plugin = "com.android.application")
        extensions.configure<ApplicationExtension> {
            configureKotlinAndroid(this)
            defaultConfig.targetSdk = libs.version("compileSdk").toInt()
        }
    }
}
