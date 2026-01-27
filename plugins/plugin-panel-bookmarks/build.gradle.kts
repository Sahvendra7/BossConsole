import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

group = "ai.rever.boss.plugin.panel"

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
                // Plugin API
                implementation(projects.plugins.pluginApi)
                implementation(projects.plugins.pluginUiCore)
                implementation(projects.plugins.pluginLogging)
                implementation(projects.plugins.pluginScrollbar)
                implementation(projects.plugins.pluginSearch)
                implementation(projects.plugins.pluginBookmarkTypes)
                implementation(projects.plugins.pluginWorkspaceTypes)

                // Compose dependencies
                implementation(libs.compose.mp.runtime)
                implementation(libs.compose.mp.ui)
                implementation(libs.compose.mp.foundation)
                implementation(libs.compose.mp.material)
                implementation(compose.materialIconsExtended)  // No base KMP artifact

                // Decompose for ComponentContext
                implementation(libs.decompose)
                implementation(libs.essenty.lifecycle)

                // Coroutines
                implementation(libs.kotlinx.coroutines.core)
            }
        }

        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }
    }
}
