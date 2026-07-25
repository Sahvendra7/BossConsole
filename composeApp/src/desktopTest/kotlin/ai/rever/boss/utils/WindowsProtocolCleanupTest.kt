package ai.rever.boss.utils

import ai.rever.boss.utils.WindowsProtocolHandler.UnregisterOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Tests for [classifyProtocolCleanup] — the safety policy behind
 * `BOSS.exe --unregister-protocol`.
 *
 * Only the `reg delete` itself needs a real registry; the decision of *whether* deleting is
 * safe is pure, and it is the part with a bad failure mode: deleting a registration that
 * belongs to a live install (or that we merely failed to read) breaks `boss://` links for
 * someone else's working BOSS. These cases run on any host.
 */
class WindowsProtocolCleanupTest {
    private val thisApp = """C:\Users\me\AppData\Local\BOSS\BOSS.exe"""
    private val otherApp = """C:\Program Files\BOSS\BOSS.exe"""

    private fun classify(
        rootPresent: Boolean = true,
        command: CommandState,
        appPath: String? = thisApp,
        existing: Set<String> = setOf(thisApp, otherApp),
    ) = classifyProtocolCleanup(rootPresent, command, appPath) { it in existing }

    @Test
    fun `absent when nothing is registered`() {
        assertEquals(
            UnregisterOutcome.ABSENT,
            classify(rootPresent = false, command = CommandState.Missing),
        )
    }

    /** Root key without a command value: a partial registration this code produced. */
    @Test
    fun `deletes a partial registration that has no command value`() {
        assertNull(classify(command = CommandState.Missing))
    }

    /**
     * Regression: an unreadable command must NOT be treated as a missing one. A
     * `REG_EXPAND_SZ` or unquoted value is what an installer-authored registration looks
     * like, and a failed registry *read* must never escalate to a *delete*.
     */
    @Test
    fun `leaves an unreadable command alone`() {
        assertEquals(
            UnregisterOutcome.UNREADABLE,
            classify(command = CommandState.Unreadable),
        )
    }

    @Test
    fun `leaves an unquoted command alone`() {
        assertEquals(
            UnregisterOutcome.UNREADABLE,
            classify(command = CommandState.Present("""C:\Program Files\BOSS\BOSS.exe "%1"""")),
        )
    }

    @Test
    fun `deletes a registration pointing at this installation`() {
        assertNull(classify(command = CommandState.Present(""""$thisApp" "%1"""")))
    }

    @Test
    fun `matches this installation case-insensitively`() {
        assertNull(classify(command = CommandState.Present(""""${thisApp.uppercase()}" "%1"""")))
    }

    @Test
    fun `deletes a registration pointing at a deleted executable`() {
        assertEquals(
            null,
            classify(
                command = CommandState.Present(""""C:\Old\BOSS\BOSS.exe" "%1""""),
                existing = setOf(thisApp),
            ),
        )
    }

    @Test
    fun `leaves another live installation registered`() {
        assertEquals(
            UnregisterOutcome.OTHER_INSTALL,
            classify(command = CommandState.Present(""""$otherApp" "%1"""")),
        )
    }

    /** Development runs cannot determine their own path; that must not license a delete. */
    @Test
    fun `leaves another live installation registered when this app path is unknown`() {
        assertEquals(
            UnregisterOutcome.OTHER_INSTALL,
            classify(command = CommandState.Present(""""$otherApp" "%1""""), appPath = null),
        )
    }

    @Test
    fun `still deletes a dead registration when this app path is unknown`() {
        assertNull(
            classify(
                command = CommandState.Present(""""C:\Old\BOSS\BOSS.exe" "%1""""),
                appPath = null,
                existing = emptySet(),
            ),
        )
    }

    /** Absent wins over everything: no key, nothing to decide. */
    @Test
    fun `absent takes precedence over a readable command`() {
        assertEquals(
            UnregisterOutcome.ABSENT,
            classify(rootPresent = false, command = CommandState.Present(""""$otherApp" "%1"""")),
        )
    }

    @Test
    fun `extracts the executable from a quoted command only`() {
        assertEquals(thisApp, extractExecutablePath(""""$thisApp" "%1""""))
        assertNull(extractExecutablePath("""$thisApp "%1""""))
        assertNull(extractExecutablePath(""))
    }
}
