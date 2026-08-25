package ai.rever.boss.components.settings.sections

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Which of the four reasons a plugin-backed settings section has no panel, which decides whether
 * it offers an Install button.
 *
 * The ordering test is the one that matters. `MissingPluginOffer.isInstalled` counts a **disabled**
 * plugin as installed - it is on disk, and the installer documents that deliberately - so asking
 * `installed` before `isDisabled` puts an Install button in front of a user who simply switched the
 * plugin off. Pressing it downloads a jar they already have and changes nothing. The same mistake
 * has already shipped once here with the bookmarks plugin, which is why it is pinned rather than
 * left to the reading order of a `when`.
 */
class PluginSectionAbsenceTest {
    @Test
    fun `a plugin that is not installed is the one case that gets an offer`() {
        assertEquals(
            PluginSectionAbsence.NOT_INSTALLED,
            pluginSectionAbsence(installed = false, isDisabled = false),
        )
    }

    @Test
    fun `an installed plugin that has not registered yet is starting, not missing`() {
        // Registration is asynchronous, so this is the honest wait - and the only one of the four
        // where "isn't loaded yet" was ever true.
        assertEquals(
            PluginSectionAbsence.STARTING,
            pluginSectionAbsence(installed = true, isDisabled = false),
        )
    }

    @Test
    fun `a disabled plugin is disabled, not starting`() {
        // It reads as installed, and it will never register. Reported as STARTING the section
        // says "isn't loaded yet" forever at a user who switched it off themselves.
        assertEquals(
            PluginSectionAbsence.DISABLED,
            pluginSectionAbsence(installed = true, isDisabled = true),
        )
    }

    @Test
    fun `disabled is decided before installed, so no Install button appears for it`() {
        // The ordering, stated as its own case: whatever `installed` says, a disabled plugin must
        // never reach NOT_INSTALLED, because that is the only value that draws the button.
        assertEquals(
            PluginSectionAbsence.DISABLED,
            pluginSectionAbsence(installed = false, isDisabled = true),
        )
        assertEquals(
            PluginSectionAbsence.DISABLED,
            pluginSectionAbsence(installed = null, isDisabled = true),
        )
    }

    @Test
    fun `cannot answer is not the same as no`() {
        // Null means no active manager or no injected installer factory - before startup finishes,
        // and in tests. Treating it as "not installed" would offer to install something that may
        // well be there, from a state where the install could not run anyway.
        assertEquals(
            PluginSectionAbsence.UNKNOWN,
            pluginSectionAbsence(installed = null, isDisabled = false),
        )
    }
}
