package ai.rever.bosseditor.features

import ai.rever.bosseditor.core.EditorRange

/**
 * Types of hyperlinks that can appear in code.
 */
enum class HyperlinkType {
    /** URL (http://, https://, ftp://) */
    URL,

    /** File path reference */
    FILE_PATH,

    /** Import/require statement */
    IMPORT,

    /** Symbol reference (go to definition) */
    SYMBOL,

    /** Issue/ticket reference (e.g., #123, JIRA-456) */
    ISSUE
}

/**
 * Represents a clickable hyperlink in the editor.
 *
 * Hyperlinks are displayed with an underline and change cursor on hover.
 * Cmd+Click (Mac) or Ctrl+Click (Windows/Linux) activates them.
 *
 * @property range The range in the document where the hyperlink appears
 * @property target The target to navigate to (URL, file path, symbol name)
 * @property type The type of hyperlink
 * @property tooltip Optional tooltip to show on hover
 */
data class Hyperlink(
    val range: EditorRange,
    val target: String,
    val type: HyperlinkType,
    val tooltip: String? = null
) {
    /** The starting line of this hyperlink */
    val startLine: Int get() = range.start.line

    /** The ending line of this hyperlink */
    val endLine: Int get() = range.end.line

    companion object {
        /** Common URL patterns */
        private val URL_REGEX = Regex(
            """(?:https?|ftp)://[^\s<>"{}|\\^`\[\]]+""",
            RegexOption.IGNORE_CASE
        )

        /** File path patterns (Unix and Windows) */
        private val FILE_PATH_REGEX = Regex(
            """(?:[/\\][\w.-]+)+|[A-Za-z]:[/\\][\w./\\-]+"""
        )

        /** Issue reference patterns (GitHub #123, JIRA-456) */
        private val ISSUE_REGEX = Regex(
            """(?:#\d+|[A-Z]{2,10}-\d+)"""
        )

        /**
         * Extracts URLs from text.
         */
        fun extractUrls(text: String, lineOffset: Int = 0): List<Hyperlink> {
            return URL_REGEX.findAll(text).map { match ->
                Hyperlink(
                    range = EditorRange(
                        ai.rever.bosseditor.core.EditorPosition(lineOffset, match.range.first),
                        ai.rever.bosseditor.core.EditorPosition(lineOffset, match.range.last + 1)
                    ),
                    target = match.value,
                    type = HyperlinkType.URL,
                    tooltip = "Open ${match.value}"
                )
            }.toList()
        }

        /**
         * Extracts issue references from text.
         */
        fun extractIssueRefs(text: String, lineOffset: Int = 0): List<Hyperlink> {
            return ISSUE_REGEX.findAll(text).map { match ->
                Hyperlink(
                    range = EditorRange(
                        ai.rever.bosseditor.core.EditorPosition(lineOffset, match.range.first),
                        ai.rever.bosseditor.core.EditorPosition(lineOffset, match.range.last + 1)
                    ),
                    target = match.value,
                    type = HyperlinkType.ISSUE,
                    tooltip = "Open issue ${match.value}"
                )
            }.toList()
        }
    }
}

/**
 * Manages hyperlinks for the editor.
 * Provides efficient lookup of hyperlinks by line and position.
 */
class HyperlinkManager {
    private val hyperlinks = mutableListOf<Hyperlink>()
    private var hyperlinksByLine: Map<Int, List<Hyperlink>> = emptyMap()

    /**
     * Sets the hyperlinks, replacing any existing ones.
     */
    fun setHyperlinks(newHyperlinks: List<Hyperlink>) {
        hyperlinks.clear()
        hyperlinks.addAll(newHyperlinks)
        rebuildIndex()
    }

    /**
     * Adds a single hyperlink.
     */
    fun addHyperlink(hyperlink: Hyperlink) {
        hyperlinks.add(hyperlink)
        rebuildIndex()
    }

    /**
     * Removes all hyperlinks.
     */
    fun clear() {
        hyperlinks.clear()
        hyperlinksByLine = emptyMap()
    }

    /**
     * Gets all hyperlinks.
     */
    fun getAllHyperlinks(): List<Hyperlink> = hyperlinks.toList()

    /**
     * Gets hyperlinks for a specific line.
     */
    fun getHyperlinksForLine(line: Int): List<Hyperlink> {
        return hyperlinksByLine[line] ?: emptyList()
    }

    /**
     * Gets hyperlink at a specific position, if any.
     */
    fun getHyperlinkAtPosition(position: ai.rever.bosseditor.core.EditorPosition): Hyperlink? {
        return getHyperlinksForLine(position.line).find { hyperlink ->
            position.column >= hyperlink.range.start.column &&
            position.column < hyperlink.range.end.column
        }
    }

    private fun rebuildIndex() {
        val byLine = mutableMapOf<Int, MutableList<Hyperlink>>()
        for (hyperlink in hyperlinks) {
            for (line in hyperlink.startLine..hyperlink.endLine) {
                byLine.getOrPut(line) { mutableListOf() }.add(hyperlink)
            }
        }
        hyperlinksByLine = byLine
    }
}
