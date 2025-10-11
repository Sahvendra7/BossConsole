package ai.rever.boss.components.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
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
fun EnrollmentSuccessContent(
    onEnrolled: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.Outlined.CheckCircle,
            contentDescription = "Success",
            tint = BossDarkAccent,
            modifier = Modifier.size(64.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Passkey Setup Complete!",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = BossDarkTextPrimary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Your device's authentication (Touch ID, Face ID, or Windows Hello) is now set up for two-factor authentication.",
            fontSize = 14.sp,
            color = BossDarkTextSecondary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onEnrolled,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            shape = RoundedCornerShape(6.dp),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = BossDarkAccent,
                contentColor = Color.White
            )
        ) {
            Text(
                text = "Continue to BOSS",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}