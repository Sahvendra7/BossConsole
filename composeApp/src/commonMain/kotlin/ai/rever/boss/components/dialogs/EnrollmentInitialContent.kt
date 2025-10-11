package ai.rever.boss.components.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import BossDarkAccent
import BossDarkTextPrimary
import BossDarkTextSecondary

@Composable
fun EnrollmentInitialContent(
    onStartEnrollment: () -> Unit,
    onDismiss: () -> Unit,
    enrollmentInProgress: Boolean,
    isMandatory: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.Default.Fingerprint,
            contentDescription = "Passkey",
            tint = BossDarkAccent,
            modifier = Modifier.size(64.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Set Up Passkey Authentication",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = BossDarkTextPrimary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Use your device's built-in authentication (Touch ID, Face ID, Windows Hello) for secure two-factor authentication.",
            fontSize = 14.sp,
            color = BossDarkTextSecondary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onStartEnrollment,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            shape = RoundedCornerShape(6.dp),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = BossDarkAccent,
                contentColor = Color.White
            ),
            enabled = !enrollmentInProgress
        ) {
            if (enrollmentInProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = if (enrollmentInProgress) "Setting Up..." else "Set Up Passkey",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
        
        if (!isMandatory) {
            Spacer(modifier = Modifier.height(12.dp))
            
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Skip for now",
                    fontSize = 14.sp,
                    color = BossDarkTextSecondary
                )
            }
        }
    }
}