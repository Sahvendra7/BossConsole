package ai.rever.boss.components.plugin.providers

import ai.rever.boss.plugin.api.TerminalTabContentProvider

/**
 * Factory function to create platform-specific TerminalTabContentProvider.
 * Desktop implementation returns TerminalTabContentProviderImpl.
 * Returns null on platforms that don't support terminal tabs.
 */
expect fun createTerminalTabContentProvider(): TerminalTabContentProvider?
