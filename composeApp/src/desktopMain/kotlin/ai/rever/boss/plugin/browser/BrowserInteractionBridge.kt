package ai.rever.boss.plugin.browser

import ai.rever.boss.plugin.api.BrowserInteractionType
import com.teamdev.jxbrowser.js.JsAccessible
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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
    private val windowId: String?,
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
        for (entry in parseBatch(json)) {
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
                windowId = windowId,
            )
        }
    }

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

        private fun JsonObject.str(key: String): String? = get(key)?.jsonPrimitive?.content

        private fun JsonObject.int(key: String): Int? = text(key)?.toIntOrNull()

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

        /** A well-behaved batch is a few KB; beyond this the caller is not the collector. */
        const val MAX_PAYLOAD_CHARS = 64 * 1024
        const val MAX_BATCH_ENTRIES = 50
    }
}
