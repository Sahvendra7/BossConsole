package ai.rever.boss.old_version.v2.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BossItem(
    mainText: String,
    secondaryText: String,
    tertiaryText: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = mainText,
                style = MaterialTheme.typography.h6
            )
            Text(
                text = secondaryText,
                style = MaterialTheme.typography.body1
            )
            Text(
                text = tertiaryText,
                style = MaterialTheme.typography.caption
            )
        }
    }
}