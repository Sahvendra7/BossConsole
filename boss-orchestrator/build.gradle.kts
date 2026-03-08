plugins {
    alias(libs.plugins.kotlinJvm)
    id("org.graalvm.buildtools.native") version "0.10.6"
}

group = "ai.rever.boss.orchestrator"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

graalvmNative {
    binaries {
        named("main") {
            mainClass.set("ai.rever.boss.orchestrator.OrchestratorMainKt")
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
    api(project(":boss-ipc"))
    api(project(":boss-process-manager"))
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.slf4j.api)
    runtimeOnly(libs.slf4j.simple)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "ai.rever.boss.orchestrator.OrchestratorMainKt"
    }
}

tasks.register<Jar>("fatJar") {
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "ai.rever.boss.orchestrator.OrchestratorMainKt"
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    with(tasks.jar.get())
}
