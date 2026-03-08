package ai.rever.boss.plugin.runtime

import ai.rever.boss.ipc.ChildProcessBootstrap
import ai.rever.boss.ipc.proto.HealthContract
import ai.rever.boss.ipc.proto.ProcessManifest
import ai.rever.boss.ipc.proto.ProcessType
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.zip.ZipFile

private val logger = LoggerFactory.getLogger("PluginProcessMain")

/**
 * Minimal fields from META-INF/boss-plugin/plugin.json needed for process bootstrap.
 * Full manifest parsing happens inside the plugin's own classloader.
 */
@Serializable
private data class PluginJsonManifest(
    val pluginId: String = "",
    val displayName: String = "",
    val version: String = "1.0.0",
    val mainClass: String = "",
    val description: String = "",
)

private val json = Json { ignoreUnknownKeys = true }

/**
 * Generic entry point for out-of-process plugin processes.
 *
 * Every plugin that runs out-of-process uses this as its main class.
 * The actual plugin code is loaded from BOSS_PLUGIN_CLASSPATH via URLClassLoader.
 *
 * Steps:
 * 1. Read BOSS_PLUGIN_CLASSPATH — path to the plugin JAR
 * 2. Read META-INF/boss-plugin/plugin.json from the JAR
 * 3. Build a ProcessManifest and register with the kernel
 * 4. Start gRPC server with PluginUIServiceImpl
 * 5. Await termination
 */
fun main() = runBlocking {
    logger.info("Boss plugin runtime starting...")

    val bootstrap = ChildProcessBootstrap()

    // 1. Locate plugin JAR
    val classpathEnv = System.getenv("BOSS_PLUGIN_CLASSPATH")
        ?: throw IllegalStateException("BOSS_PLUGIN_CLASSPATH environment variable not set")

    logger.info("Loading plugin from: {}", classpathEnv)

    // 2. Read plugin manifest from JAR
    val pluginManifest = readPluginManifest(classpathEnv)
        ?: throw IllegalStateException(
            "No META-INF/boss-plugin/plugin.json found in JAR: $classpathEnv"
        )

    logger.info(
        "Loaded plugin: {} v{} (class={})",
        pluginManifest.displayName, pluginManifest.version, pluginManifest.mainClass,
    )

    // 3. Build ProcessManifest for kernel registration
    val processManifest = ProcessManifest.newBuilder()
        .setProcessId(bootstrap.processId)
        .setDisplayName(pluginManifest.displayName)
        .setProcessType(ProcessType.PROCESS_TYPE_PLUGIN)
        .setVersion(pluginManifest.version)
        .setMainClass(pluginManifest.mainClass)
        .setBehaviorSpec(pluginManifest.description)
        .setHealthContract(
            HealthContract.newBuilder()
                .setHeartbeatIntervalMs(5_000)
                .setStartupTimeoutMs(30_000)
                .build()
        )
        .build()

    // 4. Connect to kernel and start gRPC server
    val connection = bootstrap.connect(processManifest)

    val uiService = PluginUIServiceImpl()
    connection.processServer.addService(uiService)
    connection.startServer()

    logger.info(
        "Plugin process {} running on: {}",
        bootstrap.processId, bootstrap.processAddress,
    )

    // 5. Await termination
    connection.awaitTermination()
}

private fun readPluginManifest(jarPath: String): PluginJsonManifest? {
    return try {
        ZipFile(jarPath).use { zip ->
            val entry = zip.getEntry("META-INF/boss-plugin/plugin.json") ?: return null
            val content = zip.getInputStream(entry).bufferedReader().readText()
            json.decodeFromString<PluginJsonManifest>(content)
        }
    } catch (e: Exception) {
        logger.error("Failed to read plugin manifest from: {}", jarPath, e)
        null
    }
}
