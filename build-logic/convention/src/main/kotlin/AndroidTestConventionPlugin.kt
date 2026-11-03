import com.android.build.api.dsl.TestExtension
import dev.southstar21.kaloria.KALORIA_JAVA
import dev.southstar21.kaloria.libs
import dev.southstar21.kaloria.version
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure

/** An Android test module that drives another one, e.g. :baselineprofile. */
class AndroidTestConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        apply(plugin = "com.android.test")
        extensions.configure<TestExtension> {
            compileSdk = libs.version("compileSdk").toInt()
            defaultConfig {
                minSdk = libs.version("minSdk").toInt()
                targetSdk = libs.version("compileSdk").toInt()
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
            compileOptions {
                sourceCompatibility = KALORIA_JAVA
                targetCompatibility = KALORIA_JAVA
            }
        }
    }
}
