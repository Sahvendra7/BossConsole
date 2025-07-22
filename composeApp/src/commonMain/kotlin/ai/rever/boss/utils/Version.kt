package ai.rever.boss.utils

import kotlinx.serialization.Serializable

/**
 * Version management and comparison utilities for BOSS application
 */
@Serializable
data class Version(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: String? = null
) : Comparable<Version> {
    
    companion object {
        // Current application version - automatically loaded from version.properties
        val CURRENT = Version(
            major = VersionConstants.MAJOR,
            minor = VersionConstants.MINOR,
            patch = VersionConstants.PATCH
        )
        
        fun parse(versionString: String): Version? {
            return try {
                val cleanVersion = versionString.removePrefix("v").trim()
                val parts = cleanVersion.split("-", limit = 2)
                val versionPart = parts[0]
                val preRelease = parts.getOrNull(1)
                
                val versionNumbers = versionPart.split(".")
                if (versionNumbers.size >= 3) {
                    Version(
                        major = versionNumbers[0].toInt(),
                        minor = versionNumbers[1].toInt(),
                        patch = versionNumbers[2].toInt(),
                        preRelease = preRelease
                    )
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }
    
    override fun compareTo(other: Version): Int {
        // Compare major.minor.patch
        val result = compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })
        if (result != 0) return result
        
        // Handle pre-release versions (pre-release < stable)
        return when {
            this.preRelease == null && other.preRelease == null -> 0
            this.preRelease == null && other.preRelease != null -> 1  // stable > prerelease
            this.preRelease != null && other.preRelease == null -> -1 // prerelease < stable
            else -> this.preRelease!!.compareTo(other.preRelease!!) // compare prerelease strings
        }
    }
    
    override fun toString(): String {
        return if (preRelease != null) {
            "$major.$minor.$patch-$preRelease"
        } else {
            "$major.$minor.$patch"
        }
    }
    
    fun isNewerThan(other: Version): Boolean = this > other
    
    fun isOlderThan(other: Version): Boolean = this < other
    
    fun isSameAs(other: Version): Boolean = this == other
}