package ai.rever.boss.utils

expect object SystemUtils {
    fun getUserHome(): String
    fun getCurrentDirectory(): String
    fun getDefaultProjectPath(): String
}