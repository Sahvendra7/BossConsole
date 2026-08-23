package ai.rever.boss.plugin

import ai.rever.boss.components.bars.horizontal.StatusMessageManager
import ai.rever.boss.plugin.updater.satisfiesVersionFloor
import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import java.io.File

/**
 * Uninstalls a plugin whose job another plugin has taken over.
 *
 * A retired plugin cannot uninstall itself, and leaving it installed means the user keeps a
 * panel that no longer does anything. The store listing can be unlisted so nobody *new*
 * installs it, but that does nothing for the machines that already have it - which is every
 * machine, when the retired plugin was a first-run default.
 *
 * The sweep runs once per launch, before any plugin is loaded, so a retired plugin never
 * registers a panel or an MCP tool on a machine where its replacement is present. No
 * "already done" flag is needed: the sweep keys on the plugin being installed, and removing
 * it is what makes the next launch a no-op.
 */
object RetiredPlugins {
    private val logger = BossLogger.forComponent("RetiredPlugins")

    /** How long the "X is now part of Y" line sits in the status bar. */
    private const val NOTICE_MS = 8_000L

    /**
     * A plugin that has been folded into another one.
     *
     * [minReplacementVersion] is the release of the replacement that actually absorbed the
     * work. Without it this would uninstall the old plugin as soon as the *name* of the new one
     * appeared in `installed.json`, including a version predating the absorption - so the user
     * would lose both halves at once.
     */
    data class Retirement(
        val pluginId: String,
        val displayName: String,
        val replacementId: String,
        val replacementDisplayName: String,
        val minReplacementVersion: String,
    )

    val ALL =
        listOf(
            // "My Secrets" and Secret Manager sat next to each other in the right sidebar
            // reading the same vault, and both listed the caller's own secrets. Secret Manager
            // now has two sections that partition it - what you can manage, and what other
            // people shared with you - so this plugin ships a notice panel and nothing else.
            //
            // The version is the secret-manager release that has those sections. It has to
            // name a real published release: too low and this deletes the user's only secrets
            // panel on a machine whose Secret Manager predates the merge.
            Retirement(
                pluginId = "ai.rever.boss.plugin.dynamic.usersecretlist",
                displayName = "My Secrets",
                replacementId = "ai.rever.boss.plugin.dynamic.secretmanager",
                replacementDisplayName = "Secret Manager",
                minReplacementVersion = "1.2.17",
            ),
        )

    /**
     * Seams with production defaults, so the decision is testable without deleting a
     * developer's own plugins or rewriting their `installed.json`. Mirrors
     * [PluginArtifactCleanup.Hooks], which this delegates the actual removal to.
     */
    class Hooks(
        val installed: (String) -> PluginPersistence.InstalledPluginEntry? = { id ->
            PluginPersistence.getInstalledPlugin(id)
        },
        val jarExists: (String) -> Boolean = { path -> path.isNotBlank() && File(path).isFile },
        val remove: (String, String) -> Unit = { id, jarPath -> PluginArtifactCleanup.remove(id, jarPath) },
        val announce: (String) -> Unit = { message -> StatusMessageManager.showMessage(message, NOTICE_MS) },
    )

    /**
     * Uninstalls every retired plugin whose replacement is installed and new enough.
     *
     * @return the plugin ids actually removed, for the caller to log.
     */
    fun sweep(
        retirements: List<Retirement> = ALL,
        hooks: Hooks = Hooks(),
    ): List<String> = retirements.filter { retire(it, hooks) }.map { it.pluginId }

    private fun retire(
        retirement: Retirement,
        hooks: Hooks,
    ): Boolean {
        val entry = hooks.installed(retirement.pluginId)
        if (entry == null || !replacementIsReady(retirement, hooks)) return false

        hooks.remove(retirement.pluginId, entry.jarPath)
        logger.info(
            LogCategory.SYSTEM,
            "Uninstalled a retired plugin",
            mapOf(
                "pluginId" to retirement.pluginId,
                "replacedBy" to retirement.replacementId,
            ),
        )
        // Said out loud, because a panel the user has had since their first run disappearing
        // with no explanation reads as a bug - or as lost secrets.
        hooks.announce("${retirement.displayName} is now part of ${retirement.replacementDisplayName}")
        return true
    }

    /**
     * Whether the replacement can actually stand in for the retired plugin.
     *
     * **Fails closed at every step**, which is the opposite of [satisfiesVersionFloor]'s own
     * default: that helper answers "true" for a blank or unparseable version because a *gated
     * update* is worse than an ungated one. Here the consequence runs the other way - a wrong
     * "yes" deletes the panel the user reads their shared credentials in - so an unknown
     * version means "not ready" and the retirement waits for a launch that can prove it.
     *
     * The jar check is not paranoia: `installPlugin` records a DISABLED entry for a plugin it
     * then rejected and deleted, so an entry alone does not mean the replacement can run. Same
     * definition of "installed" as `PluginDependencyResolution.installedAndOnDisk`.
     */
    // Guard clauses: each refusal logs a different reason, and a reader debugging "why is the
    // old panel still there" needs to know which one fired. Folding them into one condition
    // would collapse three distinct answers into one. Same call as VersionFloor.kt's.
    @Suppress("ReturnCount")
    private fun replacementIsReady(
        retirement: Retirement,
        hooks: Hooks,
    ): Boolean {
        val replacement = hooks.installed(retirement.replacementId)
        if (replacement == null) {
            logger.info(
                LogCategory.SYSTEM,
                "Keeping a retired plugin: its replacement is not installed",
                mapOf("pluginId" to retirement.pluginId, "replacementId" to retirement.replacementId),
            )
            return false
        }
        if (!hooks.jarExists(replacement.jarPath)) {
            logger.warn(
                LogCategory.SYSTEM,
                "Keeping a retired plugin: its replacement has an installed.json entry but no jar",
                mapOf("pluginId" to retirement.pluginId, "jarPath" to replacement.jarPath),
            )
            return false
        }
        val version = replacement.installedVersion
        val newEnough =
            !version.isNullOrBlank() &&
                satisfiesVersionFloor(required = retirement.minReplacementVersion, installed = version)
        if (!newEnough) {
            logger.info(
                LogCategory.SYSTEM,
                "Keeping a retired plugin: its replacement predates the version that absorbed it",
                mapOf(
                    "pluginId" to retirement.pluginId,
                    "replacementVersion" to (version ?: "unknown"),
                    "required" to retirement.minReplacementVersion,
                ),
            )
            return false
        }
        return true
    }
}
