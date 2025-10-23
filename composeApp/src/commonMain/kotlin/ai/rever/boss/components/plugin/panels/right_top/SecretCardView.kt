package ai.rever.boss.components.plugin.panels.right_top

import ai.rever.boss.services.supabase.models.SecretEntry
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.FeatherIcons
import compose.icons.feathericons.*

/**
 * Secret card view displaying a single secret entry
 */
@Composable
fun SecretCard(
    secret: SecretEntry,
    isPasswordVisible: Boolean,
    isMetadataExpanded: Boolean,
    onTogglePassword: () -> Unit,
    onToggleMetadata: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        backgroundColor = Color(0xFF3C3F41),
        elevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Website and actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Website
                    Text(
                        text = secret.website,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // Username
                    Text(
                        text = secret.username,
                        color = Color.Gray,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Action buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onShare,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            FeatherIcons.Share2,
                            contentDescription = "Share",
                            tint = Color(0xFF64B5F6),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            FeatherIcons.Edit,
                            contentDescription = "Edit",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            FeatherIcons.Trash2,
                            contentDescription = "Delete",
                            tint = Color(0xFFE57373),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Password field
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2B2D30), RoundedCornerShape(4.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isPasswordVisible) secret.password else "••••••••",
                    color = if (isPasswordVisible) Color.White else Color.Gray,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(
                    onClick = onTogglePassword,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        if (isPasswordVisible) FeatherIcons.EyeOff else FeatherIcons.Eye,
                        contentDescription = if (isPasswordVisible) "Hide password" else "Show password",
                        tint = Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Tags
            if (secret.tags.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    secret.tags.forEach { tag ->
                        TagBadge(tag)
                    }
                }
            }

            // Expiration warning
            if (secret.expirationDate != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        FeatherIcons.Calendar,
                        contentDescription = null,
                        tint = Color(0xFFFFB74D),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Expires: ${secret.expirationDate}",
                        color = Color(0xFFFFB74D),
                        fontSize = 12.sp
                    )
                }
            }

            // "More" button to show metadata
            if (secret.metadata != null && secret.metadata.twofaEnabled) {
                Divider(color = Color(0xFF4E5254), thickness = 1.dp)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleMetadata() }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            FeatherIcons.Shield,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "2FA Details",
                            color = Color(0xFF4CAF50),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Icon(
                        if (isMetadataExpanded) FeatherIcons.ChevronUp else FeatherIcons.ChevronDown,
                        contentDescription = if (isMetadataExpanded) "Hide details" else "Show details",
                        tint = Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Expanded metadata
                if (isMetadataExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF2B2D30), RoundedCornerShape(4.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 2FA Type
                        if (secret.metadata.twofaType != null) {
                            MetadataRow(
                                label = "2FA Type",
                                value = secret.metadata.twofaType.uppercase()
                            )
                        }

                        // Recovery codes
                        if (secret.metadata.recoveryCodes.isNotEmpty()) {
                            Text(
                                text = "Recovery Codes:",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            secret.metadata.recoveryCodes.forEach { code ->
                                Text(
                                    text = "• $code",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Notes
            if (secret.notes != null && secret.notes.isNotBlank()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Notes:",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = secret.notes,
                        color = Color.White,
                        fontSize = 12.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Tag badge component
 */
@Composable
fun TagBadge(tag: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF4CAF50).copy(alpha = 0.2f)
    ) {
        Text(
            text = tag,
            color = Color(0xFF4CAF50),
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

/**
 * Metadata row component
 */
@Composable
fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = Color.Gray,
            fontSize = 12.sp
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
