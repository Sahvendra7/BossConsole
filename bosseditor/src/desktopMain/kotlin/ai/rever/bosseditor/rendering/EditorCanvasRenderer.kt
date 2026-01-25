package ai.rever.bosseditor.rendering

import ai.rever.bosseditor.core.EditorPosition
import ai.rever.bosseditor.features.Diagnostic
import ai.rever.bosseditor.features.DiagnosticSeverity
import ai.rever.bosseditor.features.GutterIcon
import ai.rever.bosseditor.features.GutterIconType
import ai.rever.bosseditor.features.Hyperlink
import ai.rever.bosseditor.features.IndentGuide
import ai.rever.bosseditor.features.InlayHint
import ai.rever.bosseditor.features.InlayHintKind
import ai.rever.bosseditor.features.InlayHintPosition
import ai.rever.bosseditor.features.RainbowBracket
import ai.rever.bosseditor.features.SpellingError
import ai.rever.bosseditor.fold.FoldType
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
        // Skip rendering if canvas is not yet sized (initial frame)
        // This prevents constraint errors from drawText when size is 0
        if (size.width <= 0f || size.height <= 0f) return

        // Skip rendering if essential dimensions are invalid
        if (ctx.charWidth <= 0f || ctx.lineHeight <= 0f) return

        // Pass 1: Draw backgrounds
        renderBackgrounds(ctx)

        // Pass 1.5: Draw indent guides (after backgrounds, before text)
        if (ctx.indentGuidesEnabled && ctx.indentGuides.isNotEmpty()) {
            drawIndentGuides(ctx)
        }

        // Pass 2: Draw text content
        renderText(ctx)

        // Pass 2.5: Draw inlay hints (after text, before overlays)
        if (ctx.inlayHintsEnabled && ctx.inlayHints.isNotEmpty()) {
            renderInlayHints(ctx)
        }

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

        // Draw bracket match backgrounds (behind text, so bracket characters are visible)
        ctx.bracketMatch?.let { match ->
            drawBracketMatchBackgrounds(ctx, match)
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
     * Draws indent guide vertical lines.
     * Guides are drawn at each indentation level, with the active guide highlighted.
     */
    private fun DrawScope.drawIndentGuides(ctx: EditorRenderingContext) {
        val colors = ctx.colors
        val gutterWidth = ctx.gutterWidth
        val charWidth = ctx.charWidth
        val lineHeight = ctx.lineHeight
        val scrollOffsetX = ctx.scrollOffsetX
        val scrollOffsetY = ctx.scrollOffsetY

        for (guide in ctx.indentGuides) {
            // Check if this guide overlaps with visible range (using document lines)
            val visibleStartVisual = ctx.visibleLineRange.first
            val visibleEndVisual = ctx.visibleLineRange.last

            // Convert guide's document lines to visual lines
            val guideStartVisual = ctx.visualLineMapper.documentToVisual(guide.startLine)
            val guideEndVisual = ctx.visualLineMapper.documentToVisual(guide.endLine)

            // Skip if completely outside visible range
            if (guideEndVisual < visibleStartVisual || guideStartVisual > visibleEndVisual) {
                continue
            }

            // Calculate the x position for this guide
            // The guide is drawn at the left edge of the indentation column
            val x = gutterWidth + guide.column * charWidth - scrollOffsetX

            // Skip if outside horizontal viewport
            if (x < gutterWidth || x > gutterWidth + ctx.viewportWidth) {
                continue
            }

            // Clamp to visible range
            val drawStartVisual = maxOf(guideStartVisual, visibleStartVisual)
            val drawEndVisual = minOf(guideEndVisual, visibleEndVisual)

            // Calculate Y coordinates
            val yStart = drawStartVisual * lineHeight - scrollOffsetY

            // Guide extends to bottom of end line
            val yEnd = (drawEndVisual + 1) * lineHeight - scrollOffsetY

            // Skip if no visible portion
            if (yStart >= yEnd) continue

            // Determine if this is the active guide
            val isActive = guide == ctx.activeIndentGuide
            val color = if (isActive) colors.activeIndentGuide else colors.indentGuide

            // Draw the vertical line
            drawLine(
                color = color,
                start = Offset(x, yStart),
                end = Offset(x, yEnd),
                strokeWidth = 1f
            )
        }
    }

    /**
     * Draws the current line highlight background.
     */
    private fun DrawScope.drawCurrentLineHighlight(ctx: EditorRenderingContext, colors: EditorColors) {
        val caretDocumentLine = ctx.caretPosition.line
        // Convert document line to visual line
        val caretVisualLine = ctx.visualLineMapper.documentToVisual(caretDocumentLine)
        if (caretVisualLine < 0 || caretVisualLine !in ctx.visibleLineRange) return

        val y = caretVisualLine * ctx.lineHeight - ctx.scrollOffsetY
        val width = ctx.viewportWidth + ctx.scrollOffsetX // Cover full width

        drawRect(
            color = colors.currentLineHighlight,
            topLeft = Offset(ctx.gutterWidth, y),
            size = Size(width, ctx.lineHeight)
        )
    }

    /**
     * Draws the selection highlight.
     * Uses visual line mapping to properly handle collapsed folds.
     */
    private fun DrawScope.drawSelection(
        ctx: EditorRenderingContext,
        selection: ai.rever.bosseditor.core.EditorRange,
        colors: EditorColors
    ) {
        // Iterate through visible visual lines
        for (visualLine in ctx.visibleLineRange) {
            val documentLine = ctx.visualLineMapper.visualToDocument(visualLine)
            if (documentLine < 0) continue

            // Check if this visual line has a collapsed fold
            val collapsedFold = ctx.visualLineMapper.getCollapsedFoldAt(visualLine)

            // For imports/doc comments folds, don't show selection (content is hidden)
            if (collapsedFold != null &&
                (collapsedFold.type == FoldType.IMPORTS || collapsedFold.type == FoldType.DOC_COMMENT)) {
                continue
            }

            // Calculate effective line length (for code folds, exclude trailing '{')
            val lineLength = if (collapsedFold != null && collapsedFold.type == FoldType.CODE) {
                val lineText = ctx.getLineText(documentLine)
                val trimmedEnd = lineText.trimEnd()
                if (trimmedEnd.endsWith("{")) {
                    trimmedEnd.dropLast(1).trimEnd().length
                } else {
                    ctx.getLineLength(documentLine)
                }
            } else {
                ctx.getLineLength(documentLine)
            }

            val lineStartPos = ai.rever.bosseditor.core.EditorPosition(documentLine, 0)
            val lineEndPos = ai.rever.bosseditor.core.EditorPosition(documentLine, lineLength)

            // Check if this line intersects with selection
            if (selection.end < lineStartPos || selection.start > lineEndPos) {
                continue // No intersection
            }

            // Calculate selection bounds on this line
            val selStartCol = if (selection.start.line < documentLine) 0 else selection.start.column.coerceAtMost(lineLength)
            val selEndCol = if (selection.end.line > documentLine) lineLength else selection.end.column.coerceAtMost(lineLength)

            if (selStartCol >= selEndCol && selection.end.line == documentLine) continue

            val y = visualLine * ctx.lineHeight - ctx.scrollOffsetY
            val xStart = ctx.gutterWidth + selStartCol * ctx.charWidth - ctx.scrollOffsetX
            val width = if (selection.end.line > documentLine) {
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
     * Uses visual line mapping to properly handle collapsed folds.
     */
    private fun DrawScope.drawSearchMatches(ctx: EditorRenderingContext, colors: EditorColors) {
        ctx.searchMatches.forEachIndexed { index, match ->
            val startDocLine = match.start.line
            val endDocLine = match.end.line

            val isCurrentMatch = index == ctx.currentSearchMatchIndex
            val bgColor = if (isCurrentMatch) {
                colors.currentSearchMatchBackground
            } else {
                colors.searchMatchBackground
            }

            // Draw match on each visible line it spans
            for (docLine in startDocLine..endDocLine) {
                // Convert document line to visual line
                val visualLine = ctx.visualLineMapper.documentToVisual(docLine)
                if (visualLine < 0) continue // Line is hidden (folded)
                if (visualLine !in ctx.visibleLineRange) continue // Line is off-screen

                val startCol = if (docLine == startDocLine) match.start.column else 0
                val endCol = if (docLine == endDocLine) match.end.column else ctx.getLineLength(docLine)

                val y = visualLine * ctx.lineHeight - ctx.scrollOffsetY
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
     * Uses visual line mapping to properly handle collapsed folds.
     */
    private fun DrawScope.renderText(ctx: EditorRenderingContext) {
        // visibleLineRange is now in terms of visual lines
        for (visualLine in ctx.visibleLineRange) {
            val documentLine = ctx.visualLineMapper.visualToDocument(visualLine)
            if (documentLine < 0) continue

            // Check if this is a collapsed fold
            val collapsedFold = ctx.visualLineMapper.getCollapsedFoldAt(visualLine)

            // For IMPORTS and DOC_COMMENT folds, only show placeholder (no line text)
            // For CODE folds, show line text (without trailing {) + placeholder
            // For other folds, show line text + placeholder
            if (collapsedFold != null && (collapsedFold.type == FoldType.IMPORTS || collapsedFold.type == FoldType.DOC_COMMENT)) {
                // Only render placeholder for imports/doc comments (no line text)
                renderFoldPlaceholder(ctx, collapsedFold, visualLine, showLineText = false)
            } else if (collapsedFold != null && collapsedFold.type == FoldType.CODE) {
                // For code folds, render line without trailing '{' and append placeholder
                renderLineWithoutTrailingBrace(ctx, documentLine, visualLine)
                renderFoldPlaceholder(ctx, collapsedFold, visualLine, showLineText = true, stripTrailingBrace = true)
            } else {
                // Render the line at the visual line position
                renderLine(ctx, documentLine, visualLine)

                // If this is a collapsed fold start, render the placeholder after line text
                if (collapsedFold != null) {
                    renderFoldPlaceholder(ctx, collapsedFold, visualLine, showLineText = true)
                }
            }
        }
    }

    /**
     * Renders the fold placeholder text (e.g., "{ ... }" or "import ...") for collapsed folds.
     *
     * @param showLineText If true, positions placeholder after the line text (for code folds).
     *                     If false, positions placeholder at the start of the line (for import folds).
     * @param stripTrailingBrace If true, positions placeholder before the trailing '{' in the line.
     */
    private fun DrawScope.renderFoldPlaceholder(
        ctx: EditorRenderingContext,
        fold: ai.rever.bosseditor.fold.FoldRegion,
        visualLine: Int,
        showLineText: Boolean = true,
        stripTrailingBrace: Boolean = false
    ) {
        val y = visualLine * ctx.lineHeight - ctx.scrollOffsetY

        // Margin after gutter/fold icon before placeholder
        val leftMargin = ctx.charWidth * 1.5f

        // Position placeholder based on whether line text is shown
        val placeholderX = if (showLineText) {
            // Position after the line text (for code blocks)
            val lineText = ctx.getLineText(fold.startLine)
            val effectiveLength = if (stripTrailingBrace) {
                // Find position before trailing '{' and whitespace
                val trimmedEnd = lineText.trimEnd()
                if (trimmedEnd.endsWith("{")) {
                    trimmedEnd.dropLast(1).trimEnd().length
                } else {
                    lineText.length
                }
            } else {
                lineText.length
            }
            ctx.gutterWidth + effectiveLength * ctx.charWidth - ctx.scrollOffsetX + ctx.charWidth * 0.5f
        } else {
            // Position at the start of the line with left margin (for imports/doc comments)
            ctx.gutterWidth - ctx.scrollOffsetX + leftMargin
        }

        // Use the fold's placeholder text or default
        val placeholderText = fold.placeholder.ifEmpty { "..." }

        // Draw placeholder background
        val measurement = TextMeasurementCache.getMeasurement(
            textMeasurer = ctx.textMeasurer,
            text = placeholderText,
            fontFamily = ctx.fontFamily,
            fontSize = ctx.fontSize * 0.9f,
            isBold = false,
            isItalic = false
        )

        val paddingH = ctx.charWidth * 0.3f
        val bgY = y + (ctx.lineHeight - measurement.height) / 2f

        drawRect(
            color = ctx.colors.foldPlaceholderBackground,
            topLeft = Offset(placeholderX, bgY),
            size = Size(measurement.width + paddingH * 2, measurement.height)
        )

        // Draw placeholder border
        drawRect(
            color = ctx.colors.foldPlaceholderBorder,
            topLeft = Offset(placeholderX, bgY),
            size = Size(measurement.width + paddingH * 2, measurement.height),
            style = Stroke(width = 1f)
        )

        // Draw placeholder text
        val style = TextStyle(
            fontFamily = ctx.fontFamily,
            fontSize = (ctx.fontSize * 0.9f).sp,
            color = ctx.colors.foldPlaceholderText
        )

        drawText(
            textMeasurer = ctx.textMeasurer,
            text = placeholderText,
            style = style,
            topLeft = Offset(placeholderX + paddingH, bgY),
            size = Size(measurement.width + ctx.charWidth, measurement.height)
        )
    }

    /**
     * Renders a single line of text.
     *
     * @param documentLine The document line number (for getting text/tokens)
     * @param visualLine The visual line number (for Y position calculation)
     */
    private fun DrawScope.renderLine(ctx: EditorRenderingContext, documentLine: Int, visualLine: Int) {
        val lineText = ctx.getLineText(documentLine)
        if (lineText.isEmpty()) return

        val y = visualLine * ctx.lineHeight - ctx.scrollOffsetY
        val tokens = ctx.getLineTokens(documentLine)

        // Get rainbow brackets for this line (column -> RainbowBracket mapping)
        val rainbowBracketsByColumn = if (ctx.rainbowBracketsEnabled) {
            getRainbowBracketsForLine(ctx, documentLine)
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

                if (hasRainbowBrackets) {
                    // Render with rainbow colors - bracket characters get rainbow colors,
                    // non-bracket characters get the token's color
                    renderTextWithRainbowBrackets(ctx, lineText, startCol, endCol, tokenColor, y,
                        isBold, isItalic, rainbowBracketsByColumn)
                } else {
                    drawLineText(ctx, lineText, startCol, endCol, tokenColor, y, isBold, isItalic)
                }
            }
        }
    }

    /**
     * Renders a line without its trailing '{' character (for collapsed code folds).
     * This allows the fold placeholder "{ ... }" to replace the opening brace.
     */
    private fun DrawScope.renderLineWithoutTrailingBrace(ctx: EditorRenderingContext, documentLine: Int, visualLine: Int) {
        val lineText = ctx.getLineText(documentLine)
        if (lineText.isEmpty()) return

        // Find where to stop rendering (before trailing '{' and whitespace)
        val trimmedEnd = lineText.trimEnd()
        val endIndex = if (trimmedEnd.endsWith("{")) {
            // Find the position of '{' and exclude it plus any whitespace before it
            val braceIndex = lineText.lastIndexOf('{')
            lineText.substring(0, braceIndex).trimEnd().length
        } else {
            lineText.length
        }

        if (endIndex <= 0) return

        val y = visualLine * ctx.lineHeight - ctx.scrollOffsetY
        val tokens = ctx.getLineTokens(documentLine)

        // Get rainbow brackets for this line (column -> RainbowBracket mapping)
        val rainbowBracketsByColumn = if (ctx.rainbowBracketsEnabled) {
            getRainbowBracketsForLine(ctx, documentLine)
        } else {
            emptyMap()
        }

        if (tokens.isEmpty()) {
            // No tokens - render as plain text up to endIndex
            val textToRender = lineText.substring(0, endIndex)
            if (rainbowBracketsByColumn.isEmpty()) {
                drawLineText(ctx, textToRender, 0, textToRender.length, ctx.colors.text, y, isBold = false, isItalic = false)
            } else {
                renderTextWithRainbowBrackets(ctx, textToRender, 0, textToRender.length, ctx.colors.text, y,
                    isBold = false, isItalic = false, rainbowBracketsByColumn)
            }
        } else {
            // Render each token with its color, stopping at endIndex
            for (token in tokens) {
                val startCol = token.startColumn.coerceIn(0, endIndex)
                val endCol = token.endColumn.coerceIn(startCol, endIndex)

                if (startCol >= endCol) continue

                val tokenColor = ctx.colors.getTokenColor(token.type)
                val isBold = token.type == TokenType.KEYWORD
                val isItalic = token.type == TokenType.COMMENT

                // Check if this token contains rainbow brackets
                val hasRainbowBrackets = rainbowBracketsByColumn.keys.any { it in startCol until endCol }

                if (hasRainbowBrackets) {
                    renderTextWithRainbowBrackets(ctx, lineText, startCol, endCol, tokenColor, y,
                        isBold, isItalic, rainbowBracketsByColumn)
                } else {
                    drawLineText(ctx, lineText, startCol, endCol, tokenColor, y, isBold, isItalic)
                }
            }
        }
    }

    /**
     * Gets rainbow brackets for a specific line as a column -> bracket map.
     */
    private fun getRainbowBracketsForLine(
        ctx: EditorRenderingContext,
        lineNumber: Int
    ): Map<Int, RainbowBracket> {
        if (ctx.rainbowBrackets.isEmpty()) return emptyMap()

        // Use document's line start offset for consistency with RainbowBrackets algorithm
        val lineStartOffset = ctx.getLineStartOffset(lineNumber)
        val lineEndOffset = lineStartOffset + ctx.getLineLength(lineNumber)

        val filtered = ctx.rainbowBrackets
            .filter { it.offset in lineStartOffset until lineEndOffset }

        return filtered.associateBy { it.offset - lineStartOffset } // Convert to column
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
        // Guard against empty text
        if (startCol < 0 || startCol >= endCol || startCol >= lineText.length) return

        val safeEndCol = minOf(endCol, lineText.length)
        val text = lineText.substring(startCol, safeEndCol)
        if (text.isEmpty()) return

        val x = ctx.gutterWidth + startCol * ctx.charWidth - ctx.scrollOffsetX

        // Skip if text is completely off-screen (optimization)
        val textWidth = text.length * ctx.charWidth
        if (x + textWidth < 0 || x > size.width) return
        if (y + ctx.lineHeight < 0 || y > size.height) return

        // Use AnnotatedString with explicit SpanStyle to ensure color is applied
        // This bypasses potential caching issues with plain text + TextStyle
        val annotatedString = androidx.compose.ui.text.AnnotatedString.Builder().apply {
            pushStyle(
                androidx.compose.ui.text.SpanStyle(
                    color = color,
                    fontFamily = ctx.fontFamily,
                    fontSize = ctx.fontSize.sp,
                    fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal
                )
            )
            append(text)
            pop()
        }.toAnnotatedString()

        // Use explicit size constraints to avoid internal constraint calculation issues
        // when topLeft.x is negative (text scrolled off-screen left)
        drawText(
            textMeasurer = ctx.textMeasurer,
            text = annotatedString,
            topLeft = Offset(x, y),
            size = Size(textWidth + ctx.charWidth, ctx.lineHeight)
        )
    }

    // ========== Pass 2.5: Inlay Hints ==========

    /**
     * Renders inlay hints (type hints, parameter hints, etc.).
     * These are semi-transparent inline hints that don't modify the actual text.
     * Uses visual line mapping to properly handle collapsed folds.
     */
    private fun DrawScope.renderInlayHints(ctx: EditorRenderingContext) {
        // Group hints by document line for efficient rendering
        val hintsByDocLine = ctx.inlayHints.groupBy { it.line }

        for (visualLine in ctx.visibleLineRange) {
            val documentLine = ctx.visualLineMapper.visualToDocument(visualLine)
            if (documentLine < 0) continue

            val lineHints = hintsByDocLine[documentLine] ?: continue
            val lineText = ctx.getLineText(documentLine)
            val y = visualLine * ctx.lineHeight - ctx.scrollOffsetY

            // Sort hints by column for proper positioning
            val sortedHints = lineHints.sortedBy { it.column }

            // Calculate cumulative offset from previous hints on this line
            var cumulativeOffset = 0f

            for (hint in sortedHints) {
                // Calculate x position based on column and cumulative offset from previous hints
                val baseX = ctx.gutterWidth + hint.column * ctx.charWidth - ctx.scrollOffsetX

                // Determine position based on hint position (BEFORE or AFTER)
                val x = when (hint.hintPosition) {
                    InlayHintPosition.BEFORE -> baseX + cumulativeOffset
                    InlayHintPosition.AFTER -> {
                        // Position after the character at this column
                        baseX + ctx.charWidth + cumulativeOffset
                    }
                }

                // Render the hint
                val hintWidth = drawInlayHint(ctx, hint, x, y)

                // Add to cumulative offset for subsequent hints
                cumulativeOffset += hintWidth
            }
        }
    }

    /**
     * Draws a single inlay hint and returns its width.
     *
     * @return The width of the rendered hint (including padding)
     */
    private fun DrawScope.drawInlayHint(
        ctx: EditorRenderingContext,
        hint: InlayHint,
        x: Float,
        y: Float
    ): Float {
        val colors = ctx.colors

        // Calculate hint colors based on kind
        val (bgColor, textColor) = when (hint.kind) {
            InlayHintKind.PARAMETER -> Pair(
                colors.inlayHintParameterBackground,
                colors.inlayHintParameterForeground
            )
            InlayHintKind.TYPE -> Pair(
                colors.inlayHintTypeBackground,
                colors.inlayHintTypeForeground
            )
            InlayHintKind.CHAIN -> Pair(
                colors.inlayHintTypeBackground.copy(alpha = 0.5f),
                colors.inlayHintTypeForeground.copy(alpha = 0.8f)
            )
            InlayHintKind.OTHER -> Pair(
                colors.inlayHintParameterBackground,
                colors.inlayHintParameterForeground
            )
        }

        // Calculate text dimensions
        val measurement = TextMeasurementCache.getMeasurement(
            textMeasurer = ctx.textMeasurer,
            text = hint.text,
            fontFamily = ctx.fontFamily,
            fontSize = ctx.fontSize * 0.9f, // Slightly smaller font
            isBold = false,
            isItalic = false
        )

        val paddingH = ctx.charWidth * 0.3f // Horizontal padding
        val paddingLeft = if (hint.paddingLeft) ctx.charWidth * 0.5f else paddingH
        val paddingRight = if (hint.paddingRight) ctx.charWidth * 0.5f else paddingH

        val totalWidth = measurement.width + paddingLeft + paddingRight
        val cornerRadius = 3f

        // Draw background with rounded corners (approximated with regular rect for simplicity)
        val bgY = y + (ctx.lineHeight - measurement.height) / 2f
        drawRect(
            color = bgColor,
            topLeft = Offset(x, bgY),
            size = Size(totalWidth, measurement.height)
        )

        // Draw hint text
        val textX = x + paddingLeft
        val textY = bgY

        // Skip if text is completely off-screen
        if (textX + measurement.width < 0 || textX > size.width) return totalWidth
        if (textY + measurement.height < 0 || textY > size.height) return totalWidth

        val style = TextStyle(
            fontFamily = ctx.fontFamily,
            fontSize = (ctx.fontSize * 0.9f).sp,
            color = textColor
        )

        drawText(
            textMeasurer = ctx.textMeasurer,
            text = hint.text,
            style = style,
            topLeft = Offset(textX, textY),
            size = Size(measurement.width + ctx.charWidth, measurement.height)
        )

        return totalWidth
    }

    // ========== Pass 3: Overlays ==========

    /**
     * Renders overlay elements: carets, bracket matching, diagnostics, hyperlinks.
     */
    private fun DrawScope.renderOverlays(ctx: EditorRenderingContext) {
        // Draw bracket matching borders (backgrounds are drawn in Pass 1)
        ctx.bracketMatch?.let { match ->
            drawBracketMatchBorders(ctx, match)
        }

        // Draw diagnostic squiggles (error, warning, info, hint underlines)
        if (ctx.diagnostics.isNotEmpty()) {
            drawDiagnostics(ctx)
        }

        // Draw spelling error squiggles (blue underlines for misspelled words)
        if (ctx.spellCheckEnabled && ctx.spellingErrors.isNotEmpty()) {
            drawSpellingErrors(ctx)
        }

        // Draw hyperlink underlines
        if (ctx.hyperlinks.isNotEmpty() && ctx.hyperlinkUnderlineVisible) {
            drawHyperlinks(ctx)
        }

        // Draw all carets (multi-caret support)
        // caretVisible controls whether caret should be shown at all
        // caretBlinkVisible controls the blink on/off state
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
     * Uses visual line mapping to properly handle collapsed folds.
     */
    private fun DrawScope.drawDiagnostics(ctx: EditorRenderingContext) {
        for (diagnostic in ctx.diagnostics) {
            val color = ctx.colors.getSquiggleColor(diagnostic.severity)

            // Draw squiggle on each visible line the diagnostic spans
            for (docLine in diagnostic.startLine..diagnostic.endLine) {
                // Convert document line to visual line
                val visualLine = ctx.visualLineMapper.documentToVisual(docLine)
                if (visualLine < 0) continue // Line is hidden (folded)
                if (visualLine !in ctx.visibleLineRange) continue // Line is off-screen

                val startCol = if (docLine == diagnostic.startLine) diagnostic.range.start.column else 0
                val endCol = if (docLine == diagnostic.endLine) diagnostic.range.end.column else ctx.getLineLength(docLine)

                if (startCol >= endCol) continue

                val y = visualLine * ctx.lineHeight - ctx.scrollOffsetY + ctx.lineHeight - 2f // 2px from bottom
                val xStart = ctx.gutterWidth + startCol * ctx.charWidth - ctx.scrollOffsetX
                val width = (endCol - startCol) * ctx.charWidth

                drawSquiggleLine(xStart, y, width, color)
            }
        }
    }

    // ========== Spelling Error Squiggles ==========

    /**
     * Draws spelling error squiggle underlines (blue, like IDE spell check).
     * Uses visual line mapping to properly handle collapsed folds.
     *
     * Optimized to iterate only visible lines and lookup errors from line index,
     * avoiding O(n) iteration through all errors.
     */
    private fun DrawScope.drawSpellingErrors(ctx: EditorRenderingContext) {
        // Use hintSquiggle color (blue) for spelling errors
        val color = ctx.colors.hintSquiggle

        // Iterate only visible visual lines and lookup errors by document line
        for (visualLine in ctx.visibleLineRange) {
            val docLine = ctx.visualLineMapper.visualToDocument(visualLine)
            if (docLine < 0) continue // Should not happen for visible lines

            // Efficient lookup by line - only process errors on this line
            val lineErrors = ctx.spellingErrorsByLine[docLine] ?: continue

            for (spellingError in lineErrors) {
                // Calculate column positions from offset range
                val lineStartOffset = ctx.getLineStartOffset(docLine)
                val startCol = spellingError.startOffset - lineStartOffset
                val endCol = spellingError.endOffset - lineStartOffset

                if (startCol >= endCol || startCol < 0) continue

                val y = visualLine * ctx.lineHeight - ctx.scrollOffsetY + ctx.lineHeight - 2f // 2px from bottom
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
     * Uses visual line mapping to properly handle collapsed folds.
     */
    private fun DrawScope.drawHyperlinks(ctx: EditorRenderingContext) {
        for (hyperlink in ctx.hyperlinks) {
            val color = ctx.colors.hyperlink

            // Draw underline on each visible line the hyperlink spans
            for (docLine in hyperlink.startLine..hyperlink.endLine) {
                // Convert document line to visual line
                val visualLine = ctx.visualLineMapper.documentToVisual(docLine)
                if (visualLine < 0) continue // Line is hidden (folded)
                if (visualLine !in ctx.visibleLineRange) continue // Line is off-screen

                val startCol = if (docLine == hyperlink.startLine) hyperlink.range.start.column else 0
                val endCol = if (docLine == hyperlink.endLine) hyperlink.range.end.column else ctx.getLineLength(docLine)

                if (startCol >= endCol) continue

                val y = visualLine * ctx.lineHeight - ctx.scrollOffsetY + ctx.lineHeight - 2f
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
        val documentLine = position.line

        // Convert document line to visual line
        val visualLine = ctx.visualLineMapper.documentToVisual(documentLine)
        if (visualLine < 0 || visualLine !in ctx.visibleLineRange) return

        // Check if this visual line has a collapsed fold
        val collapsedFold = ctx.visualLineMapper.getCollapsedFoldAt(visualLine)

        // Calculate effective max column based on fold type
        val maxColumn = if (collapsedFold != null &&
            (collapsedFold.type == FoldType.IMPORTS || collapsedFold.type == FoldType.DOC_COMMENT)) {
            // For imports/doc comments, caret at position 0 (content hidden)
            0
        } else if (collapsedFold != null && collapsedFold.type == FoldType.CODE) {
            // For code folds, limit to visible part (before trailing '{')
            val lineText = ctx.getLineText(documentLine)
            val trimmedEnd = lineText.trimEnd()
            if (trimmedEnd.endsWith("{")) {
                trimmedEnd.dropLast(1).trimEnd().length
            } else {
                ctx.getLineLength(documentLine)
            }
        } else {
            ctx.getLineLength(documentLine)
        }

        val caretColumn = position.column.coerceAtMost(maxColumn)

        val x = ctx.gutterWidth + caretColumn * ctx.charWidth - ctx.scrollOffsetX
        val y = visualLine * ctx.lineHeight - ctx.scrollOffsetY

        // Ensure caret has valid dimensions
        val caretWidth = 2f
        val caretHeight = ctx.lineHeight.coerceAtLeast(14f)

        // Caret opacity: brighter when focused, dimmer when not (like BossTerm)
        val caretAlpha = if (ctx.isFocused) 1.0f else 0.4f
        val caretColor = ctx.colors.caret.copy(alpha = caretAlpha)

        // Draw a thin line caret
        drawRect(
            color = caretColor,
            topLeft = Offset(x, y),
            size = Size(caretWidth, caretHeight)
        )
    }

    /**
     * Draws bracket matching backgrounds for both source and matching brackets.
     * Called in Pass 1 (before text) so bracket characters remain visible.
     */
    private fun DrawScope.drawBracketMatchBackgrounds(
        ctx: EditorRenderingContext,
        match: ai.rever.bosseditor.features.BracketMatch
    ) {
        val colors = ctx.colors

        // Convert offsets to positions (document lines)
        val sourcePos = ctx.offsetToPosition(match.sourceOffset)
        val matchingPos = ctx.offsetToPosition(match.matchingOffset)

        // Convert to visual lines for visibility check
        val sourceVisualLine = ctx.visualLineMapper.documentToVisual(sourcePos.line)
        val matchingVisualLine = ctx.visualLineMapper.documentToVisual(matchingPos.line)

        // Draw background for source bracket
        if (sourceVisualLine >= 0 && sourceVisualLine in ctx.visibleLineRange) {
            drawBracketBackground(ctx, sourcePos, sourceVisualLine, colors)
        }

        // Draw background for matching bracket
        if (matchingVisualLine >= 0 && matchingVisualLine in ctx.visibleLineRange) {
            drawBracketBackground(ctx, matchingPos, matchingVisualLine, colors)
        }
    }

    /**
     * Draws bracket matching borders for both source and matching brackets.
     * Called in Pass 3 (after text) so borders appear on top.
     */
    private fun DrawScope.drawBracketMatchBorders(
        ctx: EditorRenderingContext,
        match: ai.rever.bosseditor.features.BracketMatch
    ) {
        val colors = ctx.colors

        // Convert offsets to positions (document lines)
        val sourcePos = ctx.offsetToPosition(match.sourceOffset)
        val matchingPos = ctx.offsetToPosition(match.matchingOffset)

        // Convert to visual lines for visibility check
        val sourceVisualLine = ctx.visualLineMapper.documentToVisual(sourcePos.line)
        val matchingVisualLine = ctx.visualLineMapper.documentToVisual(matchingPos.line)

        // Draw border for source bracket
        if (sourceVisualLine >= 0 && sourceVisualLine in ctx.visibleLineRange) {
            drawBracketBorder(ctx, sourcePos, sourceVisualLine, colors)
        }

        // Draw border for matching bracket
        if (matchingVisualLine >= 0 && matchingVisualLine in ctx.visibleLineRange) {
            drawBracketBorder(ctx, matchingPos, matchingVisualLine, colors)
        }
    }

    /**
     * Draws a single bracket background (filled rectangle).
     */
    private fun DrawScope.drawBracketBackground(
        ctx: EditorRenderingContext,
        position: EditorPosition,
        visualLine: Int,
        colors: EditorColors
    ) {
        val x = ctx.gutterWidth + position.column * ctx.charWidth - ctx.scrollOffsetX
        val y = visualLine * ctx.lineHeight - ctx.scrollOffsetY

        drawRect(
            color = colors.matchedBracketBackground,
            topLeft = Offset(x, y),
            size = Size(ctx.charWidth, ctx.lineHeight)
        )
    }

    /**
     * Draws a single bracket border (stroked rectangle).
     */
    private fun DrawScope.drawBracketBorder(
        ctx: EditorRenderingContext,
        position: EditorPosition,
        visualLine: Int,
        colors: EditorColors
    ) {
        val x = ctx.gutterWidth + position.column * ctx.charWidth - ctx.scrollOffsetX
        val y = visualLine * ctx.lineHeight - ctx.scrollOffsetY

        drawRect(
            color = colors.matchedBracketForeground,
            topLeft = Offset(x, y),
            size = Size(ctx.charWidth, ctx.lineHeight),
            style = Stroke(width = 1f)
        )
    }

    /**
     * Draws mark occurrences highlights for all occurrences of the word under cursor.
     * Uses visual line mapping to properly handle collapsed folds.
     */
    private fun DrawScope.drawMarkOccurrences(
        ctx: EditorRenderingContext,
        colors: EditorColors
    ) {
        for (occurrence in ctx.markOccurrences) {
            // Convert offset range to position range (document lines)
            val startPos = ctx.offsetToPosition(occurrence.start)
            val endPos = ctx.offsetToPosition(occurrence.end)

            // Draw occurrence on each visible line it spans (usually just one line for words)
            for (docLine in startPos.line..endPos.line) {
                // Convert document line to visual line
                val visualLine = ctx.visualLineMapper.documentToVisual(docLine)
                if (visualLine < 0) continue // Line is hidden (folded)
                if (visualLine !in ctx.visibleLineRange) continue // Line is off-screen

                val startCol = if (docLine == startPos.line) startPos.column else 0
                val endCol = if (docLine == endPos.line) endPos.column else ctx.getLineLength(docLine)

                val y = visualLine * ctx.lineHeight - ctx.scrollOffsetY
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

        // visibleLineRange is in terms of visual lines
        for (visualLine in ctx.visibleLineRange) {
            val documentLine = ctx.visualLineMapper.visualToDocument(visualLine)
            if (documentLine < 0) continue

            val lineNumberText = (documentLine + 1).toString() // 1-indexed display
            val y = visualLine * ctx.lineHeight - ctx.scrollOffsetY

            // Use brighter color for current line
            val lineColor = if (documentLine == currentLine) {
                colors.lineNumberActive
            } else {
                colors.lineNumber
            }

            // Right-align line numbers with proper padding
            val measurement = TextMeasurementCache.getMeasurement(
                textMeasurer = ctx.textMeasurer,
                text = lineNumberText,
                fontFamily = ctx.fontFamily,
                fontSize = ctx.fontSize
            )
            // Position line numbers with padding on both sides
            val leftPadding = 4f
            val rightPadding = 4f
            val foldIndicatorSpace = if (ctx.foldingEnabled) 20f else 0f
            // Right-align but ensure minimum left padding
            val x = maxOf(leftPadding, ctx.gutterWidth - foldIndicatorSpace - rightPadding - measurement.width)

            // Skip if line number is completely off-screen
            if (x + measurement.width < 0 || x > size.width) continue
            if (y + ctx.lineHeight < 0 || y > size.height) continue

            drawText(
                textMeasurer = ctx.textMeasurer,
                text = lineNumberText,
                style = style.copy(color = lineColor),
                topLeft = Offset(x, y),
                size = Size(measurement.width + ctx.charWidth, ctx.lineHeight)
            )

            // Draw gutter icon if present
            drawGutterIconForLine(ctx, documentLine, y, colors)

            // Draw fold indicator if this line is a fold start
            if (ctx.foldingEnabled) {
                drawFoldIndicatorForLine(ctx, documentLine, y, colors)
            }
        }
    }

    /**
     * Draws a fold indicator (> or v) for fold start lines.
     * - Collapsed: > (right-pointing chevron)
     * - Expanded: v (down-pointing chevron)
     */
    private fun DrawScope.drawFoldIndicatorForLine(
        ctx: EditorRenderingContext,
        documentLine: Int,
        y: Float,
        colors: EditorColors
    ) {
        // Find fold region starting at this line
        val foldRegion = ctx.allFoldRegions.find { it.startLine == documentLine } ?: return

        // Fold indicator dimensions with minimal padding
        val indicatorSize = 16f
        val paddingRight = 4f // Minimal padding from gutter border
        val strokeWidth = 2f

        // Position in the fold indicator area (20px allocated in EditorCanvas.kt)
        val indicatorX = ctx.gutterWidth - indicatorSize - paddingRight
        val indicatorY = y + (ctx.lineHeight - indicatorSize) / 2f

        val indicatorColor = colors.foldIndicator

        if (foldRegion.isCollapsed) {
            // ChevronRight style - › pointing right
            // Material Icons typically use 45° angles and centered positioning
            val chevronPadding = indicatorSize * 0.25f  // 25% padding
            val chevronWidth = indicatorSize * 0.4f     // Width of chevron
            val chevronHeight = indicatorSize * 0.5f    // Height of chevron

            val path = Path()
            val centerY = indicatorY + indicatorSize / 2f
            val startX = indicatorX + chevronPadding

            // Top line of chevron (pointing down-right)
            path.moveTo(startX, centerY - chevronHeight / 2f)
            path.lineTo(startX + chevronWidth, centerY)

            // Bottom line of chevron (pointing up-right)
            path.lineTo(startX, centerY + chevronHeight / 2f)

            drawPath(
                path = path,
                color = indicatorColor,
                style = Stroke(
                    width = strokeWidth,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                )
            )
        } else {
            // ExpandMore style - ˅ pointing down
            // Material Icons typically use 45° angles and centered positioning
            val chevronPadding = indicatorSize * 0.25f  // 25% padding
            val chevronWidth = indicatorSize * 0.5f     // Width of chevron
            val chevronHeight = indicatorSize * 0.4f    // Height of chevron

            val path = Path()
            val centerX = indicatorX + indicatorSize / 2f
            val startY = indicatorY + chevronPadding

            // Left line of chevron (pointing down-right)
            path.moveTo(centerX - chevronWidth / 2f, startY)
            path.lineTo(centerX, startY + chevronHeight)

            // Right line of chevron (pointing down-left)
            path.lineTo(centerX + chevronWidth / 2f, startY)

            drawPath(
                path = path,
                color = indicatorColor,
                style = Stroke(
                    width = strokeWidth,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                )
            )
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
