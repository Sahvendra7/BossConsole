package ai.rever.boss.keymap

import ai.rever.boss.keymap.model.KeyBinding
import ai.rever.boss.keymap.model.KeyStroke
import ai.rever.boss.keymap.model.KeymapActions
import ai.rever.boss.keymap.model.KeymapSettings
import ai.rever.boss.keymap.model.ShortcutContext
import ai.rever.boss.keymap.presets.KeymapPresets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Migration of an existing ~/.boss/keymap-settings.json onto a newer preset.
 *
 * Adding missing ACTIONS was enough while presets only ever gained actions. A preset can also
 * gain a new alternate chord for an action every keymap file already contains — zoom in picking
 * up Cmd+Shift+Equals, what a US keyboard reports for "Cmd+Plus" — and that change reaches
 * nobody who has launched BOSS before, because the stored alternate-less copy wins.
 */
class KeymapMigrationTest {
    /** A keymap as written before the standard-browser-chords change: no alternates anywhere. */
    private fun legacySettings(): KeymapSettings {
        val stripped =
            KeymapPresets
                .getBOSSDefault()
                .shortcuts
                .filterKeys { it !in KeymapActions.TAB_SELECT_BY_INDEX && it != KeymapActions.TAB_REOPEN_CLOSED }
                .mapValues { (_, binding) -> binding.clearAlternateKeystrokes() }
        return KeymapSettings(shortcuts = stripped, presetName = "BOSS Default")
    }

    @Test
    fun `an untouched binding gains the preset's new alternate`() {
        val migrated = KeymapSettingsManager.migrateSettings(legacySettings())

        val zoomIn = assertNotNull(migrated.getBinding(KeymapActions.BROWSER_ZOOM_IN))
        assertEquals("Equals", zoomIn.key, "the primary is untouched")
        assertTrue(
            zoomIn.alternateKeystrokes.any { it.key == "Equals" && it.modifiers.any { m -> m.equals("Shift", true) } },
            "Cmd+Plus should reach an existing install, not just a fresh profile",
        )
    }

    @Test
    fun `a rebound binding is left alone`() {
        // The user moved zoom in to Cmd+Alt+Z. Bolting the preset's alternates onto that would
        // resurrect a chord they deliberately moved away from.
        val rebound =
            KeyBinding(
                actionId = KeymapActions.BROWSER_ZOOM_IN,
                key = "Z",
                modifiers = listOf("Cmd", "Alt"),
                context = ShortcutContext.BROWSER,
            )
        val settings = legacySettings().let { it.copy(shortcuts = it.shortcuts + (rebound.actionId to rebound)) }

        val migrated = KeymapSettingsManager.migrateSettings(settings)

        val zoomIn = assertNotNull(migrated.getBinding(KeymapActions.BROWSER_ZOOM_IN))
        assertEquals("Z", zoomIn.key)
        assertTrue(zoomIn.alternateKeystrokes.isEmpty(), "a rebound chord is the user's, not the preset's")
    }

    @Test
    fun `new actions still arrive`() {
        val migrated = KeymapSettingsManager.migrateSettings(legacySettings())

        assertNotNull(migrated.getBinding(KeymapActions.TAB_REOPEN_CLOSED), "Cmd+Shift+T should be added")
        assertNotNull(migrated.getBinding(KeymapActions.TAB_SELECT_1))
    }

    @Test
    fun `an already-current keymap migrates to itself`() {
        // Idempotence matters: loadSettingsSync rewrites the file whenever migration changes
        // anything, so a non-identity result here would rewrite on every launch.
        val current = KeymapPresets.getBOSSDefault()

        assertEquals(current, KeymapSettingsManager.migrateSettings(current))
    }

    @Test
    fun `an existing alternate is not duplicated`() {
        val current = KeymapPresets.getBOSSDefault()
        val migrated = KeymapSettingsManager.migrateSettings(KeymapSettingsManager.migrateSettings(current))

        val zoomIn = assertNotNull(migrated.getBinding(KeymapActions.BROWSER_ZOOM_IN))
        assertEquals(
            zoomIn.alternateKeystrokes.distinct().size,
            zoomIn.alternateKeystrokes.size,
            "repeated migration should not stack duplicate alternates",
        )
        assertEquals(listOf(KeyStroke("Equals", listOf("Cmd", "Shift"))), zoomIn.alternateKeystrokes)
    }
}
