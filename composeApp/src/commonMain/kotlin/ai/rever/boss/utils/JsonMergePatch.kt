package ai.rever.boss.utils

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/**
 * Applies a JSON Merge Patch (RFC 7396) to this JsonElement.
 *
 * @param patch The patch to apply.
 * @return The merged JsonElement.
 */
fun JsonElement?.mergePatch(patch: JsonElement): JsonElement {
    if (patch !is JsonObject) {
        return patch
    }

    val targetObj = if (this is JsonObject) this else JsonObject(emptyMap())
    val result = targetObj.toMutableMap()

    for ((key, value) in patch) {
        if (value is JsonNull) {
            result.remove(key)
        } else {
            val targetChild = result[key]
            result[key] = targetChild.mergePatch(value)
        }
    }

    return JsonObject(result)
}
