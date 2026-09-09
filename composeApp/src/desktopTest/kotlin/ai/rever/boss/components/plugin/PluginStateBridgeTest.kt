package ai.rever.boss.components.plugin

import ai.rever.boss.ipc.proto.PluginIntentEnvelope
import ai.rever.boss.ipc.proto.PluginStateDelta
import ai.rever.boss.ipc.proto.PluginStateEnvelope
import ai.rever.boss.ipc.proto.PluginStateRequest
import ai.rever.boss.ipc.proto.PluginStateServiceGrpcKt
import ai.rever.boss.ipc.proto.PluginStateUpdate
import com.google.protobuf.ByteString
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.Server
import io.grpc.ServerBuilder
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.UUID
import kotlin.test.assertTrue

class PluginStateBridgeTest {
    private lateinit var server: Server
    private lateinit var channel: ManagedChannel
    private lateinit var mockService: MockPluginStateService
    private lateinit var bridge: PluginStateBridge

    @Before
    fun setup() {
        mockService = MockPluginStateService()
        server = ServerBuilder.forPort(0)
            .addService(mockService)
            .build()
            .start()
            
        channel = ManagedChannelBuilder.forAddress("localhost", server.port)
            .usePlaintext()
            .build()

        bridge = PluginStateBridge("test-plugin", "test-instance", channel)
    }

    @After
    fun teardown() {
        bridge.dispose()
        channel.shutdownNow()
        server.shutdownNow()
    }

    @Test
    fun `delta patch with matching version updates state successfully`() =
        runBlocking {
            bridge.start()

            // Wait for connection
            while (!bridge.connected.value) {
                delay(10)
            }

            // Bridge starts and fetches initial state (version 10)
            val initialJson = """{"count": 0, "name": "plugin"}"""
            assertEquals(10L, bridge.version.value)
            assertEquals(initialJson, bridge.state.value.decodeToString())

            // Send delta from version 10 to 11
            val deltaJson = """{"count": 1}"""
            mockService.updates.emit(
                PluginStateUpdate
                    .newBuilder()
                    .setDeltaState(
                        PluginStateDelta
                            .newBuilder()
                            .setBaseVersion(10)
                            .setNewVersion(11)
                            .setPatchBytes(ByteString.copyFromUtf8(deltaJson))
                            .build(),
                    ).build(),
            )

            // Wait for update
            while (bridge.version.value != 11L) {
                delay(10)
            }

            val expectedMerged = """{"count":1,"name":"plugin"}"""
            assertEquals(expectedMerged, bridge.state.value.decodeToString())
        }

    @Test
    fun `delta patch with mismatched version falls back to full state`() =
        runBlocking {
            bridge.start()

            while (!bridge.connected.value) {
                delay(10)
            }

            assertEquals(10L, bridge.version.value)

            // Set up the next full state fetch response
            mockService.nextFullStateJson = """{"recovered": true}"""
            mockService.nextFullStateVersion = 20L

            // Send stale delta (base = 5)
            val deltaJson = """{"count": 1}"""
            mockService.updates.emit(
                PluginStateUpdate
                    .newBuilder()
                    .setDeltaState(
                        PluginStateDelta
                            .newBuilder()
                            .setBaseVersion(5) // Mismatched version
                            .setNewVersion(6)
                            .setPatchBytes(ByteString.copyFromUtf8(deltaJson))
                            .build(),
                    ).build(),
            )

            // Bridge should fall back to fetchCurrentState() and get version 20
            while (bridge.version.value != 20L) {
                delay(10)
            }

            assertEquals("""{"recovered": true}""", bridge.state.value.decodeToString())
        }

    @Test
    fun `malformed patch json falls back to full state`() =
        runBlocking {
            bridge.start()

            while (!bridge.connected.value) {
                delay(10)
            }

            assertEquals(10L, bridge.version.value)

            mockService.nextFullStateJson = """{"recovered": "from_error"}"""
            mockService.nextFullStateVersion = 30L

            // Send invalid JSON in patch
            mockService.updates.emit(
                PluginStateUpdate
                    .newBuilder()
                    .setDeltaState(
                        PluginStateDelta
                            .newBuilder()
                            .setBaseVersion(10)
                            .setNewVersion(11)
                            .setPatchBytes(ByteString.copyFromUtf8("invalid json"))
                            .build(),
                    ).build(),
            )

            // Bridge should fall back to fetchCurrentState() and get version 30
            while (bridge.version.value != 30L) {
                delay(10)
            }

            assertEquals("""{"recovered": "from_error"}""", bridge.state.value.decodeToString())
        }

    private class MockPluginStateService : PluginStateServiceGrpcKt.PluginStateServiceCoroutineImplBase() {
        val updates = MutableSharedFlow<PluginStateUpdate>(extraBufferCapacity = 10)

        var nextFullStateJson = """{"count": 0, "name": "plugin"}"""
        var nextFullStateVersion = 10L

        override suspend fun getCurrentState(request: PluginStateRequest): PluginStateEnvelope =
            PluginStateEnvelope
                .newBuilder()
                .setVersion(nextFullStateVersion)
                .setStateBytes(ByteString.copyFromUtf8(nextFullStateJson))
                .build()

        override fun syncState(requests: Flow<PluginIntentEnvelope>): Flow<PluginStateUpdate> = updates
    }
}
