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
// Version Pattern Constants - Shared regex patterns for validation
// ============================================================================

/**
 * Pattern for validating prerelease suffix format (e.g., "beta.1", "rc.2")
 * Does not capture groups - use for validation only
 */
val PRERELEASE_SUFFIX_VALIDATION_PATTERN = Regex("^(alpha|beta|rc)\\.[1-9]\\d*$")

/**
 * Pattern for parsing prerelease suffix with capture groups
 * Group 1: prerelease type (alpha, beta, rc)
 * Group 2: prerelease number
 */
val PRERELEASE_SUFFIX_PARSE_PATTERN = Regex("^(alpha|beta|rc)\\.([1-9]\\d*)$")

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
val prereleaseSuffix = versionProps["app.prerelease.suffix"]?.toString()?.takeIf { it.isNotBlank() }

// Construct version strings
val appVersion = if (prereleaseSuffix != null) {
    "$versionMajor.$versionMinor.$versionPatch-$prereleaseSuffix"
} else {
    "$versionMajor.$versionMinor.$versionPatch"
}
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
    set("prereleaseSuffix", prereleaseSuffix)
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
 * Includes validation for clean git state to prevent transaction safety issues
 */
abstract class VersionPropertyWriteTask : DefaultTask() {
    @get:InputFile
    abstract val inputFile: RegularFileProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:org.gradle.api.tasks.Input
    @get:org.gradle.api.tasks.Optional
    abstract val skipGitCheck: org.gradle.api.provider.Property<Boolean>

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

    /**
     * Validates that version.properties has no uncommitted changes.
     * This prevents accidental overwrites during concurrent operations.
     * @throws GradleException if version.properties has uncommitted changes
     */
    protected fun validateGitState() {
        // Skip check if explicitly disabled (e.g., in CI where git state is controlled)
        if (skipGitCheck.getOrElse(false)) {
            return
        }

        try {
            val process = ProcessBuilder("git", "diff", "--quiet", "version.properties")
                .directory(project.rootDir)
                .start()
            val exitCode = process.waitFor()

            if (exitCode != 0) {
                // Check if there are staged changes too
                val stagedProcess = ProcessBuilder("git", "diff", "--cached", "--quiet", "version.properties")
                    .directory(project.rootDir)
                    .start()
                val stagedExitCode = stagedProcess.waitFor()

                val message = buildString {
                    append("⚠️ version.properties has uncommitted changes.\n")
                    append("   This could indicate a concurrent modification or unsaved work.\n")
                    append("   Options:\n")
                    append("   1. Commit or stash your changes first\n")
                    append("   2. Run with -PskipGitCheck=true to bypass this check (CI only)\n")
                    append("   3. Discard changes with: git checkout version.properties")
                }
                throw GradleException(message)
            }
        } catch (e: Exception) {
            when (e) {
                is GradleException -> throw e
                else -> {
                    // Git not available or not a git repo - skip check
                    logger.warn("⚠️ Could not verify git state: ${e.message}")
                }
            }
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
        val prerelease = props["app.prerelease.suffix"]?.toString()?.takeIf { it.isNotBlank() }
        val baseVersion = "$major.$minor.$patch"
        val av = if (prerelease != null) "$baseVersion-$prerelease" else baseVersion
        val bv = props["app.bundle.version"]
        val bn = props["app.build.number"]
        val channel = props["app.release.channel"] ?: "stable"
        val jn = "BOSS-$av-all.jar"
        val dn = "BOSS-$av.dmg"
        val mn = "BOSS-$av.msi"

        val nextBuildNumber = bn.toString().toInt() + 1
        val prereleaseInfo = if (prerelease != null) """
║ Prerelease Suffix:   $prerelease
║ Release Channel:     $channel""" else ""

        println("""
╔════════════════════════════════════════╗
║           BOSS Version Info            ║
╠════════════════════════════════════════╣
║ Application Version: $av
║ Bundle Version:      $bv
║ Build Number:        $bn             $prereleaseInfo
║ JAR Name:            $jn
║ DMG Name:            $dn
║ MSI Name:            $mn
╚════════════════════════════════════════╝

💡 Version Management Commands:
   ./gradlew incrementBuildNumber  - Increment build number ($bn → $nextBuildNumber)
   ./gradlew incrementVersion      - Increment patch version and reset build number
   ./gradlew incrementMinor        - Increment minor version and reset build number
   ./gradlew incrementMajor        - Increment major version and reset build number

🧪 Prerelease Commands:
   ./gradlew setPrereleaseSuffix -Psuffix=beta.1  - Set prerelease suffix
   ./gradlew incrementPrerelease                   - Increment prerelease number
   ./gradlew clearPrereleaseSuffix                 - Clear suffix (promote to stable)
        """.trimIndent())
    }
}

/**
 * Increment patch version and reset build number
 */
abstract class IncrementVersionTask : VersionPropertyWriteTask() {
    @TaskAction
    fun increment() {
        validateGitState()
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
        validateGitState()
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
        validateGitState()
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
        validateGitState()
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
        validateGitState()
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

/**
 * Set prerelease suffix (e.g., alpha.1, beta.2, rc.1)
 * Updates app.version, app.bundle.version, and app.release.channel accordingly
 */
abstract class SetPrereleaseSuffixTask : VersionPropertyWriteTask() {
    @get:org.gradle.api.tasks.Input
    @get:org.gradle.api.tasks.Optional
    abstract val suffix: org.gradle.api.provider.Property<String>

    @TaskAction
    fun setSuffix() {
        validateGitState()

        val suffixValue = suffix.orNull
            ?: throw GradleException("Please provide a suffix using -Psuffix=<value> (e.g., -Psuffix=beta.1)")

        // Validate suffix format: alpha.N, beta.N, or rc.N where N is a positive integer
        if (!PRERELEASE_SUFFIX_VALIDATION_PATTERN.matches(suffixValue)) {
            throw GradleException(
                "Invalid prerelease suffix format: '$suffixValue'\n" +
                "Expected format: alpha.N, beta.N, or rc.N where N is a positive integer\n" +
                "Examples: alpha.1, beta.2, rc.1"
            )
        }

        val props = loadProperties()

        val major = props["app.version.major"]
        val minor = props["app.version.minor"]
        val patch = props["app.version.patch"]
        val baseVersion = "$major.$minor.$patch"
        val fullVersion = "$baseVersion-$suffixValue"

        // Determine release channel from suffix type
        val channel = suffixValue.substringBefore(".")

        // Update properties
        props["app.prerelease.suffix"] = suffixValue
        props["app.version"] = fullVersion
        props["app.bundle.version"] = fullVersion
        props["app.release.channel"] = channel
        props["app.build.date"] = SimpleDateFormat("yyyy-MM-dd").format(Date())

        saveProperties(props, "Set prerelease suffix to $suffixValue")

        println("🧪 Prerelease suffix set: $suffixValue")
        println("📦 Version is now: $fullVersion")
        println("📢 Release channel: $channel")
    }
}

/**
 * Clear prerelease suffix (promotes to stable release)
 * Resets app.version, app.bundle.version, and app.release.channel to stable
 */
abstract class ClearPrereleaseSuffixTask : VersionPropertyWriteTask() {
    @TaskAction
    fun clearSuffix() {
        validateGitState()
        val props = loadProperties()

        val currentSuffix = props["app.prerelease.suffix"]?.toString()?.takeIf { it.isNotBlank() }
        if (currentSuffix == null) {
            println("ℹ️ No prerelease suffix to clear - already a stable version")
            return
        }

        val major = props["app.version.major"]
        val minor = props["app.version.minor"]
        val patch = props["app.version.patch"]
        val stableVersion = "$major.$minor.$patch"

        // Update properties
        props["app.prerelease.suffix"] = ""
        props["app.version"] = stableVersion
        props["app.bundle.version"] = stableVersion
        props["app.release.channel"] = "stable"
        props["app.build.date"] = SimpleDateFormat("yyyy-MM-dd").format(Date())

        saveProperties(props, "Cleared prerelease suffix - promoted to stable")

        println("🎉 Promoted to stable release!")
        println("📦 Version is now: $stableVersion")
        println("📢 Release channel: stable")
    }
}

/**
 * Increment prerelease number (e.g., beta.1 → beta.2)
 */
abstract class IncrementPrereleaseTask : VersionPropertyWriteTask() {
    @TaskAction
    fun incrementPrerelease() {
        validateGitState()
        val props = loadProperties()

        val currentSuffix = props["app.prerelease.suffix"]?.toString()?.takeIf { it.isNotBlank() }
            ?: throw GradleException(
                "No prerelease suffix to increment.\n" +
                "First set a prerelease suffix using: ./gradlew setPrereleaseSuffix -Psuffix=beta.1"
            )

        // Parse current suffix using shared pattern
        val match = PRERELEASE_SUFFIX_PARSE_PATTERN.matchEntire(currentSuffix)
            ?: throw GradleException("Invalid existing prerelease suffix format: '$currentSuffix'")

        val type = match.groupValues[1]
        val currentNumber = match.groupValues[2].toInt()
        val newNumber = currentNumber + 1
        val newSuffix = "$type.$newNumber"

        val major = props["app.version.major"]
        val minor = props["app.version.minor"]
        val patch = props["app.version.patch"]
        val baseVersion = "$major.$minor.$patch"
        val fullVersion = "$baseVersion-$newSuffix"

        // Update properties
        props["app.prerelease.suffix"] = newSuffix
        props["app.version"] = fullVersion
        props["app.bundle.version"] = fullVersion
        props["app.build.date"] = SimpleDateFormat("yyyy-MM-dd").format(Date())

        saveProperties(props, "Incremented prerelease number")

        println("🔢 Prerelease incremented: $currentSuffix → $newSuffix")
        println("📦 Version is now: $fullVersion")
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
    skipGitCheck.set(providers.gradleProperty("skipGitCheck").map { it.toBoolean() }.orElse(false))
}

tasks.register<IncrementMinorTask>("incrementMinor") {
    group = "versioning"
    description = "Increment minor version and reset patch and build number"
    inputFile.set(versionPropsFile)
    outputFile.set(versionPropsFile)
    skipGitCheck.set(providers.gradleProperty("skipGitCheck").map { it.toBoolean() }.orElse(false))
}

tasks.register<IncrementMajorTask>("incrementMajor") {
    group = "versioning"
    description = "Increment major version and reset minor/patch and build number"
    inputFile.set(versionPropsFile)
    outputFile.set(versionPropsFile)
    skipGitCheck.set(providers.gradleProperty("skipGitCheck").map { it.toBoolean() }.orElse(false))
}

tasks.register<IncrementBuildNumberTask>("incrementBuildNumber") {
    group = "versioning"
    description = "Increment build number only"
    inputFile.set(versionPropsFile)
    outputFile.set(versionPropsFile)
    skipGitCheck.set(providers.gradleProperty("skipGitCheck").map { it.toBoolean() }.orElse(false))
}

tasks.register<AutoIncrementBuildNumberTask>("autoIncrementBuildNumber") {
    group = "versioning"
    description = "Auto-increment build number for package builds"
    inputFile.set(versionPropsFile)
    outputFile.set(versionPropsFile)
    skipGitCheck.set(providers.gradleProperty("skipGitCheck").map { it.toBoolean() }.orElse(false))
}

tasks.register<SetPrereleaseSuffixTask>("setPrereleaseSuffix") {
    group = "versioning"
    description = "Set prerelease suffix (e.g., -Psuffix=beta.1)"
    inputFile.set(versionPropsFile)
    outputFile.set(versionPropsFile)
    suffix.set(providers.gradleProperty("suffix"))
    skipGitCheck.set(providers.gradleProperty("skipGitCheck").map { it.toBoolean() }.orElse(false))
}

tasks.register<ClearPrereleaseSuffixTask>("clearPrereleaseSuffix") {
    group = "versioning"
    description = "Clear prerelease suffix (promote to stable)"
    inputFile.set(versionPropsFile)
    outputFile.set(versionPropsFile)
    skipGitCheck.set(providers.gradleProperty("skipGitCheck").map { it.toBoolean() }.orElse(false))
}

tasks.register<IncrementPrereleaseTask>("incrementPrerelease") {
    group = "versioning"
    description = "Increment prerelease number (e.g., beta.1 → beta.2)"
    inputFile.set(versionPropsFile)
    outputFile.set(versionPropsFile)
    skipGitCheck.set(providers.gradleProperty("skipGitCheck").map { it.toBoolean() }.orElse(false))
}
