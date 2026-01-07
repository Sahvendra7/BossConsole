package ai.rever.boss.components.workspaces

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock

/**
 * Represents a tab workspace
 */
@Serializable
data class TabConfig(
    val type: String, // "browser", "terminal", "editor"
    val title: String,
    val url: String? = null, // For browser tabs
    val filePath: String? = null, // For editor tabs
    val faviconCacheKey: String? = null, // Cache key for browser tab favicon
    val initialCommand: String? = null, // For terminal tabs - command to run on start
    val workingDirectory: String? = null // For terminal tabs - working directory
)

/**
 * Represents a panel workspace
 */
@Serializable
data class PanelConfig(
    val id: String,
    val tabs: List<TabConfig>
)

/**
 * Represents a split workspace
 */
@Serializable
sealed class SplitConfig {
    @Serializable
    data class SinglePanel(
        val panel: PanelConfig
    ) : SplitConfig()
    
    @Serializable
    data class VerticalSplit(
        val left: SplitConfig,
        val right: SplitConfig
    ) : SplitConfig()
    
    @Serializable
    data class HorizontalSplit(
        val top: SplitConfig,
        val bottom: SplitConfig
    ) : SplitConfig()
}

/**
 * Extract all panels from a SplitConfig with human-readable labels
 * Returns list of (panelId, label) pairs
 */
fun SplitConfig.extractPanels(prefix: String = ""): List<Pair<String, String>> {
    return when (this) {
        is SplitConfig.SinglePanel -> {
            val label = if (prefix.isEmpty()) "Main Panel" else prefix.trim() + " Panel"
            listOf(panel.id to label)
        }
        is SplitConfig.VerticalSplit -> {
            left.extractPanels("${prefix}Left ") + right.extractPanels("${prefix}Right ")
        }
        is SplitConfig.HorizontalSplit -> {
            top.extractPanels("${prefix}Top ") + bottom.extractPanels("${prefix}Bottom ")
        }
    }
}

/**
 * Breadcrumb display workspace
 */
@Serializable
data class BreadcrumbConfig(
    val enabled: Boolean = true,
    val showWorkspacePath: Boolean = true,
    val showTabPath: Boolean = true,
    val maxLength: Int = 50,
    val separator: String = " › "
)

/**
 * Represents a complete layout workspace
 */
@Serializable
data class LayoutWorkspace(
    val id: String = "",  // Unique identifier for the workspace
    val name: String,
    val description: String,
    val layout: SplitConfig,
    val breadcrumbConfig: BreadcrumbConfig = BreadcrumbConfig(),
    val timestamp: Long = 0L,
    val projectPath: String? = null  // Project path associated with this workspace
) {
    companion object {
        fun generateId(): String = "workspace-${Clock.System.now().toEpochMilliseconds()}"
    }
}

/**
 * Predefined workspaces matching the split templates.
 * Uses placeholders that are resolved at runtime:
 * - {projectPath}: Current project directory
 * - {gitRemoteUrl}: Git remote origin URL
 */
object PredefinedWorkspaces {
    private fun generatePanelId() = "panel-${System.currentTimeMillis()}-${(Math.random() * 10000).toInt()}"

    val allWorkspaces = listOf(
        // Claude Code: Terminal + Browser
        LayoutWorkspace(
            id = "workspace-claude-code",
            name = "Claude Code",
            description = "Terminal with Claude CLI + Browser with GitHub",
            layout = SplitConfig.VerticalSplit(
                left = SplitConfig.SinglePanel(
                    PanelConfig(
                        id = generatePanelId(),
                        tabs = listOf(
                            TabConfig(
                                type = "terminal",
                                title = "Claude Code",
                                initialCommand = "cd {projectPath} && clear && claude {claudeContinueFlag} --dangerously-skip-permissions",
                                workingDirectory = "{projectPath}"
                            )
                        )
                    )
                ),
                right = SplitConfig.SinglePanel(
                    PanelConfig(
                        id = generatePanelId(),
                        tabs = listOf(
                            TabConfig(
                                type = "browser",
                                title = "GitHub",
                                url = "{gitRemoteUrl}"
                            )
                        )
                    )
                )
            )
        ),

        // Code Review: Editor (left) + Browser (right) + Terminal (bottom)
        LayoutWorkspace(
            id = "workspace-code-review",
            name = "Code Review",
            description = "README + GitHub + Claude Code",
            layout = SplitConfig.HorizontalSplit(
                top = SplitConfig.VerticalSplit(
                    left = SplitConfig.SinglePanel(
                        PanelConfig(
                            id = generatePanelId(),
                            tabs = listOf(
                                TabConfig(
                                    type = "editor",
                                    title = "README.md",
                                    filePath = "{projectPath}/README.md"
                                )
                            )
                        )
                    ),
                    right = SplitConfig.SinglePanel(
                        PanelConfig(
                            id = generatePanelId(),
                            tabs = listOf(
                                TabConfig(
                                    type = "browser",
                                    title = "GitHub",
                                    url = "{gitRemoteUrl}"
                                )
                            )
                        )
                    )
                ),
                bottom = SplitConfig.SinglePanel(
                    PanelConfig(
                        id = generatePanelId(),
                        tabs = listOf(
                            TabConfig(
                                type = "terminal",
                                title = "Claude Code",
                                initialCommand = "cd {projectPath} && clear && claude {claudeContinueFlag} --dangerously-skip-permissions",
                                workingDirectory = "{projectPath}"
                            )
                        )
                    )
                )
            )
        ),

        // Gemini: Terminal + Browser
        LayoutWorkspace(
            id = "workspace-gemini",
            name = "Gemini",
            description = "Gemini CLI + GitHub",
            layout = SplitConfig.VerticalSplit(
                left = SplitConfig.SinglePanel(
                    PanelConfig(
                        id = generatePanelId(),
                        tabs = listOf(
                            TabConfig(
                                type = "terminal",
                                title = "Gemini",
                                initialCommand = "cd {projectPath} && clear && gemini",
                                workingDirectory = "{projectPath}"
                            )
                        )
                    )
                ),
                right = SplitConfig.SinglePanel(
                    PanelConfig(
                        id = generatePanelId(),
                        tabs = listOf(
                            TabConfig(
                                type = "browser",
                                title = "GitHub",
                                url = "{gitRemoteUrl}"
                            )
                        )
                    )
                )
            )
        ),

        // Codex: Terminal + Browser
        LayoutWorkspace(
            id = "workspace-codex",
            name = "Codex",
            description = "OpenAI Codex CLI + GitHub",
            layout = SplitConfig.VerticalSplit(
                left = SplitConfig.SinglePanel(
                    PanelConfig(
                        id = generatePanelId(),
                        tabs = listOf(
                            TabConfig(
                                type = "terminal",
                                title = "Codex",
                                initialCommand = "cd {projectPath} && clear && codex",
                                workingDirectory = "{projectPath}"
                            )
                        )
                    )
                ),
                right = SplitConfig.SinglePanel(
                    PanelConfig(
                        id = generatePanelId(),
                        tabs = listOf(
                            TabConfig(
                                type = "browser",
                                title = "GitHub",
                                url = "{gitRemoteUrl}"
                            )
                        )
                    )
                )
            )
        ),

        // OpenCode: Terminal + Browser
        LayoutWorkspace(
            id = "workspace-opencode",
            name = "OpenCode",
            description = "OpenCode AI CLI + GitHub",
            layout = SplitConfig.VerticalSplit(
                left = SplitConfig.SinglePanel(
                    PanelConfig(
                        id = generatePanelId(),
                        tabs = listOf(
                            TabConfig(
                                type = "terminal",
                                title = "OpenCode",
                                initialCommand = "cd {projectPath} && clear && opencode",
                                workingDirectory = "{projectPath}"
                            )
                        )
                    )
                ),
                right = SplitConfig.SinglePanel(
                    PanelConfig(
                        id = generatePanelId(),
                        tabs = listOf(
                            TabConfig(
                                type = "browser",
                                title = "GitHub",
                                url = "{gitRemoteUrl}"
                            )
                        )
                    )
                )
            )
        ),

        // Terminal + Browser
        LayoutWorkspace(
            id = "workspace-terminal-browser",
            name = "Terminal + Browser",
            description = "Terminal on left, Browser on right",
            layout = SplitConfig.VerticalSplit(
                left = SplitConfig.SinglePanel(
                    PanelConfig(
                        id = generatePanelId(),
                        tabs = listOf(
                            TabConfig(
                                type = "terminal",
                                title = "Terminal",
                                initialCommand = "cd {projectPath}",
                                workingDirectory = "{projectPath}"
                            )
                        )
                    )
                ),
                right = SplitConfig.SinglePanel(
                    PanelConfig(
                        id = generatePanelId(),
                        tabs = listOf(
                            TabConfig(
                                type = "browser",
                                title = "Google",
                                url = "https://google.com"
                            )
                        )
                    )
                )
            )
        ),

        // Dual Terminal
        LayoutWorkspace(
            id = "workspace-dual-terminal",
            name = "Dual Terminal",
            description = "Two terminals side by side",
            layout = SplitConfig.VerticalSplit(
                left = SplitConfig.SinglePanel(
                    PanelConfig(
                        id = generatePanelId(),
                        tabs = listOf(
                            TabConfig(
                                type = "terminal",
                                title = "Terminal 1",
                                initialCommand = "cd {projectPath}",
                                workingDirectory = "{projectPath}"
                            )
                        )
                    )
                ),
                right = SplitConfig.SinglePanel(
                    PanelConfig(
                        id = generatePanelId(),
                        tabs = listOf(
                            TabConfig(
                                type = "terminal",
                                title = "Terminal 2",
                                initialCommand = "cd {projectPath}",
                                workingDirectory = "{projectPath}"
                            )
                        )
                    )
                )
            )
        )
    )
}

/**
 * JSON serializer for workspaces
 */
object WorkspaceSerializer {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    fun serialize(config: LayoutWorkspace): String {
        return json.encodeToString(config)
    }
    
    fun deserialize(jsonString: String): LayoutWorkspace {
        return json.decodeFromString(jsonString)
    }
}
