package ai.rever.boss.orchestrator

import ai.rever.boss.ipc.ChildProcessBootstrap
import ai.rever.boss.ipc.proto.*
import ai.rever.boss.process.ProcessMonitor
import ai.rever.boss.process.ProcessRegistry
import ai.rever.boss.process.ProcessSpawner
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Entry point for the Orchestrator process.
 *
 * Connects to the kernel, registers itself, starts the OrchestratorService gRPC server,
 * and listens for process failures from ProcessMonitor to drive self-healing.
 */
fun main() {
    val logger = LoggerFactory.getLogger("OrchestratorMain")
    logger.info("Orchestrator starting...")

    val bootstrap = ChildProcessBootstrap()
    val dataDir = File(
        System.getenv("BOSS_DATA_DIR") ?: "${System.getProperty("user.home")}/.boss"
    )

    val manifest = ProcessManifest.newBuilder()
        .setProcessId(bootstrap.processId)
        .setProcessType(ProcessType.PROCESS_TYPE_ORCHESTRATOR)
        .setDisplayName("BOSS Orchestrator")
        .setVersion("1.0.0")
        .setMainClass("ai.rever.boss.orchestrator.OrchestratorMainKt")
        .setBehaviorSpec(
            "AI-powered self-healing orchestrator. Monitors all processes, diagnoses failures " +
            "using manifest repair hints and error pattern matching, and executes repair strategies " +
            "including restart, state reset, config patch, source patch, and escalation."
        )
        .addAllSourceFiles(listOf(
            "boss-orchestrator/src/main/kotlin/ai/rever/boss/orchestrator/OrchestratorMain.kt",
            "boss-orchestrator/src/main/kotlin/ai/rever/boss/orchestrator/OrchestratorServiceImpl.kt",
            "boss-orchestrator/src/main/kotlin/ai/rever/boss/orchestrator/RepairEngine.kt",
            "boss-orchestrator/src/main/kotlin/ai/rever/boss/orchestrator/CrashAnalyzer.kt",
            "boss-orchestrator/src/main/kotlin/ai/rever/boss/orchestrator/SnapshotManager.kt",
        ))
        .addAllExposedServices(listOf("boss.ipc.v1.OrchestratorService"))
        .setHealthContract(
            HealthContract.newBuilder()
                .setHeartbeatIntervalMs(5000)
                .setStartupTimeoutMs(20000)
                .build()
        )
        .build()

    runBlocking {
        val connection = bootstrap.connect(manifest)

        val processRegistry = ProcessRegistry()
        val processSpawner = ProcessSpawner(bootstrap.kernelAddress)
        val snapshotManager = SnapshotManager(dataDir)
        val analyzer = CrashAnalyzer()
        val repairEngine = RepairEngine(processSpawner, processRegistry, analyzer, snapshotManager)
        val monitor = ProcessMonitor(processRegistry)

        val orchestratorService = OrchestratorServiceImpl(repairEngine, processRegistry)
        connection.processServer.addService(orchestratorService)

        connection.startServer()
        logger.info("Orchestrator running on: {}", bootstrap.processAddress)

        // Forward ProcessMonitor failures to the repair engine
        launch {
            monitor.failures.collect { failure ->
                logger.warn(
                    "Process failure detected: id={}, reason={}",
                    failure.processId, failure.reason,
                )
                val report = ProcessFailureReport.newBuilder()
                    .setProcessId(failure.processId)
                    .setErrorType(failure.reason.name)
                    .setErrorMessage(failure.errorMessage)
                    .setStackTrace(failure.stackTrace)
                    .setExitCode(failure.exitCode)
                    .setTimestamp(failure.timestamp)
                    .setConsecutiveFailures(processRegistry.getRestartCount(failure.processId) + 1)
                    .build()
                repairEngine.handleFailure(report)
            }
        }

        connection.awaitTermination()
    }
}
