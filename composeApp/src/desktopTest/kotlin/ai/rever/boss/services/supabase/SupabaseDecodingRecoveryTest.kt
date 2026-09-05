package ai.rever.boss.services.supabase

import ai.rever.boss.services.supabase.models.SecretEntry
import ai.rever.boss.utils.logging.BossLogger
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SupabaseDecodingRecoveryTest {
    private val logger = BossLogger.forComponent("SupabaseDecodingRecoveryTest")

    @Test
    fun `decodeListRecovering drops bad row but keeps good rows`() {
        val payload =
            """
            [
                {
                    "id": "1",
                    "website": "github.com",
                    "username": "user",
                    "password": "pwd",
                    "created_at": "now",
                    "updated_at": "now"
                },
                {
                    "id": "2",
                    "website": "gitlab.com",
                    "username": "user",
                    "password": null,
                    "created_at": "now",
                    "updated_at": "now"
                },
                {
                    "id": "3",
                    "website": "bitbucket.org",
                    "username": "user",
                    "password": "pwd",
                    "created_at": "now",
                    "updated_at": "now"
                }
            ]
            """.trimIndent()

        val jsonElement = Json.parseToJsonElement(payload)

        // Row 2 has null password where string is expected, so it should fail decoding
        val results = decodeListRecovering<SecretEntry>(jsonElement, logger, "testOperation")

        assertEquals(2, results.size, "Should recover and parse 2 good rows")
        assertEquals("1", results[0].id)
        assertEquals("3", results[1].id)
    }

    @Test
    fun `coerceInputValues allows nulls for properties with default values`() {
        // Here tags is omitted, which should use default emptyList().
        // But what if it's explicitly null?
        // With coerceInputValues = true, a null for a non-nullable property with a default value
        // will be coerced to the default value instead of throwing an exception.
        val payload =
            """
            {
                "id": "1",
                "website": "github.com",
                "username": "user",
                "password": "pwd",
                "tags": null,
                "created_at": "now",
                "updated_at": "now"
            }
            """.trimIndent()

        val jsonElement = Json.parseToJsonElement(payload)

        val result =
            runCatching {
                supabaseJson.decodeFromJsonElement(SecretEntry.serializer(), jsonElement)
            }.getOrNull()

        assertNotNull(result, "Should successfully parse SecretEntry")
        assertTrue(result.tags.isEmpty(), "Null tags should be coerced to emptyList()")
    }
}
