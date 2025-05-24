import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            
            // Export Decompose and Essenty for iOS
            export(libs.decompose)
            export(libs.essenty.lifecycle)
            export(libs.essenty.state.keeper)
        }
    }
    
    jvm("desktop")
    
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        moduleName = "composeApp"
        browser {
            val rootDirPath = project.rootDir.path
            val projectDirPath = project.projectDir.path
            commonWebpackConfig {
                outputFileName = "composeApp.js"
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                    static = (static ?: mutableListOf()).apply {
                        // Serve sources to debug inside browser
                        add(rootDirPath)
                        add(projectDirPath)
                    }
                }
            }
        }
        binaries.executable()
    }
    
    sourceSets {
        val desktopMain by getting

        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(projects.shared)
            implementation(libs.precompose)
//            implementation(libs.precompose.molecule)
            implementation(libs.precompose.viewmodel)

            // Decompose dependencies
            implementation(libs.decompose)
            implementation(libs.decompose.extensions.compose)
            implementation(libs.decompose.extensions.compose.experimental)
            implementation(libs.essenty.lifecycle)
            implementation(libs.essenty.state.keeper)
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.kotlinx.serialization.json)
            
            // Compose Icons dependencies
            implementation(libs.compose.icons.feather)
            implementation(libs.compose.icons.fontawesome)
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(compose.components.resources)
            
            // PTY4J with all required dependencies
            implementation(libs.pty4j)
            implementation(libs.purejavacomm) // Explicitly add this dependency
            implementation(libs.jna.platform) // JNA platform specific
            
            // For ANSI terminal emulation
            implementation(libs.lanterna)
            
            // Logging
            implementation(libs.slf4j.api)
            implementation(libs.slf4j.simple)
        }
    }
}

android {
    namespace = "ai.rever.boss"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    
    sourceSets {
        named("main") {
            manifest.srcFile("src/androidMain/AndroidManifest.xml")
            res.srcDirs("src/androidMain/res")
            // Don't include commonMain/resources in android resources
        }
    }
    
    defaultConfig {
        applicationId = "ai.rever.boss"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    dependencies {
        debugImplementation(libs.compose.ui.tooling)
    }
}

compose.desktop {
    application {
        mainClass = "ai.rever.boss.MainKt"
        
        // Add JVM arguments to help with native library loading
        jvmArgs(
            "-Dpty4j.preferred.native.folder=${layout.buildDirectory.dir("pty4j-native").get().asFile.absolutePath}",
            "-Djna.nosys=true",
            "-Dpty4j.tmpdir=${layout.buildDirectory.dir("tmp").get().asFile.absolutePath}",
            "-Djava.io.tmpdir=${layout.buildDirectory.dir("tmp").get().asFile.absolutePath}"
        )
        
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "ai.rever.boss"
            packageVersion = "1.0.0"
            
            windows {
                menuGroup = "Boss"
                upgradeUuid = "8a5a7659-2e0f-41bd-bbbb-3140b1e7dd7d"
            }
            
            macOS {
                bundleID = "ai.rever.boss"
            }
        }
    }
}

// Manually extract pty4j native libraries
tasks.register<Copy>("extractPty4jNative") {
    from(project.configurations.getByName("desktopRuntimeClasspath")) {
        include("**/pty4j-*.jar")
        include("**/purejavacomm-*.jar")
        include("**/jna-*.jar")
    }
    
    into(layout.buildDirectory.dir("tmpJars"))
    
    doLast {
        val tmpJarsDir = layout.buildDirectory.dir("tmpJars").get().asFile
        val pty4jNativeDir = layout.buildDirectory.dir("pty4j-native").get().asFile
        val tmpDir = layout.buildDirectory.dir("tmp").get().asFile
        
        // Create directories
        pty4jNativeDir.mkdirs()
        tmpDir.mkdirs()
        
        // Extract native libraries from jars
        tmpJarsDir.listFiles()?.forEach { jarFile ->
            if (jarFile.name.startsWith("pty4j-")) {
                println("Extracting from ${jarFile.name}")
                copy {
                    from(zipTree(jarFile)) {
                        include("**/libpty.dylib")
                        include("**/darwin/**")
                        include("**/linux/**")
                        include("**/win/**")
                        include("**/os/**")
                    }
                    into(pty4jNativeDir)
                }
            }
        }
        
        // Print the extracted files for debugging
        println("Extracted PTY4J native libraries to: ${pty4jNativeDir.absolutePath}")
        pty4jNativeDir.walk().forEach { file ->
            if (file.isFile) {
                println(" - ${file.relativeTo(pty4jNativeDir)}")
            }
        }
    }
}

// Make run tasks depend on the extraction task
afterEvaluate {
    tasks.findByName("run")?.dependsOn("extractPty4jNative")
}
