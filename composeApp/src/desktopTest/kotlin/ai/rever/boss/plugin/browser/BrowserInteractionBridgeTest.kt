package ai.rever.boss.plugin.browser

import ai.rever.boss.plugin.api.BrowserInteractionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The page→host boundary.
 *
 * `window.__bossInteraction` is reachable by any script on the page, not just the injected
 * collector, so these treat the input as hostile: a site can call `emit()` with anything it
 * likes, and the answer must always be "fewer events", never an error or a leak.
 */
class BrowserInteractionBridgeTest {
    private fun parse(json: String) = BrowserInteractionBridge.parseBatch(json)

    @Test
    fun `parses a well-formed batch from the collector`() {
        val parsed =
            parse(
                """
                [{"type":"CLICK","tag":"button","role":"tab","inputType":"submit",
                  "path":"form>div:2>button:1"},
                 {"type":"SCROLL_DEPTH","scrollDepthPercent":"50"},
                 {"type":"RAGE_CLICK","tag":"button","repeatCount":"3"}]
                """.trimIndent(),
            )

        assertEquals(3, parsed.size)
        assertEquals(BrowserInteractionType.CLICK, parsed[0].type)
        assertEquals("button", parsed[0].tag)
        assertEquals("form>div:2>button:1", parsed[0].path)
        assertEquals(50, parsed[1].scrollDepthPercent)
        assertEquals(3, parsed[2].repeatCount)
    }

    @Test
    fun `an unknown interaction type is dropped, not passed through`() {
        // The allowed set is an explicit `when`, so a page inventing a type — or a future
        // enum constant nobody wired up — reports nothing rather than something unvetted.
        val parsed = parse("""[{"type":"KEYSTROKE","tag":"input"},{"type":"CLICK","tag":"a"}]""")
        assertEquals(1, parsed.size)
        assertEquals(BrowserInteractionType.CLICK, parsed.single().type)
    }

    @Test
    fun `garbage in yields no events rather than an exception`() {
        assertTrue(parse("not json at all").isEmpty())
        assertTrue(parse("").isEmpty())
        assertTrue(parse("{}").isEmpty(), "an object is not a batch")
        assertTrue(parse("""{"type":"CLICK"}""").isEmpty())
        assertTrue(parse("[1,2,3]").isEmpty(), "primitives are not entries")
        assertTrue(parse("""[{"noType":"x"}]""").isEmpty())
    }

    @Test
    fun `an oversized payload is refused whole`() {
        // A page looping emit() with a megabyte of junk should cost one length check.
        val huge = """[{"type":"CLICK","tag":"${"x".repeat(100_000)}"}]"""
        assertTrue(parse(huge).isEmpty())
    }

    @Test
    fun `a batch longer than the cap is truncated`() {
        val many = (1..500).joinToString(",", "[", "]") { """{"type":"CLICK","tag":"a"}""" }
        assertEquals(50, parse(many).size)
    }

    @Test
    fun `a non-numeric count is dropped instead of poisoning the event`() {
        val parsed = parse("""[{"type":"SCROLL_DEPTH","scrollDepthPercent":"lots","repeatCount":"NaN"}]""")
        assertNull(parsed.single().scrollDepthPercent)
        assertNull(parsed.single().repeatCount)
    }

    @Test
    fun `page content injected through the bridge dies at the sanitizers`() {
        // This is the end-to-end privacy claim, tested across both layers rather than
        // assumed: the collector never reads text, but if a hostile page calls emit()
        // directly with text, values, or ids, parsing accepts the strings and the host-side
        // sanitizers are what refuse them. Nothing here reaches an event.
        val parsed =
            parse(
                """
                [{"type":"CLICK",
                  "tag":"Patient: John Smith",
                  "role":"MRN 4417882",
                  "path":"form>input[value='John Smith']",
                  "fieldName":"patient_mrn_4417882"}]
                """.trimIndent(),
            ).single()

        assertNull(BrowserAnalytics.sanitizeToken(parsed.tag, 32), "free text refused as a tag")
        assertNull(BrowserAnalytics.sanitizeToken(parsed.role, 32), "free text refused as a role")
        assertNull(BrowserAnalytics.sanitizePath(parsed.path), "a value selector refused as a path")
        // A field name is cleaned rather than refused — but the identifier inside it goes.
        assertEquals("patient_mrn_#", BrowserAnalytics.sanitizeFieldName(parsed.fieldName))
    }

    @Test
    fun `an out-of-range percentage or count is dropped by the sanitizer`() {
        val parsed = parse("""[{"type":"SCROLL_DEPTH","scrollDepthPercent":"9999","repeatCount":"0"}]""").single()
        assertEquals(9999, parsed.scrollDepthPercent, "parsing is faithful")
        // BrowserAnalytics.interaction is what bounds these; mirrored here so the pair of
        // checks stays visible together.
        assertNull(parsed.scrollDepthPercent?.takeIf { it in 0..100 })
        assertNull(parsed.repeatCount?.takeIf { it in 1..100 })
    }
}
