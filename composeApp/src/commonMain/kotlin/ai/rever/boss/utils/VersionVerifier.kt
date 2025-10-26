package ai.rever.boss.utils

import kotlin.jvm.JvmStatic

/**
 * Runtime version verification to detect mismatches between version.properties
 * and VersionConstants.kt (which is auto-generated at build time).
 *
 * This addresses Issue #111 where stale VersionConstants caused wrong versions
 * to be embedded in release artifacts.
 *
 * Usage: Call verifyVersionConsistency() early in application startup
 */
object VersionVerifier {

    /**
     * Verify that the runtime version matches expected version from properties.
     *
     * This is a safety check to detect if VersionConstants.kt was not regenerated
     * before the build, which was the root cause of Issue #111.
     *
     * Logs a warning if mismatch is detected. Does not throw exception to avoid
     * breaking app startup, but logs clearly for debugging.
     */
    @JvmStatic
    fun verifyVersionConsistency() {
        try {
            // Get runtime version from VersionConstants
            val runtimeVersion = Version.CURRENT

            // Try to load version from properties file if available
            // Note: In production builds, version.properties may not be embedded
            val propsVersion = loadVersionFromProperties()

            if (propsVersion != null) {
                if (runtimeVersion != propsVersion) {
                    // VERSION MISMATCH DETECTED - This is the Issue #111 scenario!
                    println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    println("⚠️  VERSION MISMATCH DETECTED!")
                    println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    println("   Expected version (version.properties): $propsVersion")
                    println("   Actual runtime version (VersionConstants): $runtimeVersion")
                    println("")
                    println("   This mismatch indicates VersionConstants.kt was not")
                    println("   regenerated before the build. This is the root cause")
                    println("   of Issue #111 where updates installed wrong versions.")
                    println("")
                    println("   To fix:")
                    println("   1. Run: ./gradlew generateVersionConstants")
                    println("   2. Rebuild the application")
                    println("   3. Or use: ./gradlew clean build")
                    println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

                    // TODO: Consider adding analytics/crash reporting here
                    // to track how often this occurs in production
                } else {
                    println("✅ Version verification passed: $runtimeVersion")
                }
            } else {
                // Production build without embedded version.properties - this is normal
                println("ℹ️  Version verification skipped (production build): $runtimeVersion")
            }
        } catch (e: Exception) {
            // Don't crash the app if verification fails
            println("⚠️ Version verification failed: ${e.message}")
        }
    }

    /**
     * Attempt to load version from embedded version.properties file.
     * Returns null if file is not available (normal for production builds).
     */
    private fun loadVersionFromProperties(): Version? {
        return try {
            // Try to load version.properties from resources
            // This may not be available in production builds
            val propsContent = object {}.javaClass.getResourceAsStream("/version.properties")
                ?.bufferedReader()
                ?.use { it.readText() }

            if (propsContent != null) {
                val lines = propsContent.lines()
                val major = lines.find { it.startsWith("app.version.major=") }
                    ?.substringAfter("=")?.toIntOrNull()
                val minor = lines.find { it.startsWith("app.version.minor=") }
                    ?.substringAfter("=")?.toIntOrNull()
                val patch = lines.find { it.startsWith("app.version.patch=") }
                    ?.substringAfter("=")?.toIntOrNull()

                if (major != null && minor != null && patch != null) {
                    Version(major, minor, patch)
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            // version.properties not available - normal for production
            null
        }
    }

    /**
     * Get current runtime version for display purposes.
     */
    @JvmStatic
    fun getCurrentVersion(): Version = Version.CURRENT

    /**
     * Get current version as string (e.g., "8.12.19").
     */
    @JvmStatic
    fun getCurrentVersionString(): String = Version.CURRENT.toString()
}
