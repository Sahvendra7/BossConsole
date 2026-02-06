package ai.rever.boss.components.wizard.plugin

import ai.rever.boss.plugin.PluginStoreSetup
import ai.rever.boss.plugin.repository.PluginInfo
import ai.rever.boss.plugin.repository.PluginWithSource
import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Provides the list of available plugins for the installation wizard.
 *
 * This class fetches plugins from the repository manager and converts them
 * to WizardPluginInfo with appropriate categories and default selections.
 */
object PluginListProvider {
    private val logger = BossLogger.forComponent("PluginListProvider")

    /**
     * Plugin IDs that should be selected by default in the wizard.
     * Plugin IDs use the format: ai.rever.boss.plugin.dynamic.<name>
     */
    val DEFAULT_PLUGIN_IDS = setOf(
        "ai.rever.boss.plugin.dynamic.terminal",
        "ai.rever.boss.plugin.dynamic.console",
        "ai.rever.boss.plugin.dynamic.codebase",
        "ai.rever.boss.plugin.dynamic.bookmarks",
        "ai.rever.boss.plugin.dynamic.topofmind"
    )

    /**
     * Map of plugin IDs to their categories.
     * Plugin IDs use the format: ai.rever.boss.plugin.dynamic.<name>
     */
    private val PLUGIN_CATEGORIES = mapOf(
        // Essential
        "ai.rever.boss.plugin.dynamic.terminal" to PluginCategory.ESSENTIAL,
        "ai.rever.boss.plugin.dynamic.console" to PluginCategory.ESSENTIAL,

        // Developer
        "ai.rever.boss.plugin.dynamic.codebase" to PluginCategory.DEVELOPER,
        "ai.rever.boss.plugin.dynamic.gitstatus" to PluginCategory.DEVELOPER,
        "ai.rever.boss.plugin.dynamic.gitlog" to PluginCategory.DEVELOPER,

        // Productivity
        "ai.rever.boss.plugin.dynamic.bookmarks" to PluginCategory.PRODUCTIVITY,
        "ai.rever.boss.plugin.dynamic.topofmind" to PluginCategory.PRODUCTIVITY,
        "ai.rever.boss.plugin.dynamic.downloads" to PluginCategory.PRODUCTIVITY,

        // Automation
        "ai.rever.boss.plugin.dynamic.llmrpa" to PluginCategory.AUTOMATION,
        "ai.rever.boss.plugin.dynamic.rparecorder" to PluginCategory.AUTOMATION,
        "ai.rever.boss.plugin.dynamic.rpaengine" to PluginCategory.AUTOMATION,

        // Admin
        "ai.rever.boss.plugin.dynamic.adminrolemanagement" to PluginCategory.ADMIN,
        "ai.rever.boss.plugin.dynamic.rolecreation" to PluginCategory.ADMIN,
        "ai.rever.boss.plugin.dynamic.secretmanager" to PluginCategory.ADMIN,
        "ai.rever.boss.plugin.dynamic.usersecretlist" to PluginCategory.ADMIN
    )

    /**
     * Map of plugin IDs to their icons.
     * Plugin IDs use the format: ai.rever.boss.plugin.dynamic.<name>
     */
    private val PLUGIN_ICONS: Map<String, ImageVector> = mapOf(
        "ai.rever.boss.plugin.dynamic.terminal" to Icons.Default.Terminal,
        "ai.rever.boss.plugin.dynamic.console" to Icons.Default.Code,
        "ai.rever.boss.plugin.dynamic.codebase" to Icons.Default.Folder,
        "ai.rever.boss.plugin.dynamic.gitstatus" to Icons.Default.Engineering,
        "ai.rever.boss.plugin.dynamic.gitlog" to Icons.Default.History,
        "ai.rever.boss.plugin.dynamic.bookmarks" to Icons.Default.Bookmark,
        "ai.rever.boss.plugin.dynamic.topofmind" to Icons.Default.Lightbulb,
        "ai.rever.boss.plugin.dynamic.downloads" to Icons.Default.Download,
        "ai.rever.boss.plugin.dynamic.llmrpa" to Icons.Default.Psychology,
        "ai.rever.boss.plugin.dynamic.rparecorder" to Icons.Default.Videocam,
        "ai.rever.boss.plugin.dynamic.rpaengine" to Icons.Default.PlayArrow,
        "ai.rever.boss.plugin.dynamic.adminrolemanagement" to Icons.Default.ManageAccounts,
        "ai.rever.boss.plugin.dynamic.rolecreation" to Icons.Default.AdminPanelSettings,
        "ai.rever.boss.plugin.dynamic.secretmanager" to Icons.Default.Key,
        "ai.rever.boss.plugin.dynamic.usersecretlist" to Icons.Default.Key,
        "ai.rever.boss.plugin.dynamic.performance" to Icons.Default.AutoAwesome,
        "ai.rever.boss.plugin.dynamic.fluck" to Icons.Default.Extension,
        "ai.rever.boss.plugin.dynamic.runconfigurations" to Icons.Default.PlayArrow
    )

    /**
     * Get available plugins for the wizard from the repository.
     *
     * @return List of plugins formatted for the wizard
     */
    suspend fun getAvailablePlugins(): List<WizardPluginInfo> {
        return try {
            val repositoryManager = PluginStoreSetup.repositoryManager
            if (repositoryManager == null) {
                logger.warn(LogCategory.SYSTEM, "Repository manager not initialized, using fallback list")
                return getFallbackPluginList()
            }

            val result = repositoryManager.listAllPlugins()
            result.fold(
                onSuccess = { pluginsWithSource: List<PluginWithSource> ->
                    logger.info(LogCategory.SYSTEM, "Fetched plugins for wizard", mapOf(
                        "count" to pluginsWithSource.size
                    ))

                    pluginsWithSource.map { pws: PluginWithSource ->
                        convertToWizardPluginInfo(pws.plugin)
                    }.sortedWith(compareBy({ it.category.ordinal }, { !it.isDefault }, { it.name }))
                },
                onFailure = { error ->
                    logger.error(LogCategory.SYSTEM, "Failed to fetch plugins", error = error)
                    getFallbackPluginList()
                }
            )
        } catch (e: Exception) {
            logger.error(LogCategory.SYSTEM, "Error getting available plugins", error = e)
            getFallbackPluginList()
        }
    }

    /**
     * Convert a PluginInfo to WizardPluginInfo.
     */
    private fun convertToWizardPluginInfo(plugin: PluginInfo): WizardPluginInfo {
        val category = PLUGIN_CATEGORIES[plugin.pluginId] ?: PluginCategory.OTHER
        val isDefault = plugin.pluginId in DEFAULT_PLUGIN_IDS
        val icon = PLUGIN_ICONS[plugin.pluginId] ?: Icons.Default.Extension

        return WizardPluginInfo(
            id = plugin.pluginId,
            name = plugin.displayName,
            description = plugin.description,
            version = plugin.version,
            icon = icon,
            isDefault = isDefault,
            category = category,
            downloadUrl = plugin.downloadUrl
        )
    }

    /**
     * Fallback list of plugins when the repository is not available.
     * This ensures the wizard can still function offline or during initialization.
     */
    private fun getFallbackPluginList(): List<WizardPluginInfo> {
        return listOf(
            // Essential
            WizardPluginInfo(
                id = "plugin-panel-terminal",
                name = "Terminal",
                description = "Integrated terminal for command-line access",
                version = "1.0.0",
                icon = Icons.Default.Terminal,
                isDefault = true,
                category = PluginCategory.ESSENTIAL
            ),
            WizardPluginInfo(
                id = "plugin-panel-console",
                name = "Console",
                description = "Application logs and debugging output",
                version = "1.0.0",
                icon = Icons.Default.Code,
                isDefault = true,
                category = PluginCategory.ESSENTIAL
            ),

            // Developer
            WizardPluginInfo(
                id = "plugin-panel-codebase",
                name = "Codebase",
                description = "File browser and code navigation",
                version = "1.0.0",
                icon = Icons.Default.Folder,
                isDefault = true,
                category = PluginCategory.DEVELOPER
            ),
            WizardPluginInfo(
                id = "plugin-panel-git-status",
                name = "Git Status",
                description = "View git repository status",
                version = "1.0.0",
                icon = Icons.Default.Engineering,
                isDefault = false,
                category = PluginCategory.DEVELOPER
            ),
            WizardPluginInfo(
                id = "plugin-panel-git-log",
                name = "Git Log",
                description = "Browse git commit history",
                version = "1.0.0",
                icon = Icons.Default.History,
                isDefault = false,
                category = PluginCategory.DEVELOPER
            ),

            // Productivity
            WizardPluginInfo(
                id = "plugin-panel-bookmarks",
                name = "Bookmarks",
                description = "Save and organize your favorite tabs",
                version = "1.0.0",
                icon = Icons.Default.Bookmark,
                isDefault = true,
                category = PluginCategory.PRODUCTIVITY
            ),
            WizardPluginInfo(
                id = "plugin-panel-topofmind",
                name = "Top of Mind",
                description = "Quick access to recent and important tabs",
                version = "1.0.0",
                icon = Icons.Default.Lightbulb,
                isDefault = true,
                category = PluginCategory.PRODUCTIVITY
            ),
            WizardPluginInfo(
                id = "plugin-panel-downloads",
                name = "Downloads",
                description = "Manage downloaded files",
                version = "1.0.0",
                icon = Icons.Default.Download,
                isDefault = false,
                category = PluginCategory.PRODUCTIVITY
            ),

            // Automation
            WizardPluginInfo(
                id = "plugin-panel-llm-rpa",
                name = "LLM RPA",
                description = "AI-powered robotic process automation",
                version = "1.0.0",
                icon = Icons.Default.Psychology,
                isDefault = false,
                category = PluginCategory.AUTOMATION
            ),
            WizardPluginInfo(
                id = "plugin-panel-rpa-recorder",
                name = "RPA Recorder",
                description = "Record automation scripts",
                version = "1.0.0",
                icon = Icons.Default.Videocam,
                isDefault = false,
                category = PluginCategory.AUTOMATION
            ),
            WizardPluginInfo(
                id = "plugin-panel-rpa-engine",
                name = "RPA Engine",
                description = "Execute automation scripts",
                version = "1.0.0",
                icon = Icons.Default.PlayArrow,
                isDefault = false,
                category = PluginCategory.AUTOMATION
            ),

            // Admin
            WizardPluginInfo(
                id = "plugin-panel-role-management",
                name = "Role Management",
                description = "Manage user roles and permissions",
                version = "1.0.0",
                icon = Icons.Default.ManageAccounts,
                isDefault = false,
                category = PluginCategory.ADMIN
            ),
            WizardPluginInfo(
                id = "plugin-panel-role-creation",
                name = "Role Creation",
                description = "Create and configure new roles",
                version = "1.0.0",
                icon = Icons.Default.AdminPanelSettings,
                isDefault = false,
                category = PluginCategory.ADMIN
            ),
            WizardPluginInfo(
                id = "plugin-panel-secret-manager",
                name = "Secret Manager",
                description = "Securely manage API keys and secrets",
                version = "1.0.0",
                icon = Icons.Default.Key,
                isDefault = false,
                category = PluginCategory.ADMIN
            )
        )
    }
}
