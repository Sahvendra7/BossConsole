package ai.rever.boss.mcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Bounded display previews, never replay arguments. Malformed structured input fails closed. */
internal object McpObservationPreview {
    const val MAX_CHARS = 4096
    private const val MAX_INPUT_CHARS = 16384
    private const val MAX_DEPTH = 8
    private val sensitive = listOf("token", "password", "secret", "key", "credential", "auth")

    fun sanitize(
        raw: String,
        maxChars: Int = MAX_CHARS,
    ): String {
        val limit = if (maxChars > 0) minOf(maxChars, MAX_CHARS) else MAX_CHARS
        val preview =
            when {
                raw.length > MAX_INPUT_CHARS -> "[OMITTED: payload too large]"
                tooDeep(raw) -> "[OMITTED: payload too deeply nested]"
                else -> parsePreview(raw)
            }
        return if (preview.length <= limit) preview else "[OMITTED: sanitized payload too large]".take(limit)
    }

    @Suppress("TooGenericExceptionCaught") // Untrusted JSON is omitted on parse failure.
    private fun parsePreview(raw: String): String =
        try {
            sanitizeElement(Json.parseToJsonElement(raw)).toString()
        } catch (_: Exception) {
            // Do not run a weaker regex on broken or truncated JSON, including escaped keys.
            if (raw.trimStart().firstOrNull() in listOf('{', '[', '"')) {
                "[OMITTED: malformed JSON]"
            } else {
                McpArgumentSanitizer.sanitizeMessage(raw)
            }
        }

    private fun sanitizeElement(element: JsonElement): JsonElement =
        when (element) {
            is JsonObject -> {
                JsonObject(
                    element.mapValues { (key, value) ->
                        if (sensitive.any { key.contains(it, ignoreCase = true) }) {
                            JsonPrimitive("[REDACTED]")
                        } else {
                            sanitizeElement(value)
                        }
                    },
                )
            }

            is JsonArray -> {
                JsonArray(element.map(::sanitizeElement))
            }

            is JsonPrimitive -> {
                if (element.isString) JsonPrimitive(McpArgumentSanitizer.sanitizeMessage(element.content)) else element
            }
        }

    /** Bound nesting before recursive parsing, while ignoring brackets inside JSON strings. */
    private fun tooDeep(raw: String): Boolean {
        var depth = 0
        var quoted = false
        var escaped = false
        for (char in raw) {
            if (quoted) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> quoted = false
                }
                continue
            }
            when (char) {
                '"' -> quoted = true
                '{', '[' -> depth++
                '}', ']' -> depth--
            }
            if (depth > MAX_DEPTH) return true
        }
        return false
    }
}
