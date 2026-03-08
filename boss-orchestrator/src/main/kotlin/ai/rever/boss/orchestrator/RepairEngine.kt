package ai.rever.boss.orchestrator

import ai.rever.boss.ipc.proto.ProcessFailureReport
import ai.rever.boss.ipc.proto.ProcessManifest
import ai.rever.boss.ipc.proto.RepairStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/**
 * Executes repair strategies for failing processes.
 *
 * Strategy selection:
 * - HIGH/MEDIUM confidence from CrashAnalyzer → use analyzer's strategy directly.
 * - LOW confidence → walk the [defaultLadder] based on consecutive failure count.
 *
 * Restart requests are delegated to [onRequestRestart] (typically calls
 * KernelService.RequestShutdown so the kernel handles the actual process lifecycle).
 */
class RepairEngine(
    private val analyzer: CrashAnalyzer,
    private val snapshotManager: SnapshotManager,
    /** Called to request a restart. Kernel handles the actual re-spawn. */
    private val onRequestRestart: suspend (processId: String, jvmArgsOverride: List<String>) -> Unit = { _, _ -> },
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
        // Use manifest from the report (quine property — process describes itself)
        val manifest: ProcessManifest? = if (report.hasManifest()) report.manifest else null
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

    private suspend fun executeStrategy(
        processId: String,
        strategy: RepairStrategy,
        report: ProcessFailureReport,
        diagnostic: DiagnosticResult,
    ): RepairOutcome {
        return when (strategy) {
            RepairStrategy.REPAIR_STRATEGY_RESTART -> {
                try {
                    onRequestRestart(processId, emptyList())
                    logger.info("Restart requested for process: {}", processId)
                    RepairOutcome.Restarted(processId)
                } catch (e: Exception) {
                    logger.error("Failed to request restart for process: {}", processId, e)
                    RepairOutcome.Failed(processId, "Restart request failed: ${e.message}")
                }
            }

            RepairStrategy.REPAIR_STRATEGY_RESTART_TUNED -> {
                try {
                    onRequestRestart(processId, listOf("-Xmx512m"))
                    logger.info("Tuned restart requested for process: {}", processId)
                    RepairOutcome.Restarted(processId)
                } catch (e: Exception) {
                    logger.error("Failed to request tuned restart for process: {}", processId, e)
                    RepairOutcome.Failed(processId, "Tuned restart request failed: ${e.message}")
                }
            }

            RepairStrategy.REPAIR_STRATEGY_RESET_STATE -> {
                try {
                    // Clear snapshots so process starts fresh, then restart
                    snapshotManager.cleanup(processId, keepLast = 0)
                    onRequestRestart(processId, emptyList())
                    logger.info("State reset + restart requested for process: {}", processId)
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
