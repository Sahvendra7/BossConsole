package ai.rever.bosseditor.features

import ai.rever.bosseditor.core.EditorPosition

/**
 * Registry for managing completion providers.
 *
 * Plugins can register their completion providers here using a unique ID.
 * The registry is a global singleton that survives across editor instances.
 *
 * ## Usage
 * ```kotlin
 * // Register a provider
 * CompletionProviderRegistry.register("my-plugin", myProvider)
 *
 * // Use all registered providers
 * val allProviders = CompletionProviderRegistry.getAll()
 *
 * // Clean up
 * CompletionProviderRegistry.unregister("my-plugin")
 * ```
 */
object CompletionProviderRegistry {
    private val lock = Any()
    private val providers = mutableMapOf<String, CompletionProvider>()

    /**
     * Registers a completion provider with the given ID.
     * If a provider with the same ID already exists, it will be replaced.
     * Thread-safe.
     */
    fun register(id: String, provider: CompletionProvider) {
        synchronized(lock) { providers[id] = provider }
    }

    /**
     * Unregisters a completion provider by ID.
     * @return true if a provider was removed, false if no such provider existed
     * Thread-safe.
     */
    fun unregister(id: String): Boolean {
        return synchronized(lock) { providers.remove(id) != null }
    }

    /**
     * Gets a registered completion provider by ID.
     * @return The provider, or null if not found
     */
    fun get(id: String): CompletionProvider? {
        return synchronized(lock) { providers[id] }
    }

    /**
     * Gets all registered completion providers.
     */
    fun getAll(): List<CompletionProvider> {
        return synchronized(lock) { providers.values.toList() }
    }

    /**
     * Removes all registered providers.
     */
    fun clear() {
        synchronized(lock) { providers.clear() }
    }
}

/**
 * A completion provider that aggregates results from multiple providers.
 *
 * Results are merged and deduplicated by label. When multiple providers
 * return items with the same label, the first occurrence is kept.
 *
 * @param providers The list of providers to aggregate
 */
class CompositeCompletionProvider(
    private val providers: List<CompletionProvider>
) : CompletionProvider {

    override suspend fun getCompletions(
        position: EditorPosition,
        prefix: String,
        triggerCharacter: Char?
    ): CompletionResult {
        if (providers.isEmpty()) {
            return CompletionResult(items = emptyList())
        }

        val allItems = mutableListOf<CompletionItem>()
        var isIncomplete = false
        val seenLabels = mutableSetOf<String>()

        for (provider in providers) {
            try {
                val result = provider.getCompletions(position, prefix, triggerCharacter)
                if (result.isIncomplete) {
                    isIncomplete = true
                }
                for (item in result.items) {
                    if (seenLabels.add(item.label)) {
                        allItems.add(item)
                    }
                }
            } catch (_: Exception) {
                // Intentionally swallowed: a failing provider should not prevent
                // other providers from contributing completions. Providers should
                // implement their own error handling/logging if needed.
            }
        }

        return CompletionResult(
            items = allItems,
            isIncomplete = isIncomplete
        )
    }
}
