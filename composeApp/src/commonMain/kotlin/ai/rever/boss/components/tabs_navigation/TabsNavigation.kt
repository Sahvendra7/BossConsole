package ai.rever.boss.components.tabs_navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update

class TabsNavigation<C : Any>(
    private val initial: List<C> = emptyList(),
    private val initialActive: Int = -1
) {
    private val _tabs = MutableValue(TabsState(tabs = initial, activeIndex = initialActive))
    val state: Value<TabsState<C>> = _tabs

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
    
    fun updateTab(index: Int, config: C) {
        _tabs.update { currentState ->
            val newTabs = currentState.tabs.toMutableList()
            if (index in newTabs.indices) {
                newTabs[index] = config
            }
            currentState.copy(tabs = newTabs)
        }
    }

    data class TabsState<C>(
        val tabs: List<C> = emptyList(),
        val activeIndex: Int = -1
    ) {
        val activeTab: C? = tabs.getOrNull(activeIndex)
    }
}

sealed interface ChildWrapper<out R : Any> {
    object None : ChildWrapper<Nothing>
    data class Child<R : Any>(val value: R) : ChildWrapper<R>
}

fun <T: Any, R: Any> ComponentContext.childTabs(
    tabsNavigation: TabsNavigation<T>,
    childFactory: (T, ComponentContext) -> R
) = TabsComponentContext(this, tabsNavigation, childFactory)

class TabsComponentContext<C : Any, R : Any>(
    componentContext: ComponentContext,
    private val tabsNavigation: TabsNavigation<C>,
    private val childFactory: (C, ComponentContext) -> R
) : ComponentContext by componentContext {

    val tabsState: Value<TabsNavigation.TabsState<C>> = tabsNavigation.state

    val children: Value<List<R>> = MutableValue<List<R>>(emptyList()).also { mutableList ->
        tabsState.subscribe { state ->
            mutableList.update {
                state.tabs.map { config -> childFactory(config, this) }
            }
        }
    }

    val activeChild: Value<ChildWrapper<R>> = MutableValue<ChildWrapper<R>>(ChildWrapper.None).also { mutableValue ->
        tabsState.subscribe { state ->
            mutableValue.update {
                state.activeTab?.let { config -> 
                    ChildWrapper.Child(childFactory(config, this)) 
                } ?: ChildWrapper.None
            }
        }
    }

    fun addTab(config: C) = tabsNavigation.addTab(config)
    fun removeTab(index: Int) = tabsNavigation.removeTab(index)
    fun selectTab(index: Int) = tabsNavigation.selectTab(index)
    fun updateTab(index: Int, config: C) = tabsNavigation.updateTab(index, config)
}