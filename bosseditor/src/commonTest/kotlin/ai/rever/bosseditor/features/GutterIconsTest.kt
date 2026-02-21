package ai.rever.bosseditor.features

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GutterIconsTest {

    @Test
    fun testGutterIconCreation() {
        val icon = GutterIcon(
            line = 5,
            type = GutterIconType.ERROR,
            tooltip = "Error message",
            priority = 1,
            action = "showError"
        )

        assertEquals(5, icon.line)
        assertEquals(GutterIconType.ERROR, icon.type)
        assertEquals("Error message", icon.tooltip)
        assertEquals(1, icon.priority)
        assertEquals("showError", icon.action)
    }

    @Test
    fun testRunIconFactory() {
        val icon = GutterIcon.run(10, "Run main")
        assertEquals(10, icon.line)
        assertEquals(GutterIconType.RUN, icon.type)
        assertEquals("Run main", icon.tooltip)
        assertEquals("run", icon.action)
        assertEquals(10, icon.priority) // Run has priority 10
    }

    @Test
    fun testDebugIconFactory() {
        val icon = GutterIcon.debug(15)
        assertEquals(15, icon.line)
        assertEquals(GutterIconType.DEBUG, icon.type)
        assertEquals("debug", icon.action)
    }

    @Test
    fun testBreakpointFactory() {
        val enabled = GutterIcon.breakpoint(20, enabled = true)
        assertEquals(GutterIconType.BREAKPOINT, enabled.type)
        assertEquals("toggleBreakpoint", enabled.action)
        assertEquals(5, enabled.priority) // Breakpoint has high priority

        val disabled = GutterIcon.breakpoint(20, enabled = false)
        assertEquals(GutterIconType.BREAKPOINT_DISABLED, disabled.type)
    }

    @Test
    fun testDiagnosticSeverityIconFactory() {
        val error = GutterIcon.fromDiagnosticSeverity(1, DiagnosticSeverity.ERROR, "Error")
        assertEquals(GutterIconType.ERROR, error.type)
        assertEquals(1, error.priority)

        val warning = GutterIcon.fromDiagnosticSeverity(2, DiagnosticSeverity.WARNING, "Warning")
        assertEquals(GutterIconType.WARNING, warning.type)
        assertEquals(2, warning.priority)

        val info = GutterIcon.fromDiagnosticSeverity(3, DiagnosticSeverity.INFO, "Info")
        assertEquals(GutterIconType.INFO, info.type)
        assertEquals(3, info.priority)

        val hint = GutterIcon.fromDiagnosticSeverity(4, DiagnosticSeverity.HINT, "Hint")
        assertEquals(GutterIconType.HINT, hint.type)
        assertEquals(4, hint.priority)
    }

    @Test
    fun testBookmarkFactory() {
        val bookmark = GutterIcon.bookmark(25, "Important")
        assertEquals(25, bookmark.line)
        assertEquals(GutterIconType.BOOKMARK, bookmark.type)
        assertEquals("Important", bookmark.tooltip)
        assertEquals("toggleBookmark", bookmark.action)
    }

    @Test
    fun testGutterIconManagerSetAndGet() {
        val manager = GutterIconManager()
        val icons = listOf(
            GutterIcon.run(0),
            GutterIcon.breakpoint(5),
            GutterIcon.fromDiagnosticSeverity(10, DiagnosticSeverity.ERROR, "Error")
        )

        manager.setIcons(icons)

        assertEquals(3, manager.getAllIcons().size)
    }

    @Test
    fun testGutterIconManagerGetByLine() {
        val manager = GutterIconManager()
        manager.setIcons(listOf(
            GutterIcon.run(0),
            GutterIcon.fromDiagnosticSeverity(0, DiagnosticSeverity.WARNING, "Warning"),
            GutterIcon.breakpoint(5)
        ))

        val line0Icons = manager.getIconsForLine(0)
        assertEquals(2, line0Icons.size)

        val line5Icons = manager.getIconsForLine(5)
        assertEquals(1, line5Icons.size)

        val line1Icons = manager.getIconsForLine(1)
        assertTrue(line1Icons.isEmpty())
    }

    @Test
    fun testGutterIconManagerPrioritySorting() {
        val manager = GutterIconManager()
        manager.setIcons(listOf(
            GutterIcon.run(0), // priority 10
            GutterIcon.fromDiagnosticSeverity(0, DiagnosticSeverity.ERROR, "Error"), // priority 1
            GutterIcon.bookmark(0) // priority 20
        ))

        val icons = manager.getIconsForLine(0)
        assertEquals(3, icons.size)

        // Should be sorted by priority (ascending)
        assertEquals(GutterIconType.ERROR, icons[0].type) // priority 1
        assertEquals(GutterIconType.RUN, icons[1].type)   // priority 10
        assertEquals(GutterIconType.BOOKMARK, icons[2].type) // priority 20
    }

    @Test
    fun testGutterIconManagerPrimaryIcon() {
        val manager = GutterIconManager()
        manager.setIcons(listOf(
            GutterIcon.run(0),
            GutterIcon.fromDiagnosticSeverity(0, DiagnosticSeverity.ERROR, "Error"),
            GutterIcon.bookmark(0)
        ))

        val primary = manager.getPrimaryIconForLine(0)
        assertNotNull(primary)
        assertEquals(GutterIconType.ERROR, primary.type) // Lowest priority = most important
    }

    @Test
    fun testGutterIconManagerToggleBreakpoint() {
        val manager = GutterIconManager()

        // Add breakpoint
        val added = manager.toggleBreakpoint(10)
        assertTrue(added)
        assertTrue(manager.hasBreakpoint(10))

        // Remove breakpoint
        val removed = manager.toggleBreakpoint(10)
        assertFalse(removed)
        assertFalse(manager.hasBreakpoint(10))
    }

    @Test
    fun testGutterIconManagerGetBreakpointLines() {
        val manager = GutterIconManager()
        manager.toggleBreakpoint(5)
        manager.toggleBreakpoint(10)
        manager.toggleBreakpoint(15)

        val lines = manager.getBreakpointLines()
        assertEquals(3, lines.size)
        assertTrue(lines.contains(5))
        assertTrue(lines.contains(10))
        assertTrue(lines.contains(15))
    }

    @Test
    fun testGutterIconManagerRemoveByType() {
        val manager = GutterIconManager()
        manager.setIcons(listOf(
            GutterIcon.run(0),
            GutterIcon.breakpoint(5),
            GutterIcon.breakpoint(10),
            GutterIcon.fromDiagnosticSeverity(15, DiagnosticSeverity.ERROR, "Error")
        ))

        assertEquals(4, manager.getAllIcons().size)

        manager.removeIconsOfType(GutterIconType.BREAKPOINT)

        assertEquals(2, manager.getAllIcons().size)
        assertFalse(manager.hasBreakpoint(5))
        assertFalse(manager.hasBreakpoint(10))
    }

    @Test
    fun testGutterIconManagerRemoveAtLine() {
        val manager = GutterIconManager()
        manager.setIcons(listOf(
            GutterIcon.run(0),
            GutterIcon.fromDiagnosticSeverity(0, DiagnosticSeverity.ERROR, "Error"),
            GutterIcon.breakpoint(5)
        ))

        // Remove all at line 0
        manager.removeIconAtLine(0)

        assertEquals(1, manager.getAllIcons().size)
        assertTrue(manager.getIconsForLine(0).isEmpty())
    }

    @Test
    fun testGutterIconManagerClear() {
        val manager = GutterIconManager()
        manager.setIcons(listOf(
            GutterIcon.run(0),
            GutterIcon.breakpoint(5)
        ))

        assertEquals(2, manager.getAllIcons().size)
        manager.clear()
        assertTrue(manager.getAllIcons().isEmpty())
    }

    @Test
    fun testGutterIconTypes() {
        assertEquals(14, GutterIconType.entries.size)
        assertTrue(GutterIconType.entries.contains(GutterIconType.RUN))
        assertTrue(GutterIconType.entries.contains(GutterIconType.DEBUG))
        assertTrue(GutterIconType.entries.contains(GutterIconType.BREAKPOINT))
        assertTrue(GutterIconType.entries.contains(GutterIconType.BREAKPOINT_DISABLED))
        assertTrue(GutterIconType.entries.contains(GutterIconType.ERROR))
        assertTrue(GutterIconType.entries.contains(GutterIconType.WARNING))
        assertTrue(GutterIconType.entries.contains(GutterIconType.INFO))
        assertTrue(GutterIconType.entries.contains(GutterIconType.HINT))
        assertTrue(GutterIconType.entries.contains(GutterIconType.FOLD_START))
        assertTrue(GutterIconType.entries.contains(GutterIconType.FOLD_END))
        assertTrue(GutterIconType.entries.contains(GutterIconType.BOOKMARK))
        assertTrue(GutterIconType.entries.contains(GutterIconType.OVERRIDE))
        assertTrue(GutterIconType.entries.contains(GutterIconType.RECURSIVE))
        assertTrue(GutterIconType.entries.contains(GutterIconType.CUSTOM))
    }
}
