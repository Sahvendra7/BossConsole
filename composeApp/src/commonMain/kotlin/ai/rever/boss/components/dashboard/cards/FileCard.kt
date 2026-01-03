package ai.rever.boss.components.dashboard.cards

import BossDarkSurface
import BossDarkTextSecondary
import ai.rever.boss.dashboard.RecentFile
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Card displaying a recent file.
 */
@Composable
fun FileCard(
    file: RecentFile,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.02f else 1f,
        animationSpec = spring(dampingRatio = 0.6f)
    )

    val backgroundColor = if (isHovered) Color(0xFF2A2D30) else BossDarkSurface
    val (icon, iconColor) = getFileIconAndColor(file.name)
    val cardShape = RoundedCornerShape(12.dp)

    Box(
        modifier = modifier.width(120.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .scale(scale)
                .clip(cardShape)
                .background(color = backgroundColor)
                .clickable { onClick() }
                .hoverable(interactionSource)
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = file.name,
                tint = iconColor,
                modifier = Modifier.size(32.dp)
            )

            // File name with fixed height for consistency
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = file.name,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }

            // Parent folder with fixed height for consistency
            val parentFolder = file.path.substringBeforeLast('/').substringAfterLast('/')
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp),
                contentAlignment = Alignment.Center
            ) {
                if (parentFolder.isNotEmpty()) {
                    Text(
                        text = parentFolder,
                        color = BossDarkTextSecondary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Remove button (visible on hover)
        if (isHovered) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(16.dp)
                    .background(
                        color = Color(0xFF3A3D40),
                        shape = CircleShape
                    )
                    .clickable { onRemove() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Remove",
                    tint = BossDarkTextSecondary,
                    modifier = Modifier.size(10.dp)
                )
            }
        }
    }
}

/**
 * Get appropriate icon and color for a file based on its extension.
 */
private fun getFileIconAndColor(fileName: String): Pair<ImageVector, Color> {
    val extension = fileName.substringAfterLast('.', "").lowercase()

    return when (extension) {
        // Code files
        "kt", "kts", "java", "scala" -> Icons.Outlined.Code to Color(0xFFB877DB)
        "js", "ts", "jsx", "tsx" -> Icons.Outlined.Code to Color(0xFFF7DF1E)
        "py" -> Icons.Outlined.Code to Color(0xFF3776AB)
        "rs" -> Icons.Outlined.Code to Color(0xFFDEA584)
        "go" -> Icons.Outlined.Code to Color(0xFF00ADD8)
        "rb" -> Icons.Outlined.Code to Color(0xFFCC342D)
        "swift" -> Icons.Outlined.Code to Color(0xFFFA7343)
        "c", "cpp", "h", "hpp" -> Icons.Outlined.Code to Color(0xFF00599C)
        "cs" -> Icons.Outlined.Code to Color(0xFF239120)
        "php" -> Icons.Outlined.Code to Color(0xFF777BB4)

        // Web files
        "html", "htm" -> Icons.Outlined.Code to Color(0xFFE34F26)
        "css", "scss", "sass", "less" -> Icons.Outlined.Code to Color(0xFF1572B6)

        // Config/Data files
        "json", "yaml", "yml", "toml" -> Icons.Outlined.Settings to Color(0xFF8BC34A)
        "xml" -> Icons.Outlined.Settings to Color(0xFFFF9800)
        "gradle" -> Icons.Outlined.Settings to Color(0xFF02303A)
        "properties" -> Icons.Outlined.Settings to Color(0xFF607D8B)

        // Documentation
        "md", "markdown", "txt", "doc", "docx" -> Icons.Outlined.Description to Color(0xFF42A5F5)
        "pdf" -> Icons.Outlined.Description to Color(0xFFE53935)

        // Images
        "png", "jpg", "jpeg", "gif", "svg", "ico", "webp" -> Icons.Outlined.Image to Color(0xFF66BB6A)

        // Default
        else -> Icons.Outlined.InsertDriveFile to Color(0xFF78909C)
    }
}
