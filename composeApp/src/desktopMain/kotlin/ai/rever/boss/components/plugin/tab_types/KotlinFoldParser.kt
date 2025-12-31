package ai.rever.boss.components.plugin.tab_types

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rsyntaxtextarea.Token
import org.fife.ui.rsyntaxtextarea.folding.Fold
import org.fife.ui.rsyntaxtextarea.folding.FoldParser
import org.fife.ui.rsyntaxtextarea.folding.FoldType
import javax.swing.text.BadLocationException

/**
 * Custom fold parser for Kotlin that handles:
 * - Import statement folding (consecutive imports fold together)
 * - Curly brace folding (classes, functions, etc.)
 * - Multi-line comment folding
 */
class KotlinFoldParser : FoldParser {

    override fun getFolds(textArea: RSyntaxTextArea): List<Fold> {
        val folds = mutableListOf<Fold>()

        try {
            val doc = textArea.document
            val text = doc.getText(0, doc.length)
            val lines = text.lines()

            // Track import region
            var importStartLine = -1
            var importEndLine = -1
            var lastImportLine = -1

            // Track brace-based folds
            val braceStack = mutableListOf<BraceInfo>()

            // Track multi-line comments
            var commentStartLine = -1
            var inBlockComment = false

            for ((lineIndex, line) in lines.withIndex()) {
                val trimmedLine = line.trim()

                // Handle import folding
                if (trimmedLine.startsWith("import ")) {
                    if (importStartLine == -1) {
                        importStartLine = lineIndex
                    }
                    lastImportLine = lineIndex
                    importEndLine = lineIndex
                } else if (importStartLine != -1 && trimmedLine.isNotEmpty() && !trimmedLine.startsWith("//")) {
                    // End of import block (non-empty, non-comment line that's not an import)
                    if (importEndLine > importStartLine) {
                        // Create fold for imports (at least 2 lines)
                        val fold = createFold(textArea, FoldType.IMPORTS, importStartLine, importEndLine)
                        if (fold != null) {
                            folds.add(fold)
                        }
                    }
                    importStartLine = -1
                    importEndLine = -1
                }

                // Handle block comment folding
                if (!inBlockComment && (trimmedLine.startsWith("/*") || trimmedLine.startsWith("/**"))) {
                    inBlockComment = true
                    commentStartLine = lineIndex
                }
                if (inBlockComment && trimmedLine.endsWith("*/")) {
                    if (lineIndex > commentStartLine) {
                        val fold = createFold(textArea, FoldType.COMMENT, commentStartLine, lineIndex)
                        if (fold != null) {
                            folds.add(fold)
                        }
                    }
                    inBlockComment = false
                    commentStartLine = -1
                }

                // Handle curly brace folding
                var charIndex = 0
                while (charIndex < line.length) {
                    val char = line[charIndex]

                    // Skip string literals
                    if (char == '"') {
                        charIndex++
                        // Handle triple-quoted strings
                        if (charIndex + 1 < line.length && line[charIndex] == '"' && line[charIndex + 1] == '"') {
                            charIndex += 2
                            while (charIndex + 2 < line.length) {
                                if (line[charIndex] == '"' && line[charIndex + 1] == '"' && line[charIndex + 2] == '"') {
                                    charIndex += 3
                                    break
                                }
                                charIndex++
                            }
                        } else {
                            // Regular string
                            while (charIndex < line.length) {
                                if (line[charIndex] == '"' && (charIndex == 0 || line[charIndex - 1] != '\\')) {
                                    charIndex++
                                    break
                                }
                                charIndex++
                            }
                        }
                        continue
                    }

                    // Skip single-quoted chars
                    if (char == '\'') {
                        charIndex++
                        while (charIndex < line.length) {
                            if (line[charIndex] == '\'' && line[charIndex - 1] != '\\') {
                                charIndex++
                                break
                            }
                            charIndex++
                        }
                        continue
                    }

                    // Skip line comments
                    if (char == '/' && charIndex + 1 < line.length && line[charIndex + 1] == '/') {
                        break // Rest of line is comment
                    }

                    // Track braces
                    if (char == '{') {
                        braceStack.add(BraceInfo(lineIndex, charIndex))
                    } else if (char == '}' && braceStack.isNotEmpty()) {
                        val openBrace = braceStack.removeAt(braceStack.lastIndex)
                        if (lineIndex > openBrace.line) {
                            val fold = createFold(textArea, FoldType.CODE, openBrace.line, lineIndex)
                            if (fold != null) {
                                folds.add(fold)
                            }
                        }
                    }

                    charIndex++
                }
            }

            // Handle imports at end of file
            if (importStartLine != -1 && importEndLine > importStartLine) {
                val fold = createFold(textArea, FoldType.IMPORTS, importStartLine, importEndLine)
                if (fold != null) {
                    folds.add(fold)
                }
            }

        } catch (e: BadLocationException) {
            // Ignore - document changed during parsing
        } catch (e: Exception) {
            // Ignore parsing errors
        }

        return folds
    }

    private fun createFold(textArea: RSyntaxTextArea, type: Int, startLine: Int, endLine: Int): Fold? {
        return try {
            val startOffset = textArea.getLineStartOffset(startLine)
            val endOffset = textArea.getLineEndOffset(endLine)

            if (startOffset < endOffset) {
                Fold(type, textArea, startOffset).also {
                    it.setEndOffset(endOffset)
                }
            } else {
                null
            }
        } catch (e: BadLocationException) {
            null
        }
    }

    private data class BraceInfo(val line: Int, val column: Int)
}
