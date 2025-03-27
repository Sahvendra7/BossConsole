package ai.rever.boss.v3.bossConsole.components

import BossNavigationItem
import BossSectionHeader
import GitHubDarkAccent
import GitHubDarkBorder
import ai.rever.boss.v3.bossConsole.BossConsoleViewModel
import ai.rever.boss.v3.bossConsole.Section
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable

@Composable
fun BossSection(
    section: Section,
    viewModel: BossConsoleViewModel,
    onNavigationItemClick: () -> Unit = {}
) {
    BossSectionHeader(
        title = section.name,
        isExpanded = section in viewModel.expandedSections,
        onClick = { viewModel.toggleSection(section) },
    )

    if (section in viewModel.expandedSections) {
        viewModel.getItemsBySection(section).forEach { item ->
            BossNavigationItem(
                item = item,
                isSelected = viewModel.currentScreen == item.screen,
                onClick = {
                    viewModel.navigateTo(item.screen)
                    onNavigationItemClick()
                },
                selectedColor = GitHubDarkAccent
            )
        }
    }

    Divider(color = GitHubDarkBorder)
}