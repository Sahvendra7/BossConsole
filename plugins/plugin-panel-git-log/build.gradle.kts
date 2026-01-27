import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

group = "ai.rever.boss.plugin"

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
                implementation(projects.plugins.pluginApi)
                implementation(projects.plugins.pluginUiCore)
                implementation(projects.plugins.pluginScrollbar)

                implementation(libs.compose.mp.runtime)
                implementation(libs.compose.mp.ui)
                implementation(libs.compose.mp.foundation)
                implementation(libs.compose.mp.material)
                implementation(compose.materialIconsExtended)  // No base KMP artifact

                implementation(libs.decompose)
                implementation(libs.essenty.lifecycle)
                implementation(libs.kotlinx.coroutines.core)

                implementation(libs.compose.icons.feather)
            }
        }

        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }
    }
}
