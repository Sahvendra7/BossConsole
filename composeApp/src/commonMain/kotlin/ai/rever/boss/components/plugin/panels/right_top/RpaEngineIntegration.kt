package ai.rever.boss.components.plugin.panels.right_top

import androidx.compose.runtime.*

/**
 * Composable wrapper for RPA Engine content
 */
@Composable
fun RpaEngineContent(component: RpaEngineComponent) {
    // Load available configurations on startup
    LaunchedEffect(Unit) {
        component.loadAvailableConfigurations()
    }
    
    component.ContentInternal()
}