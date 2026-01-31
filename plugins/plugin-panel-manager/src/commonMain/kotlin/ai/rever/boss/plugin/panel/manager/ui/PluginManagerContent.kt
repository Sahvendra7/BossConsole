package ai.rever.boss.plugin.panel.manager

import androidx.compose.material.icons.filled.Extension
import ai.rever.boss.plugin.repository.PluginInfo
import ai.rever.boss.plugin.updater.UpdateInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Main content for the Plugin Manager panel.
 */
@Composable
fun PluginManagerContent(component: PluginManagerComponent) {
    val state by component.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Header with tabs
        PluginManagerHeader(
            currentTab = state.currentTab,
            updateCount = state.updates.size,
            onTabSelected = { component.selectTab(it) },
            onRefresh = { component.refresh() },
            onInstall = { component.installFromFilePicker() },
            isLoading = state.isLoading
        )

        // Search bar
        SearchBar(
            query = state.searchQuery,
            onQueryChange = { component.setSearchQuery(it) }
        )

        // Error message
        if (state.error != null) {
            ErrorBanner(
                message = state.error!!,
                onDismiss = { component.clearError() }
            )
        }

        // Content based on selected tab
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            when (state.currentTab) {
                PluginManagerTab.INSTALLED -> InstalledPluginsTab(
                    plugins = filterPlugins(state.installedPlugins, state.searchQuery),
                    onToggleEnabled = { id, enabled -> component.togglePluginEnabled(id, enabled) },
                    onUninstall = { id -> component.uninstallPlugin(id) },
                    isLoading = state.isLoading
                )
                PluginManagerTab.AVAILABLE -> AvailablePluginsTab(
                    plugins = filterAvailablePlugins(state.availablePlugins, state.searchQuery),
                    installedIds = state.installedPlugins.map { it.pluginId }.toSet(),
                    onInstall = { pluginId -> component.installFromRemote(pluginId) },
                    isLoading = state.isLoading
                )
                PluginManagerTab.UPDATES -> UpdatesTab(
                    updates = state.updates,
                    onUpdate = { id -> component.updatePlugin(id) },
                    onUpdateAll = { component.updateAllPlugins() },
                    isLoading = state.isLoading
                )
            }
        }
    }
}

@Composable
private fun PluginManagerHeader(
    currentTab: PluginManagerTab,
    updateCount: Int,
    onTabSelected: (PluginManagerTab) -> Unit,
    onRefresh: () -> Unit,
    onInstall: () -> Unit,
    isLoading: Boolean
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Plugins",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Row {
                IconButton(onClick = onRefresh, enabled = !isLoading) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                }
                IconButton(onClick = onInstall) {
                    Icon(Icons.Default.Add, "Install Plugin")
                }
            }
        }

        TabRow(
            selectedTabIndex = currentTab.ordinal
        ) {
            Tab(
                selected = currentTab == PluginManagerTab.INSTALLED,
                onClick = { onTabSelected(PluginManagerTab.INSTALLED) },
                text = { Text("Installed") }
            )
            Tab(
                selected = currentTab == PluginManagerTab.AVAILABLE,
                onClick = { onTabSelected(PluginManagerTab.AVAILABLE) },
                text = { Text("Available") }
            )
            Tab(
                selected = currentTab == PluginManagerTab.UPDATES,
                onClick = { onTabSelected(PluginManagerTab.UPDATES) },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Updates")
                        if (updateCount > 0) {
                            Spacer(Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = updateCount.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        placeholder = { Text("Search plugins...") },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        singleLine = true,
        shape = RoundedCornerShape(8.dp)
    )
}

@Composable
private fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Warning,
                null,
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, "Dismiss")
        }
    }
}

@Composable
private fun InstalledPluginsTab(
    plugins: List<InstalledPluginState>,
    onToggleEnabled: (String, Boolean) -> Unit,
    onUninstall: (String) -> Unit,
    isLoading: Boolean
) {
    if (plugins.isEmpty()) {
        EmptyState(
            message = "No plugins installed",
            description = "Click the + button to install a plugin"
        )
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(plugins, key = { it.pluginId }) { plugin ->
                InstalledPluginCard(
                    plugin = plugin,
                    onToggleEnabled = { onToggleEnabled(plugin.pluginId, it) },
                    onUninstall = { onUninstall(plugin.pluginId) },
                    isLoading = isLoading
                )
            }
        }
    }
}

@Composable
private fun InstalledPluginCard(
    plugin: InstalledPluginState,
    onToggleEnabled: (Boolean) -> Unit,
    onUninstall: () -> Unit,
    isLoading: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (plugin.enabled)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = plugin.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "v${plugin.version}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!plugin.healthy) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Default.Warning,
                            "Plugin unhealthy",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
                if (plugin.description.isNotEmpty()) {
                    Text(
                        text = plugin.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = plugin.enabled,
                    onCheckedChange = { onToggleEnabled(it) },
                    enabled = !isLoading
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = onUninstall,
                    enabled = !isLoading && plugin.canUnload
                ) {
                    Icon(
                        Icons.Default.Delete,
                        "Uninstall",
                        tint = if (plugin.canUnload)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}

@Composable
private fun AvailablePluginsTab(
    plugins: List<PluginInfo>,
    installedIds: Set<String>,
    onInstall: (String) -> Unit,
    isLoading: Boolean
) {
    if (plugins.isEmpty()) {
        EmptyState(
            message = "No plugins available",
            description = "Check back later for new plugins"
        )
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(plugins, key = { it.pluginId }) { plugin ->
                AvailablePluginCard(
                    plugin = plugin,
                    isInstalled = plugin.pluginId in installedIds,
                    onInstall = { onInstall(plugin.pluginId) },
                    isLoading = isLoading
                )
            }
        }
    }
}

@Composable
private fun AvailablePluginCard(
    plugin: PluginInfo,
    isInstalled: Boolean,
    onInstall: () -> Unit,
    isLoading: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = plugin.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "v${plugin.version}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (plugin.verified) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Default.Check,
                            "Verified",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (plugin.description.isNotEmpty()) {
                    Text(
                        text = plugin.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (plugin.author.isNotEmpty()) {
                    Text(
                        text = "by ${plugin.author}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isInstalled) {
                Text(
                    text = "Installed",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Button(
                    onClick = onInstall,
                    enabled = !isLoading,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Install")
                }
            }
        }
    }
}

@Composable
private fun UpdatesTab(
    updates: List<UpdateInfo>,
    onUpdate: (String) -> Unit,
    onUpdateAll: () -> Unit,
    isLoading: Boolean
) {
    if (updates.isEmpty()) {
        EmptyState(
            message = "All plugins are up to date",
            description = "No updates available"
        )
    } else {
        Column {
            // Update All button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = onUpdateAll,
                    enabled = !isLoading
                ) {
                    Text("Update All (${updates.size})")
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(updates, key = { it.pluginId }) { update ->
                    UpdateCard(
                        update = update,
                        onUpdate = { onUpdate(update.pluginId) },
                        isLoading = isLoading
                    )
                }
            }
        }
    }
}

@Composable
private fun UpdateCard(
    update: UpdateInfo,
    onUpdate: () -> Unit,
    isLoading: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (update.critical)
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = update.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    if (update.critical) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Critical",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.errorContainer,
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = "${update.currentVersion} → ${update.newVersion}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (update.changelog.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = update.changelog,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Button(
                onClick = onUpdate,
                enabled = !isLoading,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text("Update")
            }
        }
    }
}

@Composable
private fun EmptyState(
    message: String,
    description: String
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Extension,
                null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}

private fun filterPlugins(
    plugins: List<InstalledPluginState>,
    query: String
): List<InstalledPluginState> {
    if (query.isEmpty()) return plugins
    val lowerQuery = query.lowercase()
    return plugins.filter {
        it.displayName.lowercase().contains(lowerQuery) ||
        it.pluginId.lowercase().contains(lowerQuery) ||
        it.description.lowercase().contains(lowerQuery)
    }
}

private fun filterAvailablePlugins(
    plugins: List<PluginInfo>,
    query: String
): List<PluginInfo> {
    if (query.isEmpty()) return plugins
    val lowerQuery = query.lowercase()
    return plugins.filter {
        it.displayName.lowercase().contains(lowerQuery) ||
        it.pluginId.lowercase().contains(lowerQuery) ||
        it.description.lowercase().contains(lowerQuery)
    }
}
