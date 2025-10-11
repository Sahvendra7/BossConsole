package ai.rever.boss.components.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import BossDarkAccent
import BossDarkTextPrimary
import BossDarkTextSecondary

@Composable
fun EnrollmentProgressContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        CircularProgressIndicator(
            color = BossDarkAccent,
            modifier = Modifier.size(64.dp),
            strokeWidth = 4.dp
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Setting Up Passkey Authentication",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = BossDarkTextPrimary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Please follow the authentication prompts from your device (Touch ID, Face ID, Windows Hello, etc.)",
            fontSize = 14.sp,
            color = BossDarkTextSecondary,
            textAlign = TextAlign.Center
        )
    }
}