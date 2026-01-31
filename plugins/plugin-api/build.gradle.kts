import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    alias(libs.plugins.kotlinSerialization)
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    `maven-publish`
    signing
}

group = "com.risaboss"
version = "1.0.0"

val isRelease = !version.toString().endsWith("-SNAPSHOT")

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
                implementation(libs.compose.mp.runtime)
                implementation(libs.compose.mp.ui)
                implementation(libs.compose.mp.foundation)

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

                // Browser service API for plugins needing browser capabilities
                api(projects.plugins.pluginApiBrowser)
            }
        }

        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

publishing {
    publications.withType<MavenPublication> {
        pom {
            name.set("BOSS Plugin API")
            description.set("Core API for building BOSS desktop application plugins")
            url.set("https://github.com/risa-labs-inc/BossConsole")
            licenses {
                license {
                    name.set("The Apache License, Version 2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                }
            }
            developers {
                developer {
                    id.set("risa-labs")
                    name.set("Risa Labs")
                    email.set("dev@risaboss.com")
                }
            }
            scm {
                connection.set("scm:git:git://github.com/risa-labs-inc/BossConsole.git")
                developerConnection.set("scm:git:ssh://github.com/risa-labs-inc/BossConsole.git")
                url.set("https://github.com/risa-labs-inc/BossConsole")
            }
        }
    }

    repositories {
        maven {
            name = "MavenCentral"
            url = uri(
                if (isRelease) "https://central.sonatype.com/api/v1/publisher/upload"
                else "https://central.sonatype.com/api/v1/publisher/upload"
            )
            credentials {
                username = System.getenv("MAVEN_CENTRAL_USERNAME") ?: project.findProperty("mavenCentralUsername") as String? ?: ""
                password = System.getenv("MAVEN_CENTRAL_PASSWORD") ?: project.findProperty("mavenCentralPassword") as String? ?: ""
            }
        }
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/risa-labs-inc/BossConsole-Releases")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: project.findProperty("gpr.user") as String? ?: ""
                password = System.getenv("GITHUB_TOKEN") ?: project.findProperty("gpr.token") as String? ?: ""
            }
        }
    }
}

signing {
    val signingKey = System.getenv("GPG_SIGNING_KEY")
    val signingPassword = System.getenv("GPG_SIGNING_PASSPHRASE")
    if (signingKey != null && signingPassword != null) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications)
    }
}
