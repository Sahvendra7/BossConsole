package ai.rever.boss.components.plugin.providers

import ai.rever.boss.plugin.api.SemanticTokenProvider

/**
 * Desktop implementation of SemanticTokenProvider factory.
 * Uses the host's PSI infrastructure for semantic highlighting.
 */
actual fun createSemanticTokenProvider(): SemanticTokenProvider? {
    return SemanticTokenProviderImpl()
}
