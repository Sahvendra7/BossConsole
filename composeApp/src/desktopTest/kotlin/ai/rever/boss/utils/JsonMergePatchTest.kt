package ai.rever.boss.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Test

class JsonMergePatchTest {
    private fun testMerge(
        target: String,
        patch: String,
        expected: String,
    ) {
        val targetJson = Json.parseToJsonElement(target)
        val patchJson = Json.parseToJsonElement(patch)
        val expectedJson = Json.parseToJsonElement(expected)

        val result = targetJson.mergePatch(patchJson)
        assertEquals(expectedJson, result)
    }

    private fun testMergeNullTarget(
        patch: String,
        expected: String,
    ) {
        val patchJson = Json.parseToJsonElement(patch)
        val expectedJson = Json.parseToJsonElement(expected)

        val result = (null as JsonElement?).mergePatch(patchJson)
        assertEquals(expectedJson, result)
    }

    @Test
    fun test_object_merge() {
        testMerge("""{"a": "b"}""", """{"c": "d"}""", """{"a": "b", "c": "d"}""")
    }

    @Test
    fun test_null_removes_property() {
        testMerge("""{"a": "b", "c": "d"}""", """{"a": null}""", """{"c": "d"}""")
    }

    @Test
    fun test_primitive_replacement() {
        testMerge("""{"a": "b"}""", """"scalar"""", """"scalar"""")
    }

    @Test
    fun test_nested_object_merge() {
        testMerge(
            """{"a": {"b": "c"}}""",
            """{"a": {"b": "d", "c": null}}""",
            """{"a": {"b": "d"}}""",
        )
    }

    @Test
    fun test_array_replacement() {
        // Arrays are completely replaced, not merged
        testMerge("""{"a": [1, 2]}""", """{"a": [3, 4]}""", """{"a": [3, 4]}""")
    }

    @Test
    fun test_target_not_object() {
        // If target is primitive and patch is object, target is treated as {}
        testMerge(""""scalar"""", """{"a": "b"}""", """{"a": "b"}""")
    }

    @Test
    fun test_null_target() {
        testMergeNullTarget("""{"a": "b"}""", """{"a": "b"}""")
    }

    @Test
    fun test_null_target_with_null_patch() {
        testMergeNullTarget("""{"a": null, "b": 1}""", """{"b": 1}""")
    }
}
