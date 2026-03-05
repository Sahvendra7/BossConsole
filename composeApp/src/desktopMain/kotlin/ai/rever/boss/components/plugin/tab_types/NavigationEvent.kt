package ai.rever.boss.components.plugin.tab_types

/**
 * Navigation event data for go-to-definition.
 *
 * @property filePath Path to the target file
 * @property line Line number (1-based)
 * @property column Column number (1-based)
 * @property offset Character offset in the target file (-1 if not available)
 */
data class NavigationEvent(
    val filePath: String,
    val line: Int,
    val column: Int,
    val offset: Int = -1
)
