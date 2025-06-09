package ai.rever.boss.components.plugin.panels.left_bottom.BossActiveTabs

import ai.rever.boss.components.window_panel.SplitViewState
import ai.rever.boss.components.configuration.ConfigurationManager
import androidx.compose.runtime.compositionLocalOf

// CompositionLocal to provide SplitViewState to panels
val LocalSplitViewState = compositionLocalOf<SplitViewState?> { null }

// CompositionLocal to provide ConfigurationManager to panels
val LocalConfigurationManager = compositionLocalOf<ConfigurationManager?> { null }