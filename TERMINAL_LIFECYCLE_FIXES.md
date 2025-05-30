# Terminal Lifecycle Fixes

## Issues Fixed

### 1. Terminal Panel Reuse Issue
**Problem**: When a terminal panel was closed and reopened, it showed the old dead session because the PanelComponentStore was reusing the same component instance.

**Solution**: 
- Added `removeComponent()` and `forceCreateComponent()` methods to `PanelComponentStore`
- Updated `BossApp` to remove the component from the store when a panel close event is received
- This ensures a fresh terminal instance is created each time the panel is opened

### 2. Terminal Tab Monitoring Issue
**Problem**: Terminal tabs were not closing on exit because the monitoring was not working correctly - it was checking for running state before the terminal had started.

**Solution**:
- Improved the monitoring logic in both `TerminalTabComponent` and `TerminalComponent`
- Added a proper startup wait loop that checks if the terminal has started before monitoring the running state
- This ensures the monitor properly detects when a terminal exits

## Code Changes

### PanelComponentStore.kt
```kotlin
// Added methods to remove and force create components
fun removeComponent(panelId: PanelId) {
    activeComponents.remove(panelId)
}

fun forceCreateComponent(panelId: PanelId): PanelComponentWithUI? {
    removeComponent(panelId)
    return getOrCreateComponent(panelId)
}
```

### BossApp.kt
```kotlin
// Added component removal when panel is closed
PanelEventBus.panelCloseEvents
    .onEach { event ->
        // ... find panel ...
        draggablePanelComponent.setPanelVisible(panel, false)
        // Remove the component from store to ensure fresh instance next time
        panelComponentStore.removeComponent(event.panelId)
    }
```

### TerminalTabComponent.kt & Terminal.kt
```kotlin
// Improved monitoring logic
coroutineScope.launch {
    // First wait for terminal to start
    var hasStarted = false
    while (!hasStarted && isActive) {
        if (terminalViewModel.wasStarted) {
            hasStarted = true
            println("[Terminal] Terminal has started")
        } else {
            delay(100)
        }
    }
    
    // Now monitor for when it stops
    terminalViewModel.isRunning.collect { isRunning ->
        if (hasStarted && !isRunning) {
            println("[Terminal] Terminal has exited, closing")
            delay(500)
            onClose() // or PanelEventBus.closePanel()
        }
    }
}
```

## Testing

1. **Panel Lifecycle Test**:
   - Open a terminal panel
   - Type some commands
   - Close the panel (click X button)
   - Reopen the terminal panel
   - ✅ Should show a fresh terminal, not the old session

2. **Tab Lifecycle Test**:
   - Open a terminal tab
   - Type `exit` in the terminal
   - ✅ Tab should automatically close when the terminal exits

## Notes

From the logs, terminals are exiting immediately with code 0. This might be due to:
- Shell initialization issues
- PTY configuration problems
- Environment variable issues

This is a separate issue from the lifecycle management, which has been fixed.