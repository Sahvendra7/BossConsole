package ai.rever.bosseditor.fold

/**
 * Fold parser for Kotlin source code.
 *
 * Detects foldable regions for:
 * - Import statements (consecutive imports)
 * - Classes, interfaces, objects
 * - Functions and lambdas
 * - When expressions
 * - If/else blocks
 * - Try/catch blocks
 * - Block comments and doc comments
 *
 * ## Placeholder Examples
 * - Imports: `import ...`
 * - Functions: `fun name(...) { ... }`
 * - Classes: `class Name { ... }`
 * - Comments: `/* ... */` or `/** ... */`
 */
class KotlinFoldParser : FoldParser {

    override val languageId: String = "kotlin"

    override fun parse(text: String): FoldParseResult {
        val regions = mutableListOf<FoldRegion>()
        val lines = text.lines()

        // Track import region
        var importStartLine = -1
        var importEndLine = -1

        // Track brace-based folds
        val braceStack = mutableListOf<BraceInfo>()

        // Track multi-line comments (for fold regions)
        var commentStartLine = -1
        var inBlockCommentForFold = false
        var isDocComment = false

        // Track block comment state for brace parsing (separate from fold tracking)
        var inBlockCommentForBraces = false

        // Track raw strings
        var inRawString = false
        var rawStringStartLine = -1

        for ((lineIndex, line) in lines.withIndex()) {
            val trimmedLine = line.trim()

            // Handle raw strings (triple-quoted)
            if (!inBlockCommentForFold) {
                val tripleQuoteCount = countTripleQuotes(line)
                if (tripleQuoteCount > 0) {
                    if (!inRawString) {
                        // Starting raw string
                        if (tripleQuoteCount == 1) {
                            inRawString = true
                            rawStringStartLine = lineIndex
                        }
                        // If count is 2, string opens and closes on same line - no fold
                    } else {
                        // Ending raw string
                        if (lineIndex > rawStringStartLine) {
                            regions.add(
                                FoldRegion(
                                    startLine = rawStringStartLine,
                                    endLine = lineIndex,
                                    type = FoldType.STRING,
                                    placeholder = "\"\"\"...\"\"\""
                                )
                            )
                        }
                        inRawString = false
                        rawStringStartLine = -1
                    }
                }
            }

            // Skip processing if inside raw string
            if (inRawString) continue

            // Handle import folding
            if (trimmedLine.startsWith("import ")) {
                if (importStartLine == -1) {
                    importStartLine = lineIndex
                }
                importEndLine = lineIndex
            } else if (importStartLine != -1 && trimmedLine.isNotEmpty() && !trimmedLine.startsWith("//")) {
                // End of import block (non-empty, non-comment line that's not an import)
                if (importEndLine > importStartLine) {
                    regions.add(FoldRegion.forImports(importStartLine, importEndLine))
                }
                importStartLine = -1
                importEndLine = -1
            }

            // Handle block comment folding (for creating fold regions)
            if (!inBlockCommentForFold) {
                val commentStart = findBlockCommentStart(trimmedLine)
                if (commentStart != null) {
                    inBlockCommentForFold = true
                    isDocComment = commentStart.isDoc
                    commentStartLine = lineIndex

                    // Check if comment ends on same line
                    if (trimmedLine.contains("*/") && trimmedLine.indexOf("*/") > trimmedLine.indexOf("/*")) {
                        inBlockCommentForFold = false
                        commentStartLine = -1
                    }
                }
            } else if (trimmedLine.contains("*/")) {
                // End of block comment
                if (lineIndex > commentStartLine) {
                    regions.add(FoldRegion.forComment(commentStartLine, lineIndex, isDocComment))
                }
                inBlockCommentForFold = false
                commentStartLine = -1
            }

            // Handle curly brace folding - parseBraces tracks its own block comment state
            // to properly skip braces inside comments
            inBlockCommentForBraces = parseBraces(line, lineIndex, lines, braceStack, regions, inBlockCommentForBraces)
        }

        // Handle imports at end of file
        if (importStartLine != -1 && importEndLine > importStartLine) {
            regions.add(FoldRegion.forImports(importStartLine, importEndLine))
        }

        // Sort by start line
        regions.sortBy { it.startLine }

        return FoldParseResult(regions)
    }

    /**
     * Parses curly braces in a line for code block folding.
     *
     * @param inBlockComment Whether we're currently inside a multi-line block comment
     * @return Whether we're still inside a block comment at end of line
     */
    private fun parseBraces(
        line: String,
        lineIndex: Int,
        allLines: List<String>,
        braceStack: MutableList<BraceInfo>,
        regions: MutableList<FoldRegion>,
        inBlockComment: Boolean
    ): Boolean {
        var charIndex = 0
        var inString = false
        var inChar = false
        var stringChar = '"'
        var currentlyInBlockComment = inBlockComment

        while (charIndex < line.length) {
            val char = line[charIndex]

            // Handle block comment end
            if (currentlyInBlockComment) {
                if (char == '*' && charIndex + 1 < line.length && line[charIndex + 1] == '/') {
                    currentlyInBlockComment = false
                    charIndex += 2
                    continue
                }
                charIndex++
                continue
            }

            // Handle block comment start
            if (char == '/' && charIndex + 1 < line.length && line[charIndex + 1] == '*') {
                currentlyInBlockComment = true
                charIndex += 2
                continue
            }

            // Handle string and char literals
            if (char == '"' || char == '\'') {
                if (char == '"') {
                    // Skip double quote inside char literal
                    if (inChar) {
                        charIndex++
                        continue
                    }

                    // Check for triple-quoted string
                    if (!inString && charIndex + 2 < line.length &&
                        line[charIndex + 1] == '"' &&
                        line[charIndex + 2] == '"'
                    ) {
                        // Skip triple-quoted string (handled separately)
                        charIndex += 3
                        while (charIndex + 2 < line.length) {
                            if (line[charIndex] == '"' &&
                                line[charIndex + 1] == '"' &&
                                line[charIndex + 2] == '"'
                            ) {
                                charIndex += 3
                                break
                            }
                            charIndex++
                        }
                        continue
                    }

                    if (!inString) {
                        inString = true
                        stringChar = '"'
                    } else if (stringChar == '"' && (charIndex == 0 || line[charIndex - 1] != '\\')) {
                        inString = false
                    }
                } else if (char == '\'') {
                    // Skip single quote inside string literal
                    if (inString) {
                        charIndex++
                        continue
                    }

                    // Toggle char literal state
                    if (!inChar) {
                        inChar = true
                    } else if (charIndex == 0 || line[charIndex - 1] != '\\') {
                        inChar = false
                    }
                }
                charIndex++
                continue
            }

            // Skip if in string or char literal
            if (inString || inChar) {
                // Handle escape sequences
                if (char == '\\' && charIndex + 1 < line.length) {
                    charIndex += 2
                    continue
                }
                charIndex++
                continue
            }

            // Skip line comments
            if (char == '/' && charIndex + 1 < line.length && line[charIndex + 1] == '/') {
                break // Rest of line is comment
            }

            // Track braces
            if (char == '{') {
                braceStack.add(BraceInfo(lineIndex, charIndex, line))
            } else if (char == '}' && braceStack.isNotEmpty()) {
                val openBrace = braceStack.removeAt(braceStack.lastIndex)
                if (lineIndex > openBrace.line) {
                    regions.add(
                        FoldRegion.forCodeBlock(
                            startLine = openBrace.line,
                            endLine = lineIndex,
                            firstLineText = openBrace.lineText
                        )
                    )
                }
            }

            charIndex++
        }

        return currentlyInBlockComment
    }

    /**
     * Counts triple quotes in a line (for raw string detection).
     */
    private fun countTripleQuotes(line: String): Int {
        var count = 0
        var i = 0
        while (i + 2 < line.length) {
            if (line[i] == '"' && line[i + 1] == '"' && line[i + 2] == '"') {
                count++
                i += 3
            } else {
                i++
            }
        }
        return count
    }

    /**
     * Finds block comment start in a line.
     */
    private fun findBlockCommentStart(trimmedLine: String): CommentStart? {
        // Check for doc comment first
        if (trimmedLine.startsWith("/**") && !trimmedLine.startsWith("/***")) {
            return CommentStart(isDoc = true)
        }
        if (trimmedLine.startsWith("/*")) {
            return CommentStart(isDoc = false)
        }
        return null
    }

    private data class BraceInfo(
        val line: Int,
        val column: Int,
        val lineText: String
    )

    private data class CommentStart(val isDoc: Boolean)
}
