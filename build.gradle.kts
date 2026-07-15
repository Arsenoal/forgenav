plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinAndroid) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.kotlinCompose) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.dokka) apply false
}

apply(from = "gradle/publish-convention.gradle.kts")
apply(from = "gradle/maven-central.gradle.kts")

allprojects {
    group = providers.gradleProperty("forgenav.group").get()
    version = providers.gradleProperty("forgenav.version").get()
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}

val libraryPublishProjects = listOf(
    ":forgenv-core",
    ":forgenv-compose",
    ":forgenv-syncforge",
    ":forgenv-testing",
)

tasks.register("publishAllToMavenLocal") {
    group = "publishing"
    description = "Publishes ForgeNav library modules to the local Maven repository."
    dependsOn(libraryPublishProjects.map { "$it:publishToMavenLocal" })
}

tasks.register("publishAllToMavenCentral") {
    group = "publishing"
    description =
        "Publishes ForgeNav library modules to Maven Central staging " +
            "(set mavenCentralPublishing=true and credentials)."
    dependsOn(
        libraryPublishProjects.map { "$it:publishAllPublicationsToMavenCentralRepository" },
    )
    onlyIf {
        providers.gradleProperty("mavenCentralPublishing").orNull == "true"
    }
}

tasks.register("verifyPublishSigning") {
    group = "verification"
    description =
        "Fails if signAllPublications=true but no Sign tasks were created (unsigned Central publish)."
    onlyIf {
        providers.gradleProperty("signAllPublications").orNull == "true"
    }
    doLast {
        val signTaskCount = subprojects.sumOf { project ->
            project.tasks.withType<org.gradle.plugins.signing.Sign>().count()
        }
        check(signTaskCount > 0) {
            "No Sign tasks were created; Maven Central publish would be unsigned"
        }
        logger.lifecycle("Publish signing wired for $signTaskCount Sign task(s)")
    }
}

tasks.register("verifyReleaseSignOff") {
    group = "verification"
    description = "Pre-publish sign-off: core/syncforge JVM tests + library compile checks."
    dependsOn(
        ":forgenv-core:jvmTest",
        ":forgenv-syncforge:jvmTest",
        ":forgenv-testing:jvmTest",
        ":forgenv-core:compileKotlinJvm",
        ":forgenv-compose:compileKotlinJvm",
        ":forgenv-syncforge:compileKotlinJvm",
        ":forgenv-testing:compileKotlinJvm",
        ":forgenv-core:compileDebugKotlinAndroid",
        ":forgenv-compose:compileDebugKotlinAndroid",
        ":forgenv-syncforge:compileDebugKotlinAndroid",
        ":forgenv-testing:compileDebugKotlinAndroid",
    )
}
