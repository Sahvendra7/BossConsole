package ai.rever.boss.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import java.awt.GraphicsEnvironment
import java.awt.Toolkit

/**
 * Display utilities for adaptive window sizing based on screen dimensions.
 *
 * This utility uses java.awt.GraphicsEnvironment to accurately detect screen
 * dimensions with platform-specific handling for Windows DPI scaling and Mac Retina displays.
 *
 * Window sizes are calculated as percentages of screen dimensions with sensible
 * min/max constraints to prevent unusable sizes on extreme displays.
 */
object DisplayUtils {

    private val isWindows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
    private val isMac = System.getProperty("os.name").startsWith("Mac", ignoreCase = true)

    /**
     * Get the display scaling factor.
     *
     * On Windows: DPI-based (96 DPI = 1.0, 120 DPI = 1.25, 144 DPI = 1.5)
     * On Mac: Retina scaling is already handled by GraphicsEnvironment
     *
     * @return Scaling factor (e.g., 1.0, 1.25, 1.5, 2.0)
     */
    private fun getDisplayScalingFactor(): Float {
        return try {
            if (isWindows) {
                val toolkit = Toolkit.getDefaultToolkit()
                val screenResolution = toolkit.screenResolution // DPI
                val scalingFactor = screenResolution / 96f // 96 DPI = 100% scaling
                println("DisplayUtils: Windows DPI: $screenResolution, scaling factor: $scalingFactor")
                scalingFactor
            } else {
                1.0f // Mac Retina is already handled by GraphicsEnvironment logical pixels
            }
        } catch (e: Exception) {
            println("DisplayUtils: Error detecting scaling factor: ${e.message}")
            1.0f
        }
    }

    /**
     * Get the primary screen dimensions in pixels.
     *
     * Uses GraphicsEnvironment to detect physical screen dimensions.
     * On Mac: Returns logical pixels (Retina-scaled)
     * On Windows: Returns physical pixels with DPI scaling
     *
     * @return Pair of (width, height) in pixels
     */
    fun getPrimaryScreenDimensions(): Pair<Int, Int> {
        return try {
            val graphicsEnvironment = GraphicsEnvironment.getLocalGraphicsEnvironment()
            val defaultScreenDevice = graphicsEnvironment.defaultScreenDevice
            val displayMode = defaultScreenDevice.displayMode

            var width = displayMode.width
            var height = displayMode.height

            // On Windows with display scaling, divide by scaling factor to get logical pixels
            if (isWindows) {
                val scalingFactor = getDisplayScalingFactor()
                if (scalingFactor > 1.0f) {
                    width = (width / scalingFactor).toInt()
                    height = (height / scalingFactor).toInt()
                    println("DisplayUtils: Adjusted for Windows scaling - Physical: ${displayMode.width}x${displayMode.height}, Logical: ${width}x${height}")
                }
            }

            println("DisplayUtils: Detected screen dimensions: ${width}x${height}")
            Pair(width, height)
        } catch (e: Exception) {
            println("DisplayUtils: Error detecting screen dimensions, using fallback: ${e.message}")
            // Fallback to common laptop resolution
            Pair(1920, 1080)
        }
    }

    /**
     * Calculate adaptive size for main application window.
     *
     * Uses 60-65% of screen dimensions with constraints:
     * - Min: 900x600 (minimum usable size)
     * - Max: 1400x900 (prevent excessive size, leaves room for taskbar and other windows)
     *
     * Height is reduced by 100px to account for taskbar/menu bars
     *
     * @return DpSize for main window
     */
    @Composable
    fun calculateMainWindowSize(): DpSize {
        return calculateAdaptiveSize(
            widthPercentage = 0.60f,  // 60% leaves more desktop space
            heightPercentage = 0.60f,  // 60% of height
            minWidth = 900,
            minHeight = 600,
            maxWidth = 1400,
            maxHeight = 900,
            taskbarOffset = 100,  // Account for Windows taskbar
            windowType = "Main"
        )
    }

    /**
     * Calculate adaptive size for settings window.
     *
     * Uses 55% of screen dimensions with constraints:
     * - Min: 700x550 (minimum for settings content)
     * - Max: 1200x800 (prevent excessive size, optimal for settings panels)
     *
     * @return DpSize for settings window
     */
    @Composable
    fun calculateSettingsWindowSize(): DpSize {
        return calculateAdaptiveSize(
            widthPercentage = 0.55f,  // 55% for settings
            heightPercentage = 0.55f,
            minWidth = 700,
            minHeight = 550,
            maxWidth = 1200,
            maxHeight = 800,
            taskbarOffset = 100,
            windowType = "Settings"
        )
    }

    /**
     * Calculate adaptive size for authentication windows.
     *
     * Uses 35-40% of screen dimensions with constraints:
     * - Min: 500x450 (minimum for auth forms)
     * - Max: 900x700 (keep auth dialogs compact but usable on larger displays)
     *
     * @return DpSize for authentication window
     */
    @Composable
    fun calculateAuthWindowSize(): DpSize {
        return calculateAdaptiveSize(
            widthPercentage = 0.38f,  // 38% for auth dialogs
            heightPercentage = 0.38f,
            minWidth = 500,
            minHeight = 450,
            maxWidth = 900,
            maxHeight = 700,
            taskbarOffset = 100,
            windowType = "Auth"
        )
    }

    /**
     * Internal function to calculate adaptive window size.
     *
     * IMPORTANT: Mac Retina Display Handling
     * - GraphicsEnvironment returns LOGICAL pixels (already scaled by macOS 2x Retina)
     * - We treat these logical pixels as dp directly without density conversion
     * - Using toDp() would cause double scaling (e.g., 1088px → 544dp on Retina)
     * - Windows/Linux typically have density=1.0, so this approach works universally
     *
     * @param widthPercentage Percentage of screen width (0.0 to 1.0)
     * @param heightPercentage Percentage of screen height (0.0 to 1.0)
     * @param minWidth Minimum width in pixels
     * @param minHeight Minimum height in pixels
     * @param maxWidth Maximum width in pixels
     * @param maxHeight Maximum height in pixels
     * @param taskbarOffset Height offset to subtract for taskbar/menu bars (default 0)
     * @param windowType Type of window for logging
     * @return DpSize calculated based on screen dimensions and constraints
     */
    @Composable
    private fun calculateAdaptiveSize(
        widthPercentage: Float,
        heightPercentage: Float,
        minWidth: Int,
        minHeight: Int,
        maxWidth: Int,
        maxHeight: Int,
        taskbarOffset: Int = 0,
        windowType: String
    ): DpSize {
        // Memoize screen dimensions to avoid repeated GraphicsEnvironment queries
        val (screenWidth, screenHeight) = remember { getPrimaryScreenDimensions() }

        // Account for taskbar by reducing available screen height
        val availableHeight = screenHeight - taskbarOffset

        // Calculate target sizes as percentages
        val targetWidth = (screenWidth * widthPercentage).toInt()
        val targetHeight = (availableHeight * heightPercentage).toInt()

        // Apply min/max constraints
        val constrainedWidth = targetWidth.coerceIn(minWidth, maxWidth)
        val constrainedHeight = targetHeight.coerceIn(minHeight, maxHeight)

        // GraphicsEnvironment returns logical pixels (already scaled on Mac Retina),
        // so we treat them as dp directly without density conversion to avoid double scaling.
        val dpSize = DpSize(constrainedWidth.dp, constrainedHeight.dp)

        println(
            "DisplayUtils: Calculated $windowType window size: " +
            "${constrainedWidth}x${constrainedHeight}px = ${dpSize.width}x${dpSize.height} " +
            "(${(widthPercentage * 100).toInt()}% of ${screenWidth}x${availableHeight}, " +
            "taskbar offset: ${taskbarOffset}px)"
        )

        return dpSize
    }
}
