package ai.rever.boss.components.plugin.panels.right_top

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Desktop implementation of environment variable access
 * DMG apps don't inherit shell environment variables from .zshrc/.zprofile/.bashrc
 * This function implements multiple fallback mechanisms:
 * 1. System.getenv() - works in development when launched from terminal
 * 2. launchctl getenv - works for system-wide environment variables
 * 3. ~/.boss/env_vars file - manual fallback for DMG distributions
 */
actual fun getEnvironmentVariable(name: String): String? {
    // First try system environment variables (works in development)
    val envValue = System.getenv(name)
    if (!envValue.isNullOrBlank()) {
        println("DEBUG: Found $name from System.getenv()")
        return envValue
    }
    
    // Try launchctl getenv for system-wide environment variables (macOS)
    try {
        val process = ProcessBuilder("launchctl", "getenv", name).start()
        process.waitFor()
        if (process.exitValue() == 0) {
            val result = process.inputStream.bufferedReader().readText().trim()
            if (result.isNotBlank()) {
                println("DEBUG: Found $name from launchctl getenv")
                return result
            }
        }
    } catch (e: Exception) {
        // launchctl might not be available or might fail - this is normal
    }
    
    // Fallback to reading from ~/.boss/env_vars file for DMG distributions
    try {
        val envFile = File(System.getProperty("user.home"), ".boss/env_vars")
        if (envFile.exists()) {
            val envVars = envFile.readLines()
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .associate { line ->
                    val parts = line.split("=", limit = 2)
                    if (parts.size == 2) {
                        parts[0].trim() to parts[1].trim()
                    } else {
                        "" to ""
                    }
                }
            val result = envVars[name]
            if (!result.isNullOrBlank()) {
                println("DEBUG: Found $name from ~/.boss/env_vars file")
                return result
            }
        }
    } catch (e: Exception) {
        println("Warning: Could not read environment variables file: ${e.message}")
    }
    
    println("DEBUG: No $name found in any location (System.getenv, launchctl, or ~/.boss/env_vars)")
    return null
}

/**
 * Desktop implementation of LLM Settings Manager
 */
actual object LLMSettingsManager {
    private val settingsFile = File(System.getProperty("user.home"), ".boss/llm_settings.json")
    
    actual suspend fun loadSettings() {
        withContext(Dispatchers.IO) {
            try {
                if (settingsFile.exists()) {
                    val json = settingsFile.readText()
                    LLMSettings.loadFromJson(json)
                }
            } catch (e: Exception) {
                println("Error loading LLM settings: ${e.message}")
            }
        }
    }
    
    actual suspend fun saveSettings() {
        withContext(Dispatchers.IO) {
            try {
                settingsFile.parentFile?.mkdirs()
                settingsFile.writeText(LLMSettings.toJson())
                
                // Create env_vars template file if it doesn't exist
                createEnvVarsTemplateIfNeeded()
            } catch (e: Exception) {
                println("Error saving LLM settings: ${e.message}")
            }
        }
    }
    
    private fun createEnvVarsTemplateIfNeeded() {
        try {
            val envFile = File(System.getProperty("user.home"), ".boss/env_vars")
            if (!envFile.exists()) {
                envFile.parentFile?.mkdirs()
                val template = """
                    # Environment variables for BOSS LLM API Keys
                    # 
                    # IMPORTANT: DMG applications on macOS don't inherit shell environment variables
                    # from .zshrc, .zprofile, .bashrc, etc. This file provides a workaround.
                    #
                    # METHOD 1: Use this file (recommended for DMG distributions)
                    # Uncomment and set your API keys below:
                    
                    # ANTHROPIC_API_KEY=your-anthropic-api-key-here
                    # OPENAI_API_KEY=your-openai-api-key-here  
                    # TOGETHER_API_KEY=your-together-api-key-here
                    # CUSTOM_LLM_API_KEY=your-custom-llm-api-key-here
                    
                    # METHOD 2: Set system-wide environment variables (advanced)
                    # Run in terminal: launchctl setenv ANTHROPIC_API_KEY "your-key-here"
                    # Then restart BOSS app
                    #
                    # METHOD 3: Use the Settings UI in BOSS
                    # Settings → LLM Providers → Enter keys manually
                    
                    # Priority order: System.getenv() → launchctl getenv → this file → Settings UI
                """.trimIndent()
                
                envFile.writeText(template)
                println("Created environment variables template at: ${envFile.absolutePath}")
            }
        } catch (e: Exception) {
            println("Warning: Could not create env_vars template: ${e.message}")
        }
    }
}