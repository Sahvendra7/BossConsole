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
    val priorAuth = LayoutConfiguration(
        name = "PriorAuth",
        description = "Prior Authorization workflow",
        layout = SplitConfig.VerticalSplit(
            left = SplitConfig.SinglePanel(
                PanelConfig(
                    id = "left",
                    tabs = listOf(
                        TabConfig(
                            type = "browser",
                            title = "PA Dashboard",
                            url = "https://pa-dashboard-dev.web.app/"
                        )
                    )
                )
            ),
            right = SplitConfig.HorizontalSplit(
                top = SplitConfig.SinglePanel(
                    PanelConfig(
                        id = "right_top",
                        tabs = listOf(
                            TabConfig(
                                type = "browser",
                                title = "OncoEMR",
                                url = "https://secure15.oncoemr.com"
                            )
                        )
                    )
                ),
                bottom = SplitConfig.SinglePanel(
                    PanelConfig(
                        id = "right_bottom",
                        tabs = listOf(
                            TabConfig(
                                type = "browser",
                                title = "CoverMyMeds",
                                url = "https://oidc.covermymeds.com/login"
                            ),
                            TabConfig(
                                type = "browser",
                                title = "OneHealthcareID",
                                url = "https://identity.onehealthcareid.com/"
                            )
                        )
                    )
                )
            )
        )
    )
    
    val designer = LayoutConfiguration(
        name = "Designer",
        description = "Design tools workspace",
        layout = SplitConfig.VerticalSplit(
            left = SplitConfig.SinglePanel(
                PanelConfig(
                    id = "left",
                    tabs = listOf(
                        TabConfig(
                            type = "browser",
                            title = "Figma",
                            url = "https://www.figma.com"
                        )
                    )
                )
            ),
            right = SplitConfig.HorizontalSplit(
                top = SplitConfig.SinglePanel(
                    PanelConfig(
                        id = "right_top",
                        tabs = listOf(
                            TabConfig(
                                type = "browser",
                                title = "Canva",
                                url = "https://www.canva.com"
                            )
                        )
                    )
                ),
                bottom = SplitConfig.SinglePanel(
                    PanelConfig(
                        id = "right_bottom",
                        tabs = listOf(
                            TabConfig(
                                type = "browser",
                                title = "Notion",
                                url = "https://www.notion.so"
                            )
                        )
                    )
                )
            )
        )
    )
    
    val coder = LayoutConfiguration(
        name = "Coder",
        description = "Development workspace",
        layout = SplitConfig.VerticalSplit(
            left = SplitConfig.HorizontalSplit(
                top = SplitConfig.SinglePanel(
                    PanelConfig(
                        id = "left_top",
                        tabs = listOf(
                            TabConfig(
                                type = "browser",
                                title = "GitHub",
                                url = "https://github.com"
                            )
                        )
                    )
                ),
                bottom = SplitConfig.SinglePanel(
                    PanelConfig(
                        id = "left_bottom",
                        tabs = listOf(
                            TabConfig(
                                type = "terminal",
                                title = "Terminal"
                            )
                        )
                    )
                )
            ),
            right = SplitConfig.HorizontalSplit(
                top = SplitConfig.SinglePanel(
                    PanelConfig(
                        id = "right_top",
                        tabs = listOf(
                            TabConfig(
                                type = "browser",
                                title = "Stack Overflow",
                                url = "https://stackoverflow.com"
                            )
                        )
                    )
                ),
                bottom = SplitConfig.SinglePanel(
                    PanelConfig(
                        id = "right_bottom",
                        tabs = listOf(
                            TabConfig(
                                type = "editor",
                                title = "untitled.kt"
                            )
                        )
                    )
                )
            )
        )
    )
    
    val mail = LayoutConfiguration(
        name = "Mail",
        description = "Communication workspace",
        layout = SplitConfig.VerticalSplit(
            left = SplitConfig.SinglePanel(
                PanelConfig(
                    id = "left",
                    tabs = listOf(
                        TabConfig(
                            type = "browser",
                            title = "Gmail",
                            url = "https://mail.google.com"
                        )
                    )
                )
            ),
            right = SplitConfig.HorizontalSplit(
                top = SplitConfig.SinglePanel(
                    PanelConfig(
                        id = "right_top",
                        tabs = listOf(
                            TabConfig(
                                type = "browser",
                                title = "LinkedIn",
                                url = "https://www.linkedin.com"
                            )
                        )
                    )
                ),
                bottom = SplitConfig.SinglePanel(
                    PanelConfig(
                        id = "right_bottom",
                        tabs = listOf(
                            TabConfig(
                                type = "browser",
                                title = "X (Twitter)",
                                url = "https://x.com"
                            )
                        )
                    )
                )
            )
        )
    )
    
    val allConfigurations = listOf(priorAuth, designer, coder, mail)
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