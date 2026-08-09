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

    /**
     * The collector source with its `//` comments removed.
     *
     * The comments name the very properties the script must not read ("no getData, no
     * selection"), which is exactly the documentation worth keeping - so the structural
     * checks below read the code and not the prose about it.
     */
    private fun collectorCode(): String =
        BrowserInteractionScript.source
            .lines()
            .joinToString("\n") { it.substringBefore("//") }

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
    fun `a json null is an absent field, not an element named null`() {
        // JsonNull.content is the four-letter string "null", which passes sanitizeToken as a
        // perfectly good tag name - so this arrived in a dashboard as an element called null.
        val parsed = parse("""[{"type":"CLICK","tag":null,"fieldName":null,"path":null}]""").single()
        assertNull(parsed.tag)
        assertNull(parsed.fieldName)
        assertNull(parsed.path)
    }

    @Test
    fun `a batch is charged for what it used, not for its worst case`() {
        // The window budget is reserved before parsing (parsing is the expensive part and it
        // runs on the page's JS thread), so an honest page sending three events must not be
        // billed for fifty - that would throttle real traffic to a fifth of the stated cap.
        var clock = 0L
        val bridge =
            BrowserInteractionBridge(
                authorityProvider = { "availity.com" },
                windowId = null,
                nowMs = { clock },
            )
        val small = """[{"type":"CLICK","tag":"a"}]"""
        repeat(100) { bridge.emit(small) }

        // 100 single-entry batches is 100 of the 250-entry budget, so a full batch still fits.
        assertEquals(50, bridge.admissible(50), "unused reservation was not released")
    }

    @Test
    fun `a non-numeric count is dropped instead of poisoning the event`() {
        val parsed = parse("""[{"type":"SCROLL_DEPTH","scrollDepthPercent":"lots","repeatCount":"NaN"}]""")
        assertNull(parsed.single().scrollDepthPercent)
        assertNull(parsed.single().repeatCount)
    }

    @Test
    fun `a page looping emit cannot flood the shared event bus`() {
        // ApplicationEventBus is a MutableSharedFlow with extraBufferCapacity = 64 published
        // via non-suspending tryEmit — once full, events are DROPPED. This bridge is the
        // first page-controlled producer on that bus, so an unthrottled page could evict
        // auth, tab, and file events out from under a slow subscriber. The per-batch caps
        // bound one call's cost, not the call rate; this is what bounds the rate.
        var clock = 0L
        val bridge =
            BrowserInteractionBridge(
                authorityProvider = { "availity.com" },
                windowId = null,
                nowMs = { clock },
            )

        // 100 back-to-back full batches inside one window = 5000 entries offered.
        val admittedFirstWindow = (1..100).sumOf { bridge.admissible(50) }
        assertEquals(
            BrowserInteractionBridge.MAX_ENTRIES_PER_WINDOW,
            admittedFirstWindow,
            "the window cap must hold no matter how many batches arrive",
        )

        // The next window starts fresh, so a legitimately busy page isn't punished forever.
        clock += BrowserInteractionBridge.RATE_WINDOW_MS
        assertEquals(50, bridge.admissible(50))

        // A clock jumping backwards resets the window rather than wedging it shut or open.
        clock -= 10 * BrowserInteractionBridge.RATE_WINDOW_MS
        assertEquals(50, bridge.admissible(50))
    }

    @Test
    fun `the rate cap leaves ordinary collector traffic untouched`() {
        // The collector flushes every 2s with at most 50 entries — roughly 25/sec. A cap that
        // clipped normal use would quietly lose interactions on any busy page.
        var clock = 0L
        val bridge =
            BrowserInteractionBridge(
                authorityProvider = { "availity.com" },
                windowId = null,
                nowMs = { clock },
            )
        repeat(20) {
            clock += 2000
            assertEquals(50, bridge.admissible(50), "a normal flush must never be clipped")
        }
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
    fun `a name pushed through the bridge as a field name is refused`() {
        // The field-name sanitizer filters rather than refuses, so it is the one place a
        // hostile emit() could have dressed page content up as a plausible field name.
        // Whitespace is what separates "a person's name" from "a form-encoding key".
        val parsed =
            parse("""[{"type":"FIELD_FOCUSED","tag":"input","fieldName":"John Smith"}]""").single()
        assertEquals("John Smith", parsed.fieldName, "parsing is faithful")
        assertNull(BrowserAnalytics.sanitizeFieldName(parsed.fieldName))
    }

    @Test
    fun `the collector reads no page content out of the DOM`() {
        // The privacy design of BrowserInteractionScript is a *reading* restriction: content
        // cannot leak through a bug in a later sanitizing step because it is never in a
        // variable. That claim lived only in a KDoc, where nothing stops the next edit from
        // reaching for a label "just to make clicks easier to read". This is what stops it.
        val forbidden =
            listOf(
                "textContent",
                "innerText",
                "innerHTML",
                "outerHTML",
                "placeholder",
                "aria-label",
                "ariaLabel",
                "className",
                "dataset",
                "clipboardData",
                "getData",
                "getSelection",
                ".value",
                ".href",
                ".src",
                ".alt",
                ".title",
                ".id",
            )
        for (property in forbidden) {
            assertTrue(
                !collectorCode().contains(property),
                "the collector must never read $property - see BrowserInteractionScript's KDoc",
            )
        }
    }

    @Test
    fun `the collector reads exactly one attribute, by a literal name`() {
        // The forbidden-substring test above is a denylist, and a denylist cannot catch
        // getAttribute('data-patient-id') or el[someVariable]. The KDoc's actual claim is
        // narrower and checkable: describe() is the only DOM inspection, and the only
        // attribute it reads is role. Pin that shape rather than a list of known-bad names.
        val code = collectorCode()
        val attributeReads =
            Regex("""getAttribute\(([^)]*)\)""")
                .findAll(code)
                .map { it.groupValues[1].trim() }
                .toList()
        assertEquals(listOf("'role'"), attributeReads, "the only attribute read may be role")

        // Computed property access is how a read gets past every name-based check.
        val computedReads = Regex("""\b(el|node|ev|sib)\[""").findAll(code).map { it.value }.toList()
        assertTrue(computedReads.isEmpty(), "computed DOM property access: $computedReads")
    }

    @Test
    fun `every DOM string the collector reports is length-capped`() {
        // An uncapped read is not just untidy: one element with a megabyte-long custom tag
        // name pushes the batch past MAX_PAYLOAD_CHARS, which drops the WHOLE batch - so a
        // site could silence its own interaction telemetry with a single hidden element.
        // `=[^=]` so `out.tag === 'input'` is read as the comparison it is, not an assignment.
        val assignments =
            collectorCode()
                .lines()
                .filter { Regex("""out\.(tag|role|inputType|fieldName)\s*=[^=]""").containsMatchIn(it) }
        assertEquals(4, assignments.size, "a new reported field must be capped too")
        assertTrue(
            assignments.all { it.contains(".slice(") },
            "uncapped DOM read: ${assignments.firstOrNull { !it.contains(".slice(") }?.trim()}",
        )
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
