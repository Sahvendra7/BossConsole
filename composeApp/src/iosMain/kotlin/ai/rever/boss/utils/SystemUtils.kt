package ai.rever.boss.utils

import platform.Foundation.NSHomeDirectory

actual object SystemUtils {
    actual fun getUserHome(): String = NSHomeDirectory()
    
    actual fun getCurrentDirectory(): String = NSHomeDirectory()
    
    actual fun getDefaultProjectPath(): String = NSHomeDirectory()
}
