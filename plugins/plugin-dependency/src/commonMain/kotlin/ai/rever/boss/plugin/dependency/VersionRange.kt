package ai.rever.boss.plugin.dependency

/**
 * Represents a semantic version.
 */
data class SemanticVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val prerelease: String? = null,
    val build: String? = null
) : Comparable<SemanticVersion> {

    companion object {
        /**
         * Parse a version string into a SemanticVersion.
         *
         * @param version Version string (e.g., "1.0.0", "2.1.3-beta", "1.0.0-alpha+build123")
         * @return Parsed version, or null if invalid
         */
        fun parse(version: String): SemanticVersion? {
            val trimmed = version.trim()

            // Extract build metadata
            val buildIndex = trimmed.indexOf('+')
            val (versionPart, build) = if (buildIndex >= 0) {
                trimmed.substring(0, buildIndex) to trimmed.substring(buildIndex + 1)
            } else {
                trimmed to null
            }

            // Extract prerelease
            val prereleaseIndex = versionPart.indexOf('-')
            val (corePart, prerelease) = if (prereleaseIndex >= 0) {
                versionPart.substring(0, prereleaseIndex) to versionPart.substring(prereleaseIndex + 1)
            } else {
                versionPart to null
            }

            // Parse core version
            val parts = corePart.split(".")
            if (parts.size !in 1..3) return null

            val major = parts.getOrNull(0)?.toIntOrNull() ?: return null
            val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0

            return SemanticVersion(major, minor, patch, prerelease, build)
        }
    }

    override fun compareTo(other: SemanticVersion): Int {
        // Compare major.minor.patch
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        if (patch != other.patch) return patch.compareTo(other.patch)

        // Prerelease versions have lower precedence
        return when {
            prerelease == null && other.prerelease == null -> 0
            prerelease == null -> 1  // No prerelease > prerelease
            other.prerelease == null -> -1
            else -> prerelease.compareTo(other.prerelease)
        }
    }

    override fun toString(): String {
        val sb = StringBuilder("$major.$minor.$patch")
        prerelease?.let { sb.append("-$it") }
        build?.let { sb.append("+$it") }
        return sb.toString()
    }
}

/**
 * Represents a version range constraint.
 *
 * Supported formats:
 * - "*" - any version
 * - "1.0.0" - exact version
 * - ">=1.0.0" - greater than or equal
 * - ">1.0.0" - greater than
 * - "<=1.0.0" - less than or equal
 * - "<1.0.0" - less than
 * - ">=1.0.0 <2.0.0" - range (AND)
 * - "^1.0.0" - compatible with (same major)
 * - "~1.0.0" - approximately (same major.minor)
 */
data class VersionRange(
    private val constraints: List<VersionConstraint>
) {
    companion object {
        /**
         * Parse a version range string.
         *
         * @param range Version range string
         * @return Parsed version range
         */
        fun parse(range: String): VersionRange {
            val trimmed = range.trim()

            // Any version
            if (trimmed == "*" || trimmed.isEmpty()) {
                return VersionRange(emptyList())
            }

            // Split by space for multiple constraints
            val parts = trimmed.split("\\s+".toRegex())
            val constraints = parts.map { parseConstraint(it) }

            return VersionRange(constraints)
        }

        private fun parseConstraint(constraint: String): VersionConstraint {
            val trimmed = constraint.trim()

            return when {
                trimmed.startsWith(">=") -> {
                    val version = SemanticVersion.parse(trimmed.substring(2))
                        ?: throw IllegalArgumentException("Invalid version: ${trimmed.substring(2)}")
                    VersionConstraint.GreaterOrEqual(version)
                }
                trimmed.startsWith(">") -> {
                    val version = SemanticVersion.parse(trimmed.substring(1))
                        ?: throw IllegalArgumentException("Invalid version: ${trimmed.substring(1)}")
                    VersionConstraint.Greater(version)
                }
                trimmed.startsWith("<=") -> {
                    val version = SemanticVersion.parse(trimmed.substring(2))
                        ?: throw IllegalArgumentException("Invalid version: ${trimmed.substring(2)}")
                    VersionConstraint.LessOrEqual(version)
                }
                trimmed.startsWith("<") -> {
                    val version = SemanticVersion.parse(trimmed.substring(1))
                        ?: throw IllegalArgumentException("Invalid version: ${trimmed.substring(1)}")
                    VersionConstraint.Less(version)
                }
                trimmed.startsWith("^") -> {
                    val version = SemanticVersion.parse(trimmed.substring(1))
                        ?: throw IllegalArgumentException("Invalid version: ${trimmed.substring(1)}")
                    // ^1.2.3 means >=1.2.3 <2.0.0
                    VersionConstraint.Compatible(version)
                }
                trimmed.startsWith("~") -> {
                    val version = SemanticVersion.parse(trimmed.substring(1))
                        ?: throw IllegalArgumentException("Invalid version: ${trimmed.substring(1)}")
                    // ~1.2.3 means >=1.2.3 <1.3.0
                    VersionConstraint.Approximate(version)
                }
                else -> {
                    val version = SemanticVersion.parse(trimmed)
                        ?: throw IllegalArgumentException("Invalid version: $trimmed")
                    VersionConstraint.Exact(version)
                }
            }
        }
    }

    /**
     * Check if a version satisfies this range.
     *
     * @param version Version to check
     * @return True if the version satisfies all constraints
     */
    fun satisfiedBy(version: String): Boolean {
        val semver = SemanticVersion.parse(version) ?: return false
        return satisfiedBy(semver)
    }

    /**
     * Check if a version satisfies this range.
     *
     * @param version Version to check
     * @return True if the version satisfies all constraints
     */
    fun satisfiedBy(version: SemanticVersion): Boolean {
        if (constraints.isEmpty()) return true
        return constraints.all { it.satisfiedBy(version) }
    }

    /**
     * Find the best matching version from a list.
     *
     * @param versions List of available versions
     * @return Best matching version, or null if none match
     */
    fun bestMatch(versions: List<String>): String? {
        return versions
            .mapNotNull { SemanticVersion.parse(it)?.let { v -> it to v } }
            .filter { (_, v) -> satisfiedBy(v) }
            .maxByOrNull { (_, v) -> v }
            ?.first
    }

    override fun toString(): String {
        if (constraints.isEmpty()) return "*"
        return constraints.joinToString(" ") { it.toString() }
    }
}

/**
 * Individual version constraint.
 */
sealed class VersionConstraint {
    abstract fun satisfiedBy(version: SemanticVersion): Boolean

    data class Exact(val version: SemanticVersion) : VersionConstraint() {
        override fun satisfiedBy(version: SemanticVersion) = version == this.version
        override fun toString() = version.toString()
    }

    data class Greater(val version: SemanticVersion) : VersionConstraint() {
        override fun satisfiedBy(version: SemanticVersion) = version > this.version
        override fun toString() = ">$version"
    }

    data class GreaterOrEqual(val version: SemanticVersion) : VersionConstraint() {
        override fun satisfiedBy(version: SemanticVersion) = version >= this.version
        override fun toString() = ">=$version"
    }

    data class Less(val version: SemanticVersion) : VersionConstraint() {
        override fun satisfiedBy(version: SemanticVersion) = version < this.version
        override fun toString() = "<$version"
    }

    data class LessOrEqual(val version: SemanticVersion) : VersionConstraint() {
        override fun satisfiedBy(version: SemanticVersion) = version <= this.version
        override fun toString() = "<=$version"
    }

    data class Compatible(val version: SemanticVersion) : VersionConstraint() {
        // ^1.2.3 means >=1.2.3 <2.0.0
        override fun satisfiedBy(version: SemanticVersion): Boolean {
            return version >= this.version && version.major == this.version.major
        }
        override fun toString() = "^$version"
    }

    data class Approximate(val version: SemanticVersion) : VersionConstraint() {
        // ~1.2.3 means >=1.2.3 <1.3.0
        override fun satisfiedBy(version: SemanticVersion): Boolean {
            return version >= this.version &&
                   version.major == this.version.major &&
                   version.minor == this.version.minor
        }
        override fun toString() = "~$version"
    }
}
