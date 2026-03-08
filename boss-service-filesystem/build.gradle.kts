plugins {
    alias(libs.plugins.kotlinJvm)
}

group = "ai.rever.boss.service.filesystem"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    implementation(project(":boss-ipc"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.slf4j.api)
    runtimeOnly(libs.slf4j.simple)

    testImplementation(libs.kotlin.test.junit)
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "ai.rever.boss.service.filesystem.FileSystemServiceMainKt"
    }
}

tasks.register<Jar>("fatJar") {
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "ai.rever.boss.service.filesystem.FileSystemServiceMainKt"
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    with(tasks.jar.get())
}
