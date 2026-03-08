plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    id("org.graalvm.buildtools.native") version "0.10.6"
}

group = "ai.rever.boss.service.workspace"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

graalvmNative {
    binaries {
        named("main") {
            mainClass.set("ai.rever.boss.service.workspace.WorkspaceServiceMainKt")
            buildArgs.add("--no-fallback")
            buildArgs.add("-H:+ReportExceptionStackTraces")
            buildArgs.add("--initialize-at-build-time=kotlin")
        }
    }
    metadataRepository {
        enabled.set(true)
    }
}

dependencies {
    implementation(project(":boss-ipc"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.slf4j.api)
    runtimeOnly(libs.slf4j.simple)

    testImplementation(libs.kotlin.test.junit)
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "ai.rever.boss.service.workspace.WorkspaceServiceMainKt"
    }
}

tasks.register<Jar>("fatJar") {
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "ai.rever.boss.service.workspace.WorkspaceServiceMainKt"
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    with(tasks.jar.get())
}
