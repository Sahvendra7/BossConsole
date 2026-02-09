import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    alias(libs.plugins.kotlinSerialization)
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

group = "ai.rever.boss.plugin"
version = "1.0.0"

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    jvmToolchain(17)

    jvm("desktop") {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_17)
                }
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                // Compose dependencies
                api(libs.compose.mp.runtime)
                api(libs.compose.mp.ui)
                api(libs.compose.mp.foundation)
                api(libs.compose.mp.material)
                api(compose.materialIconsExtended)

                // Decompose for ComponentContext
                api(libs.decompose)
                api(libs.essenty.lifecycle)

                // Coroutines
                api(libs.kotlinx.coroutines.core)

                // Logging (SLF4J for BossLogger)
                api(libs.slf4j.api)

                // Serialization for PluginManifest
                api(libs.kotlinx.serialization.json)

                // Type modules for provider interfaces
                api(projects.plugins.pluginBookmarkTypes)
                api(projects.plugins.pluginWorkspaceTypes)

                // Browser service API for plugins needing browser capabilities
                api(projects.plugins.pluginApiBrowser)

                // UI core for context menu data types
                api(projects.plugins.pluginUiCore)
            }
        }

        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }
    }
}
