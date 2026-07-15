plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinCompose)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { target ->
        target.binaries.framework {
            baseName = "ForgeNavSample"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":forgenv-core"))
            implementation(project(":forgenv-compose"))
            implementation(project(":forgenv-syncforge"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        iosMain.dependencies {
            // Compose UIKit + native Main dispatcher via coroutines
        }
    }
}
