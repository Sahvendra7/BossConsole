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
    id("com.teamdev.jxbrowser") version "2.0.0"
}

repositories {
    google()
    mavenCentral()
    maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
}

jxbrowser {
    version = "8.7.0"
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
            implementation(libs.kotlinx.datetime)
            
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
            
            // JxBrowser with Compose support
            implementation(jxbrowser.currentPlatform)
            implementation(jxbrowser.compose)
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
            "-Djava.io.tmpdir=${layout.buildDirectory.dir("tmp").get().asFile.absolutePath}",
            // JCEF arguments
            "-Djcef.path=${layout.buildDirectory.dir("jcef-natives").get().asFile.absolutePath}",
            "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
            "--add-opens=java.desktop/sun.lwawt=ALL-UNNAMED",
            "--add-opens=java.desktop/sun.lwawt.macosx=ALL-UNNAMED",
            "-Dapple.awt.application.appearance=system"
        )
        
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "BOSS"
            packageVersion = "1.0.0"
            description = "Business Operating System Service - Intelligent service automation platform"
            copyright = "© 2024 Risa Labs Inc. All rights reserved."
            vendor = "Risa Labs Inc."
            
            windows {
                menuGroup = "Boss"
                upgradeUuid = "8a5a7659-2e0f-41bd-bbbb-3140b1e7dd7d"
            }
            
            macOS {
                bundleID = "ai.rever.boss"
                iconFile.set(project.file("src/desktopMain/resources/risa_icon.icns"))
                packageName = "BOSS"
                dmgPackageVersion = "1.0.0"
                dmgPackageBuildVersion = "1"
                
                // DMG customization
                infoPlist {
                    extraKeysRawXml = """
                        <key>LSMinimumSystemVersion</key>
                        <string>10.15</string>
                        <key>CFBundleShortVersionString</key>
                        <string>1.0.0</string>
                        <key>CFBundleVersion</key>
                        <string>1</string>
                        <key>NSHighResolutionCapable</key>
                        <true/>
                        <key>NSSupportsAutomaticGraphicsSwitching</key>
                        <true/>
                    """.trimIndent()
                }
            }
        }
    }
}

// Manually extract pty4j native libraries
tasks.register("extractPty4jNative") {
    doLast {
        val pty4jNativeDir = layout.buildDirectory.dir("pty4j-native").get().asFile
        val tmpDir = layout.buildDirectory.dir("tmp").get().asFile
        
        // Create directories
        pty4jNativeDir.mkdirs()
        tmpDir.mkdirs()
        
        // Find PTY4J jar in classpath
        val pty4jJar = project.configurations.getByName("desktopRuntimeClasspath").files.find { 
            it.name.startsWith("pty4j-") && it.name.endsWith(".jar")
        }
        
        if (pty4jJar != null) {
            println("Extracting from ${pty4jJar.name}")
            
            // Extract using built-in copy function
            copy {
                from(zipTree(pty4jJar)) {
                    include("**/native/**")
                }
                into(pty4jNativeDir)
                includeEmptyDirs = false
                
                eachFile {
                    // Remove the path prefix before 'native'
                    val nativeIndex = path.indexOf("native/")
                    if (nativeIndex >= 0) {
                        path = path.substring(nativeIndex + "native/".length)
                    }
                }
            }
            
            // Make native libraries executable
            pty4jNativeDir.walkTopDown().forEach { file ->
                if (file.isFile && (file.name.endsWith(".so") || file.name.endsWith(".dylib"))) {
                    file.setExecutable(true)
                }
            }
        } else {
            println("Warning: PTY4J jar not found in classpath")
        }
        
        // Print the extracted files for debugging
        println("Extracted PTY4J native libraries to: ${pty4jNativeDir.absolutePath}")
    }
}

// Extract JCEF natives
tasks.register("extractJcefNatives") {
    doLast {
        val jcefNativeDir = layout.buildDirectory.dir("jcef-natives").get().asFile
        jcefNativeDir.mkdirs()
        
        // The jcefmaven library will download natives automatically
        // We just need to ensure the directory exists
        println("JCEF natives directory: ${jcefNativeDir.absolutePath}")
    }
}

// Make run tasks depend on the extraction tasks
afterEvaluate {
    tasks.findByName("run")?.apply {
        dependsOn("extractPty4jNative")
        dependsOn("extractJcefNatives")
    }
}
