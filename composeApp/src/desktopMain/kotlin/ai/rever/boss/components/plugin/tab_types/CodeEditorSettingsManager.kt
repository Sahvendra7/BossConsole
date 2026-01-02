package ai.rever.boss.components.plugin.tab_types

import ai.rever.boss.font.FontManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

// Global settings object
object CodeEditorSettings {
    var fontFamily: String = FontManager.BUNDLED_JETBRAINS_MONO
    var fontSize: Int = 14
    var theme: String = "Dark"
    var useLigatures: Boolean = true
    var useAntialiasing: Boolean = true
    var lineSpacing: Float = 1.2f

    /**
     * Whether to use the native BossEditor (Compose Canvas) instead of RSyntaxTextArea (Swing).
     * When true (default), uses BossEditorIntegration for code editing.
     * When false, uses RSyntaxEditorWithGutter for Swing compatibility.
     */
    var useNativeEditor: Boolean = true

    /** Whether to show the minimap (code overview) */
    var showMinimap: Boolean = false

    /** Minimap width in pixels */
    var minimapWidth: Int = 80

    // Theme colors
    fun getBackgroundColor(): Color = when (theme) {
        "Light" -> Color(0xFF_FFFFFF)
        "Dracula" -> Color(0xFF_282A36)
        "Monokai" -> Color(0xFF_272822)
        "Solarized Dark" -> Color(0xFF_002B36)
        "Solarized Light" -> Color(0xFF_FDF6E3)
        else -> Color(0xFF_1E1E1E) // Dark theme
    }
    
    fun getTextColor(): Color = when (theme) {
        "Light" -> Color(0xFF_000000)
        "Dracula" -> Color(0xFF_F8F8F2)
        "Monokai" -> Color(0xFF_F8F8F2)
        "Solarized Dark" -> Color(0xFF_839496)
        "Solarized Light" -> Color(0xFF_657B83)
        else -> Color(0xFF_D4D4D4) // Dark theme
    }
    
    fun getLineNumberColor(): Color = when (theme) {
        "Light" -> Color(0xFF_6E7681)
        "Dracula" -> Color(0xFF_6272A4)
        "Monokai" -> Color(0xFF_75715E)
        "Solarized Dark" -> Color(0xFF_586E75)
        "Solarized Light" -> Color(0xFF_93A1A1)
        else -> Color(0xFF_858585) // Dark theme
    }
    
    fun getLineNumberBgColor(): Color = when (theme) {
        "Light" -> Color(0xFF_F6F8FA)
        "Dracula" -> Color(0xFF_21222C)
        "Monokai" -> Color(0xFF_1E1F1C)
        "Solarized Dark" -> Color(0xFF_073642)
        "Solarized Light" -> Color(0xFF_EEE8D5)
        else -> Color(0xFF_2D2D30) // Dark theme
    }

    fun getKeywordColor(): Color = when (theme) {
        "Light" -> Color(0xFF_CF222E)
        "Dracula" -> Color(0xFF_FF79C6)
        "Monokai" -> Color(0xFF_F92672)
        "Solarized Dark" -> Color(0xFF_268BD2)
        "Solarized Light" -> Color(0xFF_268BD2)
        else -> Color(0xFF_569CD6) // Dark theme
    }

    fun getCommentColor(): Color = when (theme) {
        "Light" -> Color(0xFF_6E7781)
        "Dracula" -> Color(0xFF_6272A4)
        "Monokai" -> Color(0xFF_75715E)
        "Solarized Dark" -> Color(0xFF_586E75)
        "Solarized Light" -> Color(0xFF_93A1A1)
        else -> Color(0xFF_6A9955) // Dark theme
    }
    
    /**
     * Get the Compose FontFamily for UI preview.
     * Uses FontManager for proper font loading including bundled fonts.
     */
    fun getFontFamily(): FontFamily = FontManager.loadComposeFontFamily(fontFamily)

    /**
     * Get a list of all available monospace fonts for the settings UI.
     */
    fun getAvailableFonts(): List<String> = FontManager.getAvailableMonospaceFonts()

    /**
     * Get fonts organized by category (Bundled, Fixed Pitch, Variable Pitch).
     */
    fun getCategorizedFonts(): Map<String, List<String>> = FontManager.getCategorizedFonts()
}

@Serializable
data class CodeEditorSettingsData(
    val fontFamily: String = FontManager.BUNDLED_JETBRAINS_MONO,
    val fontSize: Int = 14,
    val theme: String = "Dark",
    val useLigatures: Boolean = true,
    val useAntialiasing: Boolean = true,
    val lineSpacing: Float = 1.2f,
    val useNativeEditor: Boolean = true,
    val showMinimap: Boolean = false,
    val minimapWidth: Int = 80
)

object CodeEditorSettingsManager {
    private val settingsFile = File(System.getProperty("user.home"), ".boss/code-editor-settings.json")
    private val json = Json { 
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    init {
        // Ensure directory exists
        settingsFile.parentFile?.mkdirs()
        
        // Load settings on initialization
        loadSettingsSync()
    }
    
    private fun loadSettingsSync() {
        try {
            if (settingsFile.exists()) {
                val content = settingsFile.readText()
                val settings = json.decodeFromString<CodeEditorSettingsData>(content)

                // Apply loaded settings
                CodeEditorSettings.fontFamily = settings.fontFamily
                CodeEditorSettings.fontSize = settings.fontSize
                CodeEditorSettings.theme = settings.theme
                CodeEditorSettings.useLigatures = settings.useLigatures
                CodeEditorSettings.useAntialiasing = settings.useAntialiasing
                CodeEditorSettings.lineSpacing = settings.lineSpacing
                CodeEditorSettings.useNativeEditor = settings.useNativeEditor
                CodeEditorSettings.showMinimap = settings.showMinimap
                CodeEditorSettings.minimapWidth = settings.minimapWidth
            }
        } catch (e: Exception) {
            println("Failed to load code editor settings: ${e.message}")
        }
    }
    
    suspend fun saveSettings() = withContext(Dispatchers.IO) {
        try {
            val settings = CodeEditorSettingsData(
                fontFamily = CodeEditorSettings.fontFamily,
                fontSize = CodeEditorSettings.fontSize,
                theme = CodeEditorSettings.theme,
                useLigatures = CodeEditorSettings.useLigatures,
                useAntialiasing = CodeEditorSettings.useAntialiasing,
                lineSpacing = CodeEditorSettings.lineSpacing,
                useNativeEditor = CodeEditorSettings.useNativeEditor,
                showMinimap = CodeEditorSettings.showMinimap,
                minimapWidth = CodeEditorSettings.minimapWidth
            )

            val content = json.encodeToString(settings)
            settingsFile.writeText(content)
        } catch (e: Exception) {
            println("Failed to save code editor settings: ${e.message}")
        }
    }
}
