package ai.rever.bosseditor.lsp.providers

import ai.rever.bosseditor.core.EditorPosition
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompletionTriggerTest {

    @Test
    fun testManualTrigger() {
        val trigger = CompletionTrigger()
        val result = trigger.shouldTrigger(null, EditorPosition(0, 0), "")

        assertIs<TriggerResult.Trigger>(result)
        assertEquals(TriggerKind.MANUAL, result.kind)
    }

    @Test
    fun testTriggerCharacterDot() {
        val trigger = CompletionTrigger(triggerCharacters = listOf('.'))
        val result = trigger.shouldTrigger('.', EditorPosition(0, 5), "foo.")

        assertIs<TriggerResult.Trigger>(result)
        assertEquals(TriggerKind.CHARACTER, result.kind)
        assertEquals('.', result.triggerCharacter)
    }

    @Test
    fun testTriggerCharacterColon() {
        val trigger = CompletionTrigger(triggerCharacters = listOf(':'))
        val result = trigger.shouldTrigger(':', EditorPosition(0, 5), "std:")

        assertIs<TriggerResult.Trigger>(result)
        assertEquals(TriggerKind.CHARACTER, result.kind)
        assertEquals(':', result.triggerCharacter)
    }

    @Test
    fun testNoTriggerOnRegularChar() {
        val trigger = CompletionTrigger(autoTriggerMinChars = 5)
        val result = trigger.shouldTrigger('a', EditorPosition(0, 1), "a")

        assertIs<TriggerResult.NoTrigger>(result)
    }

    @Test
    fun testAutoTriggerAfterMinChars() {
        val trigger = CompletionTrigger(autoTriggerMinChars = 3)
        val result = trigger.shouldTrigger('d', EditorPosition(0, 4), "word")

        assertIs<TriggerResult.Trigger>(result)
        assertEquals(TriggerKind.AUTO, result.kind)
        assertEquals("word", result.prefix)
    }

    @Test
    fun testNoAutoTriggerBeforeMinChars() {
        val trigger = CompletionTrigger(autoTriggerMinChars = 5)
        val result = trigger.shouldTrigger('d', EditorPosition(0, 4), "word")

        assertIs<TriggerResult.NoTrigger>(result)
    }

    @Test
    fun testAutoTriggerDisabled() {
        val trigger = CompletionTrigger(autoTriggerEnabled = false, autoTriggerMinChars = 1)
        val result = trigger.shouldTrigger('d', EditorPosition(0, 4), "word")

        assertIs<TriggerResult.NoTrigger>(result)
    }

    @Test
    fun testCancelOnNewline() {
        val trigger = CompletionTrigger()
        val result = trigger.shouldTrigger('\n', EditorPosition(0, 0), "")

        assertIs<TriggerResult.Cancel>(result)
    }

    @Test
    fun testCancelOnTab() {
        val trigger = CompletionTrigger()
        val result = trigger.shouldTrigger('\t', EditorPosition(0, 0), "")

        assertIs<TriggerResult.Cancel>(result)
    }

    @Test
    fun testShouldCancelOnWhitespace() {
        val trigger = CompletionTrigger()
        assertTrue(trigger.shouldCancel(' ', "prefix"))
    }

    @Test
    fun testShouldNotCancelOnIdentifierChar() {
        val trigger = CompletionTrigger()
        assertFalse(trigger.shouldCancel('a', "prefix"))
        assertFalse(trigger.shouldCancel('Z', "prefix"))
        assertFalse(trigger.shouldCancel('5', "prefix"))
        assertFalse(trigger.shouldCancel('_', "prefix"))
    }

    @Test
    fun testShouldNotCancelOnTriggerChar() {
        val trigger = CompletionTrigger(triggerCharacters = listOf('.'))
        assertFalse(trigger.shouldCancel('.', "prefix"))
    }

    @Test
    fun testGetIdentifierPrefixSimple() {
        val trigger = CompletionTrigger()
        val prefix = trigger.getIdentifierPrefix("hello", 5)
        assertEquals("hello", prefix)
    }

    @Test
    fun testGetIdentifierPrefixMiddle() {
        val trigger = CompletionTrigger()
        val prefix = trigger.getIdentifierPrefix("foo.bar", 7)
        assertEquals("bar", prefix)
    }

    @Test
    fun testGetIdentifierPrefixAfterDot() {
        val trigger = CompletionTrigger()
        val prefix = trigger.getIdentifierPrefix("foo.", 4)
        assertEquals("", prefix)
    }

    @Test
    fun testGetIdentifierPrefixWithUnderscore() {
        val trigger = CompletionTrigger()
        val prefix = trigger.getIdentifierPrefix("my_variable", 11)
        assertEquals("my_variable", prefix)
    }

    @Test
    fun testGetIdentifierPrefixWithNumbers() {
        val trigger = CompletionTrigger()
        val prefix = trigger.getIdentifierPrefix("var123", 6)
        assertEquals("var123", prefix)
    }

    @Test
    fun testGetIdentifierPrefixEmptyLine() {
        val trigger = CompletionTrigger()
        val prefix = trigger.getIdentifierPrefix("", 0)
        assertEquals("", prefix)
    }

    @Test
    fun testGetIdentifierPrefixOutOfBounds() {
        val trigger = CompletionTrigger()
        val prefix = trigger.getIdentifierPrefix("hello", 10)
        assertEquals("", prefix)
    }

    @Test
    fun testGetTriggerCharacterAtPosition() {
        val trigger = CompletionTrigger(triggerCharacters = listOf('.', ':'))

        assertEquals('.', trigger.getTriggerCharacterAt("foo.", 4))
        assertEquals(':', trigger.getTriggerCharacterAt("std:", 4))
        assertNull(trigger.getTriggerCharacterAt("foo", 3))
        assertNull(trigger.getTriggerCharacterAt("", 0))
    }

    @Test
    fun testFromServerCapabilities() {
        val trigger = CompletionTrigger.fromServerCapabilities(
            triggerCharacters = listOf('.', ':', '<'),
            autoTriggerMinChars = 2
        )

        val result = trigger.shouldTrigger('.', EditorPosition(0, 3), "foo.")
        assertIs<TriggerResult.Trigger>(result)
        assertEquals(TriggerKind.CHARACTER, result.kind)
    }

    @Test
    fun testFromServerCapabilitiesEmpty() {
        val trigger = CompletionTrigger.fromServerCapabilities(
            triggerCharacters = emptyList()
        )

        // Should use default trigger characters
        val result = trigger.shouldTrigger('.', EditorPosition(0, 3), "foo.")
        assertIs<TriggerResult.Trigger>(result)
    }

    @Test
    fun testDefaultTriggerCharacters() {
        val defaults = CompletionTrigger.DEFAULT_TRIGGER_CHARACTERS
        assertTrue(defaults.contains('.'))
        assertTrue(defaults.contains(':'))
        assertTrue(defaults.contains('<'))
        assertTrue(defaults.contains('('))
    }

    @Test
    fun testDefaultCancelCharacters() {
        val defaults = CompletionTrigger.DEFAULT_CANCEL_CHARACTERS
        assertTrue(defaults.contains('\n'))
        assertTrue(defaults.contains('\r'))
        assertTrue(defaults.contains('\t'))
    }
}

class CompletionSessionTest {

    @Test
    fun testSessionCreation() {
        val session = CompletionSession(
            documentUri = "file:///test.kt",
            triggerPosition = EditorPosition(5, 10),
            triggerKind = TriggerKind.CHARACTER,
            triggerCharacter = '.',
            initialPrefix = ""
        )

        assertEquals("file:///test.kt", session.documentUri)
        assertEquals(5, session.triggerPosition.line)
        assertEquals(10, session.triggerPosition.column)
        assertEquals(TriggerKind.CHARACTER, session.triggerKind)
        assertEquals('.', session.triggerCharacter)
        assertEquals("", session.currentPrefix)
        assertTrue(session.isActive)
    }

    @Test
    fun testUpdatePrefix() {
        val session = CompletionSession(
            documentUri = "file:///test.kt",
            triggerPosition = EditorPosition(0, 5),
            triggerKind = TriggerKind.AUTO,
            initialPrefix = "get"
        )

        assertEquals("get", session.currentPrefix)

        session.updatePrefix("getV")
        assertEquals("getV", session.currentPrefix)

        session.updatePrefix("getValue")
        assertEquals("getValue", session.currentPrefix)
    }

    @Test
    fun testCancel() {
        val session = CompletionSession(
            documentUri = "file:///test.kt",
            triggerPosition = EditorPosition(0, 0),
            triggerKind = TriggerKind.MANUAL
        )

        assertTrue(session.isActive)
        session.cancel()
        assertFalse(session.isActive)
    }

    @Test
    fun testIsValidPositionSameLine() {
        val session = CompletionSession(
            documentUri = "file:///test.kt",
            triggerPosition = EditorPosition(5, 10),
            triggerKind = TriggerKind.CHARACTER
        )

        assertTrue(session.isValidPosition(EditorPosition(5, 10), "some text here"))
        assertTrue(session.isValidPosition(EditorPosition(5, 15), "some text here"))
        assertTrue(session.isValidPosition(EditorPosition(5, 20), "some text here"))
    }

    @Test
    fun testIsValidPositionDifferentLine() {
        val session = CompletionSession(
            documentUri = "file:///test.kt",
            triggerPosition = EditorPosition(5, 10),
            triggerKind = TriggerKind.CHARACTER
        )

        assertFalse(session.isValidPosition(EditorPosition(4, 10), "some text"))
        assertFalse(session.isValidPosition(EditorPosition(6, 10), "some text"))
    }

    @Test
    fun testIsValidPositionBeforeTrigger() {
        val session = CompletionSession(
            documentUri = "file:///test.kt",
            triggerPosition = EditorPosition(5, 10),
            triggerKind = TriggerKind.CHARACTER
        )

        assertFalse(session.isValidPosition(EditorPosition(5, 5), "some text"))
        assertFalse(session.isValidPosition(EditorPosition(5, 9), "some text"))
    }
}

class CompletionDebouncerTest {

    @Test
    fun testInitiallyAllowsRequest() {
        val debouncer = CompletionDebouncer(delayMs = 100)
        assertTrue(debouncer.shouldRequest())
    }

    @Test
    fun testBlocksRequestAfterMark() {
        val debouncer = CompletionDebouncer(delayMs = 1000)
        debouncer.markRequested()
        assertFalse(debouncer.shouldRequest())
    }

    @Test
    fun testAllowsRequestAfterDelay() = runBlocking {
        val debouncer = CompletionDebouncer(delayMs = 10)
        debouncer.markRequested()
        delay(20)
        assertTrue(debouncer.shouldRequest())
    }

    @Test
    fun testReset() {
        val debouncer = CompletionDebouncer(delayMs = 1000)
        debouncer.markRequested()
        assertFalse(debouncer.shouldRequest())

        debouncer.reset()
        assertTrue(debouncer.shouldRequest())
    }
}
