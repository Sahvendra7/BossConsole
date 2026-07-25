package ai.rever.boss.components.plugin.remote

import ai.rever.boss.kernel.ui.RemoteUiSurfaceRegistry
import ai.rever.boss.kernel.ui.SurfaceRegistration
import ai.rever.boss.ui.sdk.WidgetEvent
import ai.rever.boss.ui.sdk.WidgetNode
import ai.rever.boss.ui.sdk.WidgetTree
import ai.rever.boss.ui.sdk.WidgetType
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Renderer behaviour asserted against a real composition.
 *
 * These three rules were fixed with care and documented in prose, but nothing executed them: focus was
 * only reported on a *transition*, a widget's local state was made pushable by the plugin without
 * clobbering in-flight typing, and `LIST` inside `SCROLL` fell back to a plain column because a
 * `LazyColumn` measured with an unbounded max height throws. Each is a claim about what happens across
 * two compositions, which is exactly what cannot be checked by reading the tree or the wire.
 *
 * The last test closes the loop the rest of this PR opens: a real click on a rendered widget, landing in
 * the surface's outgoing queue as a `UIEvent` with its payload.
 */
class RemoteWidgetRendererComposeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a text field reports no focus event on its first composition`() {
        // onFocusChanged also fires when a node's focus state is first resolved on attach. Wired straight
        // through, every field announced focus *loss* the moment it rendered, and a plugin could not tell
        // that from a real blur.
        val events = mutableListOf<Pair<String, WidgetEvent>>()
        compose.setContent {
            RemoteWidgetRenderer(textFieldTree("seed"), onEvent = { id, event -> events += id to event })
        }
        compose.waitForIdle()

        assertTrue(
            events.none { it.second is WidgetEvent.Focus },
            "first composition must not report a focus transition, got $events",
        )
    }

    @Test
    fun `focusing a text field reports the transition`() {
        val events = mutableListOf<Pair<String, WidgetEvent>>()
        compose.setContent {
            RemoteWidgetRenderer(textFieldTree("seed"), onEvent = { id, event -> events += id to event })
        }

        compose.onNode(hasSetTextAction()).performTextReplacement("typed")
        compose.waitForIdle()

        assertEquals(
            listOf(WidgetEvent.Focus(hasFocus = true)),
            events.map { it.second }.filterIsInstance<WidgetEvent.Focus>(),
            "gaining focus is a real transition and must be reported exactly once",
        )
        assertEquals(FIELD, events.first().first, "events must be tagged with the node that raised them")
    }

    @Test
    fun `a plugin can push a new value into a field without clobbering what the user is typing`() {
        var tree by mutableStateOf(textFieldTree("seed"))
        compose.setContent { RemoteWidgetRenderer(tree) }
        compose.onNodeWithText("seed").assertExists()

        compose.onNode(hasSetTextAction()).performTextReplacement("typed")
        compose.waitForIdle()
        compose.onNodeWithText("typed").assertExists()

        // A tree update that repeats the value the plugin already sent is the common case — it happens on
        // every unrelated change — and must leave the buffer alone.
        tree = textFieldTree("seed").copy(version = 2)
        compose.waitForIdle()
        compose.onNodeWithText("typed").assertExists()

        // A genuinely new value is the plugin driving its own widget: clearing a box after submit,
        // echoing back a normalized value, rejecting an edit. The plugin wins.
        tree = textFieldTree("pushed-back")
        compose.waitForIdle()
        compose.onNodeWithText("pushed-back").assertExists()
    }

    @Test
    fun `a list nested inside a scroll renders its rows instead of throwing`() {
        // A LazyColumn measured with an unbounded max height throws, so this tree shape — which a plugin
        // can send at any time — used to take the whole surface down.
        val tree =
            WidgetTree(
                rootId = "scroll",
                nodes =
                    mapOf(
                        "scroll" to WidgetNode("scroll", WidgetType.SCROLL, childIds = listOf("list")),
                        "list" to
                            WidgetNode(
                                id = "list",
                                type = WidgetType.LIST,
                                properties = mapOf("items" to "alpha,beta"),
                            ),
                    ),
            )

        compose.setContent { RemoteWidgetRenderer(tree) }
        compose.waitForIdle()

        compose.onNodeWithText("alpha").assertExists()
        compose.onNodeWithText("beta").assertExists()
    }

    @Test
    fun `a click on a rendered button reaches the surface's outgoing queue with its event id`() {
        val registry = RemoteUiSurfaceRegistry()
        val surface = (registry.register(PANEL, "plugin-a") as SurfaceRegistration.Accepted).surface
        val panel = RemotePanelComponent(PANEL, "Test Panel", "plugin-a", registry)
        surface.pushTree(buttonTree())
        // Attached after the tree arrived: the surface retains it, so the panel renders immediately.
        panel.attach()

        compose.setContent { panel.Content() }
        compose.onNodeWithText("Save").performClick()
        compose.waitForIdle()

        val queued = runBlocking { surface.events().take(1).toList() }.single()
        assertEquals(PANEL, queued.surfaceId)
        assertEquals(BUTTON, queued.targetNodeId)
        assertEquals("save_settings", queued.click.eventId)
        panel.dispose()
    }

    private fun textFieldTree(value: String): WidgetTree =
        WidgetTree(
            rootId = FIELD,
            nodes =
                mapOf(
                    FIELD to
                        WidgetNode(
                            id = FIELD,
                            type = WidgetType.TEXT_FIELD,
                            properties = mapOf("value" to value, "onChangeEvent" to "changed"),
                        ),
                ),
        )

    private fun buttonTree(): WidgetTree =
        WidgetTree(
            rootId = BUTTON,
            nodes =
                mapOf(
                    BUTTON to
                        WidgetNode(
                            id = BUTTON,
                            type = WidgetType.BUTTON,
                            properties = mapOf("label" to "Save", "clickEventId" to "save_settings"),
                        ),
                ),
        )

    private companion object {
        const val FIELD = "field-1"
        const val BUTTON = "button-1"
        const val PANEL = "panel-1"
    }
}
