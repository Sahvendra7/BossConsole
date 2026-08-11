package ai.rever.boss.components.plugin

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Which build of a plugin is actually running.
 *
 * The manifest version alone cannot answer this. A locally built jar and the released one carry
 * the same `version`, and an in-place hot reload (the evolver copying a jar over the same path)
 * rewrites neither the manifest nor `installed.json` - so without this, a panel running code that
 * was never published looks identical to the store build, and successive reload iterations look
 * identical to each other.
 *
 * Two independent facts, because a jar can be both:
 *
 * - [storeVetted] - a store signature covers these exact bytes. Note this means "the store agreed
 *   about this jar", not "downloaded from the store": system plugins come from GitHub releases and
 *   have their sidecar backfilled from the store row, which is deliberate - they are released
 *   builds and should carry no tag.
 * - [reloadStamp] - the bytes on disk were replaced after the install was recorded, i.e. this is a
 *   hot reload. Carries the jar's modification time, which is what makes one reload iteration
 *   distinguishable from the next.
 */
data class PluginBuildInfo(
    val pluginId: String,
    val displayName: String,
    /** The canonical manifest version, never suffixed - update checks and signing anchors use it. */
    val version: String,
    val storeVetted: Boolean,
    val reloadStamp: Long?,
) {
    /**
     * The version as a person should read it: `1.0.3`, `1.0.3-debug`, `1.0.3-debug+1754890231447`.
     *
     * `-debug` says the store never vetted these bytes; `+<millis>` identifies which hot reload you
     * are looking at. Display only. Nothing parses this back, and it is never written to the
     * `version` field of any record.
     */
    val displayVersion: String
        get() =
            buildString {
                append(version)
                if (!storeVetted) append("-debug")
                reloadStamp?.let {
                    append('+')
                    append(it)
                }
            }

    /** Short pill text, or null for a released build (which gets no tag at all). */
    val tagLabel: String?
        get() =
            when {
                reloadStamp != null -> "HOT"
                !storeVetted -> "DEBUG"
                else -> null
            }

    /** Whether this build is worth tagging in the UI. */
    val isTagged: Boolean get() = tagLabel != null

    /** Long form for tooltips, accessibility and the overflow menu's version row. */
    val description: String
        get() =
            when {
                reloadStamp != null -> "Hot reloaded build, not the store version"
                !storeVetted -> "Local build, not the store version"
                else -> "Store version"
            }
}

/**
 * Host-side, commonMain-visible registry of which build each plugin is running. Populated from the
 * desktop layer through `DynamicPluginManager.pluginBuildProbe` on every successful install, which
 * covers cold start, update, reload and evolver hot reload alike.
 *
 * Mirrors [PluginUpdateRegistry] deliberately: panel headers already observe that one for the
 * "update available" badge, so the tag rides the same shape rather than inventing a second.
 */
object PluginBuildRegistry {
    private val _builds = MutableStateFlow<Map<String, PluginBuildInfo>>(emptyMap())
    val builds: StateFlow<Map<String, PluginBuildInfo>> = _builds.asStateFlow()

    /**
     * Atomic read-modify-write, matching [PluginUpdateRegistry]: installs run under the manager's
     * mutex but this is published after it releases, so two plugins finishing at once must not
     * clobber each other.
     */
    fun put(info: PluginBuildInfo) {
        _builds.update { it + (info.pluginId to info) }
    }

    fun clear(pluginId: String) {
        _builds.update { it - pluginId }
    }

    fun get(pluginId: String): PluginBuildInfo? = _builds.value[pluginId]

    /** For tests: drop everything, so one test's registrations cannot leak into the next. */
    fun reset() {
        _builds.value = emptyMap()
    }
}
