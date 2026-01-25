package ai.rever.boss.utils

import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import ai.rever.boss.components.plugin.tab_types.fluck.FluckEngine
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.system.exitProcess

object ApplicationRestarter {
    private val logger = BossLogger.forComponent("ApplicationRestarter")

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
                
                logger.info(LogCategory.SYSTEM, "Restarting application", mapOf("command" to command.joinToString(" ")))
                
                // Start new instance
                val processBuilder = ProcessBuilder(command)
                processBuilder.directory(File(System.getProperty("user.dir")))
                processBuilder.start()
                
                // Give the new instance time to start
                delay(1000)
                
                // Exit current instance
                exitProcess(0)
                
            } catch (e: Exception) {
                logger.error(LogCategory.SYSTEM, "Failed to restart application", error = e)
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
                logger.debug(LogCategory.SYSTEM, "Closing browser engine")
                engine.close()
                delay(500) // Give time for engine to close
            }
        } catch (e: Exception) {
            logger.warn(LogCategory.SYSTEM, "Error during graceful shutdown", error = e)
        }
    }
    
    @OptIn(DelicateCoroutinesApi::class)
    fun scheduleRestart(delayMillis: Long = 1000) {
        GlobalScope.launch {
            delay(delayMillis)
            restartApplication()
        }
    }

    /**
     * Quit the application without restarting
     * Used when an update helper script is waiting to install the update after the app quits
     *
     * This performs a graceful shutdown and exits cleanly, allowing external scripts
     * to monitor the process PID and proceed with installation once the app has fully terminated.
     *
     * Uses runBlocking to ensure cleanup completes synchronously before exit,
     * preventing race conditions with other code.
     */
    fun quitForUpdate() {
        if (isRestarting) return // Prevent multiple quit attempts
        isRestarting = true

        logger.info(LogCategory.SYSTEM, "Quitting application for update installation")

        // Use runBlocking to ensure cleanup completes synchronously
        // This prevents race conditions where the function might return
        // before cleanup has even started
        runBlocking {
            try {
                // Perform graceful shutdown
                performGracefulShutdown()

                logger.info(LogCategory.SYSTEM, "Shutdown complete. Exiting")

                // Give cleanup time to complete
                delay(200)

                // Exit cleanly - update script will wait for this PID to terminate
                exitProcess(0)

            } catch (e: Exception) {
                logger.error(LogCategory.SYSTEM, "Error during quit", error = e)
                // Exit anyway
                exitProcess(1)
            }
        }
        // This point is NEVER reached - exitProcess() terminates the JVM
    }
}
