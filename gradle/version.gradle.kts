// Version Management for BOSS Application
// Kotlin DSL with proper Provider API for Gradle 9+ configuration cache compatibility

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

// ============================================================================
// Extension Properties - Available to all build files
// ============================================================================

// Load version properties
val versionPropsFileObject = file("version.properties")
val versionProps = Properties()

if (versionPropsFileObject.exists()) {
    versionProps.load(FileInputStream(versionPropsFileObject))
} else {
    throw GradleException("version.properties file not found! Please create it with version information.")
}

// Extract version components
val versionMajor = versionProps["app.version.major"].toString().toInt()
val versionMinor = versionProps["app.version.minor"].toString().toInt()
val versionPatch = versionProps["app.version.patch"].toString().toInt()

// Construct version strings
val appVersion = "$versionMajor.$versionMinor.$versionPatch"
val bundleVersion = versionProps["app.bundle.version"].toString()
val buildNumber = versionProps["app.build.number"].toString()

// Build artifact names
val jarName = "BOSS-$appVersion-all.jar"
val dmgName = "BOSS-$appVersion.dmg"
val dmgUniversalName = "BOSS-$appVersion-Universal.dmg"
val msiName = "BOSS-$appVersion.msi"
val packageZipName = "BOSS-package-$appVersion.zip"

// Set as extra properties for access from other build files
project.extra.apply {
    set("versionMajor", versionMajor)
    set("versionMinor", versionMinor)
    set("versionPatch", versionPatch)
    set("appVersion", appVersion)
    set("bundleVersion", bundleVersion)
    set("buildNumber", buildNumber)
    set("jarName", jarName)
    set("dmgName", dmgName)
    set("dmgUniversalName", dmgUniversalName)
    set("msiName", msiName)
    set("packageZipName", packageZipName)
}

// Version info for logging
println("📦 BOSS Version: $appVersion")
println("🔢 Build Number: $buildNumber")
println("📅 Build Date: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())}")

// ============================================================================
// Task Classes - Proper Provider API usage
// ============================================================================

/**
 * Base class for version property reading tasks
 */
abstract class VersionPropertyReadTask : DefaultTask() {
    @get:InputFile
    abstract val versionFile: RegularFileProperty

    protected fun loadProperties(): Properties {
        val props = Properties()
        versionFile.get().asFile.inputStream().use { props.load(it) }
        return props
    }
}

/**
 * Base class for version property modification tasks
 */
abstract class VersionPropertyWriteTask : DefaultTask() {
    @get:InputFile
    abstract val inputFile: RegularFileProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    protected fun loadProperties(): Properties {
        val props = Properties()
        inputFile.get().asFile.inputStream().use { props.load(it) }
        return props
    }

    protected fun saveProperties(props: Properties, comment: String) {
        outputFile.get().asFile.outputStream().use {
            props.store(it, comment)
        }
    }
}

/**
 * Display current version information
 */
abstract class ShowVersionTask : VersionPropertyReadTask() {
    @TaskAction
    fun showVersion() {
        val props = loadProperties()

        val major = props["app.version.major"]
        val minor = props["app.version.minor"]
        val patch = props["app.version.patch"]
        val av = "$major.$minor.$patch"
        val bv = props["app.bundle.version"]
        val bn = props["app.build.number"]
        val jn = "BOSS-$av-all.jar"
        val dn = "BOSS-$av.dmg"
        val mn = "BOSS-$av.msi"

        val nextBuildNumber = bn.toString().toInt() + 1

        println("""
╔════════════════════════════════════════╗
║           BOSS Version Info            ║
╠════════════════════════════════════════╣
║ Application Version: $av           ║
║ Bundle Version:      $bv           ║
║ Build Number:        $bn             ║
║ JAR Name:            $jn    ║
║ DMG Name:            $dn         ║
║ MSI Name:            $mn         ║
╚════════════════════════════════════════╝

💡 Version Management Commands:
   ./gradlew incrementBuildNumber  - Increment build number ($bn → $nextBuildNumber)
   ./gradlew incrementVersion      - Increment patch version and reset build number
   ./gradlew incrementMinor        - Increment minor version and reset build number
   ./gradlew incrementMajor        - Increment major version and reset build number
        """.trimIndent())
    }
}

/**
 * Increment patch version and reset build number
 */
abstract class IncrementVersionTask : VersionPropertyWriteTask() {
    @TaskAction
    fun increment() {
        val props = loadProperties()

        val currentPatch = props["app.version.patch"].toString().toInt()
        val newPatch = currentPatch + 1

        // Update patch version and reset build number
        props["app.version.patch"] = newPatch.toString()
        props["app.version"] = "${props["app.version.major"]}.${props["app.version.minor"]}.$newPatch"
        props["app.bundle.version"] = props["app.version"]
        props["app.build.date"] = SimpleDateFormat("yyyy-MM-dd").format(Date())
        props["app.build.number"] = "1"

        saveProperties(props, "Auto-incremented patch version")

        println("✅ Version incremented to ${props["app.version"]}")
        println("🔢 Build number reset to 1")
    }
}

/**
 * Increment minor version, reset patch and build number
 */
abstract class IncrementMinorTask : VersionPropertyWriteTask() {
    @TaskAction
    fun increment() {
        val props = loadProperties()

        val currentMinor = props["app.version.minor"].toString().toInt()
        val newMinor = currentMinor + 1

        // Update minor version and reset patch and build number
        props["app.version.minor"] = newMinor.toString()
        props["app.version.patch"] = "0"
        props["app.version"] = "${props["app.version.major"]}.$newMinor.0"
        props["app.bundle.version"] = props["app.version"]
        props["app.build.date"] = SimpleDateFormat("yyyy-MM-dd").format(Date())
        props["app.build.number"] = "1"

        saveProperties(props, "Auto-incremented minor version")

        println("✅ Version incremented to ${props["app.version"]}")
        println("🔢 Build number reset to 1")
    }
}

/**
 * Increment major version, reset minor/patch and build number
 */
abstract class IncrementMajorTask : VersionPropertyWriteTask() {
    @TaskAction
    fun increment() {
        val props = loadProperties()

        val currentMajor = props["app.version.major"].toString().toInt()
        val newMajor = currentMajor + 1

        // Update major version and reset minor/patch and build number
        props["app.version.major"] = newMajor.toString()
        props["app.version.minor"] = "0"
        props["app.version.patch"] = "0"
        props["app.version"] = "$newMajor.0.0"
        props["app.bundle.version"] = props["app.version"]
        props["app.build.date"] = SimpleDateFormat("yyyy-MM-dd").format(Date())
        props["app.build.number"] = "1"

        saveProperties(props, "Auto-incremented major version")

        println("✅ Version incremented to ${props["app.version"]}")
        println("🔢 Build number reset to 1")
    }
}

/**
 * Increment build number only
 */
abstract class IncrementBuildNumberTask : VersionPropertyWriteTask() {
    @TaskAction
    fun increment() {
        val props = loadProperties()

        val currentBuildNumber = props["app.build.number"].toString().toInt()
        val newBuildNumber = currentBuildNumber + 1

        // Update build number only, keep version the same
        props["app.build.number"] = newBuildNumber.toString()
        props["app.build.date"] = SimpleDateFormat("yyyy-MM-dd").format(Date())

        saveProperties(props, "Auto-incremented build number")

        println("✅ Build number incremented to $newBuildNumber for version ${props["app.version"]}")
    }
}

/**
 * Auto-increment build number for package builds (silent version)
 */
abstract class AutoIncrementBuildNumberTask : VersionPropertyWriteTask() {
    @TaskAction
    fun increment() {
        val props = loadProperties()

        val currentBuildNumber = props["app.build.number"].toString().toInt()
        val newBuildNumber = currentBuildNumber + 1

        // Update build number only, keep version the same
        props["app.build.number"] = newBuildNumber.toString()
        props["app.build.date"] = SimpleDateFormat("yyyy-MM-dd").format(Date())

        saveProperties(props, "Auto-incremented build number for package build")

        println("🔢 Build number auto-incremented: $currentBuildNumber → $newBuildNumber")
    }
}

// ============================================================================
// Task Registration
// ============================================================================

val versionPropsFile = layout.projectDirectory.file("version.properties")

tasks.register<ShowVersionTask>("showVersion") {
    group = "versioning"
    description = "Display current version information"
    versionFile.set(versionPropsFile)
}

tasks.register<IncrementVersionTask>("incrementVersion") {
    group = "versioning"
    description = "Increment patch version and reset build number"
    inputFile.set(versionPropsFile)
    outputFile.set(versionPropsFile)
}

tasks.register<IncrementMinorTask>("incrementMinor") {
    group = "versioning"
    description = "Increment minor version and reset patch and build number"
    inputFile.set(versionPropsFile)
    outputFile.set(versionPropsFile)
}

tasks.register<IncrementMajorTask>("incrementMajor") {
    group = "versioning"
    description = "Increment major version and reset minor/patch and build number"
    inputFile.set(versionPropsFile)
    outputFile.set(versionPropsFile)
}

tasks.register<IncrementBuildNumberTask>("incrementBuildNumber") {
    group = "versioning"
    description = "Increment build number only"
    inputFile.set(versionPropsFile)
    outputFile.set(versionPropsFile)
}

tasks.register<AutoIncrementBuildNumberTask>("autoIncrementBuildNumber") {
    group = "versioning"
    description = "Auto-increment build number for package builds"
    inputFile.set(versionPropsFile)
    outputFile.set(versionPropsFile)
}
