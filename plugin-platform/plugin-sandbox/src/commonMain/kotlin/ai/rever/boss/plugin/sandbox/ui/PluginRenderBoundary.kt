package ai.rever.boss.plugin.sandbox.ui

import ai.rever.boss.plugin.logging.BossLogger
import ai.rever.boss.plugin.logging.LogCategory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.Layout
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException

/**
 * Contains exceptions thrown while *rendering* a plugin's subtree — during
 * measure, placement or draw — and attributes them to [pluginId] by position in
 * the tree rather than by inspecting stack frames.
 *
 * ### Why this exists
 *
 * [PluginCrashInterceptor] attributes a crash by looking for the plugin in the
 * stack: a sandbox thread name, a class whose name starts with the plugin id, or
 * a class loadable from the plugin's classloader. That works for exceptions
 * thrown *during composition*, which is what it was built for — a
 * `NoSuchMethodError` from a binary incompatibility has the plugin's own frames
 * on the stack.
 *
 * It cannot work for the layout phases. By the time Compose measures a subtree,
 * the plugin's composable functions have already run and returned; all they did
 * was emit `LayoutNode`s. Compose walks those nodes later from its own loop, so
 * the stack holds nothing but `androidx.compose.*` frames and nothing anywhere
 * records which plugin owns a node. Attribution returns null, and the window
 * handler in main.kt then treats it as a host crash — which disposes the window
 * and takes the whole app down.
 *
 * That is not hypothetical: BossConsole-Releases#16 was a bookmarks-plugin bug
 * (two LazyColumn items under one key, so one `LayoutNode` was measured twice in
 * a pass) that killed BossConsole with
 * `IllegalStateException: layout state is not idle before measure starts`, and
 * the reported stack trace contains not one plugin frame.
 *
 * This boundary sidesteps attribution entirely: whatever is thrown underneath it
 * belongs to the plugin it wraps, because of where it sits.
 *
 * ### What happens on a crash
 *
 * The throw is swallowed for the current frame, the subtree collapses to the
 * minimum size the parent allows, and [onRenderCrash] is invoked. That records
 * the crash, which tears the subtree down and rebuilds it so
 * [PluginErrorBoundary] can render its fallback — the same recovery path a
 * composition crash already takes.
 *
 * Reported once per boundary instance. A failing measure is usually retried
 * several times before the rebuild lands, and one broken panel should not
 * produce a burst of identical crash records.
 *
 * ### What it does not cover
 *
 * Compose can re-measure a dirty node *directly*, using its last constraints,
 * without walking down from its ancestors. When that node is inside the plugin
 * subtree this boundary is not on the stack and cannot intercept. The reported
 * crash did enter through the ancestors, and a size change from such a remeasure
 * propagates upward and re-enters here on the next pass — but the gap is real,
 * which is why the window handler must also stop treating an unattributed render
 * exception as fatal.
 *
 * @param pluginId Plugin that owns [content]; used only for logging here.
 * @param onRenderCrash Invoked at most once, on the render thread, with the
 *   throwable. Must be cheap and must not itself throw — see
 *   [PluginCrashRegistry.recordCrash], which defers its state writes to the EDT
 *   precisely so it is safe to call from inside a render pass.
 */

// Catching Throwable is this function's entire contract: a boundary that only
// caught the exception types it could predict would not be a boundary. The
// narrowing that does matter is [rethrowIfUncontainable], applied first at every
// catch site.
@Suppress("TooGenericExceptionCaught")
@Composable
internal fun PluginRenderBoundary(
    pluginId: String,
    onRenderCrash: (Throwable) -> Unit,
    content: @Composable () -> Unit,
) {
    val logger = remember { BossLogger.forComponent("PluginRenderBoundary") }
    val reported = remember(pluginId) { AtomicBoolean(false) }

    // Not a composable: called from measure, placement and draw, each of which has
    // already run rethrowIfUncontainable on the throwable.
    fun report(
        phase: String,
        error: Throwable,
    ) {
        if (!reported.compareAndSet(false, true)) return

        logger.error(
            LogCategory.UI,
            "Plugin crashed during $phase — containing it and rebuilding the panel",
            mapOf(
                "pluginId" to pluginId,
                "phase" to phase,
                "errorType" to error::class.simpleName.orEmpty(),
            ),
            error,
        )
        try {
            onRenderCrash(error)
        } catch (secondary: Throwable) {
            secondary.rethrowIfUncontainable()
            // The handler failing must not turn a contained crash into a fatal one.
            logger.warn(
                LogCategory.UI,
                "Crash handler threw while containing a render crash",
                mapOf("pluginId" to pluginId),
                secondary,
            )
        }
    }

    Layout(
        content = content,
        modifier =
            Modifier.drawWithContent {
                try {
                    drawContent()
                } catch (t: Throwable) {
                    t.rethrowIfUncontainable()
                    report("draw", t)
                }
            },
    ) { measurables, constraints ->
        // Box semantics: children see relaxed minimums so a wrap-content host
        // (a status bar item, say) is not forced to expand, while a child that
        // asks to fill still fills.
        val childConstraints = constraints.copy(minWidth = 0, minHeight = 0)

        val placeables =
            try {
                measurables.map { it.measure(childConstraints) }
            } catch (t: Throwable) {
                t.rethrowIfUncontainable()
                report("measure", t)
                // Deliberately no children: measuring again this frame would just
                // hit the same throw. The rebuild triggered by onRenderCrash
                // replaces this subtree on a later frame.
                emptyList()
            }

        val width = (placeables.maxOfOrNull { it.width } ?: 0).coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = (placeables.maxOfOrNull { it.height } ?: 0).coerceIn(constraints.minHeight, constraints.maxHeight)

        layout(width, height) {
            try {
                placeables.forEach { it.place(0, 0) }
            } catch (t: Throwable) {
                t.rethrowIfUncontainable()
                report("placement", t)
            }
        }
    }
}

/**
 * Rethrow what a plugin boundary has no business containing.
 *
 * A cancelled composition is control flow, not a fault, and swallowing it would
 * strand whoever is awaiting the cancellation. An [OutOfMemoryError] says the JVM
 * is done; reporting it as "this plugin crashed" would be both wrong and a
 * distraction from the real problem.
 *
 * Deliberately not a `when` inside each catch block: pulled out here it stays one
 * decision in one place, applied identically by measure, placement, draw and the
 * crash handler itself.
 */
private fun Throwable.rethrowIfUncontainable() {
    if (this is CancellationException) throw this
    if (this is OutOfMemoryError) throw this
}
