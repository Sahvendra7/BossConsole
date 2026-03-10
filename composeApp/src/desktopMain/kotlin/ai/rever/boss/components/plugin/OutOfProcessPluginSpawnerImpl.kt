package ai.rever.boss.components.plugin

import ai.rever.boss.ipc.BossIpcClient
import ai.rever.boss.plugin.api.PluginManifest
import ai.rever.boss.process.ManagedProcess
import ai.rever.boss.process.ProcessConfig
import ai.rever.boss.process.ProcessSpawner
import ai.rever.boss.process.ProcessType
import ai.rever.boss.process.RestartPolicy
import io.grpc.ManagedChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Implementation of [OutOfProcessPluginSpawner] that uses [ProcessSpawner]
 * to launch plugin child JVM processes.
 *
 * Each spawned plugin:
 * 1. Runs the generic `PluginProcessMain` entry point
 * 2. Reads its plugin JAR from `BOSS_PLUGIN_CLASSPATH`
 * 3. Connects back to the kernel via `BOSS_KERNEL_IPC_ADDR`
 * 4. Registers with the kernel's process registry
 * 5. Starts its gRPC server for UI streaming and state sync
 *
 * The spawner tracks all managed processes and provides connection info
 * (gRPC channel) for [PluginStateBridge] and remote UI components.
 */
class OutOfProcessPluginSpawnerImpl(
    private val processSpawner: ProcessSpawner,
    private val windowId: String = "",
    private val projectPath: String = "",
) : OutOfProcessPluginSpawner {

    private val logger = LoggerFactory.getLogger(OutOfProcessPluginSpawnerImpl::class.java)

    /** Active managed processes keyed by plugin ID. */
    private val managedProcesses = ConcurrentHashMap<String, ManagedProcess>()

    /** gRPC channels to plugin processes keyed by plugin ID. */
    private val pluginChannels = ConcurrentHashMap<String, ManagedChannel>()

    /** State bridges keyed by plugin ID. */
    private val stateBridges = ConcurrentHashMap<String, PluginStateBridge>()

    /**
     * Classpath for the plugin runtime fat JAR.
     * Resolved from BOSS_PLUGIN_RUNTIME_JAR env var or default location.
     */
    private val runtimeClasspath: String by lazy {
        System.getenv("BOSS_PLUGIN_RUNTIME_JAR")
            ?: findRuntimeJar()
            ?: throw IllegalStateException(
                "Cannot find boss-plugin-runtime JAR. Set BOSS_PLUGIN_RUNTIME_JAR env var."
            )
    }

    override suspend fun spawn(manifest: PluginManifest, jarPath: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val pluginId = manifest.pluginId

                // Build classpath: runtime JAR + plugin JAR
                val classpath = "$runtimeClasspath${File.pathSeparator}$jarPath"

                val config = ProcessConfig(
                    processId = "plugin-$pluginId",
                    processType = ProcessType.PLUGIN,
                    displayName = manifest.displayName,
                    mainClass = "ai.rever.boss.plugin.runtime.PluginProcessMainKt",
                    classpath = classpath,
                    nativeImagePath = manifest.nativeImagePath?.takeIf { it.isNotEmpty() },
                    jvmArgs = buildJvmArgs(manifest),
                    workDir = File(projectPath.ifEmpty { System.getProperty("user.dir") }),
                    restartPolicy = RestartPolicy.ON_FAILURE,
                    maxRestarts = manifest.sandbox.maxRestartAttempts,
                    environment = buildEnvironment(pluginId, jarPath),
                    startupTimeoutMs = manifest.healthContract?.startupTimeoutMs ?: 30_000,
                    heartbeatIntervalMs = manifest.healthContract?.heartbeatIntervalMs ?: 5_000,
                )

                logger.info(
                    "Spawning out-of-process plugin: id={}, jar={}, runtime={}",
                    pluginId, jarPath, runtimeClasspath
                )

                val managedProcess = processSpawner.spawn(config)
                managedProcesses[pluginId] = managedProcess

                // Wait for the child process to register with the kernel
                waitForReady(pluginId, managedProcess, config.startupTimeoutMs)

                // Create gRPC channel to the plugin process
                val channel = BossIpcClient(managedProcess.ipcAddress).channel
                pluginChannels[pluginId] = channel

                // Create and start state bridge
                val bridge = PluginStateBridge(
                    pluginId = pluginId,
                    instanceId = "plugin-$pluginId",
                    channel = channel,
                )
                bridge.start()
                stateBridges[pluginId] = bridge

                logger.info(
                    "Out-of-process plugin ready: id={}, pid={}, ipc={}",
                    pluginId, managedProcess.pid, managedProcess.ipcAddress
                )

                Result.success(Unit)
            } catch (e: Exception) {
                logger.error(
                    "Failed to spawn out-of-process plugin: manifest={}",
                    manifest.pluginId, e
                )
                Result.failure(e)
            }
        }
    }

    override suspend fun terminate(pluginId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                // Dispose state bridge
                stateBridges.remove(pluginId)?.dispose()

                // Shutdown gRPC channel
                pluginChannels.remove(pluginId)?.shutdown()

                // Destroy process
                val process = managedProcesses.remove(pluginId)
                if (process != null) {
                    logger.info("Terminating plugin process: id={}, pid={}", pluginId, process.pid)
                    process.destroy()

                    // Wait for graceful shutdown, then force kill
                    withTimeout(5_000) {
                        while (process.isAlive) {
                            delay(100)
                        }
                    }
                } else {
                    logger.warn("No managed process found for plugin: {}", pluginId)
                }

                Result.success(Unit)
            } catch (e: Exception) {
                // Force kill if graceful shutdown failed
                managedProcesses[pluginId]?.destroyForcibly()
                managedProcesses.remove(pluginId)
                logger.warn("Force-killed plugin process: id={}", pluginId, e)
                Result.success(Unit)
            }
        }
    }

    /**
     * Get the gRPC channel to a plugin process.
     * Used by RemotePanelComponent/RemoteTabComponent for UI streaming.
     */
    fun getChannel(pluginId: String): ManagedChannel? = pluginChannels[pluginId]

    /**
     * Get the state bridge for a plugin.
     */
    fun getStateBridge(pluginId: String): PluginStateBridge? = stateBridges[pluginId]

    /**
     * Get the managed process for a plugin.
     */
    fun getManagedProcess(pluginId: String): ManagedProcess? = managedProcesses[pluginId]

    /**
     * Check if a plugin process is alive.
     */
    fun isAlive(pluginId: String): Boolean = managedProcesses[pluginId]?.isAlive == true

    private fun buildJvmArgs(manifest: PluginManifest): List<String> = buildList {
        add("-Xmx256m")
        add("-Xms64m")
        // Enable virtual threads on Java 21+
        add("--enable-preview")
    }

    private fun buildEnvironment(pluginId: String, jarPath: String): Map<String, String> = buildMap {
        put("BOSS_PLUGIN_CLASSPATH", jarPath)
        if (windowId.isNotEmpty()) put("BOSS_WINDOW_ID", windowId)
        if (projectPath.isNotEmpty()) put("BOSS_PROJECT_PATH", projectPath)
    }

    /**
     * Wait for the child process to become ready (registered with kernel).
     */
    private suspend fun waitForReady(
        pluginId: String,
        process: ManagedProcess,
        timeoutMs: Long,
    ) {
        withTimeout(timeoutMs) {
            // Wait for the child process to register with the kernel via gRPC.
            // The kernel's KernelServiceImpl.onProcessRegistered callback is called
            // when the child connects. We check the kernel's process registry
            // rather than the local ManagedProcess.manifest (which is a different object).
            val kernelRegistryCheck: () -> Boolean = try {
                val bootstrapCls = Class.forName("ai.rever.boss.kernel.KernelBootstrap")
                val companionCls = Class.forName("ai.rever.boss.kernel.KernelBootstrap\$Companion")
                val companion = bootstrapCls.getDeclaredField("Companion").get(null)
                val getInstance = companionCls.getMethod("getInstance")
                val kernelInstance = getInstance.invoke(companion)
                if (kernelInstance != null) {
                    val registry = bootstrapCls.getMethod("getProcessRegistry").invoke(kernelInstance)
                    val registryCls = registry!!::class.java
                    val getManifest = registryCls.getMethod("getManifest", String::class.java)
                    val processId = "plugin-$pluginId";
                    { getManifest.invoke(registry, processId) != null }
                } else {
                    { process.manifest != null }
                }
            } catch (_: Exception) {
                { process.manifest != null }
            }

            while (true) {
                if (!process.isAlive) {
                    throw IllegalStateException(
                        "Plugin process died during startup: $pluginId (exit=${process.process.exitValue()})"
                    )
                }
                if (kernelRegistryCheck()) {
                    break
                }
                delay(100)
            }
        }
    }

    /**
     * Find the plugin runtime fat JAR in standard locations.
     */
    private fun findRuntimeJar(): String? {
        val bossDataDir = try {
            ai.rever.boss.plugin.pathutils.BossDirectories.rootDir.absolutePath
        } catch (_: Exception) {
            System.getenv("BOSS_DATA_DIR") ?: "${System.getProperty("user.home")}/.boss"
        }
        val candidates = listOf(
            // Development: build output
            "boss-plugin-runtime/build/libs/boss-plugin-runtime-all.jar",
            // Production: alongside the app
            "lib/boss-plugin-runtime-all.jar",
            // Data directory (respects .boss_debug in dev mode)
            "$bossDataDir/lib/boss-plugin-runtime-all.jar",
        )
        return candidates.firstOrNull { File(it).exists() }
    }
}
