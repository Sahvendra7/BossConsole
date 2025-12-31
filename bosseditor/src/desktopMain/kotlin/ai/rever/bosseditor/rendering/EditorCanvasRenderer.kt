package ai.rever.bosseditor.rendering

import ai.rever.bosseditor.core.EditorPosition
import ai.rever.bosseditor.features.Diagnostic
import ai.rever.bosseditor.features.DiagnosticSeverity
import ai.rever.bosseditor.features.GutterIcon
import ai.rever.bosseditor.features.GutterIconType
import ai.rever.bosseditor.features.Hyperlink
import ai.rever.bosseditor.features.RainbowBracket
import ai.rever.bosseditor.highlight.TokenType
import ai.rever.bosseditor.theme.EditorColors
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

/**
 * Canvas renderer for BossEditor.
 *
 * Uses a 3-pass rendering system (inspired by BossTerm):
 * - Pass 1: Draw backgrounds (current line highlight, selection)
 * - Pass 2: Draw text (syntax-highlighted tokens)
 * - Pass 3: Draw overlays (caret, search matches, bracket matching)
 *
 * This separation allows for efficient rendering and clear layering.
 */
object EditorCanvasRenderer {

    /**
     * Main rendering entry point.
     * Renders the entire visible portion of the editor.
     */
    fun DrawScope.renderEditor(ctx: EditorRenderingContext) {
        // Pass 1: Draw backgrounds
        renderBackgrounds(ctx)

        // Pass 2: Draw text content
        renderText(ctx)

        // Pass 3: Draw overlays
        renderOverlays(ctx)

        // Pass 4: Draw gutter (if enabled)
        if (ctx.showLineNumbers) {
            renderGutter(ctx)
        }
    }

    // ========== Pass 1: Backgrounds ==========

    /**
     * Renders background elements: current line highlight, selection, mark occurrences.
     */
    private fun DrawScope.renderBackgrounds(ctx: EditorRenderingContext) {
        val colors = ctx.colors

        // Draw current line highlight
        if (ctx.highlightCurrentLine) {
            drawCurrentLineHighlight(ctx, colors)
        }

        // Draw mark occurrences (behind selection and text)
        if (ctx.markOccurrences.isNotEmpty()) {
            drawMarkOccurrences(ctx, colors)
        }

        // Draw selections (all caret selections if multi-caret, otherwise primary selection)
        if (ctx.hasMultipleCarets) {
            for (caret in ctx.allCarets) {
                caret.selection?.let { selection ->
                    drawSelection(ctx, selection, colors)
                }
            }
        } else {
            ctx.selection?.let { selection ->
                drawSelection(ctx, selection, colors)
            }
        }

        // Draw search match backgrounds
        if (ctx.searchQuery != null && ctx.searchMatches.isNotEmpty()) {
            drawSearchMatches(ctx, colors)
        }
    }

    /**
     * Draws the current line highlight background.
     */
    private fun DrawScope.drawCurrentLineHighlight(ctx: EditorRenderingContext, colors: EditorColors) {
        val caretLine = ctx.caretPosition.line
        if (caretLine !in ctx.visibleLineRange) return

        val y = caretLine * ctx.lineHeight - ctx.scrollOffsetY
        val width = ctx.viewportWidth + ctx.scrollOffsetX // Cover full width

        drawRect(
            color = colors.currentLineHighlight,
            topLeft = Offset(ctx.gutterWidth, y),
            size = Size(width, ctx.lineHeight)
        )
    }

    /**
     * Draws the selection highlight.
     */
    private fun DrawScope.drawSelection(
        ctx: EditorRenderingContext,
        selection: ai.rever.bosseditor.core.EditorRange,
        colors: EditorColors
    ) {
        // Iterate through visible lines that intersect with selection
        for (line in ctx.visibleLineRange) {
            val lineStartPos = ai.rever.bosseditor.core.EditorPosition(line, 0)
            val lineLength = ctx.getLineLength(line)
            val lineEndPos = ai.rever.bosseditor.core.EditorPosition(line, lineLength)

            // Check if this line intersects with selection
            if (selection.end < lineStartPos || selection.start > lineEndPos) {
                continue // No intersection
            }

            // Calculate selection bounds on this line
            val selStartCol = if (selection.start.line < line) 0 else selection.start.column
            val selEndCol = if (selection.end.line > line) lineLength else selection.end.column

            if (selStartCol >= selEndCol && selection.end.line == line) continue

            val y = line * ctx.lineHeight - ctx.scrollOffsetY
            val xStart = ctx.gutterWidth + selStartCol * ctx.charWidth - ctx.scrollOffsetX
            val width = if (selection.end.line > line) {
                // Selection continues to next line - extend to viewport edge
                ctx.viewportWidth - xStart + ctx.gutterWidth + ctx.scrollOffsetX
            } else {
                (selEndCol - selStartCol) * ctx.charWidth
            }

            drawRect(
                color = colors.selectionBackground,
                topLeft = Offset(xStart, y),
                size = Size(width.coerceAtLeast(0f), ctx.lineHeight)
            )
        }
    }

    /**
     * Draws search match highlights.
     */
    private fun DrawScope.drawSearchMatches(ctx: EditorRenderingContext, colors: EditorColors) {
        ctx.searchMatches.forEachIndexed { index, match ->
            // Only draw matches in visible range
            val startLine = match.start.line
            val endLine = match.end.line

            if (endLine < ctx.visibleLineRange.first || startLine > ctx.visibleLineRange.last) {
                return@forEachIndexed
            }

            val isCurrentMatch = index == ctx.currentSearchMatchIndex
            val bgColor = if (isCurrentMatch) {
                colors.currentSearchMatchBackground
            } else {
                colors.searchMatchBackground
            }

            // Draw match on each line it spans
            for (line in maxOf(startLine, ctx.visibleLineRange.first)..minOf(endLine, ctx.visibleLineRange.last)) {
                val startCol = if (line == startLine) match.start.column else 0
                val endCol = if (line == endLine) match.end.column else ctx.getLineLength(line)

                val y = line * ctx.lineHeight - ctx.scrollOffsetY
                val xStart = ctx.gutterWidth + startCol * ctx.charWidth - ctx.scrollOffsetX
                val width = (endCol - startCol) * ctx.charWidth

                drawRect(
                    color = bgColor,
                    topLeft = Offset(xStart, y),
                    size = Size(width, ctx.lineHeight)
                )
            }
        }
    }

    // ========== Pass 2: Text ==========

    /**
     * Renders all visible text with syntax highlighting.
     */
    private fun DrawScope.renderText(ctx: EditorRenderingContext) {
        for (line in ctx.visibleLineRange) {
            renderLine(ctx, line)
        }
    }

    /**
     * Renders a single line of text.
     */
    private fun DrawScope.renderLine(ctx: EditorRenderingContext, lineNumber: Int) {
        val lineText = ctx.getLineText(lineNumber)
        if (lineText.isEmpty()) return

        val y = lineNumber * ctx.lineHeight - ctx.scrollOffsetY
        val tokens = ctx.getLineTokens(lineNumber)

        // Get rainbow brackets for this line (column -> RainbowBracket mapping)
        val rainbowBracketsByColumn = if (ctx.rainbowBracketsEnabled) {
            getRainbowBracketsForLine(ctx, lineNumber)
        } else {
            emptyMap()
        }

        if (tokens.isEmpty()) {
            // No tokens - render as plain text, but handle rainbow brackets
            if (rainbowBracketsByColumn.isEmpty()) {
                drawLineText(ctx, lineText, 0, lineText.length, ctx.colors.text, y, isBold = false, isItalic = false)
            } else {
                renderTextWithRainbowBrackets(ctx, lineText, 0, lineText.length, ctx.colors.text, y,
                    isBold = false, isItalic = false, rainbowBracketsByColumn)
            }
        } else {
            // Render each token with its color
            for (token in tokens) {
                val startCol = token.startColumn.coerceIn(0, lineText.length)
                val endCol = token.endColumn.coerceIn(startCol, lineText.length)

                if (startCol >= endCol) continue

                val tokenColor = ctx.colors.getTokenColor(token.type)
                val isBold = token.type == TokenType.KEYWORD
                val isItalic = token.type == TokenType.COMMENT

                // Check if this token contains rainbow brackets
                val hasRainbowBrackets = rainbowBracketsByColumn.keys.any { it in startCol until endCol }

                if (hasRainbowBrackets && isBracketToken(token.type)) {
                    // Render with rainbow colors
                    renderTextWithRainbowBrackets(ctx, lineText, startCol, endCol, tokenColor, y,
                        isBold, isItalic, rainbowBracketsByColumn)
                } else {
                    drawLineText(ctx, lineText, startCol, endCol, tokenColor, y, isBold, isItalic)
                }
            }
        }
    }

    /**
     * Checks if a token type represents a bracket.
     */
    private fun isBracketToken(tokenType: TokenType): Boolean {
        return tokenType == TokenType.BRACKET ||
               tokenType == TokenType.PARENTHESIS ||
               tokenType == TokenType.PUNCTUATION
    }

    /**
     * Gets rainbow brackets for a specific line as a column -> bracket map.
     */
    private fun getRainbowBracketsForLine(
        ctx: EditorRenderingContext,
        lineNumber: Int
    ): Map<Int, RainbowBracket> {
        if (ctx.rainbowBrackets.isEmpty()) return emptyMap()

        // Calculate line start/end offsets
        val lineStartOffset = calculateLineStartOffset(ctx, lineNumber)
        val lineEndOffset = lineStartOffset + ctx.getLineLength(lineNumber)

        return ctx.rainbowBrackets
            .filter { it.offset in lineStartOffset until lineEndOffset }
            .associateBy { it.offset - lineStartOffset } // Convert to column
    }

    /**
     * Calculates the start offset for a given line.
     */
    private fun calculateLineStartOffset(ctx: EditorRenderingContext, lineNumber: Int): Int {
        var offset = 0
        for (i in 0 until lineNumber) {
            offset += ctx.getLineLength(i) + 1 // +1 for newline
        }
        return offset
    }

    /**
     * Renders text with rainbow bracket colors.
     * Splits the text at bracket positions to apply different colors.
     */
    private fun DrawScope.renderTextWithRainbowBrackets(
        ctx: EditorRenderingContext,
        lineText: String,
        startCol: Int,
        endCol: Int,
        defaultColor: Color,
        y: Float,
        isBold: Boolean,
        isItalic: Boolean,
        rainbowBracketsByColumn: Map<Int, RainbowBracket>
    ) {
        var currentStart = startCol

        while (currentStart < endCol) {
            // Check if current position is a rainbow bracket
            val rainbowBracket = rainbowBracketsByColumn[currentStart]

            if (rainbowBracket != null) {
                // Render single bracket character with rainbow color
                val bracketColor = ctx.colors.getRainbowBracketColor(rainbowBracket.depth)
                drawLineText(ctx, lineText, currentStart, currentStart + 1, bracketColor, y, isBold = false, isItalic = false)
                currentStart++
            } else {
                // Find the next rainbow bracket position or end of range
                val nextBracketCol = rainbowBracketsByColumn.keys
                    .filter { it > currentStart && it < endCol }
                    .minOrNull() ?: endCol

                // Render text up to next bracket with default color
                if (currentStart < nextBracketCol) {
                    drawLineText(ctx, lineText, currentStart, nextBracketCol, defaultColor, y, isBold, isItalic)
                    currentStart = nextBracketCol
                }
            }
        }
    }

    /**
     * Draws a portion of text on a line.
     */
    private fun DrawScope.drawLineText(
        ctx: EditorRenderingContext,
        lineText: String,
        startCol: Int,
        endCol: Int,
        color: Color,
        y: Float,
        isBold: Boolean,
        isItalic: Boolean
    ) {
        val text = lineText.substring(startCol, endCol)
        val x = ctx.gutterWidth + startCol * ctx.charWidth - ctx.scrollOffsetX

        val style = TextStyle(
            fontFamily = ctx.fontFamily,
            fontSize = ctx.fontSize.sp,
            color = color,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal
        )

        // Use cached measurement for consistent positioning
        val measurement = TextMeasurementCache.getMeasurement(
            textMeasurer = ctx.textMeasurer,
            text = text,
            fontFamily = ctx.fontFamily,
            fontSize = ctx.fontSize,
            isBold = isBold,
            isItalic = isItalic
        )

        // Calculate baseline offset for proper vertical alignment
        val baselineY = y + ctx.baselineOffset

        drawText(
            textMeasurer = ctx.textMeasurer,
            text = text,
            style = style,
            topLeft = Offset(x, y)
        )
    }

    // ========== Pass 3: Overlays ==========

    /**
     * Renders overlay elements: carets, bracket matching, diagnostics, hyperlinks.
     */
    private fun DrawScope.renderOverlays(ctx: EditorRenderingContext) {
        // Draw bracket matching highlight
        ctx.bracketMatch?.let { match ->
            drawBracketMatch(ctx, match)
        }

        // Draw diagnostic squiggles (error, warning, info, hint underlines)
        if (ctx.diagnostics.isNotEmpty()) {
            drawDiagnostics(ctx)
        }

        // Draw hyperlink underlines
        if (ctx.hyperlinks.isNotEmpty() && ctx.hyperlinkUnderlineVisible) {
            drawHyperlinks(ctx)
        }

        // Draw all carets (multi-caret support)
        if (ctx.caretVisible && ctx.caretBlinkVisible) {
            if (ctx.hasMultipleCarets) {
                drawAllCarets(ctx)
            } else {
                drawCaret(ctx, ctx.caretPosition)
            }
        }
    }

    // ========== Diagnostic Squiggles ==========

    /**
     * Draws diagnostic squiggle underlines for errors, warnings, etc.
     */
    private fun DrawScope.drawDiagnostics(ctx: EditorRenderingContext) {
        for (diagnostic in ctx.diagnostics) {
            // Skip if diagnostic is entirely outside visible range
            if (diagnostic.endLine < ctx.visibleLineRange.first ||
                diagnostic.startLine > ctx.visibleLineRange.last) {
                continue
            }

            val color = ctx.colors.getSquiggleColor(diagnostic.severity)

            // Draw squiggle on each line the diagnostic spans
            for (line in maxOf(diagnostic.startLine, ctx.visibleLineRange.first)..
                        minOf(diagnostic.endLine, ctx.visibleLineRange.last)) {
                val startCol = if (line == diagnostic.startLine) diagnostic.range.start.column else 0
                val endCol = if (line == diagnostic.endLine) diagnostic.range.end.column else ctx.getLineLength(line)

                if (startCol >= endCol) continue

                val y = line * ctx.lineHeight - ctx.scrollOffsetY + ctx.lineHeight - 2f // 2px from bottom
                val xStart = ctx.gutterWidth + startCol * ctx.charWidth - ctx.scrollOffsetX
                val width = (endCol - startCol) * ctx.charWidth

                drawSquiggleLine(xStart, y, width, color)
            }
        }
    }

    /**
     * Draws a wavy/squiggly line (like IntelliJ's error underlines).
     *
     * @param x Start X position
     * @param y Y position (baseline of squiggle)
     * @param width Width of the squiggle line
     * @param color Color of the squiggle
     */
    private fun DrawScope.drawSquiggleLine(
        x: Float,
        y: Float,
        width: Float,
        color: Color
    ) {
        if (width <= 0f) return

        val waveHeight = 2f  // Height of each wave
        val wavelength = 4f  // Width of each wave segment

        val path = Path()
        var currentX = x

        path.moveTo(currentX, y)

        while (currentX < x + width) {
            val segmentWidth = minOf(wavelength / 2f, x + width - currentX)
            val nextX = currentX + segmentWidth

            // Draw downward then upward curve (sine wave approximation)
            if ((((currentX - x) / (wavelength / 2f)).toInt() % 2) == 0) {
                // Going down
                path.quadraticTo(
                    currentX + segmentWidth / 2f, y + waveHeight,
                    nextX, y
                )
            } else {
                // Going up
                path.quadraticTo(
                    currentX + segmentWidth / 2f, y - waveHeight,
                    nextX, y
                )
            }

            currentX = nextX
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 1.5f)
        )
    }

    // ========== Hyperlinks ==========

    /**
     * Draws hyperlink underlines (shown when Cmd/Ctrl is held).
     */
    private fun DrawScope.drawHyperlinks(ctx: EditorRenderingContext) {
        for (hyperlink in ctx.hyperlinks) {
            // Skip if hyperlink is entirely outside visible range
            if (hyperlink.endLine < ctx.visibleLineRange.first ||
                hyperlink.startLine > ctx.visibleLineRange.last) {
                continue
            }

            val color = ctx.colors.hyperlink

            // Draw underline on each line the hyperlink spans
            for (line in maxOf(hyperlink.startLine, ctx.visibleLineRange.first)..
                        minOf(hyperlink.endLine, ctx.visibleLineRange.last)) {
                val startCol = if (line == hyperlink.startLine) hyperlink.range.start.column else 0
                val endCol = if (line == hyperlink.endLine) hyperlink.range.end.column else ctx.getLineLength(line)

                if (startCol >= endCol) continue

                val y = line * ctx.lineHeight - ctx.scrollOffsetY + ctx.lineHeight - 2f
                val xStart = ctx.gutterWidth + startCol * ctx.charWidth - ctx.scrollOffsetX
                val width = (endCol - startCol) * ctx.charWidth

                // Draw solid underline for hyperlinks
                drawLine(
                    color = color,
                    start = Offset(xStart, y),
                    end = Offset(xStart + width, y),
                    strokeWidth = 1f
                )
            }
        }
    }

    /**
     * Draws all carets for multi-caret editing.
     */
    private fun DrawScope.drawAllCarets(ctx: EditorRenderingContext) {
        for (caret in ctx.allCarets) {
            drawCaret(ctx, caret.position)
        }
    }

    /**
     * Draws a single text caret (cursor) at the given position.
     */
    private fun DrawScope.drawCaret(ctx: EditorRenderingContext, position: EditorPosition) {
        val caretLine = position.line
        val caretColumn = position.column

        // Check if caret is in visible range
        if (caretLine !in ctx.visibleLineRange) return

        val x = ctx.gutterWidth + caretColumn * ctx.charWidth - ctx.scrollOffsetX
        val y = caretLine * ctx.lineHeight - ctx.scrollOffsetY

        // Draw a thin line caret (2 pixels wide)
        drawRect(
            color = ctx.colors.caret,
            topLeft = Offset(x, y),
            size = Size(2f, ctx.lineHeight)
        )
    }

    /**
     * Draws bracket matching highlight for both source and matching brackets.
     */
    private fun DrawScope.drawBracketMatch(
        ctx: EditorRenderingContext,
        match: ai.rever.bosseditor.features.BracketMatch
    ) {
        val colors = ctx.colors

        // Convert offsets to positions
        val sourcePos = ctx.offsetToPosition(match.sourceOffset)
        val matchingPos = ctx.offsetToPosition(match.matchingOffset)

        // Draw highlight for source bracket
        if (sourcePos.line in ctx.visibleLineRange) {
            drawBracketHighlight(ctx, sourcePos, colors)
        }

        // Draw highlight for matching bracket
        if (matchingPos.line in ctx.visibleLineRange) {
            drawBracketHighlight(ctx, matchingPos, colors)
        }
    }

    /**
     * Draws a single bracket highlight (rectangle with optional border).
     */
    private fun DrawScope.drawBracketHighlight(
        ctx: EditorRenderingContext,
        position: EditorPosition,
        colors: EditorColors
    ) {
        val x = ctx.gutterWidth + position.column * ctx.charWidth - ctx.scrollOffsetX
        val y = position.line * ctx.lineHeight - ctx.scrollOffsetY

        // Draw background
        drawRect(
            color = colors.matchedBracketBackground,
            topLeft = Offset(x, y),
            size = Size(ctx.charWidth, ctx.lineHeight)
        )

        // Draw border for better visibility
        drawRect(
            color = colors.matchedBracketForeground,
            topLeft = Offset(x, y),
            size = Size(ctx.charWidth, ctx.lineHeight),
            style = Stroke(width = 1f)
        )
    }

    /**
     * Draws mark occurrences highlights for all occurrences of the word under cursor.
     */
    private fun DrawScope.drawMarkOccurrences(
        ctx: EditorRenderingContext,
        colors: EditorColors
    ) {
        for (occurrence in ctx.markOccurrences) {
            // Convert offset range to position range
            val startPos = ctx.offsetToPosition(occurrence.start)
            val endPos = ctx.offsetToPosition(occurrence.end)

            // Skip if entirely outside visible range
            if (endPos.line < ctx.visibleLineRange.first || startPos.line > ctx.visibleLineRange.last) {
                continue
            }

            // Draw occurrence on each line it spans (usually just one line for words)
            for (line in maxOf(startPos.line, ctx.visibleLineRange.first)..minOf(endPos.line, ctx.visibleLineRange.last)) {
                val startCol = if (line == startPos.line) startPos.column else 0
                val endCol = if (line == endPos.line) endPos.column else ctx.getLineLength(line)

                val y = line * ctx.lineHeight - ctx.scrollOffsetY
                val xStart = ctx.gutterWidth + startCol * ctx.charWidth - ctx.scrollOffsetX
                val width = (endCol - startCol) * ctx.charWidth

                // Draw background
                drawRect(
                    color = colors.markOccurrences,
                    topLeft = Offset(xStart, y),
                    size = Size(width, ctx.lineHeight)
                )

                // Draw border for better visibility
                drawRect(
                    color = colors.markOccurrences.copy(alpha = 0.8f),
                    topLeft = Offset(xStart, y),
                    size = Size(width, ctx.lineHeight),
                    style = Stroke(width = 1f)
                )
            }
        }
    }

    // ========== Pass 4: Gutter ==========

    /**
     * Renders the line number gutter.
     */
    private fun DrawScope.renderGutter(ctx: EditorRenderingContext) {
        val colors = ctx.colors

        // Draw gutter background
        drawRect(
            color = colors.gutterBackground,
            topLeft = Offset.Zero,
            size = Size(ctx.gutterWidth, ctx.viewportHeight)
        )

        // Draw gutter border
        drawLine(
            color = colors.gutterBorder,
            start = Offset(ctx.gutterWidth - 1, 0f),
            end = Offset(ctx.gutterWidth - 1, ctx.viewportHeight),
            strokeWidth = 1f
        )

        // Draw line numbers
        val currentLine = ctx.caretPosition.line
        val style = TextStyle(
            fontFamily = ctx.fontFamily,
            fontSize = ctx.fontSize.sp
        )

        for (line in ctx.visibleLineRange) {
            val lineNumberText = (line + 1).toString() // 1-indexed display
            val y = line * ctx.lineHeight - ctx.scrollOffsetY

            // Use brighter color for current line
            val lineColor = if (line == currentLine) {
                colors.lineNumberActive
            } else {
                colors.lineNumber
            }

            // Right-align line numbers
            val measurement = TextMeasurementCache.getMeasurement(
                textMeasurer = ctx.textMeasurer,
                text = lineNumberText,
                fontFamily = ctx.fontFamily,
                fontSize = ctx.fontSize
            )
            val x = ctx.gutterWidth - measurement.width - 8f // 8px padding from right edge

            drawText(
                textMeasurer = ctx.textMeasurer,
                text = lineNumberText,
                style = style.copy(color = lineColor),
                topLeft = Offset(x, y)
            )

            // Draw gutter icon if present
            drawGutterIconForLine(ctx, line, y, colors)
        }
    }

    /**
     * Draws a gutter icon for a specific line.
     */
    private fun DrawScope.drawGutterIconForLine(
        ctx: EditorRenderingContext,
        line: Int,
        y: Float,
        colors: EditorColors
    ) {
        val icon = ctx.gutterIcons.find { it.line == line } ?: return

        val iconSize = ctx.lineHeight * 0.8f // 80% of line height
        val iconX = 4f // Left padding
        val iconY = y + (ctx.lineHeight - iconSize) / 2f

        when (icon.type) {
            GutterIconType.ERROR -> {
                // Red circle with X
                drawCircle(
                    color = colors.gutterError,
                    radius = iconSize / 2f,
                    center = Offset(iconX + iconSize / 2f, iconY + iconSize / 2f)
                )
            }
            GutterIconType.WARNING -> {
                // Yellow triangle
                val path = Path()
                path.moveTo(iconX + iconSize / 2f, iconY) // Top
                path.lineTo(iconX + iconSize, iconY + iconSize) // Bottom right
                path.lineTo(iconX, iconY + iconSize) // Bottom left
                path.close()
                drawPath(path, colors.gutterWarning)
            }
            GutterIconType.INFO -> {
                // Blue circle with i
                drawCircle(
                    color = colors.gutterInfo,
                    radius = iconSize / 2f,
                    center = Offset(iconX + iconSize / 2f, iconY + iconSize / 2f)
                )
            }
            GutterIconType.HINT -> {
                // Gray circle
                drawCircle(
                    color = colors.gutterHint,
                    radius = iconSize / 2f,
                    center = Offset(iconX + iconSize / 2f, iconY + iconSize / 2f)
                )
            }
            GutterIconType.RUN -> {
                // Green play triangle
                val path = Path()
                path.moveTo(iconX, iconY) // Top left
                path.lineTo(iconX + iconSize, iconY + iconSize / 2f) // Right point
                path.lineTo(iconX, iconY + iconSize) // Bottom left
                path.close()
                drawPath(path, Color(0xFF4CAF50)) // Green
            }
            GutterIconType.DEBUG -> {
                // Green bug icon (simplified as circle)
                drawCircle(
                    color = Color(0xFF4CAF50),
                    radius = iconSize / 2f,
                    center = Offset(iconX + iconSize / 2f, iconY + iconSize / 2f)
                )
            }
            GutterIconType.BREAKPOINT -> {
                // Red filled circle
                drawCircle(
                    color = Color(0xFFFF5252),
                    radius = iconSize / 2f,
                    center = Offset(iconX + iconSize / 2f, iconY + iconSize / 2f)
                )
            }
            GutterIconType.BREAKPOINT_DISABLED -> {
                // Gray circle outline
                drawCircle(
                    color = Color(0xFF9E9E9E),
                    radius = iconSize / 2f,
                    center = Offset(iconX + iconSize / 2f, iconY + iconSize / 2f),
                    style = Stroke(width = 2f)
                )
            }
            GutterIconType.BOOKMARK -> {
                // Blue bookmark flag
                val path = Path()
                path.moveTo(iconX, iconY) // Top left
                path.lineTo(iconX + iconSize, iconY) // Top right
                path.lineTo(iconX + iconSize, iconY + iconSize * 0.8f) // Bottom right
                path.lineTo(iconX + iconSize / 2f, iconY + iconSize * 0.6f) // Point
                path.lineTo(iconX, iconY + iconSize * 0.8f) // Bottom left
                path.close()
                drawPath(path, Color(0xFF2196F3))
            }
            GutterIconType.FOLD_START, GutterIconType.FOLD_END -> {
                // Fold icon handled elsewhere (code folding)
            }
            GutterIconType.OVERRIDE, GutterIconType.RECURSIVE -> {
                // Arrow down for override
                drawCircle(
                    color = Color(0xFF9C27B0),
                    radius = iconSize / 2f,
                    center = Offset(iconX + iconSize / 2f, iconY + iconSize / 2f)
                )
            }
        }
    }
}
