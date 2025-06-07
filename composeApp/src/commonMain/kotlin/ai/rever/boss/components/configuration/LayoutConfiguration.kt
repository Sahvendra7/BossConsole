package ai.rever.boss.components.configuration

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

/**
 * Represents a tab configuration
 */
@Serializable
data class TabConfig(
    val type: String, // "browser", "terminal", "editor"
    val title: String,
    val url: String? = null, // For browser tabs
    val filePath: String? = null // For editor tabs
)

/**
 * Represents a panel configuration
 */
@Serializable
data class PanelConfig(
    val id: String,
    val tabs: List<TabConfig>
)

/**
 * Represents a split configuration
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
 * Represents a complete layout configuration
 */
@Serializable
data class LayoutConfiguration(
    val name: String,
    val description: String,
    val layout: SplitConfig,
    val timestamp: Long = 0L
)

/**
 * Predefined configurations
 */
object PredefinedConfigurations {
    // No predefined configurations - all configurations will be user-created
    val allConfigurations = listOf<LayoutConfiguration>()
}

/**
 * JSON serializer for configurations
 */
object ConfigurationSerializer {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    fun serialize(config: LayoutConfiguration): String {
        return json.encodeToString(config)
    }
    
    fun deserialize(jsonString: String): LayoutConfiguration {
        return json.decodeFromString(jsonString)
    }
}