package ai.rever.boss.v4.components.bars.horizontal

import BossDarkAccent
import BossDarkBorder
import ai.rever.boss.v4.components.buttons.BossActionButton
import ai.rever.boss.v4.components.overlays.ContextMenuItem
import ai.rever.boss.v4.components.overlays.contextMenu
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.FeatherIcons
import compose.icons.feathericons.GitBranch


@Composable
fun BossTopBar() {

    val items = listOf(
        ContextMenuItem(
            text = "Edit",
            icon = Icons.Outlined.Edit,
            onClick = { /* Handle edit action */ }
        ),
        ContextMenuItem(isDivider = true),
        ContextMenuItem(
            text = "Save",
            icon = Icons.Outlined.Save,
            onClick = { /* Handle save action */ }
        )
    )


    HorizontalBar(modifier = Modifier.contextMenu(items = items), height = 40.dp) {
        HorizontalBarRow(modifier = Modifier.fillMaxHeight().padding(start = 36.dp)) {
            BossTopLeftBar()
            Spacer(modifier = Modifier.weight(1f))
            BossTopRunBar()
            Spacer(modifier = Modifier.weight(0.1f))
            BossTopRightBar()
        }
    }
    Divider(color = BossDarkBorder)
}

@Composable
fun Logo(name: String) {
    Surface(
        modifier = Modifier
            .padding(2.dp)
            .height(22.dp)
            .width(22.dp)
        ,
        shape = RoundedCornerShape(4.dp),
        color = BossDarkAccent,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Text(text = name.substring(0, 2).uppercase(),
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.Center)
            )
        }
    }
}

@Composable
fun BossActionButtonWithLogo(
    text: String, 
    contextMenuItems: List<ContextMenuItem>,
    hintText: String? = null,
    onClick: () -> Unit = {}
) {
    BossActionButton(
        leftLogo = { Logo(text) },
        text = text,
        contextMenuItems = contextMenuItems,
        hintText = hintText,
        onClick = onClick
    )
}

@Composable
fun BossTopLeftBar() {
    BossActionButtonWithLogo(
        text = "Nycbs", 
        contextMenuItems = emptyList(),
        hintText = "BOSS Platform - Based on NYCBS"
    )
    BossActionButton(
        leftIcon = FeatherIcons.GitBranch, 
        text = "main",
        hintText = "Current Git Branch: main"
    ) { }
}

val lanagerContextMenuItems get() = listOf(
    ContextMenuItem(
        text = "Start Lanager",
        icon = Icons.Outlined.PlayArrow,
        onClick = { /* Handle start lanager action */ }
    ),
    ContextMenuItem(
        text = "View Agents",
        icon = Icons.Outlined.People,
        onClick = { /* Handle view agents action */ }
    ),
    ContextMenuItem(isDivider = true),
    ContextMenuItem(
        text = "Configure Lanager",
        icon = Icons.Outlined.Settings,
        onClick = { /* Handle configure action */ }
    ),
    ContextMenuItem(isDivider = true),
    ContextMenuItem(
        text = "Restart Lanager",
        icon = Icons.Outlined.Refresh,
        onClick = { /* Handle restart action */ }
    ),
    ContextMenuItem(
        text = "Stop Lanager",
        icon = Icons.Outlined.Stop,
        onClick = { /* Handle stop action */ }
    )
)

@Composable
fun BossTopRunBar() {
    BossActionButton(
        leftIcon = Icons.Outlined.Diversity2,
        text = "lanager [boss]",
        contextMenuItems = lanagerContextMenuItems,
        hintText = "Lanager: Manage AI agent swarm for collaborative tasks"
    )
    
    BossActionButton(
        imageVector = Icons.Outlined.PlayArrow,
        text = "Run",
        hintText = "Run the current configuration"
    ) {}
    
    BossActionButton(
        imageVector = Icons.Outlined.BugReport,
        text = "Bug",
        hintText = "Debug the current execution"
    ) {}
    
    BossActionButton(
        imageVector = Icons.Outlined.Stop,
        text = "Stop",
        hintText = "Stop all running processes"
    ) {}
    
    BossActionButton(
        imageVector = Icons.Outlined.MoreVert,
        text = "More",
        hintText = "Additional actions and settings"
    ) {}
}

@Composable
fun BossTopRightBar() {
    BossActionButton(
        imageVector = Icons.Outlined.PersonAdd,
        text = "Sign Out",
        hintText = "Sign out of your account"
    ) {}
    
    BossActionButton(
        imageVector = Icons.Outlined.Search,
        text = "Search",
        hintText = "Search for files, commands, or actions"
    ) {}
    
    BossActionButton(
        imageVector = Icons.Outlined.Settings,
        text = "Settings",
        hintText = "Configure application settings"
    ) {}
}