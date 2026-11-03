pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Kaloria"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
include(":core:model")
include(":core:designsystem")
include(":core:database")
include(":core:datastore")
include(":core:data")
include(":core:network")
include(":core:scanner")
include(":core:sync")
include(":core:health")
include(":core:ui")
include(":core:testing")
include(":feature:today")
include(":feature:foodsearch")
include(":feature:foods")
include(":feature:settings")
include(":feature:scan")
include(":feature:progress")
include(":widget")
include(":baselineprofile")
