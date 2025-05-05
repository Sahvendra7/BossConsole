package ai.rever.boss.v4

import BossTheme
import ai.rever.boss.v4.components.bars.horizontal.BossBottomBar
import ai.rever.boss.v4.components.bars.horizontal.BossTitleBar
import ai.rever.boss.v4.components.bars.horizontal.BossTopBar
import ai.rever.boss.v4.components.bars.vertical.BossLeftSideBar
import ai.rever.boss.v4.components.bars.vertical.BossRightSideBar
import ai.rever.boss.v4.components.model.BossWindowPanelModel
import ai.rever.boss.v4.components.model.Panel
import ai.rever.boss.v4.components.model.Panel.Companion.bottom
import ai.rever.boss.v4.components.model.Panel.Companion.left
import ai.rever.boss.v4.components.model.Panel.Companion.right
import ai.rever.boss.v4.components.model.Panel.Companion.top
import ai.rever.boss.v4.components.model.PanelData
import ai.rever.boss.v4.components.model.SidebarItem
import ai.rever.boss.v4.components.overlays.DraggingItemOverlay
import ai.rever.boss.v4.components.window_panel.BossWindow
import ai.rever.boss.v4.components.window_panel.components.main_window_panels.BossTabsComponent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import compose.icons.FeatherIcons
import compose.icons.FontAwesomeIcons
import compose.icons.feathericons.Activity
import compose.icons.feathericons.Database
import compose.icons.feathericons.GitBranch
import compose.icons.feathericons.MessageSquare
import compose.icons.fontawesomeicons.Brands
import compose.icons.fontawesomeicons.brands.*


val leftTopItems get() = listOf(
    SidebarItem("lighthouse", Icons.Outlined.Tungsten, "Lighthouse"),
    SidebarItem("system_of_record", Icons.Outlined.Widgets, "System of Record"),
    SidebarItem("value", Icons.Outlined.AutoGraph, "North Star")
)

val leftBottomItems get() = listOf(
    SidebarItem("lanager", Icons.Outlined.Diversity2, "Lanager"),
    SidebarItem("mastery", Icons.Outlined.Mediation, "Mastery"),
    SidebarItem("taskResolver", Icons.Outlined.Grain, "Task Resolver"),
)

val bottomItems get() = listOf(
    SidebarItem("terminal", Icons.Outlined.Terminal, "Terminal"),
    SidebarItem("bugReport", Icons.Outlined.BugReport, "Bug Report"),
    SidebarItem("git", FeatherIcons.GitBranch, "Git"),
    SidebarItem("activity", FeatherIcons.Activity, "Activity"),
    SidebarItem("errors", Icons.Outlined.Info, "Error"),
)

val rightTopItems get() = listOf(
    SidebarItem("docker", FontAwesomeIcons.Brands.Docker, "Docker"),
    SidebarItem("database", FeatherIcons.Database, "Database"),
    SidebarItem("chrome", FontAwesomeIcons.Brands.Chrome, "Chrome"),
    SidebarItem("agent", FeatherIcons.MessageSquare, "Agent"),
    SidebarItem("llm_rpa", FontAwesomeIcons.Brands.Hotjar, "LLM RPA"),
)

val rightBottomItems get() = listOf(
    SidebarItem("rpa", FontAwesomeIcons.Brands.React, "RPA"),
    SidebarItem("ehr_explorer", FontAwesomeIcons.Brands.Gripfire, "EHR Explorer"),
    SidebarItem("more_plugin", Icons.Outlined.MoreHoriz, "More Plugin"),
)

@Composable
fun BossApp(tabsComponent: BossTabsComponent) {

    // Create and remember the model here to share state across sidebars
    val windowPanelModel = remember {
        BossWindowPanelModel()
    }

    BossTheme {
        Box(modifier = Modifier.fillMaxSize()) { // Use Box to allow overlaying the drag ghost
            Column(modifier = Modifier.fillMaxSize()) {
                BossTitleBar()
                BossTopBar()
                Row(modifier = Modifier.weight(1f)) {
                    // Pass the shared model down to both sidebars
                    BossLeftSideBar(windowPanelModel)
                    BossWindow(
                        modifier = Modifier.weight(1f),
                        tabsComponent = tabsComponent,
                        windowPanelModel = windowPanelModel
                    )
                    BossRightSideBar(windowPanelModel)
                }
                BossBottomBar()
            }
            // Draw the dragging item overlay (ghost) if an item is being dragged
            DraggingItemOverlay(windowPanelModel)
        }
    }
}







