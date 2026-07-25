package ai.rever.boss.components.plugin.remote

import ai.rever.boss.plugin.ui.BossColorScheme
import ai.rever.boss.plugin.ui.BossTheme
import ai.rever.boss.ui.sdk.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Renders a [WidgetTree] from an out-of-process plugin as Compose components.
 *
 * This is the kernel-side renderer for Phase 4/7: plugins in separate JVM processes
 * send declarative widget trees over IPC, which the kernel renders using this component.
 *
 * How a node's properties and modifier are interpreted lives in `boss-ui-sdk`
 * ([resolveClickEventId], [resolveListItems], [resolveDropdownOptions], [effectiveAlpha],
 * [parseBackgroundColor]) so that this renderer and the native `boss-remote-ui` renderer in the Rust
 * shell agree by construction, and so the rules are testable without a UI toolkit.
 *
 * @param tree      The widget tree to render
 * @param onEvent   Callback for UI events, forwarded to the owning plugin as proto `UIEvent`s
 */
@Composable
fun RemoteWidgetRenderer(
    tree: WidgetTree,
    onEvent: (nodeId: String, event: WidgetEvent) -> Unit = { _, _ -> },
) {
    val root = tree.nodes[tree.rootId] ?: return
    RenderNode(node = root, tree = tree, onEvent = onEvent)
}

@Composable
private fun RenderNode(
    node: WidgetNode,
    tree: WidgetTree,
    onEvent: (nodeId: String, event: WidgetEvent) -> Unit,
) {
    val modifier = node.modifier.toComposeModifier(node, onEvent)

    when (node.type) {
        WidgetType.COLUMN -> {
            Column(modifier = modifier) {
                RenderChildren(node = node, tree = tree, onEvent = onEvent)
            }
        }

        WidgetType.ROW -> {
            Row(modifier = modifier) {
                RenderChildren(node = node, tree = tree, onEvent = onEvent)
            }
        }

        WidgetType.BOX -> {
            Box(modifier = modifier) {
                RenderChildren(node = node, tree = tree, onEvent = onEvent)
            }
        }

        WidgetType.SCROLL -> {
            val scrollState = rememberScrollState()
            Column(modifier = modifier.verticalScroll(scrollState)) {
                RenderChildren(node = node, tree = tree, onEvent = onEvent)
            }
        }

        WidgetType.TEXT -> {
            val value = node.properties["value"] ?: ""
            val fontSize = node.properties["fontSize"]?.toFloatOrNull() ?: 14f
            Text(
                text = value,
                fontSize = fontSize.sp,
                modifier = modifier,
            )
        }

        WidgetType.BUTTON -> {
            // Accepts both spellings of the event id — see resolveClickEventId.
            val clickEventId = node.resolveClickEventId()
            Button(
                onClick = { onEvent(node.id, WidgetEvent.Click(clickEventId)) },
                modifier = modifier,
            ) {
                Text(node.properties["label"] ?: "")
            }
        }

        WidgetType.TEXT_FIELD -> {
            // Keyed by node id: the plugin's `value` seeds the buffer, and the buffer survives tree
            // updates that keep the node's identity (see WidgetTreeBuilder's deterministic ids).
            var value by remember(node.id) { mutableStateOf(node.properties["value"] ?: "") }
            val placeholder = node.properties["placeholder"] ?: ""
            OutlinedTextField(
                value = value,
                onValueChange = { newValue ->
                    value = newValue
                    onEvent(node.id, WidgetEvent.TextChange(newValue))
                },
                placeholder = { Text(placeholder) },
                modifier =
                    modifier.onFocusChanged { focusState ->
                        onEvent(node.id, WidgetEvent.Focus(focusState.isFocused))
                    },
            )
        }

        WidgetType.CHECKBOX -> {
            var checked by remember(node.id) { mutableStateOf(node.properties["checked"]?.toBoolean() ?: false) }
            val label = node.properties["label"] ?: ""
            Row(modifier = modifier, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = { newChecked ->
                        checked = newChecked
                        onEvent(node.id, WidgetEvent.Toggle(newChecked))
                    },
                )
                if (label.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(label)
                }
            }
        }

        WidgetType.TOGGLE -> {
            var checked by remember(node.id) { mutableStateOf(node.properties["checked"]?.toBoolean() ?: false) }
            Switch(
                checked = checked,
                onCheckedChange = { newChecked ->
                    checked = newChecked
                    onEvent(node.id, WidgetEvent.Toggle(newChecked))
                },
                modifier = modifier,
            )
        }

        WidgetType.PROGRESS -> {
            val value = node.properties["value"]?.toFloatOrNull() ?: 0f
            val indeterminate = node.properties["indeterminate"]?.toBoolean() ?: false
            if (indeterminate) {
                LinearProgressIndicator(modifier = modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(progress = value, modifier = modifier.fillMaxWidth())
            }
        }

        WidgetType.SPACER -> {
            val height = node.properties["height"]?.toIntOrNull() ?: 8
            Spacer(modifier = Modifier.height(height.dp))
        }

        WidgetType.DIVIDER -> {
            Divider(modifier = modifier)
        }

        WidgetType.LIST -> {
            val children = node.childIds.mapNotNull { tree.nodes[it] }
            LazyColumn(modifier = modifier) {
                if (children.isNotEmpty()) {
                    items(items = children, key = { it.id }) { child ->
                        RenderNode(node = child, tree = tree, onEvent = onEvent)
                    }
                } else {
                    // WidgetTreeBuilder.list() carries rows as an `items` property rather than child
                    // nodes; walking childIds alone drew an empty list.
                    items(node.resolveListItems()) { row -> Text(text = row) }
                }
            }
        }

        WidgetType.ICON -> {
            // Icon rendering — use a Text placeholder for now
            // Full icon mapping from BossEditor icon set to be wired in Phase 7
            val name = node.properties["name"] ?: "?"
            val size = node.properties["size"]?.toIntOrNull() ?: 16
            Text(
                text = "[$name]",
                fontSize = size.sp,
                modifier = modifier,
            )
        }

        WidgetType.DROPDOWN -> {
            var expanded by remember(node.id) { mutableStateOf(false) }
            val selected = node.properties["selected"] ?: ""
            val options = node.resolveDropdownOptions()
            Box(modifier = modifier) {
                Text(
                    text = selected,
                    modifier = Modifier.clickable { expanded = true },
                )
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    options.forEachIndexed { index, option ->
                        DropdownMenuItem(onClick = {
                            expanded = false
                            onEvent(node.id, WidgetEvent.Selection(option, index))
                        }) {
                            Text(option)
                        }
                    }
                }
            }
        }

        // Complex widgets (CODE_EDITOR, TERMINAL, BROWSER) delegate to host composites
        // These are rendered in-process using the host's actual implementations
        WidgetType.CODE_EDITOR, WidgetType.TERMINAL, WidgetType.BROWSER -> {
            val message =
                when (node.type) {
                    WidgetType.CODE_EDITOR -> "Editor (host-rendered)"
                    WidgetType.TERMINAL -> "Terminal (host-rendered)"
                    else -> "Browser (host-rendered)"
                }
            Text(
                text = message,
                modifier = modifier.background(BossTheme.colors.raised).padding(8.dp),
                color = BossTheme.colors.textPrimary,
            )
        }

        // Remaining types render as placeholders
        else -> {
            val typeName = node.type.name
            Box(modifier = modifier) {
                Text(text = "[$typeName]", fontSize = 10.sp, color = BossTheme.colors.textMuted)
            }
        }
    }
}

/**
 * Render a container's children, giving each one a Compose identity keyed by its node id.
 *
 * Without the [key], Compose identifies children *positionally*, so inserting or reordering a
 * sibling shifts every `remember`ed state (a text field's buffer, a dropdown's expanded flag) onto
 * the wrong node.
 */
@Composable
private fun RenderChildren(
    node: WidgetNode,
    tree: WidgetTree,
    onEvent: (nodeId: String, event: WidgetEvent) -> Unit,
) {
    node.childIds.forEach { childId ->
        tree.nodes[childId]?.let { child ->
            key(child.id) {
                RenderNode(node = child, tree = tree, onEvent = onEvent)
            }
        }
    }
}

/**
 * Resolve a design-system token to a color from the host's active scheme.
 *
 * A table rather than a `when` so [ThemeToken] coverage is asserted by
 * `RemoteWidgetRendererThemeTokenTest` instead of being spread across the renderer.
 */
internal val themeTokenColors: Map<ThemeToken, BossColorScheme.() -> Color> =
    mapOf(
        ThemeToken.INK to { ink },
        ThemeToken.PANEL to { panel },
        ThemeToken.RAISED to { raised },
        ThemeToken.LINE to { line },
        ThemeToken.LINE_STRONG to { lineStrong },
        ThemeToken.TEXT_PRIMARY to { textPrimary },
        ThemeToken.TEXT_SECONDARY to { textSecondary },
        ThemeToken.TEXT_MUTED to { textMuted },
        ThemeToken.SIGNAL to { signal },
        ThemeToken.SIGNAL_DIM to { signalDim },
        ThemeToken.SIGNAL_WASH to { signalWash },
        ThemeToken.DATA to { data },
        ThemeToken.OK to { ok },
        ThemeToken.WARN to { warn },
        ThemeToken.ALERT to { alert },
        ThemeToken.ON_SIGNAL to { onSignal },
        ThemeToken.ON_DATA to { onData },
    )

/**
 * Resolve a `WidgetModifier.background_color` spec against [scheme].
 *
 * `ui_protocol.proto` promises "hex color string … or theme token"; only hex was ever parsed, so
 * every token value was silently dropped. Tokens resolve through the host's *active* theme, so a
 * plugin asking for `panel` re-skins with the app.
 */
internal fun resolveBackgroundColor(
    spec: String,
    scheme: BossColorScheme,
): Color? =
    when (val parsed = parseBackgroundColor(spec)) {
        is BackgroundSpec.Token -> themeTokenColors[parsed.token]?.invoke(scheme)
        is BackgroundSpec.Hex -> Color(parsed.argb)
        BackgroundSpec.None -> null
    }

/**
 * Convert [WidgetModifier] to a Compose [Modifier].
 */
@Composable
private fun WidgetModifier.toComposeModifier(
    node: WidgetNode,
    onEvent: (nodeId: String, event: WidgetEvent) -> Unit,
): Modifier {
    var m: Modifier = Modifier

    if (width > 0) {
        m = m.width(width.dp)
    } else if (width == -1) {
        m = m.fillMaxWidth()
    }

    if (height > 0) {
        m = m.height(height.dp)
    } else if (height == -1) {
        m = m.fillMaxHeight()
    }

    val hasPadding = paddingStart > 0 || paddingTop > 0 || paddingEnd > 0 || paddingBottom > 0
    if (hasPadding) {
        m =
            m.padding(
                start = paddingStart.dp,
                top = paddingTop.dp,
                end = paddingEnd.dp,
                bottom = paddingBottom.dp,
            )
    }

    if (backgroundColor.isNotEmpty()) {
        resolveBackgroundColor(backgroundColor, BossTheme.colors)?.let { color ->
            m = m.background(color)
        }
    }

    // After background, so a translucent widget keeps its own backdrop crisp — matches the native
    // renderer. `effectiveAlpha()` resolves proto3's "unset is 0.0" trap; see WidgetModifier.alpha.
    effectiveAlpha()?.let { resolved ->
        m = m.alpha(resolved)
    }

    if (clickable && clickEventId.isNotEmpty()) {
        m = m.clickable { onEvent(node.id, WidgetEvent.Click(clickEventId)) }
    }

    return m
}
