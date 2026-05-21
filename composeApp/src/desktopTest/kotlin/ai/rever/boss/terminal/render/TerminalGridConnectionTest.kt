package ai.rever.boss.terminal.render

import ai.rever.boss.ipc.BossIpcClient
import ai.rever.boss.ipc.proto.services.SendKeyEventRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * End-to-end host-side validation of the Phase C connection scaffolding.
 *
 * Spins up a [LoopbackTerminalServer] over real gRPC + Unix domain
 * sockets (or TCP loopback on Windows), connects via
 * [TerminalGridConnection.connect], and verifies that grid frames,
 * cursor state, and key events all traverse the wire correctly.
 *
 * The server lives in the same JVM as the client but talks through a
 * real channel, so the path being exercised is identical to what a
 * spawned terminal plugin will use in Phase D.
 */
class TerminalGridConnectionTest {

    @Test
    fun `client receives an initial full redraw from loopback server`() {
        LoopbackTerminalServer(instanceTag = uniqueTag()).start().use { server ->
            TerminalGridConnection.connect(
                address = server.address,
                sessionId = "session-1",
            ).use { conn ->
                val first = runBlocking {
                    withTimeout(5_000) { conn.client.gridFrames().first() }
                }

                assertTrue(first.isFullRedraw, "first frame must be a full redraw")
                assertEquals(80, first.cols)
                assertEquals(24, first.rows)
                assertTrue(first.rowsChangedCount >= 1, "full redraw must carry at least one row")
                val banner = first.getRowsChanged(0).text
                assertTrue(banner.startsWith("BOSS terminal"), "got banner: $banner")
            }
        }
    }

    @Test
    fun `streamGrid emits incremental blink frames after the initial redraw`() {
        LoopbackTerminalServer(instanceTag = uniqueTag()).start().use { server ->
            TerminalGridConnection.connect(
                address = server.address,
                sessionId = "session-2",
            ).use { conn ->
                val frames = runBlocking {
                    withTimeout(5_000) { conn.client.gridFrames().take(3).toList() }
                }
                assertEquals(3, frames.size)
                assertTrue(frames[0].isFullRedraw)
                assertTrue(!frames[1].isFullRedraw, "second frame should be incremental")
                assertTrue(frames[1].revision > frames[0].revision, "revisions monotonic")
                assertTrue(frames[2].revision > frames[1].revision)
            }
        }
    }

    @Test
    fun `streamCursor delivers a cursor state over the wire`() {
        LoopbackTerminalServer(instanceTag = uniqueTag()).start().use { server ->
            TerminalGridConnection.connect(
                address = server.address,
                sessionId = "session-3",
            ).use { conn ->
                val cursor = runBlocking {
                    withTimeout(5_000) { conn.client.cursorFrames().first() }
                }
                assertEquals(0, cursor.col)
                assertTrue(cursor.visible)
                assertTrue(cursor.blink)
            }
        }
    }

    @Test
    fun `sendKey reaches the server and increments its counter`() {
        LoopbackTerminalServer(instanceTag = uniqueTag()).start().use { server ->
            TerminalGridConnection.connect(
                address = server.address,
                sessionId = "session-4",
            ).use { conn ->
                runBlocking {
                    withTimeout(5_000) {
                        conn.client.sendKey(
                            SendKeyEventRequest.newBuilder()
                                .setSessionId("session-4")
                                .setKeyCode(65)
                                .setText("h")
                                .setIsPress(true)
                                .setRepeatCount(1)
                                .build(),
                        )
                        conn.client.sendKey(
                            SendKeyEventRequest.newBuilder()
                                .setSessionId("session-4")
                                .setKeyCode(73)
                                .setText("i")
                                .setIsPress(true)
                                .setRepeatCount(1)
                                .build(),
                        )
                    }
                }
                assertEquals(2, server.keyEventCount)
                assertEquals("hi", server.lastTypedText)
            }
        }
    }

    @Test
    fun `adopt does not shut down externally-owned channel on close`() {
        LoopbackTerminalServer(instanceTag = uniqueTag()).start().use { server ->
            val external = BossIpcClient(server.address)
            try {
                // Adopt wraps an existing channel without taking ownership;
                // close() must not terminate it. We verify by closing the
                // connection and then issuing a follow-up RPC on the same channel.
                val conn = TerminalGridConnection.adopt(external.channel, sessionId = "adopt-test")
                conn.close()
                assertFalse(external.channel.isShutdown, "adopt() must not shut down the channel")

                // The channel still serves RPCs after the connection is closed.
                val second = TerminalGridConnection.adopt(external.channel, sessionId = "adopt-followup")
                val first = runBlocking {
                    withTimeout(5_000) { second.client.gridFrames().first() }
                }
                assertTrue(first.isFullRedraw)
            } finally {
                external.shutdown()
            }
        }
    }

    private fun uniqueTag(): String = UUID.randomUUID().toString().take(8)
}
