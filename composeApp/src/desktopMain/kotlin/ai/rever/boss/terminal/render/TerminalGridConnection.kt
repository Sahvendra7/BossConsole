package ai.rever.boss.terminal.render

import ai.rever.boss.ipc.BossIpcClient
import ai.rever.boss.ipc.proto.services.TerminalServiceGrpcKt
import io.grpc.ManagedChannel

/**
 * Host-side connection to a child plugin's `TerminalService`. Pairs a
 * managed gRPC channel with a [TerminalGridClient] built on top of it
 * and owns the channel lifecycle so callers can dispose the whole
 * thing in one step.
 *
 * Two construction paths:
 *  - [connect] — for tests / dev tooling that knows the raw IPC
 *    address (e.g. the loopback validator).
 *  - [adopt] — for production wiring that already has a channel from
 *    [ai.rever.boss.components.plugin.OutOfProcessPluginSpawnerImpl.getChannel].
 *    [adopt] does not take ownership of the channel — Phase D will
 *    wire that up.
 */
class TerminalGridConnection internal constructor(
    val client: TerminalGridClient,
    private val ownedIpcClient: BossIpcClient?,
) : AutoCloseable {

    override fun close() {
        ownedIpcClient?.shutdown(SHUTDOWN_TIMEOUT_MS)
    }

    companion object {
        private const val SHUTDOWN_TIMEOUT_MS = 3_000L

        /**
         * Open a fresh channel to [address] (e.g. `unix:///tmp/foo.sock`
         * or `tcp://localhost:57100`) and wrap it. The returned
         * connection owns the channel and will shut it down on [close].
         */
        fun connect(
            address: String,
            sessionId: String,
            maxFpsHint: Int = 60,
        ): TerminalGridConnection {
            val ipcClient = BossIpcClient(address)
            val stub = TerminalServiceGrpcKt.TerminalServiceCoroutineStub(ipcClient.channel)
            val client = TerminalGridClient(
                sessionId = sessionId,
                stub = stub,
                maxFpsHint = maxFpsHint,
            )
            return TerminalGridConnection(client = client, ownedIpcClient = ipcClient)
        }

        /**
         * Wrap an existing channel (typically obtained from the OOP
         * plugin spawner). The caller retains ownership of the channel;
         * [close] is a no-op for this path.
         */
        fun adopt(
            channel: ManagedChannel,
            sessionId: String,
            maxFpsHint: Int = 60,
        ): TerminalGridConnection {
            val stub = TerminalServiceGrpcKt.TerminalServiceCoroutineStub(channel)
            val client = TerminalGridClient(
                sessionId = sessionId,
                stub = stub,
                maxFpsHint = maxFpsHint,
            )
            return TerminalGridConnection(client = client, ownedIpcClient = null)
        }
    }
}
