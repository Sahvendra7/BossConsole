package ai.rever.boss

import ai.rever.boss.ui.common.BossHeader
import androidx.compose.runtime.Composable

@Composable
fun PreviewFileForWorkList(onBack: () -> Unit) {
    BossHeader("Preview File", "Home", onNavigateBack = onBack)
}