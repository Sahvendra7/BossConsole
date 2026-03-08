package ai.rever.boss.orchestrator

import ai.rever.boss.ipc.proto.ProcessFailureReport
import ai.rever.boss.ipc.proto.ProcessManifest
import ai.rever.boss.ipc.proto.RepairStrategy
import ai.rever.boss.process.ProcessRegistry
import ai.rever.boss.process.ProcessSpawner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/**
 * Executes repair strategies for failing processes.
 *
 * Strategy selection:
 * - HIGH/MEDIUM confidence from CrashAnalyzer → use analyzer's strategy directly.
 * - LOW confidence → walk the [defaultLadder] based on consecutive failure count.
 */
class RepairEngine(
    private val processSpawner: ProcessSpawner,
    private val processRegistry: ProcessRegistry,
    private val analyzer: CrashAnalyzer,
    private val snapshotManager: SnapshotManager,
) {
    private val logger = LoggerFactory.getLogger(RepairEngine::class.java)

    // Escalation ladder applied when analyzer confidence is LOW
    private val defaultLadder = listOf(
        RepairStrategy.REPAIR_STRATEGY_RESTART,
        RepairStrategy.REPAIR_STRATEGY_RESTART,
        RepairStrategy.REPAIR_STRATEGY_RESET_STATE,
        RepairStrategy.REPAIR_STRATEGY_PATCH_CONFIG,
        RepairStrategy.REPAIR_STRATEGY_ESCALATE,
    )

    suspend fun handleFailure(report: ProcessFailureReport): RepairOutcome = withContext(Dispatchers.IO) {
        val processId = report.processId
        val manifest: ProcessManifest? = processRegistry.getManifest(processId)
            ?: if (report.hasManifest()) report.manifest else null
        val diagnostic = analyzer.analyze(report, manifest)

        logger.info(
            "Handling failure for {}: rootCause={}, strategy={}, confidence={}",
            processId, diagnostic.rootCause, diagnostic.strategy, diagnostic.confidence,
        )

        val strategy = if (diagnostic.confidence != Confidence.LOW) {
            diagnostic.strategy
        } else {
            val idx = (report.consecutiveFailures - 1).coerceIn(0, defaultLadder.lastIndex)
            defaultLadder[idx]
        }

        executeStrategy(processId, strategy, report, diagnostic)
    }

    private fun executeStrategy(
        processId: String,
        strategy: RepairStrategy,
        report: ProcessFailureReport,
        diagnostic: DiagnosticResult,
    ): RepairOutcome {
        return when (strategy) {
            RepairStrategy.REPAIR_STRATEGY_RESTART,
            RepairStrategy.REPAIR_STRATEGY_RESTART_TUNED -> {
                val process = processRegistry.getProcess(processId)
                    ?: return RepairOutcome.Failed(processId, "Process not found in registry")
                try {
                    process.destroy()
                    val extraArgs = if (strategy == RepairStrategy.REPAIR_STRATEGY_RESTART_TUNED) {
                        listOf("-Xmx512m")
                    } else emptyList()
                    val config = if (extraArgs.isNotEmpty()) {
                        process.config.copy(jvmArgs = process.config.jvmArgs + extraArgs)
                    } else process.config
                    val newProcess = processSpawner.spawn(config)
                    processRegistry.register(processId, newProcess, processRegistry.getManifest(processId))
                    processRegistry.incrementRestartCount(processId)
                    logger.info("Restarted process: {}", processId)
                    RepairOutcome.Restarted(processId)
                } catch (e: Exception) {
                    logger.error("Failed to restart process: {}", processId, e)
                    RepairOutcome.Failed(processId, "Restart failed: ${e.message}")
                }
            }

            RepairStrategy.REPAIR_STRATEGY_RESET_STATE -> {
                val process = processRegistry.getProcess(processId)
                    ?: return RepairOutcome.Failed(processId, "Process not found in registry")
                try {
                    process.destroy()
                    val newProcess = processSpawner.spawn(process.config)
                    processRegistry.register(processId, newProcess, processRegistry.getManifest(processId))
                    processRegistry.incrementRestartCount(processId)
                    logger.info("Reset state for process: {}", processId)
                    RepairOutcome.StateReset(processId)
                } catch (e: Exception) {
                    logger.error("Failed to reset state for process: {}", processId, e)
                    RepairOutcome.Failed(processId, "State reset failed: ${e.message}")
                }
            }

            RepairStrategy.REPAIR_STRATEGY_PATCH_CONFIG -> {
                logger.info("Config patch for process {} — manual intervention required", processId)
                RepairOutcome.ConfigPatched(
                    processId,
                    diagnostic.suggestedFix ?: "Manual config review required",
                )
            }

            RepairStrategy.REPAIR_STRATEGY_PATCH_SOURCE -> {
                logger.info("Source patch for process {} — proposing code fix", processId)
                RepairOutcome.CodeFixProposed(
                    processId,
                    "// TODO: AI-generated patch for ${diagnostic.rootCause}",
                )
            }

            else -> {
                logger.warn("Escalating failure for process: {}", processId)
                RepairOutcome.Escalated(processId, buildEscalationReport(processId, report, diagnostic))
            }
        }
    }

    private fun buildEscalationReport(
        processId: String,
        report: ProcessFailureReport,
        diagnostic: DiagnosticResult,
    ): String = buildString {
        appendLine("=== ESCALATION REPORT: $processId ===")
        appendLine("Root Cause: ${diagnostic.rootCause}")
        appendLine("Error Type: ${report.errorType}")
        appendLine("Error Message: ${report.errorMessage}")
        appendLine("Consecutive Failures: ${report.consecutiveFailures}")
        appendLine("Suggested Fix: ${diagnostic.suggestedFix ?: "None"}")
        if (report.stackTrace.isNotBlank()) {
            appendLine("Stack Trace:")
            appendLine(report.stackTrace.take(2000))
        }
    }
}

sealed class RepairOutcome {
    data class Restarted(val processId: String) : RepairOutcome()
    data class StateReset(val processId: String) : RepairOutcome()
    data class ConfigPatched(val processId: String, val patchDescription: String) : RepairOutcome()
    data class CodeFixProposed(val processId: String, val diff: String) : RepairOutcome()
    data class Escalated(val processId: String, val report: String) : RepairOutcome()
    data class Failed(val processId: String, val reason: String) : RepairOutcome()
}
