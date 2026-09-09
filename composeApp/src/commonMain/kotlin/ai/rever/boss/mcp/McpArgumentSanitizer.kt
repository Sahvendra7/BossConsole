package ai.rever.boss.mcp

import ai.rever.boss.plugin.logging.LogSanitizer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Sanitizes MCP tool arguments before they reach an operator (the approval dialog) or
 * disk (the operation ledger).
 *
 * Deliberately narrower than [LogSanitizer.sanitizeMap]: that function treats any string
 * of 20 or more characters as secret-shaped ([LogSanitizer.looksLikeSecret]) and masks it
 * via [LogSanitizer.maskToken] - which is exactly wrong here, since a long file path, URL,
 * or shell command is both longer than 20 characters and the thing an operator most needs
 * to read before approving a mutating tool call. A value is only masked here when its key
 * names it as sensitive, or its shape is unambiguously a credential (JWT, GitHub token,
 * sk_/pk_ vendor key) - never on length alone.
 */
object McpArgumentSanitizer {
    private val sensitiveKeyWords =
        setOf("token", "password", "secret", "api_key", "apikey", "key", "credential")

    /** Same credential shapes [LogSanitizer] recognizes: a JWT, a GitHub token, or a vendor sk_/pk_ key. */
    private val credentialShapePattern =
        Regex(
            "(?<![A-Za-z0-9_.])(?:" +
                """eyJ[A-Za-z0-9_-]{4,}\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]*""" +
                "|(?:gh[pousr]_|github_pat_)[A-Za-z0-9_]{8,}" +
                "|(?:sk|pk)[-_][A-Za-z0-9_-]{8,}" +
                ")",
        )

    /** Parse only for audit/approval; malformed input must never reach those surfaces verbatim. */
    @Suppress("TooGenericExceptionCaught") // Invalid nested JSON must not enter the audit surface verbatim.
    fun parseArguments(raw: String): Map<String, Any?> =
        try {
            if (raw.length > 16_384) {
                mapOf("arguments" to "[OMITTED: too large]")
            } else {
                (Json.parseToJsonElement(raw) as? JsonObject)?.toMap()
                    ?: mapOf("arguments" to "[OMITTED: invalid JSON object]")
            }
        } catch (_: Exception) {
            mapOf("arguments" to "[OMITTED: invalid JSON]")
        }

    fun sanitize(args: Map<String, Any?>): Map<String, String> = sanitizeMap(args, 0)

    private fun sanitizeMap(
        args: Map<String, Any?>,
        depth: Int,
    ): Map<String, String> =
        args.mapValues { (key, value) ->
            if (sensitiveKeyWords.any { key.contains(it, ignoreCase = true) } || key.contains("auth", true)) {
                "[REDACTED]"
            } else {
                sanitizeValue(value, depth).take(4096)
            }
        }

    private fun sanitizeValue(
        value: Any?,
        depth: Int,
    ): String =
        if (depth >= 8) {
            "[OMITTED: too deeply nested]"
        } else {
            when (value) {
                is JsonObject -> {
                    sanitizeMap(value.toMap(), depth + 1).toString()
                }

                is JsonArray -> {
                    value.joinToString(prefix = "[", postfix = "]") { sanitizeValue(it, depth + 1) }
                }

                is JsonPrimitive -> {
                    sanitizeMessage(value.content)
                }

                is Map<*, *> -> {
                    val nested = value.entries.associate { it.key.toString() to it.value }
                    sanitizeMap(nested, depth + 1).toString()
                }

                is Iterable<*> -> {
                    value.joinToString(prefix = "[", postfix = "]") { sanitizeValue(it, depth + 1) }
                }

                else -> {
                    sanitizeMessage(value?.toString() ?: "null")
                }
            }
        }

    private val sensitiveAssignment =
        Regex(
            """(?i)"?(?:password|token|secret|api[_-]?key|authorization|credential)"?""" +
                """\s*[:=]\s*(?:"[^"]*"|'[^']*'|[^\s&,;}]+)""",
        )
    private val bearer = Regex("""(?i)Bearer\s+[^\s"',;}]+""")

    /**
     * Sanitizes arbitrary MCP tool results. Attempts to parse as JSON to accurately redact
     * keys regardless of format. Falls back to plain text regex redaction for malformed
     * or non-JSON results.
     */
    fun sanitizeResult(raw: String): String =
        try {
            val element =
                kotlinx.serialization.json.Json
                    .parseToJsonElement(raw)
            sanitizeJsonElement(element, 0).toString()
        } catch (_: Exception) {
            sanitizeMessage(raw)
        }

    private fun sanitizeJsonElement(
        element: kotlinx.serialization.json.JsonElement,
        depth: Int,
    ): kotlinx.serialization.json.JsonElement {
        if (depth >= 8) return kotlinx.serialization.json.JsonPrimitive("[OMITTED: too deeply nested]")
        return when (element) {
            is kotlinx.serialization.json.JsonObject -> {
                val sanitizedMap =
                    element.mapValues { (key, value) ->
                        if (sensitiveKeyWords.any { key.contains(it, ignoreCase = true) } || key.contains("auth", true)) {
                            kotlinx.serialization.json.JsonPrimitive("[REDACTED]")
                        } else {
                            sanitizeJsonElement(value, depth + 1)
                        }
                    }
                kotlinx.serialization.json.JsonObject(sanitizedMap)
            }

            is kotlinx.serialization.json.JsonArray -> {
                kotlinx.serialization.json.JsonArray(element.map { sanitizeJsonElement(it, depth + 1) })
            }

            is kotlinx.serialization.json.JsonPrimitive -> {
                if (element.isString) {
                    kotlinx.serialization.json.JsonPrimitive(sanitizeMessage(element.content))
                } else {
                    element
                }
            }

            else -> {
                element
            }
        }
    }

    fun sanitizeMessage(text: String): String =
        text
            .replace(credentialShapePattern, "[REDACTED]")
            .replace(sensitiveAssignment, "[REDACTED]")
            .replace(bearer, "Bearer [REDACTED]")
}
