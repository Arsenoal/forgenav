// Lowercase name avoids Gradle type-safe accessor filename/class casing bugs on Linux CI.
rootProject.name = "forgenav"

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        mavenLocal()
    }
}

// Optional composite SyncForge when developing next to a local clone.
// CI / publish use Maven Central (studio.syncforge:syncforge) instead.
val localSyncForge = file("../syncforge")
if (localSyncForge.isDirectory && file(localSyncForge, "settings.gradle.kts").exists()) {
    includeBuild(localSyncForge) {
        dependencySubstitution {
            substitute(module("studio.syncforge:syncforge")).using(project(":syncforge"))
            substitute(module("studio.syncforge:syncforge-annotations"))
                .using(project(":syncforge-annotations"))
        }
    }
}

include(
    ":forgenv-core",
    ":forgenv-compose",
    ":forgenv-syncforge",
    ":sample-android",
    ":sample-desktop",
    ":sample-ios",
)
