import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    alias(libs.plugins.kotlinSerialization)
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

group = "ai.rever.boss.plugin"

kotlin {
    // Suppress expect/actual classes beta warning (KT-61573)
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    jvmToolchain(17)

    // Desktop JVM target
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
                implementation(compose.runtime)
                implementation(compose.ui)
                implementation(compose.foundation)

                // Decompose for ComponentContext
                implementation(libs.decompose)
                implementation(libs.essenty.lifecycle)

                // Coroutines
                implementation(libs.kotlinx.coroutines.core)

                // Serialization for Panel
                implementation(libs.kotlinx.serialization.json)

                // Type modules for provider interfaces
                api(projects.plugins.pluginBookmarkTypes)
                api(projects.plugins.pluginWorkspaceTypes)
            }
        }

        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }
    }
}
