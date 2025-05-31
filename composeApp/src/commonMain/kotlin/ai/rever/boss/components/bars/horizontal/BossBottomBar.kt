package ai.rever.boss.components.bars.horizontal

import BossDarkTextSecondary
import BossDarkBorder
import ai.rever.boss.components.buttons.BossActionButton
import ai.rever.boss.components.bars.ScrollbarConfig
import ai.rever.boss.components.bars.horizontalScrollWithScrollbar
import ai.rever.boss.components.plugin.tab_types.EditorTabInfo
import ai.rever.boss.components.plugin.tab_types.fluck.FluckTabInfo
import ai.rever.boss.components.plugin.tab_types.TerminalTabInfo
import ai.rever.boss.components.window_panel.components.main_window_panels.BossTabsComponent
import ai.rever.boss.utils.SystemUtils
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arkivanov.decompose.extensions.compose.subscribeAsState


@Composable
fun BossBottomBar(tabsComponent: BossTabsComponent? = null) {
    Divider(color = BossDarkBorder)
    HorizontalBar(height = 30.dp) {
        HorizontalBarRow {
            BossLeftBottomBar(tabsComponent)
            Spacer(modifier = Modifier.weight(0.1f))
            BossRightBottomBar()
        }
    }
}

@Composable
fun RightArrow() {
    Icon(imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
        modifier = Modifier.size(18.dp),
        contentDescription = "Right Arrow",
        tint = BossDarkTextSecondary)
}

@Composable
fun RowScope.BossLeftBottomBar(tabsComponent: BossTabsComponent? = null) {
    Column(modifier = Modifier.weight(2f).padding(horizontal = 8.dp)) {
        Row(
            modifier = Modifier
                .horizontalScrollWithScrollbar(
                    rememberScrollState(),
                    scrollbarConfig = ScrollbarConfig(
                        indicatorThickness = 2.dp,
                        indicatorColor = BossDarkTextSecondary,
                        indicatorCornerRadius = 4.dp,
                        horizontalScrollbarAtTop = true
                    )
                )
            ,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (tabsComponent != null) {
                val tabsState by tabsComponent.tabsState.subscribeAsState()
                val activeTab = tabsState.activeTab
                
                when (activeTab) {
                    is EditorTabInfo -> {
                        // Show file path from project root
                        val projectRoot = "/Users/kshivang/Development/BOSS-Kotlin/"
                        val relativePath = activeTab.filePath.removePrefix(projectRoot)
                        val pathParts = relativePath.split("/")
                        
                        pathParts.forEachIndexed { index, part ->
                            if (part.isNotEmpty()) {
                                BossActionButton(
                                    text = part,
                                    color = BossDarkTextSecondary,
                                    onClick = {}
                                )
                                if (index < pathParts.lastIndex && pathParts[index + 1].isNotEmpty()) {
                                    RightArrow()
                                }
                            }
                        }
                    }
                    is FluckTabInfo -> {
                        // Show current URL
                        Text(
                            text = activeTab.currentUrl,
                            color = BossDarkTextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                    is TerminalTabInfo -> {
                        // Show terminal text
                        Text(
                            text = "Terminal",
                            color = BossDarkTextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                    else -> {
                        // Default content when no tab is active
                        BossActionButton(text = "Lanager", color = BossDarkTextSecondary, onClick = {})
                        RightArrow()
                        BossActionButton(text = "Mastery", color = BossDarkTextSecondary, onClick = {})
                        RightArrow()
                        BossActionButton(text = "Task Resolver", color = BossDarkTextSecondary, onClick = {})
                    }
                }
            } else {
                // Fallback to default content if no tabs component
                BossActionButton(text = "Lanager", color = BossDarkTextSecondary, onClick = {})
                RightArrow()
                BossActionButton(text = "Mastery", color = BossDarkTextSecondary, onClick = {})
                RightArrow()
                BossActionButton(text = "Task Resolver", color = BossDarkTextSecondary, onClick = {})
            }
        }
    }
}

@Composable
fun BossRightBottomBar() {
    BossActionButton(text = "UTF-8", color = BossDarkTextSecondary, onClick = {})
    BossActionButton(imageVector = Icons.Outlined.Info, text = "Info", color = BossDarkTextSecondary, onClick = {})
}
