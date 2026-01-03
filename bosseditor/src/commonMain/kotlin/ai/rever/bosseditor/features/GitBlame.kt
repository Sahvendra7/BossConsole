package ai.rever.bosseditor.features

/**
 * Represents git blame information for a single line.
 *
 * Contains the commit hash, author, date, and summary for the last
 * modification of a line.
 *
 * @property commitHash The commit hash (short or full)
 * @property author The author name
 * @property authorEmail The author email (optional)
 * @property timestamp Unix timestamp of the commit
 * @property summary The commit message summary (first line)
 * @property line The line number this blame info applies to (0-indexed)
 */
data class BlameInfo(
    val commitHash: String,
    val author: String,
    val authorEmail: String? = null,
    val timestamp: Long,
    val summary: String,
    val line: Int
) {
    /** Whether this is an uncommitted change */
    val isUncommitted: Boolean
        get() = commitHash.startsWith("0000000")

    /** Age of the commit in days from now */
    fun ageInDays(nowTimestamp: Long = System.currentTimeMillis() / 1000): Long {
        return (nowTimestamp - timestamp) / (24 * 60 * 60)
    }

    /** Short commit hash (first 7 characters) */
    val shortHash: String
        get() = if (commitHash.length > 7) commitHash.substring(0, 7) else commitHash

    /** Formatted display text for the blame gutter */
    fun formatForGutter(maxAuthorLength: Int = 15): String {
        val truncatedAuthor = if (author.length > maxAuthorLength) {
            author.substring(0, maxAuthorLength - 2) + ".."
        } else {
            author.padEnd(maxAuthorLength)
        }
        return "$truncatedAuthor $shortHash"
    }

    companion object {
        /**
         * Creates a BlameInfo for uncommitted changes.
         */
        fun uncommitted(line: Int): BlameInfo = BlameInfo(
            commitHash = "00000000",
            author = "Not Committed Yet",
            timestamp = System.currentTimeMillis() / 1000,
            summary = "Uncommitted changes",
            line = line
        )
    }
}

/**
 * Represents blame information for an entire file.
 *
 * @property filePath The file path
 * @property lines Blame info for each line, indexed by line number
 */
data class FileBlameInfo(
    val filePath: String,
    val lines: Map<Int, BlameInfo>
) {
    /** Gets blame info for a specific line */
    fun getBlameForLine(line: Int): BlameInfo? = lines[line]

    /** Gets all unique commits in this file's blame */
    fun getUniqueCommits(): Set<String> = lines.values.map { it.commitHash }.toSet()

    /** Gets all unique authors */
    fun getUniqueAuthors(): Set<String> = lines.values.map { it.author }.toSet()
}

/**
 * Age categories for blame coloring.
 */
enum class BlameAge {
    /** Very recent (< 1 week) - bright/prominent color */
    VERY_RECENT,

    /** Recent (< 1 month) */
    RECENT,

    /** Moderate (< 3 months) */
    MODERATE,

    /** Old (< 1 year) */
    OLD,

    /** Very old (> 1 year) - faded color */
    VERY_OLD,

    /** Uncommitted changes */
    UNCOMMITTED;

    companion object {
        /**
         * Determines the age category based on days since commit.
         */
        fun fromDays(days: Long): BlameAge = when {
            days < 0 -> UNCOMMITTED
            days < 7 -> VERY_RECENT
            days < 30 -> RECENT
            days < 90 -> MODERATE
            days < 365 -> OLD
            else -> VERY_OLD
        }
    }
}

/**
 * Configuration for git blame display.
 *
 * @property enabled Whether blame annotations are shown
 * @property showAuthor Whether to show author name
 * @property showDate Whether to show commit date
 * @property showHash Whether to show commit hash
 * @property gutterWidth Width of the blame gutter in dp
 * @property colorByAge Whether to color-code by commit age
 */
data class BlameConfig(
    val enabled: Boolean = false,
    val showAuthor: Boolean = true,
    val showDate: Boolean = false,
    val showHash: Boolean = true,
    val gutterWidth: Float = 180f,
    val colorByAge: Boolean = true
)

/**
 * Manager for git blame state in the editor.
 */
class BlameManager {
    private var fileBlame: FileBlameInfo? = null
    private var isLoading: Boolean = false
    private var error: String? = null

    /**
     * Gets the current blame info, or null if not loaded.
     */
    fun getBlameInfo(): FileBlameInfo? = fileBlame

    /**
     * Gets blame for a specific line.
     */
    fun getBlameForLine(line: Int): BlameInfo? = fileBlame?.getBlameForLine(line)

    /**
     * Sets the blame info for the file.
     */
    fun setBlameInfo(blame: FileBlameInfo?) {
        fileBlame = blame
        isLoading = false
        error = null
    }

    /**
     * Sets loading state.
     */
    fun setLoading(loading: Boolean) {
        isLoading = loading
    }

    /**
     * Sets error state.
     */
    fun setError(errorMessage: String?) {
        error = errorMessage
        isLoading = false
    }

    /**
     * Returns true if blame is currently loading.
     */
    fun isLoading(): Boolean = isLoading

    /**
     * Returns any error message, or null.
     */
    fun getError(): String? = error

    /**
     * Returns true if blame data is available.
     */
    fun hasBlame(): Boolean = fileBlame != null

    /**
     * Clears blame data.
     */
    fun clear() {
        fileBlame = null
        isLoading = false
        error = null
    }
}
