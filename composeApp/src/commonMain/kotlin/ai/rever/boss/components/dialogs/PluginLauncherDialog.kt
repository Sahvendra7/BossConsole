package ai.rever.boss.components.dialogs

import ai.rever.boss.components.common.BossSearchBar
import ai.rever.boss.components.model.BossDraggableComponent
import ai.rever.boss.components.sidebar.SidebarVisibilitySettings
import ai.rever.boss.plugin.api.SidebarItem
import ai.rever.boss.plugin.ui.BossDialog
import ai.rever.boss.plugin.ui.BossTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** How tall the list is allowed to get before it scrolls. */
private val LIST_MAX_HEIGHT = 380.dp

/** Dialog width, matching [NewTabDialog] so the two do not read as different kinds of thing. */
private val DIALOG_WIDTH = 500.dp

/**
 * Every plugin, searchable, for when the strips that normally hold them are not on screen.
 *
 * Opened by the launcher whose position [ai.rever.boss.app.pluginLauncherPlacement] decides. The
 * contents do not depend on which strip is missing: this lists them all, which is why the launcher
 * wears a plugin icon rather than an arrow pointing at the absent side.
 *
 * **Unfiltered by the hidden set, on purpose.** `getItemsForSlot` drops the panels a user hid from
 * their sidebar, and this is the one surface where that would be exactly wrong: the dialog exists
 * because plugins are otherwise unreachable, so a plugin hidden from a strip is precisely the one
 * somebody would come here to find. Managing the hidden set stays with the customize menu.
 *
 * Clicking a row goes through [BossDraggableComponent.handleSidebarItemClick], the same entry
 * point the sidebar icons and the More menu use, so a plugin opened from here behaves exactly as
 * it does from its icon - custom `onClick` handlers included.
 */
@Composable
fun BossDraggableComponent.PluginLauncherDialog(onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }

    // Every slot, in the order they are drawn down the two rails, so the list reads the way the
    // strips do rather than in registration order.
    // Deliberately NOT remembered. itemsBySlot is a Compose state map, so reading it here is what
    // subscribes this dialog to a plugin loading or unloading while it is open; a remember would
    // freeze the list at whatever was registered when the dialog opened.
    val allItems =
        SidebarVisibilitySettings.ALL_SLOT_IDS
            .map(SidebarVisibilitySettings::panelFor)
            .flatMap(::getItemsForSlotUnfiltered)
            .distinctBy { it.id }
    val matches = allItems.filter { matchesPluginQuery(it, query) }

    BossDialog(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier
                    .width(DIALOG_WIDTH)
                    .background(BossTheme.colors.panel, RoundedCornerShape(8.dp))
                    .border(1.dp, BossTheme.colors.line, RoundedCornerShape(8.dp))
                    .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BossSearchBar(
                query = query,
                onQueryChange = { query = it },
                placeholder = "Search plugins...",
                modifier = Modifier.fillMaxWidth(),
            )

            if (matches.isEmpty()) {
                Text(
                    text = if (allItems.isEmpty()) "No plugins are loaded" else "No plugins match \"$query\"",
                    color = BossTheme.colors.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = LIST_MAX_HEIGHT),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(items = matches, key = { it.id }) { item ->
                        PluginRow(
                            item = item,
                            onClick = {
                                handleSidebarItemClick(item)
                                onDismiss()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PluginRow(
    item: SidebarItem,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val colors = BossTheme.colors

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(if (hovered) colors.raised else Color.Transparent)
                .hoverable(interactionSource)
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = if (hovered) colors.textPrimary else colors.textSecondary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = item.label,
            color = colors.textPrimary,
            fontSize = 13.sp,
            maxLines = 1,
        )
    }
}

/**
 * Whether a plugin belongs in the results for [query].
 *
 * Label and id both, because the two answer different questions: the label is what the user sees
 * on the icon's tooltip, and the id is what they will have read in a plugin's own documentation or
 * in the Toolbox. An empty query matches everything, so the dialog opens as a full list rather
 * than as an empty state waiting to be typed into.
 *
 * Pure so the matching is testable without composing a dialog.
 */
internal fun matchesPluginQuery(
    item: SidebarItem,
    query: String,
): Boolean {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return true
    return item.label.contains(trimmed, ignoreCase = true) || item.id.contains(trimmed, ignoreCase = true)
}
