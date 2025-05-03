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
import ai.rever.boss.v4.components.window_panel.components.main_window_panels.BossConsoleComponent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Diversity2
import androidx.compose.material.icons.outlined.Dock
import androidx.compose.material.icons.outlined.Filter8
import androidx.compose.material.icons.outlined.Fireplace
import androidx.compose.material.icons.outlined.Grain
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Mediation
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.PrecisionManufacturing
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.RunCircle
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tungsten
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.outlined.WorkHistory
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import compose.icons.AllIcons
import compose.icons.FeatherIcons
import compose.icons.FontAwesomeIcons
import compose.icons.feathericons.Activity
import compose.icons.feathericons.Database
import compose.icons.feathericons.Figma
import compose.icons.feathericons.GitBranch
import compose.icons.feathericons.GitCommit
import compose.icons.feathericons.GitMerge
import compose.icons.feathericons.GitPullRequest
import compose.icons.feathericons.MessageSquare
import compose.icons.feathericons.RotateCw
import compose.icons.fontawesomeicons.Brands
import compose.icons.fontawesomeicons.Regular
import compose.icons.fontawesomeicons.brands.Chrome
import compose.icons.fontawesomeicons.brands.Docker
import compose.icons.fontawesomeicons.brands.FirefoxBrowser
import compose.icons.fontawesomeicons.brands.Firstdraft
import compose.icons.fontawesomeicons.brands.FontAwesome
import compose.icons.fontawesomeicons.brands.Git
import compose.icons.fontawesomeicons.brands.Github
import compose.icons.fontawesomeicons.brands.Gripfire
import compose.icons.fontawesomeicons.brands.Hotjar
import compose.icons.fontawesomeicons.brands.InternetExplorer
import compose.icons.fontawesomeicons.brands.Pushed
import compose.icons.fontawesomeicons.brands.React
import compose.icons.fontawesomeicons.brands.Rev
import compose.icons.fontawesomeicons.brands.Rocketchat
import compose.icons.fontawesomeicons.brands.Rockrms
import compose.icons.fontawesomeicons.brands.Safari
import compose.icons.fontawesomeicons.brands.Simplybuilt
import compose.icons.fontawesomeicons.brands.TelegramPlane

@Composable
fun BossApp(bossConsoleComponent: BossConsoleComponent) {

    val leftTopItems = listOf(
        SidebarItem("lighthouse", Icons.Outlined.Tungsten, "Lighthouse"),
        SidebarItem("system_of_record", Icons.Outlined.Widgets, "System of Record"),
        SidebarItem("value", Icons.Outlined.AutoGraph, "North Star")
    )

    val leftBottomItems =  listOf(
        SidebarItem("lanager", Icons.Outlined.Diversity2, "Lanager"),
        SidebarItem("mastery", Icons.Outlined.Mediation, "Mastery"),
        SidebarItem("taskResolver", Icons.Outlined.Grain, "Task Resolver"),
    )

    val bottomItems = listOf(
        SidebarItem("terminal", Icons.Outlined.Terminal, "Terminal"),
        SidebarItem("bugReport", Icons.Outlined.BugReport, "Bug Report"),
        SidebarItem("git", FeatherIcons.GitBranch, "Git"),
        SidebarItem("activity", FeatherIcons.Activity, "Activity"),
        SidebarItem("errors", Icons.Outlined.Info, "Error"),
    )

    val rightTopItems = listOf(
        SidebarItem("docker", FontAwesomeIcons.Brands.Docker, "Docker"),
        SidebarItem("database", FeatherIcons.Database, "Database"),
        SidebarItem("chrome", FontAwesomeIcons.Brands.Chrome, "Chrome"),
        SidebarItem("agent", FeatherIcons.MessageSquare, "Agent"),
        SidebarItem("llm_rpa", FontAwesomeIcons.Brands.Hotjar, "LLM RPA"),
    )

    val rightBottom = listOf(
        SidebarItem("rpa", FontAwesomeIcons.Brands.React, "RPA"),
        SidebarItem("ehr_explorer", FontAwesomeIcons.Brands.Gripfire, "EHR Explorer"),
        SidebarItem("more_plugin", Icons.Outlined.MoreHoriz, "More Plugin"),
    )


    // Create and remember the model here to share state across sidebars
    val bossWindowPanelModel = remember {
        val itemsBySlot = mutableMapOf<Panel, List<SidebarItem>>(
            left.top.top to leftTopItems,
            left.top.bottom to leftBottomItems,
            left.bottom to bottomItems,
            right.top.top to rightTopItems,
            right.top.bottom to rightBottom
        )

        val panelData = mutableMapOf<Panel, PanelData>(
            left.top to PanelData(itemsBySlot[left.top.top]?.first(), true),
            left.bottom to PanelData(itemsBySlot[left.top.bottom]?.first(), false),
            right.top to PanelData(itemsBySlot[right.top.top]?.first(), true),
            right.bottom to PanelData(itemsBySlot[right.top.bottom]?.first(), false),
            bottom to PanelData(itemsBySlot[left.bottom]?.first(), true)
        )

        BossWindowPanelModel(itemsBySlot, panelData)
    }

    BossTheme {
        Box(modifier = Modifier.fillMaxSize()) { // Use Box to allow overlaying the drag ghost
            Column(modifier = Modifier.fillMaxSize()) {
                BossTitleBar()
                BossTopBar()
                Row(modifier = Modifier.weight(1f)) {
                    // Pass the shared model down to both sidebars
                    BossLeftSideBar(bossWindowPanelModel)
                    BossWindow(
                        modifier = Modifier.weight(1f),
                        bossConsoleComponent = bossConsoleComponent,
                        windowPanelModel = bossWindowPanelModel)
                    BossRightSideBar(bossWindowPanelModel)
                }
                BossBottomBar()
            }
            // Draw the dragging item overlay (ghost) if an item is being dragged
            DraggingItemOverlay(bossWindowPanelModel)
        }
    }
}







