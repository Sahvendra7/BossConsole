package ai.rever.boss.components.plugin.providers

import ai.rever.boss.plugin.panel.codebase.DirectoryPickerProvider

/**
 * Platform-specific implementation of DirectoryPickerProvider.
 * Uses native file dialogs on each platform.
 */
expect class DirectoryPickerProviderImpl() : DirectoryPickerProvider
