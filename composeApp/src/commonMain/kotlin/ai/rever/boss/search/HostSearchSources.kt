package ai.rever.boss.search

import ai.rever.boss.dashboard.RecentBrowserPagesManager
import ai.rever.boss.mcp.McpToolRegistryImpl

/**
 * Wire the host singletons the global search reads into [SearchSources].
 *
 * Called once at startup, beside the settings index's own registration. One function for both,
 * rather than a line each at the call site, so the two cannot drift apart - a source that is never
 * registered contributes nothing and says nothing, which is the failure mode this whole seam was
 * chosen to avoid, so the wiring is worth keeping in one readable place.
 *
 * `McpToolRegistryImpl` and `RecentBrowserPagesManager` are both commonMain, so [GlobalSearchService]
 * could read them directly and did at first. Going through suppliers buys two things a direct read
 * cannot: a fake in tests - which is what finally gave the RBAC filter below a test, the one path
 * where a regression leaks admin-only tool names and descriptions to a signed-out user - and a
 * `search()` call that touches no disk, since reaching the registry forces it to load its
 * disabled-tools file from `~/.boss`.
 *
 * **Recent pages carry no permission gate, and MCP tools do.** Not an oversight: an MCP tool's name
 * and description describe a capability of the *install*, so on a shared or signed-out machine they
 * enumerate what the operator can be made to run, which is why `permittedTools()` filters them.
 * Recent pages are the browsing history of whoever is sitting at the machine, shown to that same
 * person on the surface they just browsed with - the dashboard already lists them unfiltered, and
 * gating them here without gating there would look like a bug rather than a boundary. Worth
 * revisiting together if BOSS ever gets real multi-user profiles on one install.
 */
internal fun registerHostSearchSources() {
    SearchSources.registerMcpTools {
        // permittedTools, not allTools. allTools is deliberately unfiltered for the management UI,
        // which shows every tool with its state; this is the everyday launcher, open to every user
        // and to nobody signed in yet, where a name and a full description of an admin-only tool
        // would be enumerable by typing.
        //
        // The argument stands on its own, and deliberately does not lean on the settings source:
        // built-in settings entries are not RBAC-filtered at all - there is no permission check on
        // a SettingsSection - only plugin pages are, through visiblePages().
        val disabled = McpToolRegistryImpl.disabledToolNames.value
        McpToolRegistryImpl.permittedTools().map { registered ->
            McpToolSearchRecord(
                name = registered.definition.name,
                providerId = registered.providerId,
                description = registered.definition.description,
                // Exposure is "permitted AND not switched off", and the list is already permitted,
                // so what is left to ask is whether it was switched off. Computed here rather than
                // in the service because getting it wrong showed a permission-denied tool as live
                // when no client could see it - and answering "is this switched off" is the row's
                // entire job.
                enabled = registered.definition.name !in disabled,
            )
        }
    }

    SearchSources.registerRecentPages {
        RecentBrowserPagesManager.recentPages.value.map { PageSearchRecord(url = it.url, title = it.title) }
    }
}
