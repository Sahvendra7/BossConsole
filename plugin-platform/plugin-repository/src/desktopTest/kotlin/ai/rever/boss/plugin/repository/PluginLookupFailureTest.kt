package ai.rever.boss.plugin.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * That [PluginRepositoryManager.getPlugin] tells "no repository has it" apart from "a repository could
 * not be asked".
 *
 * Both used to arrive as `success(null)`, because each repository's `Result` was flattened with
 * `getOrNull()`. The visible cost was a wrong diagnosis: one store row whose dependency entry failed
 * to decode made the first-run wizard report "Tool not found in repository" for a plugin that was
 * published and fine, and nothing short of the stack trace in the log said otherwise.
 *
 * The asymmetry in the last test is the point of the design - a broken repository must not be able to
 * hide a plugin another repository can serve, so a failure is only reported when nothing was found.
 */
class PluginLookupFailureTest {
    @Test
    fun `no repository having it is absence, not failure`() =
        runTest {
            val manager = PluginRepositoryManager()
            manager.addRepository(FakeRepository(id = "local", isLocal = true, answer = Result.success(null)))

            val result = manager.getPlugin("ai.rever.boss.plugin.dynamic.flowtab")

            assertTrue(result.isSuccess, "an absent plugin is not an error")
            assertNull(result.getOrNull())
        }

    @Test
    fun `a repository that cannot answer is a failure, not absence`() =
        runTest {
            val manager = PluginRepositoryManager()
            manager.addRepository(
                FakeRepository(
                    id = "store",
                    isLocal = false,
                    answer = Result.failure(IllegalStateException("Field 'versionRange' is required")),
                ),
            )

            val result = manager.getPlugin("ai.rever.boss.plugin.dynamic.flowtab")

            val failure = result.exceptionOrNull()
            assertIs<PluginLookupException>(failure)
            assertEquals("ai.rever.boss.plugin.dynamic.flowtab", failure.pluginId)
            // The cause carries the detail; the message stays short enough for a UI row.
            assertTrue(
                "versionRange" in (failure.cause?.message ?: ""),
                "the real error must survive as the cause",
            )
            assertTrue(failure.message!!.length < 300, "message went to the wizard's list: ${failure.message}")
        }

    @Test
    fun `a broken repository cannot hide a plugin another one has`() =
        runTest {
            val manager = PluginRepositoryManager()
            manager.addRepository(
                FakeRepository(id = "local", isLocal = true, answer = Result.failure(IllegalStateException("boom"))),
            )
            manager.addRepository(
                FakeRepository(
                    id = "store",
                    isLocal = false,
                    answer = Result.success(pluginInfo("ai.rever.boss.plugin.dynamic.flowtab")),
                ),
            )

            val result = manager.getPlugin("ai.rever.boss.plugin.dynamic.flowtab")

            assertTrue(result.isSuccess, "a hit must win over an earlier repository's failure")
            assertEquals("store", result.getOrNull()?.source?.repositoryId)
        }

    private fun pluginInfo(pluginId: String) =
        PluginInfo(
            pluginId = pluginId,
            displayName = "Flow",
            version = "1.0.14",
            description = "Visual flow builder",
        )

    /** Answers every lookup with one canned [Result]; nothing else is exercised. */
    private class FakeRepository(
        override val id: String,
        override val isLocal: Boolean,
        private val answer: Result<PluginInfo?>,
    ) : PluginRepository {
        override val name: String = id
        override val isAvailable: Boolean = true

        override suspend fun getPlugin(pluginId: String): Result<PluginInfo?> = answer

        override suspend fun listPlugins(): Result<List<PluginInfo>> = Result.success(emptyList())

        override suspend fun searchPlugins(filter: PluginSearchFilter): Result<PluginSearchResult> =
            Result.failure(UnsupportedOperationException())

        override suspend fun getPluginVersions(pluginId: String): Result<List<PluginInfo>> = Result.success(emptyList())

        override suspend fun downloadPlugin(
            pluginId: String,
            version: String?,
            targetPath: String,
        ): Result<String> = Result.failure(UnsupportedOperationException())

        override fun getDownloadProgress(pluginId: String): Flow<Float>? = null

        override suspend fun refresh(): Result<Unit> = Result.success(Unit)
    }
}
