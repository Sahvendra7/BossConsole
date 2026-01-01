package ai.rever.bosseditor.rendering

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * LRU cache for text measurements.
 *
 * TextMeasurer.measure() is expensive - it performs font lookup, glyph shaping,
 * and layout calculations. For a code editor rendering many similar characters,
 * caching measurements provides significant performance gains.
 *
 * This pattern is from BossTerm (issue #147 optimization).
 *
 * Thread-safe: Uses synchronized access for multi-window scenarios.
 */
object TextMeasurementCache {

    private const val MAX_CACHE_SIZE = 256

    /**
     * Cache key including all factors that affect measurement.
     */
    data class MeasurementKey(
        val text: String,
        val fontFamilyHash: Int,
        val fontSize: Float,
        val isBold: Boolean,
        val isItalic: Boolean
    )

    /**
     * Cached measurement result.
     */
    data class CachedMeasurement(
        val width: Float,
        val height: Float,
        val firstBaseline: Float
    )

    // LRU cache using LinkedHashMap with access-order
    private val cache = object : LinkedHashMap<MeasurementKey, CachedMeasurement>(
        MAX_CACHE_SIZE, 0.75f, true
    ) {
        override fun removeEldestEntry(eldest: Map.Entry<MeasurementKey, CachedMeasurement>): Boolean {
            return size > MAX_CACHE_SIZE
        }
    }

    private val lock = Any()

    /**
     * Gets a cached measurement or computes and caches it.
     */
    fun getMeasurement(
        textMeasurer: TextMeasurer,
        text: String,
        fontFamily: FontFamily,
        fontSize: Float,
        isBold: Boolean = false,
        isItalic: Boolean = false
    ): CachedMeasurement {
        val key = MeasurementKey(
            text = text,
            fontFamilyHash = fontFamily.hashCode(),
            fontSize = fontSize,
            isBold = isBold,
            isItalic = isItalic
        )

        synchronized(lock) {
            return cache.getOrPut(key) {
                val style = TextStyle(
                    fontFamily = fontFamily,
                    fontSize = androidx.compose.ui.unit.TextUnit(fontSize, androidx.compose.ui.unit.TextUnitType.Sp),
                    fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal
                )
                val result = textMeasurer.measure(text, style)
                CachedMeasurement(
                    width = result.size.width.toFloat(),
                    height = result.size.height.toFloat(),
                    firstBaseline = result.firstBaseline
                )
            }
        }
    }

    /**
     * Gets measurement for a single character.
     * Optimized path for the common case.
     */
    fun getCharMeasurement(
        textMeasurer: TextMeasurer,
        char: Char,
        fontFamily: FontFamily,
        fontSize: Float,
        isBold: Boolean = false,
        isItalic: Boolean = false
    ): CachedMeasurement {
        return getMeasurement(textMeasurer, char.toString(), fontFamily, fontSize, isBold, isItalic)
    }

    /**
     * Clears the cache.
     * Should be called when font size or font family changes.
     */
    fun invalidate() {
        synchronized(lock) {
            cache.clear()
        }
    }

    /**
     * Returns the current cache size (for debugging).
     */
    fun size(): Int {
        synchronized(lock) {
            return cache.size
        }
    }
}

/**
 * Extension function for convenient measurement with TextStyle.
 */
fun TextMeasurer.measureCached(
    text: String,
    fontFamily: FontFamily,
    fontSize: Float,
    isBold: Boolean = false,
    isItalic: Boolean = false
): TextMeasurementCache.CachedMeasurement {
    return TextMeasurementCache.getMeasurement(
        textMeasurer = this,
        text = text,
        fontFamily = fontFamily,
        fontSize = fontSize,
        isBold = isBold,
        isItalic = isItalic
    )
}
