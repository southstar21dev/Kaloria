package dev.southstar21.kaloria

import org.gradle.api.Project

private const val FALLBACK_VERSION = "0.1.0"
private val RELEASE_TAG = Regex("""v(\d+)\.(\d+)\.(\d+)""")
private val CONVENTIONAL_SUBJECT = Regex("""^(\w+)(?:\([^)]*\))?(!)?: """)
private val BREAKING_FOOTER = Regex("""^BREAKING[ -]CHANGE: """, RegexOption.MULTILINE)

/**
 * The version semantic-release will give the next release: the highest release tag, bumped by the
 * Conventional Commits made since. Uses the fallback without git history, as in a shallow clone.
 *
 * Tags land on master, so the newest may not be an ancestor of this branch. `tag..HEAD` still
 * lists only the commits that tag doesn't contain.
 */
fun Project.semanticVersion(): String {
    val tag = git("tag", "--list", "v*")?.lineSequence()
        ?.mapNotNull { RELEASE_TAG.matchEntire(it.trim()) }
        ?.map { match -> match.groupValues.drop(1).map(String::toInt) }
        ?.maxWithOrNull(compareBy({ it[0] }, { it[1] }, { it[2] }))
        ?: return FALLBACK_VERSION
    val (major, minor, patch) = tag
    val messages = git("log", "--format=%B%x00", "v$major.$minor.$patch..HEAD")
        ?.split('\u0000').orEmpty()
    val subjects = messages.mapNotNull { CONVENTIONAL_SUBJECT.find(it.trim()) }
    return when {
        messages.any { BREAKING_FOOTER.containsMatchIn(it) } ||
            subjects.any { it.groupValues[2] == "!" } -> "${major + 1}.0.0"

        subjects.any { it.groupValues[1] == "feat" } -> "$major.${minor + 1}.0"

        subjects.any { it.groupValues[1] in setOf("fix", "perf") } -> "$major.$minor.${patch + 1}"

        else -> "$major.$minor.$patch"
    }
}

/**
 * 1.2.3 becomes 1020399, so releases install in order. Minor and patch must stay below 100.
 *
 * The `-rc.N` form (1.2.3-rc.4 becomes 1020304) is no longer produced since the staging branch was
 * dropped; it is still parsed so the scheme, and with it every existing version code, stays the same.
 */
fun versionCodeOf(version: String): Int {
    val match = requireNotNull(Regex("""(\d+)\.(\d+)\.(\d+)(?:-rc\.(\d+))?""").matchEntire(version)) {
        "Unsupported version: $version"
    }
    val parts = match.groupValues
    val major = parts[1].toInt()
    val minor = parts[2].toInt()
    val patch = parts[3].toInt()
    val rc = parts[4].toIntOrNull()
    require(minor < 100 && patch < 100 && (rc ?: 0) < 99) { "Version doesn't fit in versionCode: $version" }
    return major * 1_000_000 + minor * 10_000 + patch * 100 + (rc ?: 99)
}

private fun Project.git(vararg args: String): String? = runCatching {
    val output = providers.exec {
        workingDir = rootDir
        commandLine("git", *args)
        isIgnoreExitValue = true
    }
    output.standardOutput.asText.get().takeIf { output.result.get().exitValue == 0 }
}.getOrNull()
