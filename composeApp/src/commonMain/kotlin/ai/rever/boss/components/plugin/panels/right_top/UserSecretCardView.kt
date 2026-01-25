package ai.rever.boss.components.plugin.panels.right_top

import ai.rever.boss.services.supabase.models.SecretEntryWithSharing
import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import ai.rever.boss.utils.createTextClipEntry
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.FeatherIcons
import compose.icons.feathericons.*

private val userSecretCardLogger = BossLogger.forComponent("UserSecretCardView")

/**
 * Read-only secret card view for user-level secret list
 *
 * Displays website:username pairs with ownership badges.
 * No password display, no edit/delete/share actions.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun UserSecretCard(
    secret: SecretEntryWithSharing,
    isMetadataExpanded: Boolean,
    onToggleMetadata: () -> Unit
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

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
            // Header: Website, Username, and Ownership Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Website with icon
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            FeatherIcons.Globe,
                            contentDescription = "Website",
                            tint = Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = secret.website,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Username with icon
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            FeatherIcons.User,
                            contentDescription = "Username",
                            tint = Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = secret.username,
                            color = Color.Gray,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Ownership badge
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (secret.isOwner) Color(0xFF4CAF50) else Color(0xFF64B5F6),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (secret.isOwner) FeatherIcons.User else FeatherIcons.Share2,
                            contentDescription = if (secret.isOwner) "Owner" else "Shared",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = if (secret.isOwner) "Owner" else "Shared",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Divider(color = Color(0xFF4E5254), thickness = 1.dp)

            // Action button: Copy Username only
            Button(
                onClick = {
                    scope.launch {
                        clipboard.setClipEntry(createTextClipEntry(secret.username))
                        userSecretCardLogger.debug(LogCategory.UI, "Copied username to clipboard", mapOf("username" to secret.username))
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Color(0xFF2B2D30),
                    contentColor = Color(0xFF64B5F6)
                ),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(8.dp)
            ) {
                Icon(
                    FeatherIcons.Copy,
                    contentDescription = "Copy Username",
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("Copy Username", fontSize = 12.sp)
            }

            // Shared by information (only for shared secrets)
            if (!secret.isOwner && secret.sharedByEmail != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF2B2D30), RoundedCornerShape(4.dp))
                        .padding(8.dp)
                ) {
                    Icon(
                        FeatherIcons.UserPlus,
                        contentDescription = "Shared by",
                        tint = Color(0xFF64B5F6),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        "Shared by: ${secret.sharedByEmail}",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
            }

            // Show details button (if has tags or notes)
            if (secret.tags.isNotEmpty() || secret.notes != null || secret.expirationDate != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleMetadata() }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (isMetadataExpanded) "Hide Details" else "Show Details",
                        color = Color(0xFF4CAF50),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        if (isMetadataExpanded) FeatherIcons.ChevronUp else FeatherIcons.ChevronDown,
                        contentDescription = if (isMetadataExpanded) "Hide" else "Show",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Expanded metadata section
                if (isMetadataExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF2B2D30), RoundedCornerShape(4.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Tags
                        if (secret.tags.isNotEmpty()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    FeatherIcons.Tag,
                                    contentDescription = "Tags",
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    secret.tags.joinToString(", "),
                                    color = Color.White,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        // Notes
                        if (secret.notes != null) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    FeatherIcons.FileText,
                                    contentDescription = "Notes",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(14.dp).padding(top = 2.dp)
                                )
                                Text(
                                    secret.notes,
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        // Expiration date
                        if (secret.expirationDate != null) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    FeatherIcons.Calendar,
                                    contentDescription = "Expires",
                                    tint = Color(0xFFFFB74D),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    "Expires: ${secret.expirationDate}",
                                    color = Color(0xFFFFB74D),
                                    fontSize = 12.sp
                                )
                            }
                        }

                        // Created date
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                FeatherIcons.Clock,
                                contentDescription = "Created",
                                tint = Color.Gray,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                "Created: ${secret.createdAt}",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
