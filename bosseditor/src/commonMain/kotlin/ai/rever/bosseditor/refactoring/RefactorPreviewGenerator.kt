package ai.rever.bosseditor.refactoring

import ai.rever.bosseditor.lsp.protocol.Position
import ai.rever.bosseditor.lsp.protocol.Range
import ai.rever.bosseditor.lsp.protocol.TextEdit
import ai.rever.bosseditor.lsp.protocol.WorkspaceEdit

/**
 * Generates preview content for refactoring changes.
 *
 * Creates unified diff format and before/after comparisons
 * for displaying changes to the user before applying them.
 */
object RefactorPreviewGenerator {

    /**
     * Generates a complete preview of all changes in a workspace edit.
     *
     * @param edit The workspace edit
     * @param fileContentProvider Function to get file content by URI
     * @return List of file changes with preview text
     */
    suspend fun generatePreview(
        edit: WorkspaceEdit,
        fileContentProvider: suspend (String) -> String?
    ): List<FileChange> {
        val changes = edit.changes ?: return emptyList()

        return changes.mapNotNull { (uri, edits) ->
            val content = fileContentProvider(uri) ?: return@mapNotNull null
            val filePath = WorkspaceEditApplier.uriToFilePath(uri)

            val previewBefore = generateBeforePreview(content, edits)
            val previewAfter = generateAfterPreview(content, edits)

            FileChange(
                uri = uri,
                filePath = filePath,
                edits = edits,
                previewBefore = previewBefore,
                previewAfter = previewAfter
            )
        }
    }

    /**
     * Generates a unified diff format preview.
     *
     * @param originalContent The original file content
     * @param edits The edits to apply
     * @return Unified diff string
     */
    fun generateUnifiedDiff(originalContent: String, edits: List<TextEdit>): String {
        val lines = originalContent.lines()
        val affectedLines = getAffectedLines(edits)

        return buildString {
            for (lineIndex in affectedLines.sorted()) {
                if (lineIndex < 0 || lineIndex >= lines.size) continue

                val lineEdits = edits.filter { it.range.start.line == lineIndex }

                // Show original line
                appendLine("- ${lines[lineIndex]}")

                // Show modified line
                val modifiedLine = applyEditsToLine(lines[lineIndex], lineIndex, lineEdits)
                appendLine("+ $modifiedLine")
            }
        }
    }

    /**
     * Generates a before preview showing lines that will change.
     */
    fun generateBeforePreview(content: String, edits: List<TextEdit>): String {
        val lines = content.lines()
        val affectedLines = getAffectedLines(edits)
        val contextLines = 2

        return buildString {
            val expandedLines = affectedLines.flatMap { line ->
                (line - contextLines..line + contextLines).toList()
            }.filter { it in lines.indices }.distinct().sorted()

            var lastLine = -10
            for (lineIndex in expandedLines) {
                // Add separator if there's a gap
                if (lineIndex > lastLine + 1 && lastLine >= 0) {
                    appendLine("...")
                }

                val prefix = if (lineIndex in affectedLines) "-" else " "
                appendLine("$prefix ${lineIndex + 1}: ${lines[lineIndex]}")
                lastLine = lineIndex
            }
        }
    }

    /**
     * Generates an after preview showing the result of changes.
     */
    fun generateAfterPreview(content: String, edits: List<TextEdit>): String {
        // Apply edits to get the new content
        val newContent = applyEditsToContent(content, edits)
        val newLines = newContent.lines()

        // Calculate which lines are new/modified
        val affectedLines = getAffectedLinesAfterEdit(edits)
        val contextLines = 2

        return buildString {
            val expandedLines = affectedLines.flatMap { line ->
                (line - contextLines..line + contextLines).toList()
            }.filter { it in newLines.indices }.distinct().sorted()

            var lastLine = -10
            for (lineIndex in expandedLines) {
                if (lineIndex > lastLine + 1 && lastLine >= 0) {
                    appendLine("...")
                }

                val prefix = if (lineIndex in affectedLines) "+" else " "
                appendLine("$prefix ${lineIndex + 1}: ${newLines[lineIndex]}")
                lastLine = lineIndex
            }
        }
    }

    /**
     * Gets the set of line numbers affected by edits.
     */
    private fun getAffectedLines(edits: List<TextEdit>): Set<Int> {
        return edits.flatMap { edit ->
            (edit.range.start.line..edit.range.end.line).toList()
        }.toSet()
    }

    /**
     * Gets the set of line numbers that will be affected after applying edits.
     */
    private fun getAffectedLinesAfterEdit(edits: List<TextEdit>): Set<Int> {
        val affectedLines = mutableSetOf<Int>()
        var lineDelta = 0

        for (edit in edits.sortedBy { it.range.start.line }) {
            val startLine = edit.range.start.line + lineDelta
            val newLines = edit.newText.count { it == '\n' }
            val oldLines = edit.range.end.line - edit.range.start.line

            // The insertion point and any new lines
            for (i in 0..newLines) {
                affectedLines.add(startLine + i)
            }

            lineDelta += newLines - oldLines
        }

        return affectedLines
    }

    /**
     * Applies edits to a single line.
     */
    private fun applyEditsToLine(line: String, lineIndex: Int, edits: List<TextEdit>): String {
        var result = line
        var charDelta = 0

        for (edit in edits.sortedBy { it.range.start.character }) {
            if (edit.range.start.line != lineIndex) continue

            val startChar = edit.range.start.character + charDelta
            val endChar = if (edit.range.end.line == lineIndex) {
                edit.range.end.character + charDelta
            } else {
                result.length
            }

            // Apply the edit
            result = result.substring(0, startChar.coerceIn(0, result.length)) +
                edit.newText.lines().first() +
                result.substring(endChar.coerceIn(0, result.length))

            charDelta += edit.newText.length - (endChar - startChar)
        }

        return result
    }

    /**
     * Applies edits to content.
     */
    private fun applyEditsToContent(content: String, edits: List<TextEdit>): String {
        val result = StringBuilder(content)

        // Sort edits in reverse order to preserve offsets
        val sortedEdits = edits.sortedWith(
            compareByDescending<TextEdit> { it.range.start.line }
                .thenByDescending { it.range.start.character }
        )

        for (edit in sortedEdits) {
            val startOffset = positionToOffset(content, edit.range.start)
            val endOffset = positionToOffset(content, edit.range.end)

            result.replace(startOffset, endOffset, edit.newText)
        }

        return result.toString()
    }

    /**
     * Converts a Position to character offset.
     */
    private fun positionToOffset(content: String, position: Position): Int {
        val lines = content.lines()
        var offset = 0
        for (i in 0 until position.line.coerceAtMost(lines.size)) {
            offset += lines[i].length + 1
        }
        if (position.line < lines.size) {
            offset += position.character.coerceAtMost(lines[position.line].length)
        }
        return offset.coerceAtMost(content.length)
    }

    /**
     * Generates a summary of changes.
     *
     * @param edit The workspace edit
     * @return Human-readable summary
     */
    fun generateSummary(edit: WorkspaceEdit): String {
        val changes = edit.changes ?: return "No changes"

        val fileCount = changes.size
        val editCount = changes.values.sumOf { it.size }

        return buildString {
            append("$editCount change")
            if (editCount != 1) append("s")
            append(" in $fileCount file")
            if (fileCount != 1) append("s")
        }
    }

    /**
     * Groups changes by file for display.
     *
     * @param edit The workspace edit
     * @return Map of file path to list of change descriptions
     */
    fun groupChangesByFile(edit: WorkspaceEdit): Map<String, List<ChangeDescription>> {
        val changes = edit.changes ?: return emptyMap()

        return changes.mapKeys { (uri, _) ->
            WorkspaceEditApplier.uriToFilePath(uri)
        }.mapValues { (_, edits) ->
            edits.map { edit ->
                ChangeDescription(
                    line = edit.range.start.line + 1,
                    description = describeEdit(edit)
                )
            }
        }
    }

    /**
     * Describes a single edit.
     */
    private fun describeEdit(edit: TextEdit): String {
        val isInsertion = edit.range.start == edit.range.end
        val isDeletion = edit.newText.isEmpty()

        return when {
            isInsertion -> "Insert \"${edit.newText.take(30)}${if (edit.newText.length > 30) "..." else ""}\""
            isDeletion -> "Delete text"
            else -> "Replace with \"${edit.newText.take(30)}${if (edit.newText.length > 30) "..." else ""}\""
        }
    }

    /**
     * Description of a single change.
     */
    data class ChangeDescription(
        val line: Int,
        val description: String
    )
}
