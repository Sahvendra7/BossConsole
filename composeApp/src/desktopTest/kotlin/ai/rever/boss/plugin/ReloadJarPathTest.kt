package ai.rever.boss.plugin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins [resolveReloadJarPath].
 *
 * The bug it exists for: a reload is usually triggered BY an update that has already replaced the
 * jar, and since the new file is version-named the old path is deleted. Reloading from the loaded
 * record therefore unloaded the plugin and then failed to load it, leaving it gone until restart -
 * observed with fluck-browser 1.2.14 -> 1.2.15, and the reason it is silent is that the unload
 * half succeeds.
 */
class ReloadJarPathTest {
    private fun resolve(
        loaded: String?,
        persisted: String?,
        present: Set<String>,
    ) = resolveReloadJarPath(loaded, persisted) { it in present }

    @Test
    fun `falls back to the installed record when an update deleted the loaded jar`() {
        // The exact fluck-browser failure: loaded from 1.2.14, which the 1.2.15 download removed.
        assertEquals(
            "/p/boss-plugin-fluck-browser-1.2.15.jar",
            resolve(
                loaded = "/p/boss-plugin-fluck-browser-1.2.14.jar",
                persisted = "/p/boss-plugin-fluck-browser-1.2.15.jar",
                present = setOf("/p/boss-plugin-fluck-browser-1.2.15.jar"),
            ),
        )
    }

    @Test
    fun `keeps the loaded jar when it is still there`() {
        // In-place hot reload (evolver copies over the same path) must behave exactly as before.
        assertEquals(
            "/p/tool.jar",
            resolve(loaded = "/p/tool.jar", persisted = "/p/tool.jar", present = setOf("/p/tool.jar")),
        )
    }

    @Test
    fun `prefers the loaded jar over a differing record while it exists`() {
        // Preference order matters: a stale installed.json must not silently downgrade a plugin
        // that is running fine from a newer jar.
        assertEquals(
            "/p/new.jar",
            resolve(loaded = "/p/new.jar", persisted = "/p/old.jar", present = setOf("/p/new.jar", "/p/old.jar")),
        )
    }

    @Test
    fun `returns null when neither jar exists, so the caller can leave the plugin running`() {
        assertNull(resolve(loaded = "/p/gone.jar", persisted = "/p/also-gone.jar", present = emptySet()))
    }

    @Test
    fun `recovers when the plugin has no loaded path at all`() {
        assertEquals(
            "/p/recorded.jar",
            resolve(loaded = null, persisted = "/p/recorded.jar", present = setOf("/p/recorded.jar")),
        )
    }

    @Test
    fun `returns null when there is nothing to go on`() {
        assertNull(resolve(loaded = null, persisted = null, present = setOf("/p/anything.jar")))
    }
}
