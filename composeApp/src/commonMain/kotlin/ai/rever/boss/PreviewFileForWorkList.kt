package ai.rever.boss

import androidx.compose.runtime.Composable

@Composable
fun PreviewFileForWorkList(onBack: () -> Unit) {
    Header("Preview File", "Home", onNavigateBack = onBack)
}