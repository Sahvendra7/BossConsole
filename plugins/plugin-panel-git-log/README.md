# Git Log Panel Plugin

Displays Git commit history with interactive features.

## Features

- Commit history with hash, author, date, and message
- Branch/tag badges
- Expandable commit details
- Actions: Cherry-pick, Revert, Checkout
- Copy commit hash to clipboard

## Registration

This plugin requires window-specific context and is dynamically registered:

```kotlin
// In DefaultPlugin.kt
val gitDataProvider = GitDataProviderImpl(windowGitState) { windowId }
GitLogPanelPlugin.register(this, gitDataProvider)
```

## Dynamic Visibility

The panel is only shown when:
- A project is selected (path is not empty)
- The project is a Git repository

## Dependencies

- `plugin-api` - Core plugin interfaces
- `plugin-git-types` - Git data types
- `plugin-ui` - Shared UI components
- `plugin-scrollbar` - Scrollbar utilities

## Panel Info

- **ID**: `git-log` (priority 15)
- **Display Name**: "Git Log"
- **Default Position**: Left Bottom
- **Icon**: GitBranch (Feather Icons)
