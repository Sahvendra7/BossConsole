package ai.rever.boss.updater

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Every method a window can call on its handle must go through `guard(...)`.
 *
 * A released handle belongs to a window that has closed, and the updater it delegates to is shared
 * by every other window. The guard is the only thing standing between "this window is gone" and
 * "this window can still mutate the state the surviving windows are looking at" - and the cost of
 * forgetting it is invisible at the call site, because an unguarded method works perfectly until
 * the window closes.
 *
 * A source check rather than a behavioural one, deliberately. The behavioural version cannot tell
 * the two apart for every method: [UpdateHandle.cancelDownload] on a live handle is itself a no-op
 * unless a download is actually running, so a released handle and a guarded live one both leave
 * `updateState` at `Idle` and the test would pass with the guard removed. What is checkable, and
 * what actually regresses, is that a newly added method was written with the guard at all.
 *
 * `cancelDownload` is the method that prompted this: it was added for the Cancel button on the
 * download banner, and it mutates a download every window can see.
 */
class UpdateHandleGuardTest {
    private fun repoRoot(): File? =
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "composeApp/build.gradle.kts").isFile }

    private fun ownershipSource(): String {
        val root = assertNotNull(repoRoot(), "could not locate the repository root")
        return File(
            root,
            "composeApp/src/commonMain/kotlin/ai/rever/boss/updater/UpdateOwnership.kt",
        ).readText()
    }

    /**
     * The body of each `override fun` in the handle, keyed by name.
     *
     * Scoped to overrides because those are exactly the [UpdateHandle] surface; the class's own
     * private helpers (`guard` itself among them) are not something a window can call.
     */
    private fun overrideBodies(source: String): Map<String, String> {
        val starts =
            Regex("""\n\s+override fun ([A-Za-z0-9_]+)\(""")
                .findAll(source)
                .map { it.groupValues[1] to it.range.first }
                .toList()
        return starts
            .mapIndexed { index, (name, start) ->
                val end = starts.getOrNull(index + 1)?.second ?: source.length
                name to source.substring(start, end)
            }.toMap()
    }

    @Test
    fun `every handle override is guarded`() {
        val bodies = overrideBodies(ownershipSource())
        // Sanity: a regex that matched nothing would pass this test vacuously, which is the
        // failure mode a source check is most prone to.
        assertTrue(bodies.size >= 5, "found only ${bodies.size} overrides - the parse is wrong, not the code")

        val unguarded = bodies.filterValues { !it.contains("guard(") }
        assertEquals(
            emptySet(),
            unguarded.keys,
            "these handle methods let a released window mutate shared update state: ${unguarded.keys}",
        )
    }

    @Test
    fun `cancelDownload is one of them`() {
        // Named explicitly so deleting the method does not quietly shrink the set above to a
        // still-passing one.
        val bodies = overrideBodies(ownershipSource())
        assertTrue(
            "cancelDownload" in bodies,
            "UpdateHandle.cancelDownload is gone; the banner's Cancel has nothing to call",
        )
        assertTrue(bodies.getValue("cancelDownload").contains("guard("), "cancelDownload must be guarded")
    }
}
