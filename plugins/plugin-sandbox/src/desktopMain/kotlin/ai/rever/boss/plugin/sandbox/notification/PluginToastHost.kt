package ai.rever.boss.plugin.sandbox.notification

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Host composable for displaying plugin toast notifications.
 *
 * Place this at the root of your composition (e.g., in a Box with alignment)
 * to display toast notifications from the plugin sandbox system.
 *
 * @param toastState The toast state manager
 * @param modifier Modifier for the host container
 */
@Composable
fun PluginToastHost(
    toastState: PluginToastState,
    modifier: Modifier = Modifier
) {
    val toasts by toastState.toasts.collectAsState()

    Column(
        modifier = modifier
            .padding(16.dp)
            .widthIn(max = 400.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.End
    ) {
        toasts.forEach { toast ->
            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it }
            ) {
                PluginToast(
                    message = toast,
                    onDismiss = { toastState.dismiss(toast.id) }
                )
            }
        }
    }
}

/**
 * Individual toast message composable.
 *
 * @param message The toast message to display
 * @param onDismiss Callback when the toast is dismissed
 */
@Composable
fun PluginToast(
    message: ToastMessage,
    onDismiss: () -> Unit
) {
    val (backgroundColor, contentColor, icon) = getToastColors(message.type)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Icon
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )

            Spacer(Modifier.width(12.dp))

            // Content
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = message.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor
                )
                Text(
                    text = message.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.8f)
                )

                // Action button
                message.action?.let { action ->
                    TextButton(
                        onClick = {
                            action.onClick()
                            onDismiss()
                        },
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = action.label,
                            color = contentColor,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            // Dismiss button (only for indefinite toasts or all toasts for better UX)
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Dismiss",
                    tint = contentColor.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Get colors and icon for a toast type.
 */
@Composable
private fun getToastColors(type: ToastType): Triple<Color, Color, ImageVector> {
    return when (type) {
        ToastType.INFO -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            Icons.Outlined.Info
        )
        ToastType.SUCCESS -> Triple(
            Color(0xFF1B5E20).copy(alpha = 0.9f), // Dark green
            Color.White,
            Icons.Outlined.CheckCircle
        )
        ToastType.WARNING -> Triple(
            Color(0xFFF57C00).copy(alpha = 0.9f), // Orange
            Color.White,
            Icons.Outlined.Warning
        )
        ToastType.ERROR -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            Icons.Outlined.Error
        )
    }
}

/**
 * Compact version of the toast for use in constrained spaces.
 *
 * @param message The toast message to display
 * @param onDismiss Callback when the toast is dismissed
 */
@Composable
fun CompactPluginToast(
    message: ToastMessage,
    onDismiss: () -> Unit
) {
    val (backgroundColor, contentColor, icon) = getToastColors(message.type)

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )

            Spacer(Modifier.width(8.dp))

            Text(
                text = message.message,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor,
                modifier = Modifier.weight(1f)
            )

            message.action?.let { action ->
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        action.onClick()
                        onDismiss()
                    }
                ) {
                    Text(
                        text = action.label,
                        color = contentColor,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Dismiss",
                    tint = contentColor.copy(alpha = 0.6f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
