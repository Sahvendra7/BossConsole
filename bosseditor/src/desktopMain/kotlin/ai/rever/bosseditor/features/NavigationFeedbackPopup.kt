package ai.rever.bosseditor.features

import ai.rever.bosseditor.theme.EditorTheme
import ai.rever.bosseditor.theme.LocalEditorTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay

/**
 * Reason why navigation failed.
 */
enum class NavigationFailureReason {
    /** No definition found at the clicked position */
    NOT_FOUND,
    /** Navigation is unavailable (non-Kotlin file, PSI not ready, etc.) */
    UNAVAILABLE
}

/**
 * State for the navigation feedback popup.
 */
data class NavigationFeedbackState(
    val isVisible: Boolean = false,
    val reason: NavigationFailureReason? = null,
    val anchorOffset: IntOffset = IntOffset.Zero
) {
    companion object {
        val Hidden = NavigationFeedbackState()
    }
}

/**
 * Lightweight popup that shows feedback when navigation fails.
 *
 * Displays a brief message explaining why navigation couldn't be performed.
 * Auto-dismisses after 2 seconds or when user presses Escape/clicks outside.
 *
 * @param reason The reason navigation failed
 * @param anchorOffset Screen position to anchor the popup (x, y)
 * @param onDismiss Callback when popup should be dismissed
 * @param theme Editor theme for styling
 */
@Composable
fun NavigationFeedbackPopup(
    reason: NavigationFailureReason,
    anchorOffset: IntOffset,
    onDismiss: () -> Unit,
    theme: EditorTheme = LocalEditorTheme.current
) {
    val colors = theme.colors

    val message = when (reason) {
        NavigationFailureReason.NOT_FOUND -> "No definition found"
        NavigationFailureReason.UNAVAILABLE -> "Navigation unavailable"
    }

    Popup(
        offset = anchorOffset,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = false)
    ) {
        Surface(
            modifier = Modifier
                .shadow(4.dp, RoundedCornerShape(6.dp))
                .clip(RoundedCornerShape(6.dp))
                .onKeyEvent { event ->
                    if (event.key == Key.Escape) {
                        onDismiss()
                        true
                    } else {
                        false
                    }
                },
            color = colors.background,
            elevation = 4.dp
        ) {
            Text(
                text = message,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                color = colors.text.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }

    // Auto-dismiss after 2 seconds
    LaunchedEffect(Unit) {
        delay(2000)
        onDismiss()
    }
}
