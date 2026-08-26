package ai.rever.boss.app

import ai.rever.boss.components.settings.search.SettingsHighlight
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Whether the settings window is up, which section it was asked to land on, and how many times it
 * has been asked to raise itself.
 *
 * One holder rather than three loose flags on [BossAppState], because the three only mean anything
 * together and the bug this exists to prevent came from writing one of them directly. Every
 * Settings affordance - the top bar, the focus-mode quick actions, the menu action, the dashboard,
 * the shortcut-help deep link - assigned `showSettingsDialog = true`, and assigning `true` to a
 * flag that is already `true` changes nothing at all. With the window open and buried behind the
 * main one, clicking Settings did nothing and read as a dead button. Nothing about a `var Boolean`
 * says that, so the fields are `private set` and [open] / [close] are the only ways in.
 */
internal class SettingsWindowState {
    /** Whether the window is composed at all. */
    var visible by mutableStateOf(false)
        private set

    /** Section to land on, for the callers that deep-link - KEYMAP from shortcut help, menu items. */
    var section by mutableStateOf<String?>(null)
        private set

    /**
     * Bumped once per request to show settings while it is already [visible].
     *
     * A counter rather than a flag, because the window has to act on *every* request and "a flag
     * that is already set" is precisely the failure above. The window keys an effect on it and
     * deiconifies, raises and focuses itself on each new value.
     */
    var focusRequest by mutableStateOf(0)
        private set

    /**
     * Bumped once per request that names a [section], so an already-open window navigates to it.
     *
     * Separate from [focusRequest], and a counter for its own reason. The window cannot key on the
     * section *value*: asking twice for the same one leaves the string unchanged, so it would
     * navigate the first time and silently do nothing the second. And it cannot share
     * [focusRequest], because a plain [open] bumps that without naming a section - re-applying the
     * last section there would yank the user off the page they were on, on the one interaction
     * that is meant only to raise the window.
     */
    var sectionRequest by mutableStateOf(0)
        private set

    /**
     * Show the settings window, or ask the one already open to raise itself and, when [section] is
     * named, to navigate there.
     *
     * [section] is applied only when given. Passing null means "just show settings" and must not
     * clear a section another caller navigated to, which a plain assignment would.
     */

    /**
     * The row to point at once the window has navigated, or null to point at nothing.
     *
     * Separate from [section] for the same reason [sectionRequest] is separate from
     * [focusRequest]: the window has to be able to tell "navigate here and light this row" from
     * "just raise yourself", and a highlight left armed from a previous pick would fire on a page
     * it does not belong to. Its own nonce makes asking twice for the same row work twice.
     */
    var highlight by mutableStateOf<SettingsHighlight?>(null)
        private set

    private var highlightNonce = 0

    /**
     * Show the settings window at [section] (or a plugin page) and light up one row.
     *
     * The one entry point for "take me to this setting" - used by the global search, which finds a
     * row by name and has to be able to land on it rather than merely on its page.
     *
     * [highlightable] false means the entry can only reach its section: either the page belongs to
     * another module, or its control carries no search target. Pointing at nothing is the honest
     * outcome there, and better than leaving the last pick's highlight armed.
     */
    fun reveal(
        section: String?,
        group: String?,
        label: String,
        highlightable: Boolean,
    ) {
        highlight =
            if (highlightable) {
                highlightNonce += 1
                SettingsHighlight(group = group, label = label, nonce = highlightNonce)
            } else {
                null
            }
        open(section)
    }

    fun open(section: String? = null) {
        if (section != null) {
            this.section = section
            sectionRequest++
        }
        if (visible) {
            focusRequest++
        } else {
            visible = true
        }
    }

    /**
     * The window was closed.
     *
     * The section is cleared with it so a later plain [open] starts at the default rather than
     * re-landing on wherever a deep link last went. [focusRequest] deliberately is NOT reset: it is
     * an ever-increasing signal, and zeroing it could make the next request repeat a value the
     * window has already handled, which would swallow it.
     */
    fun close() {
        visible = false
        section = null
    }
}
