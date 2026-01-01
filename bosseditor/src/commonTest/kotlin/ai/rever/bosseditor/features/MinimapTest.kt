package ai.rever.bosseditor.features

import ai.rever.bosseditor.core.EditorDocument
import ai.rever.bosseditor.core.OffsetRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MinimapTest {

    @Test
    fun testGetMinimapLines() {
        val doc = EditorDocument("line1\nline2\nline3")
        val minimap = Minimap(doc)

        val lines = minimap.getMinimapLines()

        assertEquals(3, lines.size)
        assertEquals(0, lines[0].lineNumber)
        assertEquals(5, lines[0].length)  // "line1" without newline
        assertEquals(1, lines[1].lineNumber)
        assertEquals(5, lines[1].length)  // "line2" without newline
        assertEquals(2, lines[2].lineNumber)
        assertEquals(5, lines[2].length)  // "line3" (last line)
    }

    @Test
    fun testGetMinimapLinesEmpty() {
        val doc = EditorDocument("")
        val minimap = Minimap(doc)

        val lines = minimap.getMinimapLines()

        assertEquals(1, lines.size)
        assertEquals(0, lines[0].lineNumber)
        assertEquals(0, lines[0].length)
        assertTrue(lines[0].isEmpty)
    }

    @Test
    fun testGetMinimapLinesWithEmptyLines() {
        val doc = EditorDocument("line1\n\nline3")
        val minimap = Minimap(doc)

        val lines = minimap.getMinimapLines()

        assertEquals(3, lines.size)
        assertEquals(5, lines[0].length)
        assertEquals(0, lines[1].length)  // Empty line
        assertTrue(lines[1].isEmpty)
        assertEquals(5, lines[2].length)
    }

    @Test
    fun testGetLineFromY() {
        val doc = EditorDocument("line1\nline2\nline3\nline4\nline5")
        val minimap = Minimap(doc)

        // With height 100 and 5 lines, each line is 20 pixels
        assertEquals(0, minimap.getLineFromY(0f, 100f))
        assertEquals(0, minimap.getLineFromY(10f, 100f))  // First half of line 0
        assertEquals(1, minimap.getLineFromY(25f, 100f))  // In line 1
        assertEquals(2, minimap.getLineFromY(50f, 100f))  // In line 2
        assertEquals(4, minimap.getLineFromY(95f, 100f))  // Last line
    }

    @Test
    fun testGetLineFromYClampsToBounds() {
        val doc = EditorDocument("line1\nline2\nline3")
        val minimap = Minimap(doc)

        // Should clamp to 0 and max line
        assertEquals(0, minimap.getLineFromY(-10f, 100f))
        assertEquals(2, minimap.getLineFromY(150f, 100f))
    }

    @Test
    fun testGetLineFromYEmptyDocument() {
        val doc = EditorDocument("")
        val minimap = Minimap(doc)

        assertEquals(0, minimap.getLineFromY(50f, 100f))
    }

    @Test
    fun testGetViewportBounds() {
        val doc = EditorDocument("line1\nline2\nline3\nline4\nline5")
        val minimap = Minimap(doc)

        // First visible line 1, 2 visible lines, height 100
        val bounds = minimap.getViewportBounds(1, 2, 100f)

        // With 5 lines, each line is 20 pixels
        assertEquals(20f, bounds.y)
        assertEquals(40f, bounds.height)
    }

    @Test
    fun testGetViewportBoundsFullDocument() {
        val doc = EditorDocument("line1\nline2\nline3")
        val minimap = Minimap(doc)

        // All lines visible
        val bounds = minimap.getViewportBounds(0, 3, 100f)

        assertEquals(0f, bounds.y)
        assertEquals(100f, bounds.height)
    }

    @Test
    fun testGetViewportBoundsEmptyDocument() {
        val doc = EditorDocument("")
        val minimap = Minimap(doc)

        val bounds = minimap.getViewportBounds(0, 30, 100f)

        assertEquals(0f, bounds.y)
        assertEquals(100f, bounds.height)
    }

    @Test
    fun testGetViewportBoundsClamp() {
        val doc = EditorDocument("line1\nline2\nline3\nline4\nline5")
        val minimap = Minimap(doc)

        // Viewport extends past end
        val bounds = minimap.getViewportBounds(4, 10, 100f)

        // Should clamp height to remaining space
        assertEquals(80f, bounds.y)  // Line 4 starts at 80%
        assertTrue(bounds.height <= 20f)  // Only 1 line left
    }

    @Test
    fun testGetSearchHighlights() {
        val doc = EditorDocument("foo bar foo baz foo")
        val minimap = Minimap(doc)

        val searchResults = listOf(
            OffsetRange(0, 3),   // First "foo"
            OffsetRange(8, 11),  // Second "foo"
            OffsetRange(16, 19) // Third "foo"
        )

        val highlights = minimap.getSearchHighlights(searchResults, 100f)

        // All on line 0, so all should have y = 0
        assertEquals(3, highlights.size)
        highlights.forEach { highlight ->
            assertEquals(0f, highlight.y)
            assertEquals(HighlightType.SEARCH, highlight.type)
        }
    }

    @Test
    fun testGetSearchHighlightsMultipleLines() {
        val doc = EditorDocument("foo\nbar\nfoo")
        val minimap = Minimap(doc)

        val searchResults = listOf(
            OffsetRange(0, 3),  // "foo" on line 0
            OffsetRange(8, 11) // "foo" on line 2
        )

        val highlights = minimap.getSearchHighlights(searchResults, 100f)

        assertEquals(2, highlights.size)
        // Line 0: y = 0, Line 2: y = 66.67
        assertTrue(highlights[0].y < highlights[1].y)
    }

    @Test
    fun testGetSelectionHighlights() {
        val doc = EditorDocument("line1\nline2\nline3")
        val minimap = Minimap(doc)

        // Select "line2"
        val selection = OffsetRange(6, 11)
        val highlights = minimap.getSelectionHighlights(selection, 100f)

        assertEquals(1, highlights.size)
        assertEquals(HighlightType.SELECTION, highlights[0].type)
    }

    @Test
    fun testGetSelectionHighlightsMultiLine() {
        val doc = EditorDocument("line1\nline2\nline3")
        val minimap = Minimap(doc)

        // Select from line1 to line3
        val selection = OffsetRange(0, 17)
        val highlights = minimap.getSelectionHighlights(selection, 100f)

        assertEquals(3, highlights.size)
        highlights.forEach { highlight ->
            assertEquals(HighlightType.SELECTION, highlight.type)
        }
    }

    @Test
    fun testGetSelectionHighlightsNull() {
        val doc = EditorDocument("line1\nline2\nline3")
        val minimap = Minimap(doc)

        val highlights = minimap.getSelectionHighlights(null, 100f)

        assertTrue(highlights.isEmpty())
    }

    @Test
    fun testGetSelectionHighlightsEmpty() {
        val doc = EditorDocument("line1\nline2\nline3")
        val minimap = Minimap(doc)

        val selection = OffsetRange(5, 5)  // Empty selection
        val highlights = minimap.getSelectionHighlights(selection, 100f)

        assertTrue(highlights.isEmpty())
    }

    @Test
    fun testGetOccurrenceHighlights() {
        val doc = EditorDocument("foo bar foo")
        val minimap = Minimap(doc)

        val occurrences = listOf(
            OffsetRange(0, 3),
            OffsetRange(8, 11)
        )

        val highlights = minimap.getOccurrenceHighlights(occurrences, 100f)

        assertEquals(2, highlights.size)
        highlights.forEach { highlight ->
            assertEquals(HighlightType.OCCURRENCE, highlight.type)
        }
    }

    @Test
    fun testGetDiagnosticMarkers() {
        val doc = EditorDocument("line1\nline2\nline3")
        val minimap = Minimap(doc)

        val diagnostics = listOf(
            DiagnosticInfo(OffsetRange(0, 5), DiagnosticSeverity.ERROR, "Error"),
            DiagnosticInfo(OffsetRange(6, 11), DiagnosticSeverity.WARNING, "Warning")
        )

        val markers = minimap.getDiagnosticMarkers(diagnostics, 100f)

        assertEquals(2, markers.size)
        assertEquals(DiagnosticSeverity.ERROR, markers[0].severity)
        assertEquals(DiagnosticSeverity.WARNING, markers[1].severity)
    }

    @Test
    fun testCalculateWidth() {
        val doc = EditorDocument("short\nvery very long line here\na")
        val minimap = Minimap(doc)

        val width = minimap.calculateWidth(200f)

        // Should be based on longest line length
        assertTrue(width >= minimap.config.minWidth)
        assertTrue(width <= 200f)
    }

    @Test
    fun testCalculateWidthEmptyDocument() {
        val doc = EditorDocument("")
        val minimap = Minimap(doc)

        val width = minimap.calculateWidth(200f)

        assertEquals(minimap.config.minWidth, width)
    }

    @Test
    fun testMinimapConfig() {
        val config = MinimapConfig(
            scale = 0.15f,
            charWidth = 2.0f,
            lineHeight = 3f,
            minWidth = 60f,
            maxWidth = 150f,
            showSlider = true,
            renderCharacters = true,
            showSearchHighlights = true,
            showSelection = true,
            enabled = true
        )

        assertEquals(0.15f, config.scale)
        assertEquals(2.0f, config.charWidth)
        assertEquals(3f, config.lineHeight)
        assertEquals(60f, config.minWidth)
        assertEquals(150f, config.maxWidth)
        assertTrue(config.showSlider)
        assertTrue(config.renderCharacters)
        assertTrue(config.showSearchHighlights)
        assertTrue(config.showSelection)
        assertTrue(config.enabled)
    }

    @Test
    fun testMinimapConfigDefaults() {
        val config = MinimapConfig()

        assertEquals(0.1f, config.scale)
        assertEquals(1.5f, config.charWidth)
        assertEquals(2f, config.lineHeight)
        assertEquals(50f, config.minWidth)
        assertEquals(120f, config.maxWidth)
        assertTrue(config.showSlider)
        assertEquals(false, config.renderCharacters)
        assertTrue(config.showSearchHighlights)
        assertTrue(config.showSelection)
        assertTrue(config.enabled)
    }

    @Test
    fun testMinimapLine() {
        val line = MinimapLine(lineNumber = 5, length = 40, isEmpty = false)

        assertEquals(5, line.lineNumber)
        assertEquals(40, line.length)
        assertEquals(false, line.isEmpty)
    }

    @Test
    fun testViewportBounds() {
        val bounds = ViewportBounds(y = 25f, height = 50f)

        assertEquals(25f, bounds.y)
        assertEquals(50f, bounds.height)
    }

    @Test
    fun testMinimapHighlight() {
        val highlight = MinimapHighlight(y = 10f, height = 5f, type = HighlightType.SEARCH)

        assertEquals(10f, highlight.y)
        assertEquals(5f, highlight.height)
        assertEquals(HighlightType.SEARCH, highlight.type)
    }

    @Test
    fun testHighlightTypes() {
        val types = HighlightType.values()

        assertTrue(types.contains(HighlightType.SEARCH))
        assertTrue(types.contains(HighlightType.SELECTION))
        assertTrue(types.contains(HighlightType.OCCURRENCE))
        assertTrue(types.contains(HighlightType.MODIFIED))
        assertTrue(types.contains(HighlightType.ADDED))
        assertTrue(types.contains(HighlightType.DELETED))
    }

    @Test
    fun testMinimapMarker() {
        val marker = MinimapMarker(y = 50f, severity = DiagnosticSeverity.ERROR)

        assertEquals(50f, marker.y)
        assertEquals(DiagnosticSeverity.ERROR, marker.severity)
    }

    @Test
    fun testDiagnosticInfo() {
        val info = DiagnosticInfo(
            range = OffsetRange(0, 10),
            severity = DiagnosticSeverity.WARNING,
            message = "Test warning"
        )

        assertEquals(0, info.range.start)
        assertEquals(10, info.range.end)
        assertEquals(DiagnosticSeverity.WARNING, info.severity)
        assertEquals("Test warning", info.message)
    }

    @Test
    fun testDiagnosticSeverity() {
        val severities = DiagnosticSeverity.values()

        assertTrue(severities.contains(DiagnosticSeverity.ERROR))
        assertTrue(severities.contains(DiagnosticSeverity.WARNING))
        assertTrue(severities.contains(DiagnosticSeverity.INFO))
        assertTrue(severities.contains(DiagnosticSeverity.HINT))
    }

    @Test
    fun testSetConfig() {
        val doc = EditorDocument("line1\nline2")
        val minimap = Minimap(doc)

        val newConfig = MinimapConfig(
            scale = 0.2f,
            minWidth = 80f
        )
        minimap.config = newConfig

        assertEquals(0.2f, minimap.config.scale)
        assertEquals(80f, minimap.config.minWidth)
    }

    @Test
    fun testLargeDocumentLineCount() {
        // Create document with 1000 lines
        val lines = (1..1000).joinToString("\n") { "line$it" }
        val doc = EditorDocument(lines)
        val minimap = Minimap(doc)

        val minimapLines = minimap.getMinimapLines()

        assertEquals(1000, minimapLines.size)
        assertEquals(0, minimapLines.first().lineNumber)
        assertEquals(999, minimapLines.last().lineNumber)
    }

    @Test
    fun testGetLineFromYPrecision() {
        val doc = EditorDocument("a\nb\nc\nd\ne\nf\ng\nh\ni\nj")  // 10 lines
        val minimap = Minimap(doc)

        // With 10 lines and height 100, each line is 10 pixels
        assertEquals(0, minimap.getLineFromY(5f, 100f))   // Middle of line 0
        assertEquals(1, minimap.getLineFromY(15f, 100f))  // Middle of line 1
        assertEquals(5, minimap.getLineFromY(55f, 100f))  // Middle of line 5
        assertEquals(9, minimap.getLineFromY(95f, 100f))  // Middle of line 9
    }
}
