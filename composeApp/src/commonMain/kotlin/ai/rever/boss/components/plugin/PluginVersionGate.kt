package ai.rever.boss.components.plugin

/**
 * A plugin the host refused to load because of a version floor, and what would actually fix it.
 *
 * **Why this exists.** `DynamicPluginLoader` refuses a plugin whose `minBossVersion` or
 * `minApiVersion` exceeds what this build provides, which is correct - running it would fail at some
 * arbitrary call site instead. But the refusal only reached an ERROR line in the log, so from the
 * user's side a plugin simply stopped existing. That is bad for any plugin and worse for a
 * `systemPlugin`: fluck-browser is the browser tab, so the observable symptom of a version floor was
 * "my browser is gone", with the reason available only to someone reading `~/.boss/logs`.
 *
 * It happened for real. fluck-browser 1.2.22 shipped requiring BOSS 9.4.23 while 9.4.22 was the
 * current release, and every host that took the plugin update lost its browser tab:
 *
 * ```
 * PluginBossVersionException: Plugin requires BOSS version 9.4.23 or later,
 *                             but current version is 9.4.22
 * ```
 *
 * Recovering meant knowing that the jar had been overwritten in place, finding the previous release,
 * and putting it back by hand. None of that is reasonable to expect, and all of it is knowable from
 * the exception the loader already throws.
 *
 * This models the refusal and the ways out. It is deliberately pure - no store, no updater, no
 * files - so the question "what should we offer the user" is answerable in a test.
 */

/** Which floor the plugin failed, and the two versions involved. */
sealed interface PluginVersionGate {
    val pluginId: String
    val displayName: String

    /** What the plugin asks for. */
    val required: String

    /** What this build has. */
    val current: String

    /** The plugin needs a newer BOSS. Fixed by updating the app, or by going back a plugin version. */
    data class NeedsNewerHost(
        override val pluginId: String,
        override val displayName: String,
        override val required: String,
        override val current: String,
    ) : PluginVersionGate

    /**
     * The plugin needs a newer plugin API layer.
     *
     * Distinct from [NeedsNewerHost] because the fix is different and much cheaper: the api layer is
     * itself a plugin, hot-swappable without a restart, so this is resolvable in place. Conflating
     * the two would send a user to download a whole application update for something the store can
     * settle in seconds.
     */
    data class NeedsNewerApi(
        override val pluginId: String,
        override val displayName: String,
        override val required: String,
        override val current: String,
    ) : PluginVersionGate
}

/** Something the user can do about a [PluginVersionGate], with enough detail to label a button. */
sealed interface PluginVersionRemedy {
    /**
     * Update the application. Only offered when an update is actually available AND high enough -
     * "Update BOSS" that lands on a version still below the floor is worse than no button, because
     * the user pays for a restart and the plugin is still missing afterwards.
     */
    data class UpdateHost(
        val availableVersion: String,
    ) : PluginVersionRemedy

    /** Install a newer api plugin from the store. */
    data class UpdateApi(
        val availableVersion: String,
    ) : PluginVersionRemedy

    /**
     * Go back to the plugin version that was working.
     *
     * The remedy that always applies, because it does not depend on anything being published: the
     * jar this one replaced is kept aside precisely so there is a way back. Named with the version
     * so the button can say what it will do rather than "Downgrade".
     */
    data class RevertPlugin(
        val toVersion: String,
    ) : PluginVersionRemedy

    /**
     * Nothing can be offered, and the reason is worth showing.
     *
     * Reached when no update is published yet and no previous jar was kept - which is exactly the
     * position a user is in when a plugin's first release already overshoots their host. Saying so
     * beats an empty dialog or, worse, silence.
     */
    data class NothingAvailable(
        val reason: String,
    ) : PluginVersionRemedy
}

/**
 * What the host can offer for [gate], most useful first.
 *
 * @param hostUpdate the app version an update would install, or null when none is available.
 * @param apiUpdate the api-plugin version the store publishes, or null when it cannot be asked.
 * @param revertTo the plugin version kept aside from the last replacement, or null when none was.
 * @param satisfies whether a candidate version meets a required floor. Injected rather than
 *   reimplemented here: the loader already owns that comparison, and a second copy of version
 *   arithmetic is how the two drift into disagreeing about the same pair of numbers.
 */
fun remediesFor(
    gate: PluginVersionGate,
    hostUpdate: String?,
    apiUpdate: String?,
    revertTo: String?,
    satisfies: (required: String, candidate: String) -> Boolean,
): List<PluginVersionRemedy> {
    val remedies = mutableListOf<PluginVersionRemedy>()
    when (gate) {
        is PluginVersionGate.NeedsNewerHost -> {
            // Only when it would actually clear the floor. An update to 9.4.22 does not help a
            // plugin asking for 9.4.23, and offering it wastes a restart to end up here again.
            hostUpdate?.takeIf { satisfies(gate.required, it) }?.let {
                remedies += PluginVersionRemedy.UpdateHost(it)
            }
        }

        is PluginVersionGate.NeedsNewerApi -> {
            apiUpdate?.takeIf { satisfies(gate.required, it) }?.let {
                remedies += PluginVersionRemedy.UpdateApi(it)
            }
        }
    }
    // Always last, and always offered when it exists: it is the remedy that needs nothing published
    // and no restart, so it is the one that works when everything else is unavailable. Last rather
    // than first because going forward is better than going back when both are possible.
    revertTo?.let { remedies += PluginVersionRemedy.RevertPlugin(it) }

    if (remedies.isNotEmpty()) return remedies
    return listOf(
        PluginVersionRemedy.NothingAvailable(
            when (gate) {
                is PluginVersionGate.NeedsNewerHost -> {
                    "This needs BOSS ${gate.required}. You have ${gate.current}, " +
                        "no update is available yet, and no earlier version of the plugin was kept."
                }

                is PluginVersionGate.NeedsNewerApi -> {
                    "This needs plugin API ${gate.required}. You have ${gate.current}, " +
                        "the store has nothing newer, and no earlier version of the plugin was kept."
                }
            },
        ),
    )
}
