plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvm("desktop")

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(projects.plugins.pluginApi)
                implementation(projects.plugins.pluginUiCore)
                implementation(projects.plugins.pluginScrollbar)
                implementation(projects.plugins.pluginSearch)

                implementation(libs.compose.mp.runtime)
                implementation(libs.compose.mp.foundation)
                implementation(libs.compose.mp.material)
                implementation(compose.materialIconsExtended)  // No base KMP artifact
                implementation(libs.compose.mp.ui)

                implementation(libs.decompose)
                implementation(libs.decompose.extensions.compose)
                implementation(libs.kotlinx.coroutines.core)

                // Feather icons
                implementation(libs.compose.icons.feather)
            }
        }
    }
}
