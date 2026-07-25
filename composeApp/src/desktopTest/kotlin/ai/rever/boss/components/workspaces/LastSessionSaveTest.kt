package ai.rever.boss.components.workspaces

import ai.rever.boss.plugin.workspace.PanelConfig
import ai.rever.boss.plugin.workspace.SplitConfig
import ai.rever.boss.plugin.workspace.TabConfig
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for the "Last Session" shutdown save (Issue #19).
 *
 * Window dispose used to `runBlocking` around [WorkspaceManager.saveCurrentWorkspace],
 * which is fire-and-forget on a `Dispatchers.Main` scope — so the wrapper awaited
 * nothing and the write could be dropped entirely when the process exited first.
 * The shutdown path now writes synchronously.
 */
class LastSessionSaveTest {
    private fun layout(name: String) =
        LayoutWorkspace(
            id = "some-other-id",
            name = name,
            description = "whatever",
            layout =
                SplitConfig.SinglePanel(
                    panel =
                        PanelConfig(
                            id = "panel-1",
                            tabs = listOf(TabConfig(type = "terminal", title = "Terminal")),
                        ),
                ),
        )

    @Test
    fun `asLastSession stamps the shared Last Session identity`() {
        val stamped = asLastSession(layout("Some Window Layout"))

        assertEquals(LAST_SESSION_ID, stamped.id)
        assertEquals(LAST_SESSION_NAME, stamped.name)
        assertEquals("Automatically saved session", stamped.description)
        // Layout content is preserved untouched.
        assertEquals(layout("Some Window Layout").layout, stamped.layout)
    }

    /**
     * The load-bearing property for shutdown: when the call returns, the bytes are
     * already on disk. No coroutine, no scope the process can outrun.
     */
    @Test
    fun `saveWorkspaceBlocking has written the file by the time it returns`() {
        val fileManager = WorkspaceFileManager()
        val fileName = "boss-last-session-save-test-${System.nanoTime()}.json"

        val path = fileManager.saveWorkspaceBlocking(asLastSession(layout("Test Layout")), fileName)

        val file = path?.let(::File)
        try {
            assertTrue(path != null, "Blocking save should report the written path")
            assertTrue(file!!.exists(), "File must exist immediately after the call returns")
            assertTrue(file.length() > 0, "File must have content immediately after the call returns")

            val reloaded = WorkspaceSerializer.deserialize(file.readText())
            assertEquals(LAST_SESSION_ID, reloaded.id)
            assertEquals(LAST_SESSION_NAME, reloaded.name)
        } finally {
            file?.delete()
        }
    }
}
