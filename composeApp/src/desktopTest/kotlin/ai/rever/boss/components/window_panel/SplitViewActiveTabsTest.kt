@file:Suppress("PackageNaming")

package ai.rever.boss.components.window_panel

import ai.rever.boss.plugin.api.TabComponentWithUI
import ai.rever.boss.plugin.api.TabInfo
import ai.rever.boss.plugin.api.TabRegistry
import ai.rever.boss.plugin.api.TabTypeId
import ai.rever.boss.plugin.api.TabTypeInfo
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SplitViewActiveTabsTest {
    private lateinit var tabRegistry: TabRegistry
    private lateinit var state: SplitViewState

    private object TestTabType : TabTypeInfo {
        override val typeId = TabTypeId("move-test", "test.plugin")
        override val displayName = "Move Test"
        override val icon = Icons.Outlined.Language
    }

    private data class TestTabInfo(
        override val id: String,
        override val typeId: TabTypeId = TestTabType.typeId,
        override val title: String = "Test Tab",
    ) : TabInfo {
        override val icon get() = Icons.Outlined.Language
    }

    private class TestTabComponent(
        ctx: ComponentContext,
        override val config: TabInfo,
    ) : TabComponentWithUI,
        ComponentContext by ctx {
        override val tabTypeInfo: TabTypeInfo = TestTabType

        @Composable
        override fun Content() {
            // no-op
        }
    }

    @BeforeTest
    fun setup() {
        tabRegistry =
            TabRegistry().apply {
                registerTabType(TestTabType) { config, ctx -> TestTabComponent(ctx, config) }
            }
        state = SplitViewState(tabRegistry, windowId = "w1")
    }

    private fun createTab(id: String) = TestTabInfo(id = id, title = "Title $id")

    @Test
    fun `single panel returns only active tab`() {
        val panel = state.getPanel(state.activePanelId)!!

        // Add multiple tabs
        val tab1 = createTab("tab1")
        val tab2 = createTab("tab2")
        val tab3 = createTab("tab3")

        panel.tabsComponent.addTab(tab1)
        panel.tabsComponent.addTab(tab2)
        panel.tabsComponent.addTab(tab3)

        // Select tab 2
        panel.tabsComponent.selectTab(1)

        state.preserveCurrentState("ws1")
        val activeTabs = state.collectAllActiveTabs(null, "w1")

        assertEquals(1, activeTabs.size, "Should only report the single active tab")
        assertEquals("tab2", activeTabs[0].tabInfo.id)
    }

    @Test
    fun `two splits return their respective active tabs`() {
        val leftPanel = state.getPanel(state.activePanelId)!!

        // Left pane has tab1 (background) and tab2 (active)
        leftPanel.tabsComponent.addTab(createTab("tab1"))
        leftPanel.tabsComponent.addTab(createTab("tab2"))
        leftPanel.tabsComponent.selectTab(1) // tab2

        // Split right
        val rightPanelId = state.splitPanel(leftPanel.id, SplitOrientation.VERTICAL)
        val rightPanel = state.getPanel(rightPanelId)!!

        // Right pane has tab3 (background) and tab4 (active)
        rightPanel.tabsComponent.addTab(createTab("tab3"))
        rightPanel.tabsComponent.addTab(createTab("tab4"))
        rightPanel.tabsComponent.selectTab(1) // tab4

        state.preserveCurrentState("ws1")
        val activeTabs = state.collectAllActiveTabs(null, "w1")

        assertEquals(2, activeTabs.size, "Should report exactly one tab per panel")

        val ids = activeTabs.map { it.tabInfo.id }.toSet()
        assertTrue(ids.contains("tab2"), "Left panel's active tab must be included")
        assertTrue(ids.contains("tab4"), "Right panel's active tab must be included")
        assertFalse(ids.contains("tab1"), "Background tab 1 must not be included")
        assertFalse(ids.contains("tab3"), "Background tab 3 must not be included")
    }

    @Test
    fun `unfocused right split remains in active tabs`() {
        val leftPanelId = state.activePanelId
        val rightPanelId = state.splitPanel(leftPanelId, SplitOrientation.VERTICAL)

        state.getPanel(leftPanelId)!!.tabsComponent.addTab(createTab("left-tab"))
        state.getPanel(rightPanelId)!!.tabsComponent.addTab(createTab("right-tab"))

        // Focus left panel
        state.setActivePanel(leftPanelId)

        state.preserveCurrentState("ws1")
        val activeTabs = state.collectAllActiveTabs(null, "w1")

        assertEquals(2, activeTabs.size)
        val ids = activeTabs.map { it.tabInfo.id }.toSet()
        assertTrue(ids.contains("right-tab"), "Right split's active tab must be reported even when unfocused")
    }

    @Test
    fun `active-tab switch updates reported tab`() {
        val panel = state.getPanel(state.activePanelId)!!
        panel.tabsComponent.addTab(createTab("tab1"))
        panel.tabsComponent.addTab(createTab("tab2"))

        state.preserveCurrentState("ws1")

        panel.tabsComponent.selectTab(0)
        assertEquals(
            "tab1",
            state
                .collectAllActiveTabs(null, "w1")
                .single()
                .tabInfo.id,
        )

        panel.tabsComponent.selectTab(1)
        assertEquals(
            "tab2",
            state
                .collectAllActiveTabs(null, "w1")
                .single()
                .tabInfo.id,
        )
    }

    @Test
    fun `duplicate tab ids are deduplicated by seenTabIds`() {
        val leftPanelId = state.activePanelId
        val rightPanelId = state.splitPanel(leftPanelId, SplitOrientation.VERTICAL)

        // In theory this shouldn't happen for active tabs, but we must test the deduplication logic
        val duplicateTab = createTab("shared-tab")
        state.getPanel(leftPanelId)!!.tabsComponent.addTab(duplicateTab)
        state.getPanel(rightPanelId)!!.tabsComponent.addTab(duplicateTab)

        state.preserveCurrentState("ws1")
        val activeTabs = state.collectAllActiveTabs(null, "w1")

        assertEquals(1, activeTabs.size, "SeenTabIds must prevent duplicates")
        assertEquals("shared-tab", activeTabs.single().tabInfo.id)
    }

    @Test
    fun `null active tab contributes nothing`() {
        val panel = state.getPanel(state.activePanelId)!!

        // Do not add any tabs. The active tab is null.
        state.preserveCurrentState("ws1")
        val activeTabs = state.collectAllActiveTabs(null, "w1")

        assertTrue(activeTabs.isEmpty(), "Panel with no active tab should contribute nothing")
    }

    @Test
    fun `background-tab regression test - old behavior would fail`() {
        val panel = state.getPanel(state.activePanelId)!!

        panel.tabsComponent.addTab(createTab("background"))
        panel.tabsComponent.addTab(createTab("foreground"))
        panel.tabsComponent.selectTab(1) // Make foreground active

        state.preserveCurrentState("ws1")
        val activeTabs = state.collectAllActiveTabs(null, "w1")

        // Under the OLD implementation, this would return 2 (both tabs).
        // It now correctly returns 1 (only the visible foreground tab).
        assertEquals(1, activeTabs.size, "OLD implementation would return 2 because it iterated all tabs in the pane")
        assertEquals("foreground", activeTabs.single().tabInfo.id)
    }
}
