package ai.rever.boss.v4.components.tabs_navigation

import ai.rever.boss.v4.components.window_panel.components.main_window_panels.BossTabsComponent.NoChild
import ai.rever.boss.v4.components.window_panel.components.main_window_panels.Child
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update

class TabsNavigation<C : Any>(
    private val initial: List<C> = emptyList(),
    private val initialActive: Int = -1
) {
    private val _tabs = MutableValue(TabsState(tabs = initial, activeIndex = initialActive))
    val state: Value<TabsState<C>> get() = _tabs

    fun addTab(config: C): Int {
        val newIndex = _tabs.value.tabs.size
        _tabs.update {
            it.copy(
                tabs = it.tabs + config,
                activeIndex = newIndex
            )
        }
        return newIndex
    }

    fun removeTab(index: Int) {
        _tabs.update { currentState ->
            val newTabs = currentState.tabs.toMutableList().apply { removeAt(index) }
            val newActiveIndex = when {
                newTabs.isEmpty() -> -1
                index == currentState.activeIndex -> minOf(index, newTabs.size - 1)
                index < currentState.activeIndex -> currentState.activeIndex - 1
                else -> currentState.activeIndex
            }
            currentState.copy(tabs = newTabs, activeIndex = newActiveIndex)
        }
    }

    fun selectTab(index: Int) {
        _tabs.update { it.copy(activeIndex = index) }
    }

    data class TabsState<C>(
        val tabs: List<C> = emptyList(),
        val activeIndex: Int = -1
    ) {
        val activeTab: C? = tabs.getOrNull(activeIndex)
    }
}

fun <T: Any> ComponentContext.childTabs(
    tabsNavigation: TabsNavigation<T>,
    childFactory: (T, ComponentContext) -> Child
) = TabsComponentContext(this, tabsNavigation, childFactory)

class TabsComponentContext<C : Any>(
    componentContext: ComponentContext,
    private val tabsNavigation: TabsNavigation<C>,
    private val childFactory: (C, ComponentContext) -> Child
) : ComponentContext by componentContext {

    val tabsState: Value<TabsNavigation.TabsState<C>> get() = tabsNavigation.state

    val children: Value<List<Any>> = MutableValue<List<Any>>(emptyList()).also { mutableList ->
        tabsState.subscribe { state ->
            mutableList.update {
                state.tabs.map { config -> childFactory(config, this) }
            }
        }
    }

    // Create an object to represent "no value"
    val activeChild: Value<Child> = MutableValue<Child>(NoChild).also { mutableValue ->
        tabsState.subscribe { state ->
            mutableValue.update {
                state.activeTab?.let { config -> childFactory(config, this) } ?: NoChild
            }
        }
    }

    fun addTab(config: C) = tabsNavigation.addTab(config)
    fun removeTab(index: Int) = tabsNavigation.removeTab(index)
    fun selectTab(index: Int) = tabsNavigation.selectTab(index)
}