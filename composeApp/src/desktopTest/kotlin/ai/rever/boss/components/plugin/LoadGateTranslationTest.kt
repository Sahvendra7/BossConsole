package ai.rever.boss.components.plugin

import ai.rever.boss.plugin.loader.PluginApiLevelException
import ai.rever.boss.plugin.loader.PluginBinaryIncompatibilityException
import ai.rever.boss.plugin.loader.PluginBossVersionException
import ai.rever.boss.plugin.loader.PluginLoadException
import ai.rever.boss.plugin.loader.PluginSignatureException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Turning a load failure into an offer, and knowing when there is no offer to make.
 *
 * The whole feature turns on this discrimination. A version floor is the one load failure with a
 * known fix, so it earns a dialog; a corrupt jar or a binary incompatibility does not, and offering
 * "Update BOSS" for one of those would send a user through a download and a restart to arrive at
 * exactly the same failure.
 */
class LoadGateTranslationTest {
    @AfterTest
    fun clearRegistry() {
        PluginLoadGateRegistry.reset()
    }

    @Test
    fun `a host version refusal becomes a host gate`() {
        val gate =
            loadGateFor(
                PluginBossVersionException(
                    "Plugin requires BOSS version 9.4.23 or later, but current version is 9.4.22",
                    "ai.rever.boss.plugin.fluck.browser",
                    "9.4.23",
                    "9.4.22",
                ),
            )
        val host = assertIs<PluginLoadGate.NeedsNewerHost>(gate)
        assertEquals("ai.rever.boss.plugin.fluck.browser", host.pluginId)
        assertEquals("9.4.23", host.required)
        assertEquals("9.4.22", host.current)
    }

    @Test
    fun `an api level refusal becomes an api gate`() {
        // A different gate because the fix is different and much cheaper - the api layer is itself
        // a hot-swappable plugin. Conflating the two would send someone to download an app release
        // for something the store settles in seconds.
        val gate =
            loadGateFor(
                PluginApiLevelException("needs api 1.0.83", "com.example.plugin", "1.0.83", "1.0.80"),
            )
        val api = assertIs<PluginLoadGate.NeedsNewerApi>(gate)
        assertEquals("1.0.83", api.required)
        assertEquals("1.0.80", api.current)
    }

    @Test
    fun `other load failures produce no gate`() {
        // Each of these is a real failure with no button that helps.
        assertNull(loadGateFor(PluginSignatureException("bad signature", "com.example.plugin")))
        assertNull(loadGateFor(PluginBinaryIncompatibilityException("incompatible", "com.example.plugin")))
        assertNull(loadGateFor(PluginLoadException("no main class", "com.example.plugin")))
        assertNull(loadGateFor(IllegalStateException("something else")))
        assertNull(loadGateFor(null))
    }

    @Test
    fun `a refusal that cannot name what it needs produces no gate`() {
        // Without the required version there is no way to tell whether an available update would
        // clear the floor, so the dialog could only offer one blind. Silence beats a wrong button.
        assertNull(loadGateFor(PluginBossVersionException("floor", "com.example.plugin", null, "9.4.22")))
        assertNull(loadGateFor(PluginBossVersionException("floor", "com.example.plugin", "", "9.4.22")))
    }

    @Test
    fun `a refusal that cannot name the plugin produces no gate`() {
        // The id is the key everything else hangs off: the rollback lookup, the registry entry, and
        // the install the remedy performs.
        assertNull(loadGateFor(PluginBossVersionException("floor", null, "9.4.23", "9.4.22")))
        assertNull(loadGateFor(PluginBossVersionException("floor", "  ", "9.4.23", "9.4.22")))
    }

    @Test
    fun `a missing current version falls back to this build`() {
        // The loader always populates it, but a null would otherwise render as "This is null."
        val gate =
            assertIs<PluginLoadGate.NeedsNewerHost>(
                loadGateFor(PluginBossVersionException("floor", "com.example.plugin", "9.9.9", null)),
            )
        assertTrue(gate.current.isNotBlank())
        assertTrue(
            Regex("""^\d+\.\d+\.\d+""").containsMatchIn(gate.current),
            "the fallback current version is not a version: ${gate.current}",
        )
    }

    @Test
    fun `the registry keeps one entry per plugin`() {
        // A refused plugin is retried on every launch and, for a systemPlugin, on every reload. One
        // entry per attempt would stack identical dialogs.
        val gate = PluginLoadGate.NeedsNewerHost("com.example.plugin", "Example", "9.4.23", "9.4.22")
        PluginLoadGateRegistry.record(gate)
        PluginLoadGateRegistry.record(gate)
        PluginLoadGateRegistry.record(gate.copy(required = "9.5.0"))
        assertEquals(1, PluginLoadGateRegistry.gates.value.size)
        val only =
            PluginLoadGateRegistry.gates.value.values
                .single()
        val stored = assertIs<PluginLoadGate.VersionFloor>(only)
        assertEquals("9.5.0", stored.required)
    }

    @Test
    fun `clearing removes only the named plugin`() {
        PluginLoadGateRegistry.record(PluginLoadGate.NeedsNewerHost("a", "A", "9.5.0", "9.4.22"))
        PluginLoadGateRegistry.record(PluginLoadGate.NeedsNewerHost("b", "B", "9.5.0", "9.4.22"))
        PluginLoadGateRegistry.clear("a")
        assertEquals(setOf("b"), PluginLoadGateRegistry.gates.value.keys)
    }

    @Test
    fun `clearing an unknown plugin does not emit`() {
        // The registry is collected by a composable, so a no-op write would recompose the dialog
        // host for nothing on every dismissal of an already-cleared gate.
        val before = PluginLoadGateRegistry.gates.value
        PluginLoadGateRegistry.clear("never-recorded")
        assertTrue(before === PluginLoadGateRegistry.gates.value)
    }
}
