package ai.rever.bosseditor.features

import ai.rever.bosseditor.core.EditorPosition
import ai.rever.bosseditor.core.EditorRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HyperlinksTest {

    @Test
    fun testHyperlinkCreation() {
        val range = EditorRange(
            EditorPosition(0, 5),
            EditorPosition(0, 25)
        )
        val hyperlink = Hyperlink(
            range = range,
            target = "https://example.com",
            type = HyperlinkType.URL,
            tooltip = "Open in browser"
        )

        assertEquals(range, hyperlink.range)
        assertEquals("https://example.com", hyperlink.target)
        assertEquals(HyperlinkType.URL, hyperlink.type)
        assertEquals("Open in browser", hyperlink.tooltip)
        assertEquals(0, hyperlink.startLine)
        assertEquals(0, hyperlink.endLine)
    }

    @Test
    fun testExtractUrls() {
        val text = "Check out https://example.com and http://test.org for more info"
        val hyperlinks = Hyperlink.extractUrls(text, lineOffset = 5)

        assertEquals(2, hyperlinks.size)

        val first = hyperlinks[0]
        assertEquals("https://example.com", first.target)
        assertEquals(HyperlinkType.URL, first.type)
        assertEquals(5, first.startLine) // Uses lineOffset

        val second = hyperlinks[1]
        assertEquals("http://test.org", second.target)
    }

    @Test
    fun testExtractUrlsEmpty() {
        val text = "No URLs here"
        val hyperlinks = Hyperlink.extractUrls(text)

        assertTrue(hyperlinks.isEmpty())
    }

    @Test
    fun testExtractIssueRefs() {
        val text = "Fixed #123 and JIRA-456 in this commit"
        val hyperlinks = Hyperlink.extractIssueRefs(text, lineOffset = 0)

        assertEquals(2, hyperlinks.size)

        val first = hyperlinks[0]
        assertEquals("#123", first.target)
        assertEquals(HyperlinkType.ISSUE, first.type)

        val second = hyperlinks[1]
        assertEquals("JIRA-456", second.target)
    }

    @Test
    fun testHyperlinkManagerSetAndGet() {
        val manager = HyperlinkManager()
        val hyperlinks = listOf(
            Hyperlink(
                range = EditorRange(EditorPosition(0, 0), EditorPosition(0, 20)),
                target = "https://example.com",
                type = HyperlinkType.URL
            ),
            Hyperlink(
                range = EditorRange(EditorPosition(1, 5), EditorPosition(1, 15)),
                target = "#123",
                type = HyperlinkType.ISSUE
            )
        )

        manager.setHyperlinks(hyperlinks)

        assertEquals(2, manager.getAllHyperlinks().size)
    }

    @Test
    fun testHyperlinkManagerGetByLine() {
        val manager = HyperlinkManager()
        manager.setHyperlinks(listOf(
            Hyperlink(
                range = EditorRange(EditorPosition(0, 0), EditorPosition(0, 20)),
                target = "https://example.com",
                type = HyperlinkType.URL
            ),
            Hyperlink(
                range = EditorRange(EditorPosition(0, 30), EditorPosition(0, 40)),
                target = "#123",
                type = HyperlinkType.ISSUE
            ),
            Hyperlink(
                range = EditorRange(EditorPosition(2, 0), EditorPosition(2, 10)),
                target = "file.kt",
                type = HyperlinkType.FILE_PATH
            )
        ))

        val line0Links = manager.getHyperlinksForLine(0)
        assertEquals(2, line0Links.size)

        val line2Links = manager.getHyperlinksForLine(2)
        assertEquals(1, line2Links.size)

        val line1Links = manager.getHyperlinksForLine(1)
        assertTrue(line1Links.isEmpty())
    }

    @Test
    fun testHyperlinkManagerGetAtPosition() {
        val manager = HyperlinkManager()
        manager.setHyperlinks(listOf(
            Hyperlink(
                range = EditorRange(EditorPosition(0, 10), EditorPosition(0, 30)),
                target = "https://example.com",
                type = HyperlinkType.URL
            )
        ))

        // Position inside hyperlink
        val insideLink = manager.getHyperlinkAtPosition(EditorPosition(0, 15))
        assertNotNull(insideLink)
        assertEquals("https://example.com", insideLink.target)

        // Position outside hyperlink
        val outsideLink = manager.getHyperlinkAtPosition(EditorPosition(0, 5))
        assertNull(outsideLink)

        // Position at start of hyperlink
        val atStartLink = manager.getHyperlinkAtPosition(EditorPosition(0, 10))
        assertNotNull(atStartLink)

        // Position at end of hyperlink (exclusive)
        val atEndLink = manager.getHyperlinkAtPosition(EditorPosition(0, 30))
        assertNull(atEndLink)
    }

    @Test
    fun testHyperlinkManagerClear() {
        val manager = HyperlinkManager()
        manager.setHyperlinks(listOf(
            Hyperlink(
                range = EditorRange(EditorPosition(0, 0), EditorPosition(0, 20)),
                target = "https://example.com",
                type = HyperlinkType.URL
            )
        ))

        assertEquals(1, manager.getAllHyperlinks().size)
        manager.clear()
        assertTrue(manager.getAllHyperlinks().isEmpty())
    }

    @Test
    fun testHyperlinkTypes() {
        assertEquals(5, HyperlinkType.entries.size)
        assertTrue(HyperlinkType.entries.contains(HyperlinkType.URL))
        assertTrue(HyperlinkType.entries.contains(HyperlinkType.FILE_PATH))
        assertTrue(HyperlinkType.entries.contains(HyperlinkType.IMPORT))
        assertTrue(HyperlinkType.entries.contains(HyperlinkType.SYMBOL))
        assertTrue(HyperlinkType.entries.contains(HyperlinkType.ISSUE))
    }
}
