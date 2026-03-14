rootProject.name = "BOSS-Kotlin"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
        mavenCentral()
    }
}

include(":composeApp")
include(":server")
include(":shared")
// Microkernel architecture modules
// protoc and protoc-gen-grpc-java do not publish Windows ARM64 binaries,
// so all microkernel modules (which depend on boss-ipc proto generation)
// are excluded on that platform. The microkernel is server-grade infrastructure
// not needed for the Windows ARM64 desktop distribution.
val settingsOsArch: String = System.getProperty("os.arch").lowercase()
val settingsOsName: String = System.getProperty("os.name").lowercase()
val isWindowsArm64 = settingsOsName.contains("win") && (settingsOsArch == "aarch64" || settingsOsArch == "arm")

if (!isWindowsArm64) {
    include(":boss-ipc")
    include(":boss-process-manager")
    include(":boss-service-auth")
    include(":boss-ui-sdk")
    include(":boss-orchestrator")
    include(":boss-microkernel-runtime")
    include(":boss-mastery-sdk")
    include(":boss-mastery-orchestrator")
    include(":boss-service-workspace")
    include(":boss-service-settings")
    include(":boss-service-filesystem")
    include(":boss-app-terminal")
    include(":boss-app-editor")
    include(":boss-app-browser")
    include(":plugins:plugin-api-ipc")
}
// Plugin modules
// plugin-api-core: Ultra-minimal core (PluginContext, DynamicPlugin, PluginManifest)
// Everything else comes from boss-plugin-api bundled plugin
include(":plugins:plugin-api-core")
include(":plugins:plugin-ui-core")
include(":plugins:plugin-logging")
include(":plugins:plugin-scrollbar")
include(":plugins:plugin-events")
include(":plugins:plugin-search")
include(":plugins:plugin-window")
include(":plugins:plugin-git-types")
include(":plugins:plugin-run-types")
include(":plugins:plugin-workspace-types")
include(":plugins:plugin-bookmark-types")
include(":plugins:plugin-icons")
include(":plugins:plugin-path-utils")
include(":plugins:plugin-sandbox")
include(":plugins:plugin-api-browser")
include(":plugins:plugin-loader")
include(":plugins:plugin-repository")
include(":plugins:plugin-dependency")
include(":plugins:plugin-updater")
// Plugin panel manager is now dynamic (loaded from boss_plugin as plugin-manager)

// Tab type plugins are now dynamic (loaded from boss_plugin):
// - editor-tab, terminal-tab, fluck-browser