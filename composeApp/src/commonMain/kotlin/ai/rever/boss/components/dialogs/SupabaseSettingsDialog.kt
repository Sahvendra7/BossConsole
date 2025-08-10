package ai.rever.boss.components.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ai.rever.boss.services.supabase.SupabaseSettingsManager
import kotlinx.coroutines.launch

@Composable
fun SupabaseSettingsDialog(
    onDismiss: () -> Unit,
    onConfigured: () -> Unit = {}
) {
    var supabaseUrl by remember { mutableStateOf("") }
    var supabaseAnonKey by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val scope = rememberCoroutineScope()
    
    // Load existing settings
    LaunchedEffect(Unit) {
        val settings = SupabaseSettingsManager.loadSettings()
        supabaseUrl = settings.supabaseUrl
        supabaseAnonKey = settings.supabaseAnonKey
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Configure Supabase",
                    style = MaterialTheme.typography.h6
                )
                
                Text(
                    text = "Enter your Supabase project credentials to enable cloud features.",
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                )
                
                OutlinedTextField(
                    value = supabaseUrl,
                    onValueChange = { supabaseUrl = it },
                    label = { Text("Supabase URL") },
                    placeholder = { Text("https://your-project.supabase.co") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isLoading
                )
                
                OutlinedTextField(
                    value = supabaseAnonKey,
                    onValueChange = { supabaseAnonKey = it },
                    label = { Text("Anonymous Key") },
                    placeholder = { Text("Your anonymous/public key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !isLoading
                )
                
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colors.error,
                        style = MaterialTheme.typography.caption
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isLoading
                    ) {
                        Text("Cancel")
                    }
                    
                    Button(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                errorMessage = null
                                
                                val result = SupabaseSettingsManager.configureAndInitialize(
                                    url = supabaseUrl.trim(),
                                    anonKey = supabaseAnonKey.trim()
                                )
                                
                                result.fold(
                                    onSuccess = {
                                        onConfigured()
                                        onDismiss()
                                    },
                                    onFailure = { error ->
                                        errorMessage = error.message ?: "Failed to configure Supabase"
                                    }
                                )
                                
                                isLoading = false
                            }
                        },
                        enabled = !isLoading && supabaseUrl.isNotBlank() && supabaseAnonKey.isNotBlank()
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colors.onPrimary
                            )
                        } else {
                            Text("Save & Connect")
                        }
                    }
                }
                
                Divider()
                
                Text(
                    text = "Note: Your credentials are stored locally in ~/.boss/supabase_settings.json",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}