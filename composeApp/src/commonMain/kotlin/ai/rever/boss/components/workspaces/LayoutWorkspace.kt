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
    val filePath: String? = null // For editor tabs
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
    val timestamp: Long = 0L
) {
    companion object {
        fun generateId(): String = "workspace-${Clock.System.now().toEpochMilliseconds()}"
    }
}

/**
 * Predefined workspaces
 */
object PredefinedWorkspaces {
    // No predefined workspaces - all workspaces will be user-created
    val allWorkspaces = listOf<LayoutWorkspace>()
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
