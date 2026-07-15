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

// Composite SyncForge from the sibling Studio project (source of truth for offline-first engine).
// Resolves studio.syncforge:syncforge to the local :syncforge project.
includeBuild("../syncforge") {
    dependencySubstitution {
        substitute(module("studio.syncforge:syncforge")).using(project(":syncforge"))
        substitute(module("studio.syncforge:syncforge-annotations"))
            .using(project(":syncforge-annotations"))
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
