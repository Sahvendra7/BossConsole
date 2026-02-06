package ai.rever.boss.components.plugin.providers

import ai.rever.boss.plugin.api.TerminalTabContentProvider

/**
 * Desktop implementation of TerminalTabContentProvider factory.
 */
actual fun createTerminalTabContentProvider(): TerminalTabContentProvider? {
    return TerminalTabContentProviderImpl()
}
