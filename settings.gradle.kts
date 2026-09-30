pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

// PREFER_SETTINGS (not FAIL_ON_PROJECT_REPOS): local init scripts such as
// ~/.gradle/init.gradle add their own repositories; fail-fast mode rejects
// those builds before they start. Dependency resolution still prefers the
// google()/mavenCentral() list declared below.
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Leaflet"
include(":app")
include(":core:model")
include(":core:storage")
include(":core:markdown")
include(":core:reader")
include(":core:eink")
