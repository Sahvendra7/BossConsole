package ai.rever.boss.utils

import java.awt.Component
import javax.swing.JComponent
import javax.swing.SwingUtilities

/**
 * macOS-specific gesture handler for trackpad pinch-to-zoom gestures.
 *
 * Uses com.apple.eawt.event.GestureUtilities which is available on macOS JVMs.
 * Requires JVM args: --add-opens java.desktop/com.apple.eawt.event=ALL-UNNAMED
 *
 * This is the standard way to handle trackpad magnification gestures on macOS
 * since they are NOT delivered as Ctrl+Wheel events to Java applications.
 */
object MacOSGestureHandler {

    private var isAvailable: Boolean? = null

    // Accumulator for smooth zooming (like Safari)
    // Uses @Volatile + synchronized for thread-safe access from gesture callbacks
    @Volatile
    private var magnificationAccumulator = 0.0
    private val accumulatorLock = Any()

    // Threshold for triggering zoom - accumulate this much gesture magnitude before firing
    // Value 0.15 chosen empirically to match Safari's feel (not too sensitive, not too sluggish)
    private const val ZOOM_THRESHOLD = 0.15

    /**
     * Check if macOS gesture APIs are available
     */
    fun isSupported(): Boolean {
        if (isAvailable != null) return isAvailable!!

        val os = System.getProperty("os.name").lowercase()
        isAvailable = try {
            if (!os.contains("mac")) {
                false
            } else {
                Class.forName("com.apple.eawt.event.GestureUtilities")
                true
            }
        } catch (e: Exception) {
            false
        }

        return isAvailable!!
    }

    /**
     * Add a magnification (pinch) gesture listener to a component.
     *
     * @param component The Swing component to listen on
     * @param onZoomIn Called when user pinches out (zoom in)
     * @param onZoomOut Called when user pinches in (zoom out)
     */
    fun addMagnificationListener(
        component: Component,
        onZoomIn: () -> Unit,
        onZoomOut: () -> Unit
    ): Boolean {
        if (!isSupported()) return false

        return try {
            val gestureUtilitiesClass = Class.forName("com.apple.eawt.event.GestureUtilities")
            val magnificationListenerClass = Class.forName("com.apple.eawt.event.MagnificationListener")
            val magnificationEventClass = Class.forName("com.apple.eawt.event.MagnificationEvent")

            // Create a dynamic proxy for MagnificationListener
            val listener = java.lang.reflect.Proxy.newProxyInstance(
                magnificationListenerClass.classLoader,
                arrayOf(magnificationListenerClass)
            ) { _, method, args ->
                if (method.name == "magnify" && args != null && args.isNotEmpty()) {
                    val event = args[0]
                    val getMagnification = magnificationEventClass.getMethod("getMagnification")
                    val magnification = getMagnification.invoke(event) as Double

                    // Accumulate magnification for smoother zooming (like Safari)
                    // Use synchronized to prevent race condition between gesture thread and UI thread
                    var shouldZoomIn = false
                    var shouldZoomOut = false

                    synchronized(accumulatorLock) {
                        magnificationAccumulator += magnification

                        // Only trigger zoom when accumulated enough
                        if (magnificationAccumulator >= ZOOM_THRESHOLD) {
                            shouldZoomIn = true
                            magnificationAccumulator = 0.0
                        } else if (magnificationAccumulator <= -ZOOM_THRESHOLD) {
                            shouldZoomOut = true
                            magnificationAccumulator = 0.0
                        }
                    }

                    // Fire callbacks outside synchronized block to avoid holding lock during UI work
                    SwingUtilities.invokeLater {
                        if (shouldZoomIn) onZoomIn()
                        else if (shouldZoomOut) onZoomOut()
                    }
                } else if (method.name == "toString") {
                    return@newProxyInstance "MacOSGestureHandler.MagnificationListener"
                } else if (method.name == "hashCode") {
                    return@newProxyInstance System.identityHashCode(this)
                } else if (method.name == "equals") {
                    return@newProxyInstance false
                }
                null
            }

            // Call GestureUtilities.addGestureListenerTo(component, listener)
            val addMethod = gestureUtilitiesClass.getMethod(
                "addGestureListenerTo",
                JComponent::class.java,
                Class.forName("com.apple.eawt.event.GestureListener")
            )

            if (component is JComponent) {
                addMethod.invoke(null, component, listener)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Reset the accumulator (call when gesture ends or focus changes)
     */
    fun resetAccumulator() {
        synchronized(accumulatorLock) {
            magnificationAccumulator = 0.0
        }
    }
}
