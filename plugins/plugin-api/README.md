# Plugin API

Core interfaces and contracts for the BOSS plugin system.

## Overview

This module defines the foundational interfaces that all plugins must implement. It provides:

- **Plugin Interface**: Base contract for plugin modules
- **PluginContext**: Runtime context provided to plugins
- **PanelRegistry/TabRegistry**: Registration APIs for UI components
- **Data Provider Interfaces**: Contracts for accessing app data

## Key Interfaces

### Plugin

```kotlin
interface Plugin {
    val pluginId: String
    val displayName: String
    fun register(context: PluginContext)
    fun dispose() {}
}
```

### PluginContext

```kotlin
interface PluginContext {
    val panelRegistry: PanelRegistry
    val tabRegistry: TabRegistry
    val pluginScope: CoroutineScope
}
```

### PanelInfo

Defines panel metadata (id, display name, icon, default position).

### PanelComponentWithUI

Interface for panels that render Compose UI.

## Usage

1. Create a plugin module that depends on `plugin-api`
2. Implement the `Plugin` interface
3. Register panels/tabs in `register(context)`
4. Clean up resources in `dispose()`

## Data Providers

The module includes interfaces for accessing various app data:

- `GitDataProvider` - Git repository operations
- `SecretDataProvider` - Credential management
- `FileSystemProvider` - File system operations
- `PerformanceProvider` - System metrics
- `BookmarkDataProvider` - Bookmark management

## Lifecycle

1. Plugin is instantiated by the host application
2. `register(context)` is called with the plugin context
3. Plugin registers its panels/tabs using the context
4. When the app closes, `dispose()` is called
5. Plugins should cancel any coroutines launched in `pluginScope`
