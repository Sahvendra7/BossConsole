package ai.rever.boss.plugin.sandbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Fallback UI shown when a plugin is disabled.
 *
 * Displays a message explaining the plugin is disabled and provides
 * a button to re-enable it. When [isIncompatible] is true, shows
 * an "incompatible" variant prompting the user to update the plugin.
 *
 * @param pluginId The ID of the disabled plugin
 * @param isIncompatible Whether the plugin was disabled due to binary incompatibility
 * @param onEnable Callback when the user clicks "Re-enable Plugin"
 */
@Composable
fun PluginDisabledFallback(
    pluginId: String,
    isIncompatible: Boolean = false,
    onEnable: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (isIncompatible) Icons.Outlined.SystemUpdate else Icons.Outlined.Block,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = if (isIncompatible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = if (isIncompatible) "Plugin Incompatible" else "Plugin Disabled",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = if (isIncompatible) {
                "Plugin '$pluginId' is incompatible with this version of BOSS"
            } else {
                "Plugin '$pluginId' has been disabled"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = if (isIncompatible) {
                "This plugin needs to be updated to work with this version of BOSS."
            } else {
                "This may be due to repeated errors or manual disabling."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        OutlinedButton(onClick = onEnable) {
            Icon(
                imageVector = if (isIncompatible) Icons.Outlined.SystemUpdate else Icons.Outlined.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(if (isIncompatible) "Check for Updates" else "Re-enable Plugin")
        }
    }
}

/**
 * Fallback UI shown when a plugin is restarting.
 *
 * Displays a loading indicator while the plugin restarts.
 *
 * @param pluginId The ID of the plugin being restarted
 */
@Composable
fun PluginRestartingFallback(
    pluginId: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(32.dp),
            strokeWidth = 3.dp
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Restarting Plugin...",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = pluginId,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Fallback UI shown when a plugin is stopped.
 *
 * Provides a button to start the plugin again.
 *
 * @param pluginId The ID of the stopped plugin
 * @param onStart Callback when the user clicks "Start Plugin"
 */
@Composable
fun PluginStoppedFallback(
    pluginId: String,
    onStart: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.PowerSettingsNew,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.outline
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Plugin Stopped",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = pluginId,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        Button(onClick = onStart) {
            Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text("Start Plugin")
        }
    }
}

/**
 * Banner shown when a plugin is unhealthy but still running.
 *
 * Displays a warning message with the error count and a restart button.
 *
 * @param pluginId The ID of the unhealthy plugin
 * @param consecutiveErrors The number of consecutive errors
 * @param onRestart Callback when the user clicks "Restart"
 */
@Composable
fun PluginUnhealthyBanner(
    pluginId: String,
    consecutiveErrors: Int,
    onRestart: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Warning,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.error
        )

        Spacer(Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Plugin Unhealthy",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                text = "$consecutiveErrors consecutive errors in '$pluginId'",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(Modifier.width(8.dp))

        TextButton(onClick = onRestart) {
            Text("Restart")
        }
    }
}

/**
 * Compact disabled fallback for use in smaller spaces.
 *
 * @param pluginId The ID of the disabled plugin
 * @param onEnable Callback when the user clicks "Enable"
 */
@Composable
fun CompactPluginDisabledFallback(
    pluginId: String,
    onEnable: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Block,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.outline
        )

        Spacer(Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = pluginId,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Plugin disabled",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.width(8.dp))

        OutlinedButton(
            onClick = onEnable,
            modifier = Modifier.height(28.dp)
        ) {
            Text(
                text = "Enable",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
