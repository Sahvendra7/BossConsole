package ai.rever.boss.terminal.render

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.flow.collectLatest

/**
 * Standalone window that mounts the [TerminalGridRenderer] against a
 * [StubTerminalGridSource]. Gated behind the `boss.terminal.oopRenderer`
 * flag (see [TerminalRenderFlag]). Existence: Phase B visual validation
 * without touching the real terminal tab plugin or decompose nav.
 *
 * The window owns its own [GridState] and stub source instances, and
 * collects the canned grid + cursor streams in [LaunchedEffect]s.
 */
@Composable
fun TerminalRenderDevWindow(onCloseRequest: () -> Unit) {
    val state = remember { GridState() }
    val source = remember { StubTerminalGridSource() }

    LaunchedEffect(source) {
        source.gridFrames().collectLatest { delta ->
            state.applyDelta(delta)
        }
    }
    LaunchedEffect(source) {
        source.cursorFrames().collectLatest { cursor ->
            state.applyCursor(cursor)
        }
    }
    LaunchedEffect(source) {
        // Drain shell events — the stub emits a PROMPT_STARTED and we don't
        // surface OSC-133 hooks in this preview, but leaving the flow
        // uncollected would let server-side state pile up in Phase D.
        source.shellEvents().collectLatest { /* ignore */ }
    }

    val windowState = rememberWindowState(
        position = WindowPosition.Aligned(Alignment.Center),
        width = 900.dp,
        height = 520.dp,
    )

    Window(
        onCloseRequest = onCloseRequest,
        state = windowState,
        title = "BOSS — Terminal OOP renderer (dev preview)",
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
        ) {
            TerminalGridRenderer(
                sessionId = source.sessionId,
                state = state,
                source = source,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
