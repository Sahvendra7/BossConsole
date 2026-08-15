package ai.rever.boss.utils

import ai.rever.boss.project.DefaultWorkingDirectory

actual object SystemUtils {
    private val osName: String = System.getProperty("os.name").lowercase()

    actual fun getUserHome(): String = System.getProperty("user.home")

    actual fun getCurrentDirectory(): String = System.getProperty("user.dir") ?: getUserHome()

    actual fun getDefaultProjectPath(): String {
        // ~/BossProjects, the directory BOSS creates projects in. Not the process working
        // directory: a packaged .app is launched with "/" as its cwd, which is no more useful
        // as a project path than the home directory this used to fall back to.
        return DefaultWorkingDirectory.path()
    }

    actual val isMacOS: Boolean = osName.contains("mac")

    actual val isWindows: Boolean = osName.contains("windows")

    actual val isLinux: Boolean = osName.contains("linux") || osName.contains("nix") || osName.contains("nux")
}
