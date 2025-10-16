package ai.rever.boss.utils

import ai.rever.boss.components.plugin.tab_types.fluck.FluckEngine
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.system.exitProcess

object ApplicationRestarter {
    
    private var isRestarting = false
    
    @OptIn(DelicateCoroutinesApi::class)
    fun restartApplication() {
        if (isRestarting) return // Prevent multiple restart attempts
        isRestarting = true
        
        // Launch restart in a coroutine
        GlobalScope.launch {
            try {
                // Perform graceful shutdown
                performGracefulShutdown()
                
                // Get the Java command
                val javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java"
                
                // Get the current classpath
                val classpath = System.getProperty("java.class.path")
                
                // Get the main class or jar
                val currentJar = try {
                    File(ApplicationRestarter::class.java.protectionDomain.codeSource.location.toURI())
                } catch (e: Exception) {
                    null
                }
                
                // Build restart command
                val command = when {
                    currentJar?.name?.endsWith(".jar") == true -> {
                        // Running from JAR
                        listOf(javaBin, "-jar", currentJar.path)
                    }
                    currentJar?.name == "classes" || currentJar?.path?.contains("build") == true -> {
                        // Running from Gradle/IDE
                        val gradlew = if (System.getProperty("os.name").lowercase().contains("windows")) {
                            "gradlew.bat"
                        } else {
                            "./gradlew"
                        }
                        // Use gradle to run the app
                        listOf(gradlew, "desktopRun", "-DmainClass=ai.rever.boss.MainKt", "--quiet")
                    }
                    else -> {
                        // Fallback: try direct Java execution
                        val mainClass = "ai.rever.boss.MainKt"
                        listOf(javaBin, "-cp", classpath, mainClass)
                    }
                }
                
                println("Restarting application with command: ${command.joinToString(" ")}")
                
                // Start new instance
                val processBuilder = ProcessBuilder(command)
                processBuilder.directory(File(System.getProperty("user.dir")))
                processBuilder.start()
                
                // Give the new instance time to start
                delay(1000)
                
                // Exit current instance
                exitProcess(0)
                
            } catch (e: Exception) {
                println("Failed to restart application: ${e.message}")
                isRestarting = false
                
                // Show error and exit anyway
                exitProcess(1)
            }
        }
    }
    
    private suspend fun performGracefulShutdown() {
        try {
            // Close browser engine if it exists
            val engine = FluckEngine.currentEngine
            if (engine != null && !engine.isClosed) {
                println("Closing browser engine...")
                engine.close()
                delay(500) // Give time for engine to close
            }
        } catch (e: Exception) {
            println("Error during graceful shutdown: ${e.message}")
        }
    }
    
    fun scheduleRestart(delayMillis: Long = 1000) {
        GlobalScope.launch {
            delay(delayMillis)
            restartApplication()
        }
    }
}
