package ai.rever.boss.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BossHeader(
    headerText: String,
    backText: String? = "back",
    hiddenBackButton: Boolean = false,
    onNavigateBack: () -> Unit,
    actions: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!hiddenBackButton) {
            Row(
                modifier = Modifier.clickable(onClick = onNavigateBack),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.padding(end = 4.dp),
                    tint = MaterialTheme.colors.primary
                )
                backText?.let {
                    Text(
                        backText,
                        style = MaterialTheme.typography.body1,
                        color = MaterialTheme.colors.primary
                    )
                }
            }
        } else {
            Icon(
                imageVector = Icons.Default.Terminal,
                contentDescription = "Terminal",
                tint = MaterialTheme.colors.primary
            )
        }
        Text(
            headerText,
            style = MaterialTheme.typography.h6,
            modifier = Modifier.padding(start = 16.dp)
        )
        actions?.let {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                it()
            }
        }
    }
}