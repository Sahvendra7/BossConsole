package ai.rever.boss.plugin.browser

import ai.rever.boss.plugin.api.BrowserInteractionType
import com.teamdev.jxbrowser.js.JsAccessible
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Page→host bridge for in-page interaction telemetry.
 *
 * An instance is published on each page's `window.__bossInteraction` and the injected
 * [BrowserInteractionScript] calls [emit] with a JSON batch. This class parses the batch
 * and hands each entry to [BrowserAnalytics], which sanitizes every field before an event
 * exists.
 *
 * **Everything arriving here is untrusted.** The bridge is reachable from any JavaScript on
 * the page, not only from the injected collector — a site can call `window.__bossInteraction
 * .emit(...)` itself with whatever it likes. So this parses defensively (unknown interaction
 * types dropped, non-conforming fields dropped by the sanitizers, oversized batches
 * truncated) and treats the collector's own discipline as a first line rather than the only
 * one. It cannot be *unreachable* — the page needs to call in — so it is instead cheap to
 * abuse and impossible to abuse usefully.
 *
 * [emit] runs on a JxBrowser thread and must not block or throw into the page's JS thread.
 */
internal class BrowserInteractionBridge(
    private val authorityProvider: () -> String?,
    /** Resolved per batch, not captured: a tab moves between windows. */
    private val windowId: () -> String?,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    @JsAccessible
    fun emit(json: String) {
        try {
            handle(json)
        } catch (_: Throwable) {
            // Never propagate into the page's JS thread. Deliberately silent rather than
            // logged: a hostile page can call emit() in a loop, and a log line per failure
            // would hand it a way to flood the log. A malformed batch costs nothing but
            // itself — parseBatch already returns empty for anything it cannot read.
        }
    }

    private fun handle(json: String) {
        val authority = authorityProvider() ?: return
        // Reserved BEFORE parsing, because parsing is the expensive part and it runs on the
        // page's JS thread. Admitting afterwards bounded what reached the bus but not the
        // work done to get there: a page looping emit() with 64 KB payloads still bought
        // itself an unbounded run of JSON parses. A full batch is reserved because the size
        // isn't known until it is parsed, and whatever the batch didn't use is released
        // straight back, so an honest page sending three events is not charged for fifty.
        val reservation = reserve(MAX_BATCH_ENTRIES)
        val budget = reservation.count
        if (budget <= 0) return
        val parsed = parseBatch(json)
        release(reservation, budget - minOf(budget, parsed.size))
        for (entry in parsed.take(budget)) {
            BrowserAnalytics.interaction(
                type = entry.type,
                authority = authority,
                elementTag = entry.tag,
                elementRole = entry.role,
                inputType = entry.inputType,
                fieldName = entry.fieldName,
                elementPath = entry.path,
                scrollDepthPercent = entry.scrollDepthPercent,
                repeatCount = entry.repeatCount,
                windowId = windowId(),
            )
        }
    }

    /**
     * How many of [requested] entries may be published right now, rate-limited per bridge
     * (so per tab). Excess is dropped silently.
     *
     * The per-batch caps in [parseBatch] bound the cost of one call, not the call *rate*.
     * That matters more here than it looks: `ApplicationEventBus` is a `MutableSharedFlow`
     * with `extraBufferCapacity = 64` published via a non-suspending `tryEmit`, so once the
     * buffer fills, events are **dropped on the floor**. This bridge is the first
     * page-controlled producer on that shared bus — without a rate cap, a page looping
     * `emit()` could evict `AuthEvent` / `TabEvent` / `FileChangeEvent` deliveries out from
     * under any subscriber that isn't keeping up. A fixed window is enough: the goal is to
     * keep abuse cheap to attempt and useless in effect, not to meter precisely.
     */
    @Synchronized
    internal fun admissible(requested: Int): Int = reserve(requested).count

    /**
     * Take up to [requested] entries out of the current window's budget.
     *
     * Returns a token rather than a bare count so [release] can name the window it is giving
     * back to. A shared "last reservation" field would be exact single-threaded and wrong the
     * moment two `emit()` calls overlap: a reservation taken in one window could be credited
     * into the next, handing it free allowance. Nothing documents JxBrowser as delivering one
     * batch at a time per handle, so the bookkeeping does not assume it.
     */
    @Synchronized
    private fun reserve(requested: Int): Reservation {
        val now = nowMs()
        // Also resets when the clock jumps backwards, which costs at most one window.
        if (now - windowStartMs !in 0 until RATE_WINDOW_MS) {
            windowStartMs = now
            windowEntries = 0
        }
        val allowed = (MAX_ENTRIES_PER_WINDOW - windowEntries).coerceIn(0, requested)
        windowEntries += allowed
        return Reservation(count = allowed, windowStartMs = windowStartMs)
    }

    /**
     * Give back [unused] of [reservation] that the batch turned out not to need.
     *
     * Only into the window it was taken from: if the window rolled over between reserving
     * and parsing, the reservation is already forgotten and crediting it would hand the new
     * window free allowance.
     */
    @Synchronized
    private fun release(
        reservation: Reservation,
        unused: Int,
    ) {
        if (unused <= 0 || reservation.windowStartMs != windowStartMs) return
        windowEntries = (windowEntries - unused).coerceAtLeast(0)
    }

    /** A claim on [count] entries of the rate window that began at [windowStartMs]. */
    private data class Reservation(
        val count: Int,
        val windowStartMs: Long,
    )

    private var windowStartMs: Long = 0
    private var windowEntries: Int = 0

    /** One entry off the wire, still unsanitized — [BrowserAnalytics] is what cleans these. */
    internal data class ParsedInteraction(
        val type: BrowserInteractionType,
        val tag: String? = null,
        val role: String? = null,
        val inputType: String? = null,
        val fieldName: String? = null,
        val path: String? = null,
        val scrollDepthPercent: Int? = null,
        val repeatCount: Int? = null,
    )

    internal companion object {
        /**
         * Parse a wire batch into entries worth reporting. Total function: anything
         * unparseable, oversized, or of an unknown type yields fewer entries rather than an
         * error, because the caller is a web page and errors are its normal output.
         */
        fun parseBatch(json: String): List<ParsedInteraction> {
            val batch =
                json
                    .takeIf { it.length <= MAX_PAYLOAD_CHARS }
                    ?.let { runCatching { parser.parseToJsonElement(it) }.getOrNull() } as? JsonArray
                    ?: return emptyList()
            return batch.take(MAX_BATCH_ENTRIES).mapNotNull { element ->
                val entry = element as? JsonObject ?: return@mapNotNull null
                val type = entry.text("type")?.let(::interactionType) ?: return@mapNotNull null
                ParsedInteraction(
                    type = type,
                    tag = entry.text("tag"),
                    role = entry.text("role"),
                    inputType = entry.text("inputType"),
                    fieldName = entry.text("fieldName"),
                    path = entry.text("path"),
                    scrollDepthPercent = entry.int("scrollDepthPercent"),
                    repeatCount = entry.int("repeatCount"),
                )
            }
        }

        private fun JsonObject.text(key: String): String? = runCatching { str(key) }.getOrNull()

        /**
         * `JsonNull.content` is the four-letter string `"null"`, which passes [sanitizeToken]
         * as a perfectly good tag name — so a JSON null arrived as an element literally named
         * "null" in a dashboard. Read it as the absent value it is.
         */
        private fun JsonObject.str(key: String): String? = get(key)?.takeIf { it !is JsonNull }?.jsonPrimitive?.content

        /**
         * A count off the wire.
         *
         * Falls back to parsing as a double so `50.0` is fifty rather than absent. The
         * collector only ever emits integers, but this class's contract is that hostile input
         * costs the page events and nothing else — a silent drop on a value any JSON encoder
         * might produce is an accident, not a decision. Non-finite values (`NaN`, `Infinity`,
         * which `isLenient` accepts) have no integer meaning and stay absent.
         */
        private fun JsonObject.int(key: String): Int? {
            val raw = text(key) ?: return null
            return raw.toIntOrNull()
                ?: raw.toDoubleOrNull()?.takeIf { it.isFinite() && it in INT_RANGE }?.toInt()
        }

        /**
         * Resolve a wire name to a known interaction type, or null.
         *
         * An explicit `when` rather than `enumValueOf`: this is a boundary a page can reach,
         * and the set of interactions the collector may report should be readable here rather
         * than implied by whatever the enum happens to contain. A new constant is then a
         * deliberate addition in two places, not an accidental widening in one.
         */
        private fun interactionType(raw: String): BrowserInteractionType? =
            when (raw) {
                "CLICK" -> BrowserInteractionType.CLICK
                "RAGE_CLICK" -> BrowserInteractionType.RAGE_CLICK
                "SCROLL_DEPTH" -> BrowserInteractionType.SCROLL_DEPTH
                "FIELD_FOCUSED" -> BrowserInteractionType.FIELD_FOCUSED
                "FORM_SUBMITTED" -> BrowserInteractionType.FORM_SUBMITTED
                "COPY" -> BrowserInteractionType.COPY
                "PASTE" -> BrowserInteractionType.PASTE
                else -> null
            }

        val parser =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
            }

        /** Bounds the double fallback in [int], so a huge literal cannot wrap on `toInt()`. */
        private val INT_RANGE = Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()

        /** A well-behaved batch is a few KB; beyond this the caller is not the collector. */
        const val MAX_PAYLOAD_CHARS = 64 * 1024
        const val MAX_BATCH_ENTRIES = 50

        /**
         * The collector flushes every 2s with at most 50 entries, so it needs ~25/sec. The
         * cap is an order of magnitude above that: generous for a busy page, far below what
         * it takes to starve a 64-slot bus.
         */
        const val RATE_WINDOW_MS = 1000L
        const val MAX_ENTRIES_PER_WINDOW = 250
    }
}
