package ai.rever.boss.v1

import ai.rever.boss.v2.ui.common.BossHeader
import androidx.compose.runtime.Composable

@Composable
fun PreviewFileForWorkList(onBack: () -> Unit) {
    BossHeader("Preview File", "Home", onNavigateBack = onBack)
}