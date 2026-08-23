package ai.rever.boss.plugin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers when a retired plugin is uninstalled and, mostly, when it is not.
 *
 * Every "not" here is a case where removing it would take away the only panel the user has for
 * the job: the replacement missing, the replacement's jar gone, or a replacement too old to have
 * absorbed the retired plugin's work. The decision therefore fails closed at every step, which
 * is the opposite of `satisfiesVersionFloor`'s own default, so it needs pinning rather than
 * trusting.
 */
class RetiredPluginsTest {
    private val removed = mutableListOf<String>()
    private val announced = mutableListOf<String>()

    private val retirement =
        RetiredPlugins.Retirement(
            pluginId = OLD,
            displayName = "Old Panel",
            replacementId = NEW,
            replacementDisplayName = "New Panel",
            minReplacementVersion = "1.2.17",
        )

    @Test
    fun `retires when the replacement is installed and new enough`() {
        val result = sweep(installed = mapOf(OLD to entry(OLD), NEW to entry(NEW, version = "1.2.17")))

        assertEquals(listOf(OLD), result)
        assertEquals(listOf(OLD), removed)
        assertEquals(listOf("Old Panel is now part of New Panel"), announced)
    }

    @Test
    fun `retires when the replacement is newer still`() {
        val result = sweep(installed = mapOf(OLD to entry(OLD), NEW to entry(NEW, version = "1.3.0")))

        assertEquals(listOf(OLD), result)
    }

    @Test
    fun `keeps it when the replacement is not installed`() {
        // The case that matters most: deleting the retired plugin here leaves the user with no
        // panel at all for what it did.
        val result = sweep(installed = mapOf(OLD to entry(OLD)))

        assertEquals(emptyList(), result)
        assertTrue(removed.isEmpty())
        assertTrue(announced.isEmpty(), "announced a removal that did not happen")
    }

    @Test
    fun `keeps it when the replacement predates the version that absorbed it`() {
        // An entry naming the replacement is not enough - that version does not have the
        // retired plugin's work in it yet, so both halves would be gone at once.
        val result = sweep(installed = mapOf(OLD to entry(OLD), NEW to entry(NEW, version = "1.2.16")))

        assertEquals(emptyList(), result)
        assertTrue(removed.isEmpty())
    }

    @Test
    fun `keeps it when the replacement's version is unknown`() {
        // satisfiesVersionFloor answers TRUE for a blank version, by design: a gated update is
        // worse than an ungated one. Here the consequence runs the other way, so an unknown
        // version must read as "not ready" and wait for a launch that can prove otherwise.
        val result = sweep(installed = mapOf(OLD to entry(OLD), NEW to entry(NEW, version = null)))

        assertEquals(emptyList(), result)
        assertTrue(removed.isEmpty())
    }

    @Test
    fun `keeps it when the replacement has a row but no jar`() {
        // installPlugin records a DISABLED entry for a plugin it then rejected and deleted, so
        // an entry alone does not mean the replacement can run.
        val result =
            sweep(
                installed = mapOf(OLD to entry(OLD), NEW to entry(NEW, version = "1.2.17")),
                jarExists = false,
            )

        assertEquals(emptyList(), result)
        assertTrue(removed.isEmpty())
    }

    @Test
    fun `does nothing when the retired plugin was never installed`() {
        val result = sweep(installed = mapOf(NEW to entry(NEW, version = "1.2.17")))

        assertEquals(emptyList(), result)
        assertTrue(removed.isEmpty())
    }

    @Test
    fun `the second launch is a no-op because the first removed the row`() {
        // Why this needs no "already done" flag: the sweep keys on the plugin being installed,
        // and PluginArtifactCleanup drops the installed.json row.
        val installed = mutableMapOf(OLD to entry(OLD), NEW to entry(NEW, version = "1.2.17"))
        val hooks =
            RetiredPlugins.Hooks(
                installed = { installed[it] },
                jarExists = { true },
                remove = { id, _ ->
                    removed += id
                    installed.remove(id)
                },
                announce = { announced += it },
            )

        assertEquals(listOf(OLD), RetiredPlugins.sweep(listOf(retirement), hooks))
        assertEquals(emptyList(), RetiredPlugins.sweep(listOf(retirement), hooks))
        assertEquals(listOf(OLD), removed, "the second sweep removed it again")
    }

    @Test
    fun `the shipped retirement names the plugin the wizard no longer installs`() {
        // Pins the pair rather than the mechanism: PluginListProvider dropped
        // `usersecretlist` and put `secretmanager` among the defaults in the same change, and
        // a retirement pointing somewhere else would silently uninstall the wrong plugin.
        val shipped = RetiredPlugins.ALL.single()
        assertEquals("ai.rever.boss.plugin.dynamic.usersecretlist", shipped.pluginId)
        assertEquals("ai.rever.boss.plugin.dynamic.secretmanager", shipped.replacementId)
        assertTrue(
            shipped.pluginId !in
                ai.rever.boss.components.wizard.plugin.PluginListProvider.DEFAULT_PLUGIN_IDS,
            "the wizard still installs a plugin this sweep uninstalls at the next launch",
        )
        assertTrue(
            shipped.replacementId in
                ai.rever.boss.components.wizard.plugin.PluginListProvider.DEFAULT_PLUGIN_IDS,
            "nothing takes the retired plugin's place in a fresh install",
        )
    }

    private fun sweep(
        installed: Map<String, PluginPersistence.InstalledPluginEntry>,
        jarExists: Boolean = true,
    ): List<String> =
        RetiredPlugins.sweep(
            retirements = listOf(retirement),
            hooks =
                RetiredPlugins.Hooks(
                    installed = { installed[it] },
                    jarExists = { jarExists },
                    remove = { id, _ -> removed += id },
                    announce = { announced += it },
                ),
        )

    private fun entry(
        pluginId: String,
        version: String? = "1.0.0",
    ) = PluginPersistence.InstalledPluginEntry(
        pluginId = pluginId,
        jarPath = "/plugins/$pluginId.jar",
        installedVersion = version,
    )

    private companion object {
        const val OLD = "ai.rever.boss.plugin.dynamic.oldpanel"
        const val NEW = "ai.rever.boss.plugin.dynamic.newpanel"
    }
}
