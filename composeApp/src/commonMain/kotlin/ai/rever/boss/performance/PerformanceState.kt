package ai.rever.boss.performance

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.StateFlow

/**
 * Platform-specific performance state access.
 * Desktop implementation uses PerformanceMonitor, other platforms return null/defaults.
 */
expect object PerformanceState {
    /**
     * Get current performance snapshot as a composable state.
     * Returns null on platforms without performance monitoring.
     */
    @Composable
    fun currentSnapshot(): PerformanceSnapshot?

    /**
     * Get current health status as a composable state.
     */
    @Composable
    fun currentHealth(): PerformanceHealth

    /**
     * Check if performance indicator should be shown.
     */
    @Composable
    fun shouldShowIndicator(): Boolean

    /**
     * Open the performance panel.
     */
    fun openPerformancePanel()
}
