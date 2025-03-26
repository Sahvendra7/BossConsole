package ai.rever.boss

import ai.rever.boss.ui.common.BossHeader
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun WorkDetails(work: Work, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        BossHeader("Details for ${work.shortDescription}", "Worklist", onNavigateBack = onBack)

        Box(modifier = Modifier.weight(1f)) {
            Tabs()
        }
        
        Button(
            onClick = onBack,
        ) {
            Text("Back to Home")
        }
    }
}