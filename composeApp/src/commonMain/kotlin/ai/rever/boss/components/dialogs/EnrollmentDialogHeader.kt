package ai.rever.boss.components.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import BossDarkTextPrimary
import BossDarkTextSecondary

@Composable
fun EnrollmentDialogHeader(
    onDismiss: () -> Unit,
    isMandatory: Boolean,
    enrollmentInProgress: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (!isMandatory && !enrollmentInProgress) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = BossDarkTextSecondary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Two-Factor Authentication",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = BossDarkTextPrimary
            )
            Text(
                text = "Secure your account with passkey authentication",
                fontSize = 13.sp,
                color = BossDarkTextSecondary.copy(alpha = 0.8f)
            )
        }
    }
}