package ai.rever.boss.utils

actual object SystemUtils {
    actual fun getUserHome(): String = System.getProperty("user.home")
    
    actual fun getCurrentDirectory(): String = System.getProperty("user.dir") ?: getUserHome()
    
    actual fun getDefaultProjectPath(): String {
        // For desktop, use current directory if available, otherwise user home
        return getCurrentDirectory()
    }
}