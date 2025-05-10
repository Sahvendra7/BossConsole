package ai.rever.boss.old_version.v1

import ai.rever.boss.old_version.v2.ui.common.BossHeader
import androidx.compose.runtime.Composable

@Composable
fun PreviewFileForWorkList(onBack: () -> Unit) {
    BossHeader("Preview File", "Home", onNavigateBack = onBack)
}