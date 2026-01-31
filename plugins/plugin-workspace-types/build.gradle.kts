import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    alias(libs.plugins.kotlinSerialization)
    `maven-publish`
    signing
}

group = "com.risaboss"
version = "1.0.0"

val isRelease = !version.toString().endsWith("-SNAPSHOT")

kotlin {
    jvmToolchain(17)

    jvm("desktop") {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.serialization.json)
            }
        }
    }
}

publishing {
    publications.withType<MavenPublication> {
        pom {
            name.set("BOSS Plugin Workspace Types")
            description.set("Workspace data types for BOSS Plugin API")
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
