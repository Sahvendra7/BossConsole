package ai.rever.boss.components.workspaces

import ai.rever.boss.plugin.workspace.SplitConfig.SinglePanel
import ai.rever.boss.utils.SystemUtils
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Locks down the platform default workspace and its one-time migration.
 *
 * The platform branch is driven explicitly through [defaultWorkspaceIdFor] and
 * the `isWindows` parameter of [WorkspaceSettingsMigrations.migrate], so both
 * branches run on every CI leg rather than only on the matching runner. The
 * host-resolved [defaultWorkspaceIdForPlatform] is checked separately - that is
 * the only assertion that this OS is wired to the right branch at all.
 */
class WorkspaceDefaultsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `windows defaults to the browser-only workspace`() {
        assertEquals(PredefinedWorkspaces.BROWSER_ONLY_ID, defaultWorkspaceIdFor(isWindows = true))
    }

    @Test
    fun `other platforms keep the claude code workspace`() {
        assertEquals(PredefinedWorkspaces.CLAUDE_CODE_ID, defaultWorkspaceIdFor(isWindows = false))
    }

    @Test
    fun `host resolution follows this platform`() {
        val expected =
            if (SystemUtils.isWindows) PredefinedWorkspaces.BROWSER_ONLY_ID else PredefinedWorkspaces.CLAUDE_CODE_ID
        assertEquals(expected, defaultWorkspaceIdForPlatform())
        assertEquals(expected, WorkspaceSettings().defaultWorkspaceId)
    }

    @Test
    fun `the browser-only workspace is a single browser panel on the boss home page`() {
        val workspace = PredefinedWorkspaces.allWorkspaces.single { it.id == PredefinedWorkspaces.BROWSER_ONLY_ID }
        val layout = workspace.layout
        assertTrue(layout is SinglePanel, "browser-only must not split the window: $layout")
        val tab = layout.panel.tabs.single()
        assertEquals("browser", tab.type)
        assertEquals("https://risalabs.ai", tab.url)
    }

    @Test
    fun `every predefined workspace id is unique`() {
        val ids = PredefinedWorkspaces.allWorkspaces.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "duplicate workspace ids: $ids")
    }

    /**
     * The settings file predates the version field, so a file written by any
     * older build decodes as version 0 - which is what makes the migration
     * reachable at all. If the field ever defaults to the current version, every
     * install silently skips every migration.
     */
    @Test
    fun `a settings file without the version field decodes as version zero`() {
        val legacy = json.decodeFromString<WorkspaceSettings>("""{"defaultWorkspaceId":"workspace-claude-code"}""")
        assertEquals(0, legacy.settingsVersion)
        assertEquals(PredefinedWorkspaces.CLAUDE_CODE_ID, legacy.defaultWorkspaceId)
    }

    @Test
    fun `an existing windows install on the old default moves to browser-only once`() {
        val legacy = WorkspaceSettings(defaultWorkspaceId = PredefinedWorkspaces.CLAUDE_CODE_ID, settingsVersion = 0)

        val migrated = assertNotNull(WorkspaceSettingsMigrations.migrate(legacy, isWindows = true))
        assertEquals(PredefinedWorkspaces.BROWSER_ONLY_ID, migrated.defaultWorkspaceId)
        assertEquals(WorkspaceSettings.CURRENT_SETTINGS_VERSION, migrated.settingsVersion)

        // Stamped version, so the next launch is a no-op rather than a rewrite.
        assertNull(WorkspaceSettingsMigrations.migrate(migrated, isWindows = true))
    }

    @Test
    fun `a windows install that chose another workspace keeps it`() {
        val chosen = WorkspaceSettings(defaultWorkspaceId = "workspace-dual-terminal", settingsVersion = 0)

        val migrated = assertNotNull(WorkspaceSettingsMigrations.migrate(chosen, isWindows = true))
        assertEquals("workspace-dual-terminal", migrated.defaultWorkspaceId)
        assertEquals(WorkspaceSettings.CURRENT_SETTINGS_VERSION, migrated.settingsVersion)
    }

    @Test
    fun `disabled auto-apply survives the migration on windows`() {
        val none = WorkspaceSettings(defaultWorkspaceId = "none", settingsVersion = 0)

        val migrated = assertNotNull(WorkspaceSettingsMigrations.migrate(none, isWindows = true))
        assertEquals("none", migrated.defaultWorkspaceId)
    }

    @Test
    fun `non-windows installs are only stamped, never repointed`() {
        val legacy = WorkspaceSettings(defaultWorkspaceId = PredefinedWorkspaces.CLAUDE_CODE_ID, settingsVersion = 0)

        val migrated = assertNotNull(WorkspaceSettingsMigrations.migrate(legacy, isWindows = false))
        assertEquals(PredefinedWorkspaces.CLAUDE_CODE_ID, migrated.defaultWorkspaceId)
        assertEquals(WorkspaceSettings.CURRENT_SETTINGS_VERSION, migrated.settingsVersion)
    }
}
