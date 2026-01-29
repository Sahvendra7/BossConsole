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
            patch = VersionConstants.PATCH,
            preRelease = VersionConstants.PRERELEASE
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
            else -> comparePreRelease(this.preRelease!!, other.preRelease!!)
        }
    }

    /**
     * Compares two prerelease strings according to semantic versioning rules.
     * Ordering: alpha < beta < rc, with numerical comparison within each type.
     * Examples: alpha.1 < alpha.2 < alpha.10 < beta.1 < beta.2 < rc.1 < rc.2
     */
    private fun comparePreRelease(a: String, b: String): Int {
        val aParsed = parsePreRelease(a)
        val bParsed = parsePreRelease(b)

        // Compare prerelease types first (alpha < beta < rc < unknown)
        val typeComparison = aParsed.first.compareTo(bParsed.first)
        if (typeComparison != 0) return typeComparison

        // Same type, compare numerical parts
        return aParsed.second.compareTo(bParsed.second)
    }

    /**
     * Parses a prerelease string into a comparable pair of (type ordinal, number).
     * Handles formats like "alpha.1", "beta.2", "rc.10"
     */
    private fun parsePreRelease(preRelease: String): Pair<Int, Int> {
        val prereleaseTypeOrder = mapOf(
            "alpha" to 0,
            "beta" to 1,
            "rc" to 2
        )

        val parts = preRelease.split(".", limit = 2)
        val type = parts.getOrNull(0)?.lowercase() ?: ""
        val number = parts.getOrNull(1)?.toIntOrNull() ?: 0

        // Unknown types sort after known types
        val typeOrdinal = prereleaseTypeOrder[type] ?: 99

        return Pair(typeOrdinal, number)
    }
    
    override fun toString(): String {
        return if (preRelease != null) {
            "$major.$minor.$patch-$preRelease"
        } else {
            "$major.$minor.$patch"
        }
    }
    
    fun isNewerThan(other: Version): Boolean = this > other

}
