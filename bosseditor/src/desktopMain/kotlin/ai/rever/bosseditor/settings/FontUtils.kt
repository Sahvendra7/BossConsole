package ai.rever.bosseditor.settings

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Typeface
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle

/**
 * Special value indicating the default editor font should be used.
 */
const val DEFAULT_EDITOR_FONT_NAME = "JetBrains Mono (Default)"

/** Section name for recommended fonts */
const val FONT_SECTION_RECOMMENDED = "Recommended"
/** Section name for fixed pitch (monospace) fonts */
const val FONT_SECTION_FIXED_PITCH = "Fixed Pitch"
/** Section name for variable pitch (proportional) fonts */
const val FONT_SECTION_VARIABLE_PITCH = "Variable Pitch"

/**
 * List of recommended programming fonts (JetBrains-style).
 * These fonts are specifically designed for code editing.
 */
private val RECOMMENDED_FONTS = listOf(
    "JetBrains Mono",
    "Fira Code",
    "Source Code Pro",
    "Cascadia Code",
    "Cascadia Mono",
    "SF Mono",
    "Monaco",
    "Menlo",
    "Consolas",
    "Inconsolata",
    "Ubuntu Mono",
    "Roboto Mono",
    "Hack",
    "IBM Plex Mono",
    "Anonymous Pro",
    "Droid Sans Mono",
    "DejaVu Sans Mono",
    "Liberation Mono",
    "Courier New"
)

/**
 * Cache for categorized fonts to avoid repeated system font scanning.
 */
private var cachedFonts: Map<String, List<String>>? = null

/**
 * Get fonts organized by category (JetBrains IDE-style).
 * Returns a map with sections: "Recommended", "Fixed Pitch", "Variable Pitch"
 *
 * The Recommended section contains well-known programming fonts that are installed.
 * Fixed Pitch contains all monospace fonts.
 * Variable Pitch contains proportional fonts (for those who prefer them).
 */
fun getEditorCategorizedFonts(): Map<String, List<String>> {
    cachedFonts?.let { return it }

    val fontMgr = FontMgr.default
    val familyCount = fontMgr.familiesCount

    val allFamilies = (0 until familyCount)
        .map { fontMgr.getFamilyName(it) }
        .filter { it.isNotEmpty() }
        .toSet()

    val fixedPitch = mutableListOf<String>()
    val variablePitch = mutableListOf<String>()
    val recommended = mutableListOf<String>()

    // First, identify which recommended fonts are available
    for (fontName in RECOMMENDED_FONTS) {
        if (allFamilies.contains(fontName)) {
            recommended.add(fontName)
        }
    }

    // Then categorize all fonts
    for (familyName in allFamilies) {
        // Skip if already in recommended
        if (recommended.contains(familyName)) continue

        try {
            val typeface = fontMgr.matchFamilyStyle(familyName, FontStyle.NORMAL)
            if (typeface != null) {
                // Check if font is monospace by comparing glyph widths
                val font = org.jetbrains.skia.Font(typeface, 12f)
                val widthW = font.measureTextWidth("W")
                val widthI = font.measureTextWidth("i")
                // Allow small tolerance for floating point comparison
                if (kotlin.math.abs(widthW - widthI) < 0.1f) {
                    fixedPitch.add(familyName)
                } else {
                    variablePitch.add(familyName)
                }
            }
        } catch (e: Exception) {
            // Skip fonts that fail to load
        }
    }

    val result = linkedMapOf(
        FONT_SECTION_RECOMMENDED to (listOf(DEFAULT_EDITOR_FONT_NAME) + recommended),
        FONT_SECTION_FIXED_PITCH to fixedPitch.sorted(),
        FONT_SECTION_VARIABLE_PITCH to variablePitch.sorted()
    )

    cachedFonts = result
    return result
}

/**
 * Get list of available monospace fonts on the system.
 * Includes recommended fonts first.
 */
fun getAvailableEditorFonts(): List<String> {
    val categorized = getEditorCategorizedFonts()
    return categorized[FONT_SECTION_RECOMMENDED]!! +
            categorized[FONT_SECTION_FIXED_PITCH]!! +
            categorized[FONT_SECTION_VARIABLE_PITCH]!!
}

/**
 * Load editor font by name.
 * @param fontName Font name from system fonts, or null/empty/DEFAULT_EDITOR_FONT_NAME for default.
 * @return FontFamily for editor rendering
 */
fun loadEditorFont(fontName: String? = null): FontFamily {
    // Use default monospace if no name specified or if it's the default marker
    if (fontName.isNullOrEmpty() || fontName == DEFAULT_EDITOR_FONT_NAME) {
        // Try to load JetBrains Mono first, then fall back to system monospace
        return tryLoadSystemFont("JetBrains Mono") ?: FontFamily.Monospace
    }

    return tryLoadSystemFont(fontName) ?: FontFamily.Monospace
}

/**
 * Try to load a system font by family name.
 * @return FontFamily if successful, null otherwise
 */
private fun tryLoadSystemFont(fontName: String): FontFamily? {
    return try {
        val skiaTypeface = FontMgr.default.matchFamilyStyle(fontName, FontStyle.NORMAL)
        if (skiaTypeface != null) {
            FontFamily(Typeface(skiaTypeface))
        } else {
            null
        }
    } catch (e: Exception) {
        System.err.println("Failed to load font '$fontName': ${e.message}")
        null
    }
}

/**
 * Check if a font is installed on the system.
 */
fun isFontInstalled(fontName: String): Boolean {
    if (fontName == DEFAULT_EDITOR_FONT_NAME) return true
    return try {
        FontMgr.default.matchFamilyStyle(fontName, FontStyle.NORMAL) != null
    } catch (e: Exception) {
        false
    }
}
