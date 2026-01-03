package ai.rever.bosseditor.print

import ai.rever.bosseditor.core.EditorDocument
import ai.rever.bosseditor.highlight.Token
import ai.rever.bosseditor.highlight.TokenType
import ai.rever.bosseditor.theme.EditorColors
import ai.rever.bosseditor.theme.EditorTheme
import java.awt.Color
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.print.PageFormat
import java.awt.print.Printable
import java.awt.print.PrinterException
import java.awt.print.PrinterJob

/**
 * Manager for printing editor content.
 *
 * Uses Java AWT print API to render editor content to printer.
 * Supports syntax highlighting, line numbers, and page formatting.
 *
 * JVM-only: Desktop target only (see CLAUDE.md).
 */
class PrintManager {
    /**
     * Shows the system print dialog and prints the document.
     *
     * @param document The document to print
     * @param settings Print settings
     * @param filename The filename for the header
     * @param getLineTokens Function to get syntax tokens for a line
     * @param colors Theme colors for syntax highlighting
     * @return PrintResult indicating success, cancellation, or error
     */
    fun print(
        document: EditorDocument,
        settings: PrintSettings = PrintSettings.DEFAULT,
        filename: String = "Untitled",
        getLineTokens: (Int) -> List<Token> = { emptyList() },
        colors: EditorColors = EditorTheme.Light.colors
    ): PrintResult {
        return try {
            val printerJob = PrinterJob.getPrinterJob()
            val pageFormat = createPageFormat(printerJob, settings)

            val printable = EditorPrintable(
                document = document,
                settings = settings,
                filename = filename,
                getLineTokens = getLineTokens,
                colors = if (settings.syntaxHighlighting) colors else null,
                pageFormat = pageFormat
            )

            printerJob.setPrintable(printable, pageFormat)
            printerJob.jobName = "Print: $filename"

            if (printerJob.printDialog()) {
                printerJob.print()
                PrintResult.Success(printable.totalPages)
            } else {
                PrintResult.Cancelled
            }
        } catch (e: PrinterException) {
            PrintResult.Error("Printer error: ${e.message}", e)
        } catch (e: Exception) {
            PrintResult.Error("Print failed: ${e.message}", e)
        }
    }

    /**
     * Gets the number of pages that would be printed.
     */
    fun getPageCount(
        document: EditorDocument,
        settings: PrintSettings = PrintSettings.DEFAULT
    ): Int {
        val printerJob = PrinterJob.getPrinterJob()
        val pageFormat = createPageFormat(printerJob, settings)

        val printable = EditorPrintable(
            document = document,
            settings = settings,
            filename = "",
            getLineTokens = { emptyList() },
            colors = null,
            pageFormat = pageFormat
        )

        return printable.totalPages
    }

    /**
     * Creates a PageFormat from print settings.
     */
    private fun createPageFormat(printerJob: PrinterJob, settings: PrintSettings): PageFormat {
        val pageFormat = printerJob.defaultPage()
        val paper = pageFormat.paper

        // Set paper size
        paper.setSize(
            settings.pageSize.widthPoints.toDouble(),
            settings.pageSize.heightPoints.toDouble()
        )

        // Set imageable area (accounting for margins)
        paper.setImageableArea(
            settings.margins.left.toDouble(),
            settings.margins.top.toDouble(),
            (settings.pageSize.widthPoints - settings.margins.horizontalTotal).toDouble(),
            (settings.pageSize.heightPoints - settings.margins.verticalTotal).toDouble()
        )

        pageFormat.paper = paper
        pageFormat.orientation = when (settings.orientation) {
            PageOrientation.PORTRAIT -> PageFormat.PORTRAIT
            PageOrientation.LANDSCAPE -> PageFormat.LANDSCAPE
        }

        return pageFormat
    }
}

/**
 * Printable implementation for editor content.
 */
private class EditorPrintable(
    private val document: EditorDocument,
    private val settings: PrintSettings,
    private val filename: String,
    private val getLineTokens: (Int) -> List<Token>,
    private val colors: EditorColors?,
    private val pageFormat: PageFormat
) : Printable {
    private val font = Font(Font.MONOSPACED, Font.PLAIN, settings.fontSize.toInt())
    private val headerFont = Font(Font.SANS_SERIF, Font.PLAIN, 9)

    // Calculate lines per page
    private val lineHeight: Int
        get() = (settings.fontSize * 1.4).toInt()

    private val linesPerPage: Int
        get() {
            val headerHeight = if (settings.headerText != null) 20 else 0
            val footerHeight = if (settings.footerText != null) 20 else 0
            val availableHeight = pageFormat.imageableHeight - headerHeight - footerHeight
            return (availableHeight / lineHeight).toInt()
        }

    val totalPages: Int
        get() = (document.lineCount + linesPerPage - 1) / linesPerPage

    override fun print(graphics: Graphics, pageFormat: PageFormat, pageIndex: Int): Int {
        if (pageIndex >= totalPages) {
            return Printable.NO_SUCH_PAGE
        }

        val g2d = graphics as Graphics2D
        g2d.translate(pageFormat.imageableX, pageFormat.imageableY)

        // Render header
        var yOffset = 0
        if (settings.headerText != null) {
            renderHeader(g2d, pageIndex, pageFormat)
            yOffset = 20
        }

        // Render content
        g2d.font = font
        val startLine = pageIndex * linesPerPage
        val endLine = minOf(startLine + linesPerPage, document.lineCount)

        val lineNumberWidth = if (settings.showLineNumbers) {
            val maxLineNumber = document.lineCount.toString()
            (maxLineNumber.length * settings.fontSize * 0.6 + 10).toInt()
        } else 0

        for (line in startLine until endLine) {
            val y = yOffset + (line - startLine + 1) * lineHeight

            // Draw line number
            if (settings.showLineNumbers) {
                g2d.color = Color.GRAY
                val lineNum = (line + 1).toString()
                g2d.drawString(lineNum, 0, y)
            }

            // Draw line content
            val lineText = document.getLineText(line)
            val xOffset = lineNumberWidth

            if (colors != null && settings.syntaxHighlighting) {
                // Render with syntax highlighting
                val tokens = getLineTokens(line)
                renderLineWithTokens(g2d, lineText, tokens, xOffset, y)
            } else {
                // Plain text
                g2d.color = Color.BLACK
                g2d.drawString(lineText, xOffset, y)
            }
        }

        // Render footer
        if (settings.footerText != null) {
            renderFooter(g2d, pageIndex, pageFormat)
        }

        return Printable.PAGE_EXISTS
    }

    private fun renderHeader(g2d: Graphics2D, pageIndex: Int, pageFormat: PageFormat) {
        g2d.font = headerFont
        g2d.color = Color.DARK_GRAY

        val text = formatHeaderFooter(settings.headerText!!, pageIndex)
        g2d.drawString(text, 0, 12)

        // Draw separator line
        g2d.drawLine(0, 16, pageFormat.imageableWidth.toInt(), 16)
    }

    private fun renderFooter(g2d: Graphics2D, pageIndex: Int, pageFormat: PageFormat) {
        g2d.font = headerFont
        g2d.color = Color.DARK_GRAY

        val y = pageFormat.imageableHeight.toInt() - 5

        // Draw separator line
        g2d.drawLine(0, y - 15, pageFormat.imageableWidth.toInt(), y - 15)

        val text = formatHeaderFooter(settings.footerText!!, pageIndex)
        // Center the footer
        val textWidth = g2d.fontMetrics.stringWidth(text)
        val x = (pageFormat.imageableWidth.toInt() - textWidth) / 2
        g2d.drawString(text, x, y)
    }

    private fun formatHeaderFooter(template: String, pageIndex: Int): String {
        return template
            .replace("%f", filename)
            .replace("%p", (pageIndex + 1).toString())
            .replace("%P", totalPages.toString())
            .replace("%d", java.time.LocalDate.now().toString())
    }

    private fun renderLineWithTokens(
        g2d: Graphics2D,
        lineText: String,
        tokens: List<Token>,
        xOffset: Int,
        y: Int
    ) {
        if (tokens.isEmpty() || colors == null) {
            g2d.color = Color.BLACK
            g2d.drawString(lineText, xOffset, y)
            return
        }

        var x = xOffset

        // Sort tokens by start offset
        val sortedTokens = tokens.sortedBy { it.startOffset }
        var lastEnd = 0

        for (token in sortedTokens) {
            // Draw any text before this token (as plain text)
            if (token.startOffset > lastEnd) {
                val beforeText = lineText.substring(lastEnd, minOf(token.startOffset, lineText.length))
                g2d.color = Color.BLACK
                g2d.drawString(beforeText, x, y)
                x += g2d.fontMetrics.stringWidth(beforeText)
            }

            // Draw the token text with color
            val tokenStart = token.startOffset.coerceIn(0, lineText.length)
            val tokenEnd = token.endOffset.coerceIn(tokenStart, lineText.length)
            if (tokenStart < tokenEnd) {
                val tokenText = lineText.substring(tokenStart, tokenEnd)
                g2d.color = tokenTypeToAwtColor(token.type)
                g2d.drawString(tokenText, x, y)
                x += g2d.fontMetrics.stringWidth(tokenText)
            }

            lastEnd = token.endOffset
        }

        // Draw any remaining text after the last token
        if (lastEnd < lineText.length) {
            val remainingText = lineText.substring(lastEnd)
            g2d.color = Color.BLACK
            g2d.drawString(remainingText, x, y)
        }
    }

    /**
     * Converts token type to AWT color for printing.
     * Uses printer-friendly colors (darker, more contrast on white paper).
     */
    private fun tokenTypeToAwtColor(type: TokenType): Color = when (type) {
        TokenType.KEYWORD, TokenType.KEYWORD_MODIFIER, TokenType.KEYWORD_CONTROL ->
            Color(0x00, 0x00, 0xCC)  // Blue

        TokenType.STRING, TokenType.CHAR, TokenType.STRING_ESCAPE, TokenType.STRING_TEMPLATE ->
            Color(0x00, 0x88, 0x00)  // Green

        TokenType.COMMENT, TokenType.COMMENT_BLOCK, TokenType.COMMENT_DOC, TokenType.COMMENT_DOC_TAG ->
            Color(0x80, 0x80, 0x80)  // Gray

        TokenType.NUMBER, TokenType.BOOLEAN ->
            Color(0x00, 0x00, 0xCC)  // Blue

        TokenType.FUNCTION, TokenType.FUNCTION_CALL ->
            Color(0x79, 0x5E, 0x26)  // Brown

        TokenType.TYPE, TokenType.TYPE_PARAMETER, TokenType.INTERFACE, TokenType.ENUM ->
            Color(0x09, 0x50, 0x79)  // Teal

        TokenType.ANNOTATION ->
            Color(0x79, 0x5E, 0x26)  // Brown

        TokenType.ERROR, TokenType.ERROR_DEPRECATED ->
            Color(0xCC, 0x00, 0x00)  // Red

        else -> Color.BLACK
    }
}
