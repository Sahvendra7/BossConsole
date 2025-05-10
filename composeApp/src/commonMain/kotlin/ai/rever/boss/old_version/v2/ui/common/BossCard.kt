package ai.rever.boss.old_version.v2.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Business
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun BossCard(
    title: String,
    description: String,
    imageVector: ImageVector = Icons.Default.Business,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card (
        modifier = modifier
            .height(200.dp)
            .clickable(onClick = onClick),
        elevation = 4.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Icon and title area
            Column {
                Icon(
                    imageVector = imageVector,
                    contentDescription = title,
                    tint = MaterialTheme.colors.primary,
                    modifier = Modifier.size(40.dp),
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.h5,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = description,
                    style = MaterialTheme.typography.body1,
                )
            }

            // Arrow icon at bottom
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Go to $title",
                )
            }
        }
    }
}