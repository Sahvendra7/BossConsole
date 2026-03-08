plugins {
    alias(libs.plugins.kotlinJvm)
}

group = "ai.rever.boss.service.auth"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    // IPC protocol and connection management
    implementation(project(":boss-ipc"))

    // Kotlin coroutines
    implementation(libs.kotlinx.coroutines.core)

    // Logging
    implementation(libs.slf4j.api)
    runtimeOnly(libs.slf4j.simple)

    // Serialization (for session data)
    implementation(libs.kotlinx.serialization.json)

    // Testing
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

// Application entry point
tasks.jar {
    manifest {
        attributes["Main-Class"] = "ai.rever.boss.service.auth.AuthServiceMainKt"
    }
}

tasks.register<Jar>("fatJar") {
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "ai.rever.boss.service.auth.AuthServiceMainKt"
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    with(tasks.jar.get())
}
