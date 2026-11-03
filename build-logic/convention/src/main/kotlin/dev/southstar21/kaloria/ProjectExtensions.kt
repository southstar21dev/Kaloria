package dev.southstar21.kaloria

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

fun VersionCatalog.lib(alias: String) = findLibrary(alias).get()

fun VersionCatalog.version(alias: String) = findVersion(alias).get().requiredVersion

/** A value from the (untracked) local.properties, or null. For per-developer settings and keys. */
fun Project.localProperty(name: String): String? {
    val file = rootProject.file("local.properties")
    if (!file.exists()) return null
    return java.util.Properties().apply { file.inputStream().use(::load) }.getProperty(name)
}
