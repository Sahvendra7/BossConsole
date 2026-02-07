package ai.rever.boss.components.plugin.providers

import ai.rever.boss.plugin.api.NavigationResolverProvider

/**
 * Desktop implementation of NavigationResolverProvider factory.
 * Uses the host's PSI infrastructure for code navigation.
 */
actual fun createNavigationResolverProvider(): NavigationResolverProvider? {
    return NavigationResolverProviderImpl()
}
