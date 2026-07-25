package ai.rever.boss.kernel.services

import ai.rever.boss.ipc.proto.ClickEvent
import ai.rever.boss.ipc.proto.PluginUIServiceGrpcKt
import ai.rever.boss.ipc.proto.TextChangeEvent
import ai.rever.boss.ipc.proto.UIEvent
import ai.rever.boss.ipc.proto.UIRegistration
import ai.rever.boss.ipc.proto.UIUnregistration
import ai.rever.boss.ipc.proto.WidgetNode
import ai.rever.boss.ipc.proto.WidgetType
import ai.rever.boss.ipc.proto.WidgetUpdate
import ai.rever.boss.kernel.ui.RemoteUiSurfaceHost
import ai.rever.boss.kernel.ui.RemoteUiSurfaceRegistry
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.Server
import io.grpc.ServerBuilder
import io.grpc.Status
import io.grpc.StatusException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import ai.rever.boss.ipc.proto.WidgetTree as ProtoWidgetTree
import ai.rever.boss.ui.sdk.WidgetTree as SdkWidgetTree

/**
 * The remote-UI transport, exercised over a real gRPC server with a real plugin-side client.
 *
 * This is the path that did not exist. `ui_protocol.proto` makes the plugin the client and the kernel
 * the server, and the host had it inverted: it dialled *out* with a `PluginUIServiceCoroutineStub` and,
 * because `StreamUI`'s request stream is typed `WidgetUpdate`, repacked every outgoing `UIEvent` as a
 * `WidgetUpdate` carrying only `surface_id`. Whatever the user did — the click id, the typed value, the
 * dropdown index — was discarded at that repack, and inbound `UIEvent`s were logged and dropped.
 *
 * So the assertion that matters throughout is not "an event arrived" but "an event arrived **with its
 * payload**". Everything here goes over localhost TCP through the generated stubs; nothing is faked at
 * the transport.
 */
class PluginUIServiceBridgeTest {
    private lateinit var registry: RemoteUiSurfaceRegistry
    private lateinit var server: Server
    private lateinit var channel: ManagedChannel
    private lateinit var plugin: PluginUIServiceGrpcKt.PluginUIServiceCoroutineStub

    @BeforeTest
    fun setUp() {
        // Port 0: the OS picks a free one, which cannot race a concurrently starting test the way
        // "find a free port, then bind it" can.
        registry = RemoteUiSurfaceRegistry()
        server =
            ServerBuilder
                .forPort(0)
                .addService(PluginUIServiceBridge(registry))
                .build()
                .start()
        channel = ManagedChannelBuilder.forAddress("localhost", server.port).usePlaintext().build()
        plugin = PluginUIServiceGrpcKt.PluginUIServiceCoroutineStub(channel)
    }

    @AfterTest
    fun tearDown() {
        channel.shutdownNow()
        channel.awaitTermination(SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        server.shutdownNow()
        server.awaitTermination(SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS)
    }

    @Test
    fun `a widget tree streamed by a plugin reaches the attached host component`() =
        runBlocking {
            val host = RecordingHost()
            registry.attach(PANEL, host)
            assertTrue(plugin.registerUI(registration(PANEL)).success)
            val updates = Channel<WidgetUpdate>(Channel.UNLIMITED)

            coroutineScope {
                val stream = holdStream(updates)
                updates.send(fullTree(PANEL, label = "Save"))

                awaitTrue { host.trees.isNotEmpty() }
                assertEquals(
                    "Save",
                    host.trees
                        .last()
                        .nodes
                        .getValue(BUTTON_NODE)
                        .properties["label"],
                )
                assertTrue(host.connections.last(), "a streaming surface must report itself connected")
                stream.cancel()
            }
        }

    @Test
    fun `a click the host emits reaches the plugin with its event id intact`() =
        runBlocking {
            assertTrue(plugin.registerUI(registration(PANEL)).success)
            val updates = Channel<WidgetUpdate>(Channel.UNLIMITED)

            coroutineScope {
                val stream = async { plugin.streamUI(updates.consumeAsFlow()).take(1).toList() }
                updates.send(fullTree(PANEL))
                awaitTrue { registry.surfaceOf(PANEL)?.streaming == true }

                assertTrue(registry.emit(PANEL, click(PANEL, "save_settings")))

                val event = stream.await().single()
                // The old transport could carry exactly the first of these five.
                assertEquals(PANEL, event.surfaceId)
                assertEquals(BUTTON_NODE, event.targetNodeId)
                assertEquals(UIEvent.EventCase.CLICK, event.eventCase)
                assertEquals("save_settings", event.click.eventId)
                assertTrue(event.timestamp > 0, "timestamp must survive the wire")
            }
        }

    @Test
    fun `a burst of text changes arrives in the order the user typed it`() =
        runBlocking {
            // TextChange carries the whole field value with last-write-wins semantics, so a reordered
            // pair silently reverts a character. Ordering is a correctness property, not a nicety.
            assertTrue(plugin.registerUI(registration(PANEL)).success)
            val typed = (1..BURST).map { "value-$it" }
            val updates = Channel<WidgetUpdate>(Channel.UNLIMITED)

            coroutineScope {
                val stream = async { plugin.streamUI(updates.consumeAsFlow()).take(typed.size).toList() }
                updates.send(fullTree(PANEL))
                awaitTrue { registry.surfaceOf(PANEL)?.streaming == true }

                typed.forEach { value -> assertTrue(registry.emit(PANEL, textChange(PANEL, value))) }

                assertEquals(typed, stream.await().map { it.textChange.newValue })
            }
        }

    @Test
    fun `two surfaces never see each other's events`() =
        runBlocking {
            assertTrue(plugin.registerUI(registration(PANEL)).success)
            assertTrue(plugin.registerUI(registration(TAB)).success)
            val panelUpdates = Channel<WidgetUpdate>(Channel.UNLIMITED)
            val tabUpdates = Channel<WidgetUpdate>(Channel.UNLIMITED)

            coroutineScope {
                val panelStream = async { plugin.streamUI(panelUpdates.consumeAsFlow()).take(1).toList() }
                val tabStream = async { plugin.streamUI(tabUpdates.consumeAsFlow()).take(1).toList() }
                panelUpdates.send(fullTree(PANEL))
                tabUpdates.send(fullTree(TAB))
                awaitTrue {
                    registry.surfaceOf(PANEL)?.streaming == true && registry.surfaceOf(TAB)?.streaming == true
                }

                assertTrue(registry.emit(PANEL, textChange(PANEL, "panel-only")))
                assertTrue(registry.emit(TAB, textChange(TAB, "tab-only")))

                assertEquals(listOf("panel-only"), panelStream.await().map { it.textChange.newValue })
                assertEquals(listOf("tab-only"), tabStream.await().map { it.textChange.newValue })
            }
        }

    @Test
    fun `unregistering completes the plugin's event stream and drops later events`() =
        runBlocking {
            assertTrue(plugin.registerUI(registration(PANEL)).success)
            val updates = Channel<WidgetUpdate>(Channel.UNLIMITED)

            coroutineScope {
                // No take(): the flow must end because the surface closed, not because we stopped reading.
                val stream = async { plugin.streamUI(updates.consumeAsFlow()).toList() }
                updates.send(fullTree(PANEL))
                awaitTrue { registry.surfaceOf(PANEL)?.streaming == true }
                assertTrue(registry.emit(PANEL, textChange(PANEL, "before-teardown")))

                plugin.unregisterUI(UIUnregistration.newBuilder().setSurfaceId(PANEL).build())

                assertEquals(listOf("before-teardown"), stream.await().map { it.textChange.newValue })
                assertNull(registry.surfaceOf(PANEL), "an unregistered surface must not stay in the registry")
                // The interesting half: a click that lands during teardown is dropped, not thrown.
                assertFalse(registry.emit(PANEL, click(PANEL, "too_late")))
            }
        }

    @Test
    fun `a plugin that disappears leaves its surface visibly disconnected, not frozen`() =
        runBlocking {
            val host = RecordingHost()
            registry.attach(PANEL, host)
            assertTrue(plugin.registerUI(registration(PANEL)).success)
            val updates = Channel<WidgetUpdate>(Channel.UNLIMITED)

            coroutineScope {
                val stream = holdStream(updates)
                updates.send(fullTree(PANEL))
                awaitTrue { host.connections.lastOrNull() == true }

                // A crash, not a graceful UnregisterUI: the transport dying is the only notice the host
                // gets that a plugin process is gone.
                channel.shutdownNow()
                stream.join()
            }

            awaitTrue { host.connections.lastOrNull() == false }
            assertNull(registry.surfaceOf(PANEL), "a dead stream must release the surface id for a respawn")
            assertTrue(host.trees.isNotEmpty(), "the last tree stays on screen — disconnected, not blank")
        }

    @Test
    fun `a duplicate surface id is rejected and says why`() =
        runBlocking {
            assertTrue(plugin.registerUI(registration(PANEL, process = "plugin-a")).success)

            val second = plugin.registerUI(registration(PANEL, process = "plugin-b"))

            assertFalse(second.success)
            assertContains(second.errorMessage, PANEL)
            assertContains(second.errorMessage, "plugin-a")
        }

    @Test
    fun `a blank surface id is rejected`() =
        runBlocking {
            val response = plugin.registerUI(registration(""))

            assertFalse(response.success)
            assertContains(response.errorMessage, "surface_id")
        }

    @Test
    fun `streaming a surface that was never registered fails with a usable reason`() =
        runBlocking {
            val updates = Channel<WidgetUpdate>(Channel.UNLIMITED)
            val stream = plugin.streamUI(updates.consumeAsFlow())
            updates.send(fullTree("never-registered"))

            val failure = assertFailsWith<StatusException> { stream.toList() }

            assertEquals(Status.Code.FAILED_PRECONDITION, failure.status.code)
            assertContains(failure.status.description.orEmpty(), "RegisterUI")
        }

    @Test
    fun `a stream that never names a surface is refused rather than left hanging`() =
        runBlocking {
            assertTrue(plugin.registerUI(registration(PANEL)).success)

            // StreamUI has no surface_id of its own; the request stream closing without one leaves the
            // call unbindable, and the plugin deserves an error instead of an RPC that never returns.
            val failure = assertFailsWith<StatusException> { plugin.streamUI(emptyFlow()).toList() }

            assertEquals(Status.Code.INVALID_ARGUMENT, failure.status.code)
            assertContains(failure.status.description.orEmpty(), "first WidgetUpdate")
        }

    @Test
    fun `a second stream for the same surface is refused so ordering stays intact`() =
        runBlocking {
            assertTrue(plugin.registerUI(registration(PANEL)).success)
            val first = Channel<WidgetUpdate>(Channel.UNLIMITED)

            coroutineScope {
                val held = async { plugin.streamUI(first.consumeAsFlow()).toList() }
                first.send(fullTree(PANEL))
                awaitTrue { registry.surfaceOf(PANEL)?.streaming == true }

                val second = Channel<WidgetUpdate>(Channel.UNLIMITED)
                val rival = plugin.streamUI(second.consumeAsFlow())
                second.send(fullTree(PANEL))
                val failure = assertFailsWith<StatusException> { rival.toList() }

                assertEquals(Status.Code.FAILED_PRECONDITION, failure.status.code)
                assertContains(failure.status.description.orEmpty(), "StreamUI")

                // The original stream is untouched by the rejection.
                assertTrue(registry.emit(PANEL, textChange(PANEL, "still-mine")))
                assertTrue(registry.unregister(PANEL))
                assertEquals(listOf("still-mine"), held.await().map { it.textChange.newValue })
            }
        }

    @Test
    fun `a registration's initial tree renders before any stream exists`() =
        runBlocking {
            val host = RecordingHost()
            registry.attach(PANEL, host)

            val response =
                plugin.registerUI(
                    registration(PANEL)
                        .toBuilder()
                        .setInitialTree(protoTree(label = "Ready"))
                        .build(),
                )

            assertTrue(response.success)
            assertEquals(
                "Ready",
                host.trees
                    .single()
                    .nodes
                    .getValue(BUTTON_NODE)
                    .properties["label"],
            )
            assertFalse(host.connections.last(), "no stream yet, so the surface is not connected")
        }

    // ---- Helpers ----

    private class RecordingHost : RemoteUiSurfaceHost {
        val trees = mutableListOf<SdkWidgetTree>()
        val connections = mutableListOf<Boolean>()

        override fun onTreeUpdated(tree: SdkWidgetTree) {
            trees += tree
        }

        override fun onConnectionChanged(connected: Boolean) {
            connections += connected
        }
    }

    /**
     * Open a `StreamUI` call and keep it open, discarding events.
     *
     * The returned RPC only runs while something collects the response flow, so tests that assert on the
     * *inbound* direction still have to hold the call open.
     */
    private fun kotlinx.coroutines.CoroutineScope.holdStream(updates: Channel<WidgetUpdate>): Job =
        launch {
            runCatching { plugin.streamUI(updates.consumeAsFlow()).toList() }
        }

    /** Poll until [condition] holds. The host applies inbound updates on a gRPC thread. */
    private suspend fun awaitTrue(condition: () -> Boolean) {
        withTimeout(AWAIT_TIMEOUT_MS) {
            while (!condition()) {
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun registration(
        surfaceId: String,
        process: String = "plugin-a",
    ): UIRegistration =
        UIRegistration
            .newBuilder()
            .setSurfaceId(surfaceId)
            .setSurfaceType("panel")
            .setDisplayName("Test Surface")
            .setProcessId(process)
            .build()

    private fun protoTree(label: String): ProtoWidgetTree =
        ProtoWidgetTree
            .newBuilder()
            .setRootId(BUTTON_NODE)
            .setVersion(1)
            .addNodes(
                WidgetNode
                    .newBuilder()
                    .setId(BUTTON_NODE)
                    .setType(WidgetType.WIDGET_TYPE_BUTTON)
                    .putProperties("label", label)
                    .putProperties("clickEventId", "save_settings"),
            ).build()

    private fun fullTree(
        surfaceId: String,
        label: String = "Save",
    ): WidgetUpdate =
        WidgetUpdate
            .newBuilder()
            .setSurfaceId(surfaceId)
            .setFullTree(protoTree(label))
            .build()

    private fun click(
        surfaceId: String,
        eventId: String,
    ): UIEvent =
        UIEvent
            .newBuilder()
            .setSurfaceId(surfaceId)
            .setTargetNodeId(BUTTON_NODE)
            .setTimestamp(System.currentTimeMillis())
            .setClick(ClickEvent.newBuilder().setEventId(eventId))
            .build()

    private fun textChange(
        surfaceId: String,
        value: String,
    ): UIEvent =
        UIEvent
            .newBuilder()
            .setSurfaceId(surfaceId)
            .setTargetNodeId(FIELD_NODE)
            .setTimestamp(System.currentTimeMillis())
            .setTextChange(TextChangeEvent.newBuilder().setNewValue(value))
            .build()

    private companion object {
        const val PANEL = "panel-1"
        const val TAB = "tab-1"
        const val BUTTON_NODE = "button-7"
        const val FIELD_NODE = "field-3"
        const val BURST = 50
        const val AWAIT_TIMEOUT_MS = 10_000L
        const val POLL_INTERVAL_MS = 5L
        const val SHUTDOWN_TIMEOUT_MS = 5_000L
    }
}
