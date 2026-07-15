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

allprojects {
    group = providers.gradleProperty("forgenav.group").get()
    version = providers.gradleProperty("forgenav.version").get()
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
