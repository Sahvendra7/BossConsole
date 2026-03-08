package ai.rever.boss.kernel

import ai.rever.boss.ipc.BossIpcServer
import ai.rever.boss.ipc.IpcAddressResolver
import ai.rever.boss.ipc.services.EventBusServiceImpl
import ai.rever.boss.ipc.services.KernelServiceImpl
import ai.rever.boss.ipc.services.StateServiceImpl
import ai.rever.boss.process.ProcessMode
import ai.rever.boss.process.ProcessMonitor
import ai.rever.boss.process.ProcessRegistry
import ai.rever.boss.process.ProcessSpawner
import ai.rever.boss.process.ProcessType
import ai.rever.boss.ipc.proto.ProcessState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * Bootstraps the microkernel infrastructure when running in KERNEL mode.
 *
 * In MONOLITH mode, this class does nothing — all code runs in-process as before.
 * In KERNEL mode, it:
 * 1. Starts a gRPC server for child processes to connect to
 * 2. Spawns the orchestrator process
 * 3. Spawns service processes (auth, workspace, etc.)
 * 4. Monitors all child processes via ProcessMonitor
 * 5. Provides graceful shutdown cascade
 */
class KernelBootstrap(private val mode: ProcessMode = ProcessMode.MONOLITH) {

    private val logger = LoggerFactory.getLogger(KernelBootstrap::class.java)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Infrastructure components (null when in MONOLITH mode)
    var ipcServer: BossIpcServer? = null; private set
    var processRegistry: ProcessRegistry? = null; private set
    var processSpawner: ProcessSpawner? = null; private set
    var processMonitor: ProcessMonitor? = null; private set
    var kernelService: KernelServiceImpl? = null; private set
    var eventBusService: EventBusServiceImpl? = null; private set
    var stateService: StateServiceImpl? = null; private set
    var kernelAddress: String? = null; private set

    /**
     * Initialize the kernel infrastructure. No-op in MONOLITH mode.
     */
    fun initialize() {
        if (mode == ProcessMode.MONOLITH) {
            logger.info("Running in MONOLITH mode — microkernel infrastructure disabled")
            return
        }

        logger.info("Initializing KERNEL mode...")

        // Create infrastructure
        kernelAddress = IpcAddressResolver.kernelAddress()
        processRegistry = ProcessRegistry()
        processSpawner = ProcessSpawner(kernelAddress!!)
        processMonitor = ProcessMonitor(processRegistry!!, scope)

        // Create gRPC services
        kernelService = KernelServiceImpl(
            onProcessRegistered = { id, manifest, _ ->
                logger.info("Process registered via IPC: {}", id)
                // updateManifest stores manifest in the registry's manifests map
                processRegistry!!.updateManifest(id, manifest)
                // Mark the process as running once it has registered itself
                processRegistry!!.getProcess(id)
                    ?.updateState(ProcessState.PROCESS_STATE_RUNNING)
            },
            onShutdownRequested = { id, force ->
                val process = processRegistry!!.getProcess(id)
                if (process != null) {
                    if (force) process.destroyForcibly() else process.destroy()
                    processRegistry!!.unregister(id)
                    true
                } else {
                    false
                }
            },
        )
        eventBusService = EventBusServiceImpl()
        stateService = StateServiceImpl()

        // Start gRPC server
        ipcServer = BossIpcServer(kernelAddress!!)
            .addService(kernelService!!)
            .addService(eventBusService!!)
            .addService(stateService!!)
            .start()

        // Start process monitor
        processMonitor!!.startGlobalMonitor()

        // Listen for failures and log them (orchestrator integration point)
        scope.launch {
            processMonitor!!.failures.collect { failure ->
                logger.error(
                    "Process failure detected: {} - {}",
                    failure.processId,
                    failure.errorMessage,
                )
                // TODO: Forward to orchestrator via IPC when orchestrator is running
            }
        }

        logger.info("KERNEL mode initialized. IPC server at: {}", kernelAddress)
    }

    /**
     * Shut down all child processes and the kernel server.
     * Called during application shutdown.
     */
    fun shutdown() {
        if (mode == ProcessMode.MONOLITH) return

        logger.info("Shutting down KERNEL mode...")

        // 1. Stop process monitor
        processMonitor?.stopAll()

        // 2. Shut down all child processes (apps first, then services)
        processRegistry?.getProcessesByType(ProcessType.PLUGIN)?.forEach { it.destroy() }
        processRegistry?.getProcessesByType(ProcessType.APP)?.forEach { it.destroy() }
        processRegistry?.getProcessesByType(ProcessType.ORCHESTRATOR)?.forEach { it.destroy() }
        processRegistry?.getProcessesByType(ProcessType.SERVICE)?.forEach { it.destroy() }

        // 3. Wait briefly for graceful shutdown
        Thread.sleep(2000)

        // 4. Force kill any remaining
        processRegistry?.getAllProcesses()?.filter { it.isAlive }?.forEach {
            logger.warn("Force-killing process: {}", it.config.processId)
            it.destroyForcibly()
        }

        // 5. Stop IPC server
        ipcServer?.stop()

        // 6. Cancel scope
        scope.cancel()

        logger.info("KERNEL mode shut down complete")
    }

    val isKernelMode: Boolean get() = mode == ProcessMode.KERNEL
}
