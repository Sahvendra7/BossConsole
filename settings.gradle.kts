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
include(":bosseditor")

// Plugin modules
include(":plugins:plugin-api")
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

// Panel plugin modules
include(":plugins:plugin-panel-console")
include(":plugins:plugin-panel-performance")
include(":plugins:plugin-panel-run-configurations")
include(":plugins:plugin-panel-git-status")
include(":plugins:plugin-panel-git-log")
include(":plugins:plugin-panel-downloads")
include(":plugins:plugin-panel-secret-manager")
include(":plugins:plugin-panel-user-secret-list")
include(":plugins:plugin-panel-admin-role-management")
include(":plugins:plugin-panel-role-creation")
include(":plugins:plugin-panel-bookmarks")
include(":plugins:plugin-panel-topofmind")
include(":plugins:plugin-panel-codebase")
include(":plugins:plugin-panel-terminal")
include(":plugins:plugin-panel-fluck")
include(":plugins:plugin-panel-llmrpa")
include(":plugins:plugin-panel-rparecorder")
include(":plugins:plugin-panel-rpaengine")

// Tab type plugin modules
include(":plugins:plugin-tab-code-editor")
include(":plugins:plugin-tab-terminal")
include(":plugins:plugin-tab-fluck")