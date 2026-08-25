package ai.rever.boss.components.settings.sections

/**
 * Why a plugin-backed settings section has nothing to render.
 *
 * The four are worth separating because only one of them is a wait. The section used to report
 * all of them as "isn't loaded yet", which for three of the four is simply false: a plugin that
 * was never installed, or that the user switched off, will not arrive however long they look at
 * it.
 */
internal enum class PluginSectionAbsence {
    /** Not on this machine. The only case that gets an Install button. */
    NOT_INSTALLED,

    /** Installed, and switched off. It will never register until the user turns it back on. */
    DISABLED,

    /** Present and on its way: startup registration is asynchronous. The honest wait. */
    STARTING,

    /** Not answerable here - no active manager, or the installer factory is not wired yet. */
    UNKNOWN,
}

/**
 * Which absence a plugin is in, as a pure function of the two facts that decide it.
 *
 * **The order is the whole thing.** `isInstalled` counts a disabled plugin as installed - it is
 * on disk, and `MissingDependencyInstaller` documents that deliberately, because offering to
 * install something already on disk downloads a jar the user already has and still leaves the
 * feature dead. So DISABLED has to be asked first, or a user who switched a plugin off is shown
 * an Install button that does nothing. That is not hypothetical: `MissingPluginOffer.isInstalled`
 * records the same failure happening with the bookmarks plugin.
 *
 * A separate function from the composable because it is the one decision here worth testing, and
 * a composable that reads two global singletons is not reachable from a unit test.
 *
 * @param installed the Install button's own predicate ([MissingPluginOffer.isInstalled]), where
 *   null means "cannot answer" - which is different from "no" and must not become an offer.
 */
internal fun pluginSectionAbsence(
    installed: Boolean?,
    isDisabled: Boolean,
): PluginSectionAbsence =
    when {
        isDisabled -> PluginSectionAbsence.DISABLED
        installed == false -> PluginSectionAbsence.NOT_INSTALLED
        installed == true -> PluginSectionAbsence.STARTING
        else -> PluginSectionAbsence.UNKNOWN
    }
