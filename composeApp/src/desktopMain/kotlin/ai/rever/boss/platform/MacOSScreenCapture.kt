package ai.rever.boss.platform

import com.sun.jna.Library
import com.sun.jna.Native

/**
 * JNA bindings for macOS CoreGraphics screen capture permission APIs.
 * These APIs allow checking and requesting screen recording permission
 * from the main application process.
 */
private interface CoreGraphics : Library {
    companion object {
        val INSTANCE: CoreGraphics? = try {
            Native.load("CoreGraphics", CoreGraphics::class.java)
        } catch (e: Exception) {
            println("CoreGraphics not available: ${e.message}")
            null
        }
    }

    /**
     * Returns true if the app has screen capture access, false otherwise.
     * This does NOT trigger a permission prompt.
     */
    fun CGPreflightScreenCaptureAccess(): Boolean

    /**
     * Requests screen capture access. If access hasn't been determined yet,
     * this will trigger the system permission dialog.
     * Returns true if access is granted, false otherwise.
     */
    fun CGRequestScreenCaptureAccess(): Boolean
}

/**
 * Helper object for macOS screen capture permissions.
 * On non-macOS platforms, these methods return true (permission assumed granted).
 */
object MacOSScreenCapture {
    private val isMacOS: Boolean = System.getProperty("os.name")?.lowercase()?.contains("mac") == true

    /**
     * Check if screen recording permission is granted.
     * @return true if permission is granted or not on macOS
     */
    fun hasPermission(): Boolean {
        if (!isMacOS) return true

        return try {
            CoreGraphics.INSTANCE?.CGPreflightScreenCaptureAccess() ?: true
        } catch (e: Exception) {
            println("Error checking screen capture permission: ${e.message}")
            true // Assume granted on error
        }
    }

    /**
     * Request screen recording permission.
     * On macOS, this will trigger the system permission dialog if permission
     * hasn't been determined yet.
     * @return true if permission is granted or not on macOS
     */
    fun requestPermission(): Boolean {
        if (!isMacOS) return true

        return try {
            val result = CoreGraphics.INSTANCE?.CGRequestScreenCaptureAccess() ?: true
            println("Screen capture permission request result: $result")
            result
        } catch (e: Exception) {
            println("Error requesting screen capture permission: ${e.message}")
            true // Assume granted on error
        }
    }
}
