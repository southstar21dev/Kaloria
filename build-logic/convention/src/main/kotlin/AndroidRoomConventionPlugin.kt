import androidx.room.gradle.RoomExtension
import dev.southstar21.kaloria.lib
import dev.southstar21.kaloria.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        apply(plugin = "androidx.room")
        apply(plugin = "com.google.devtools.ksp")
        extensions.configure<RoomExtension> {
            schemaDirectory("$projectDir/schemas")
        }
        dependencies {
            "implementation"(libs.lib("androidx-room-runtime"))
            "implementation"(libs.lib("androidx-room-ktx"))
            "ksp"(libs.lib("androidx-room-compiler"))
        }
    }
}
