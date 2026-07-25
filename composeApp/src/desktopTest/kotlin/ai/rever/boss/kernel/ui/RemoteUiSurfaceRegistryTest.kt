package ai.rever.boss.kernel.ui

import ai.rever.boss.ipc.proto.TextChangeEvent
import ai.rever.boss.ipc.proto.UIEvent
import ai.rever.boss.ipc.proto.WidgetUpdate
import ai.rever.boss.ui.sdk.DiffOperation
import ai.rever.boss.ui.sdk.WidgetNode
import ai.rever.boss.ui.sdk.WidgetProtoConverter.toProto
import ai.rever.boss.ui.sdk.WidgetProtoConverter.toProtoDiff
import ai.rever.boss.ui.sdk.WidgetTree
import ai.rever.boss.ui.sdk.WidgetType
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The rendezvous rules that let a surface's two halves start, stop and restart independently.
 *
 * A plugin process and the panel or tab it draws into have unrelated lifetimes: the plugin registers
 * whenever it comes up, the component is built when the user opens the surface, and a plugin can crash
 * and respawn under a component that never went away. Every ordering here is therefore normal, and none
 * of them may lose a tree, misroute an event, or throw at a caller.
 */
class RemoteUiSurfaceRegistryTest {
    private val registry = RemoteUiSurfaceRegistry()

    @Test
    fun `a component attached before the plugin exists still gets the first tree`() {
        val host = RecordingHost()
        registry.attach(SURFACE, host)

        val surface = registry.register(SURFACE, PROCESS).accepted()
        surface.pushTree(tree("first"))

        assertEquals(listOf("first"), host.trees.map { it.label() })
    }

    @Test
    fun `a component attached after the plugin is replayed the current state instead of rendering blank`() {
        val surface = registry.register(SURFACE, PROCESS).accepted()
        surface.pushTree(tree("already-here"))
        assertIs<SurfaceStream.Bound>(registry.openStream(SURFACE))

        val host = RecordingHost()
        registry.attach(SURFACE, host)

        assertEquals(listOf("already-here"), host.trees.map { it.label() })
        assertEquals(listOf(true), host.connections, "the surface was already streaming when we attached")
    }

    @Test
    fun `a duplicate registration is refused and names the process holding the id`() {
        registry.register(SURFACE, "plugin-a").accepted()

        val second = registry.register(SURFACE, "plugin-b")

        val rejected = assertIs<SurfaceRegistration.Rejected>(second)
        assertContains(rejected.reason, SURFACE)
        assertContains(rejected.reason, "plugin-a")
    }

    @Test
    fun `a surface id is reusable once its stream dies, so a respawned plugin can take it back`() {
        val first = registry.register(SURFACE, "plugin-a").accepted()
        assertIs<SurfaceStream.Bound>(registry.openStream(SURFACE))
        registry.closeStream(first)

        val second = registry.register(SURFACE, "plugin-a-respawned")

        assertIs<SurfaceRegistration.Accepted>(second)
    }

    @Test
    fun `a surface claimed but never streamed is reclaimable by its own process`() {
        // The gap closeStream cannot see: a plugin can die between RegisterUI returning and StreamUI
        // binding, or claim more surfaces than it streams. Refusing the respawn's claim would leave that
        // panel permanently disconnected with no way back.
        val abandoned = registry.register(SURFACE, PROCESS).accepted()

        val respawn = registry.register(SURFACE, PROCESS)

        val surface = assertIs<SurfaceRegistration.Accepted>(respawn).surface
        assertNotSame(abandoned, surface)
        assertSame(surface, registry.surfaceOf(SURFACE))
        assertFalse(abandoned.emit(textChange("to-the-dead-one")), "the reclaimed surface is closed")
    }

    @Test
    fun `a different process cannot take over a claim, streamed or not`() {
        registry.register(SURFACE, "plugin-a").accepted()

        val intruder = registry.register(SURFACE, "plugin-b")

        assertIs<SurfaceRegistration.Rejected>(intruder)
    }

    @Test
    fun `a streaming surface is not reclaimable even by its own process`() {
        // Otherwise a buggy plugin that re-registers mid-session would silently cut its own live stream.
        registry.register(SURFACE, PROCESS).accepted()
        assertIs<SurfaceStream.Bound>(registry.openStream(SURFACE))

        assertIs<SurfaceRegistration.Rejected>(registry.register(SURFACE, PROCESS))
    }

    @Test
    fun `clear closes every surface and reports the disconnection`() {
        val host = RecordingHost()
        registry.attach(SURFACE, host)
        val surface = registry.register(SURFACE, PROCESS).accepted()
        assertIs<SurfaceStream.Bound>(registry.openStream(SURFACE))

        registry.clear()

        assertNull(registry.surfaceOf(SURFACE))
        assertFalse(surface.emit(textChange("after-clear")))
        assertEquals(listOf(false, true, false), host.connections)
    }

    @Test
    fun `a registration's descriptor is retained on the surface`() {
        val descriptor =
            RemoteUiSurfaceDescriptor(
                surfaceType = "panel",
                displayName = "Inbox",
                iconName = "mail",
                defaultSlot = "left.top.top",
            )

        val surface = registry.register(SURFACE, PROCESS, descriptor).accepted()

        assertEquals(descriptor, surface.descriptor)
    }

    @Test
    fun `an attached component follows a plugin across a crash and respawn`() {
        val host = RecordingHost()
        registry.attach(SURFACE, host)
        val crashed = registry.register(SURFACE, PROCESS).accepted()
        assertIs<SurfaceStream.Bound>(registry.openStream(SURFACE))
        registry.closeStream(crashed)

        val respawned = registry.register(SURFACE, PROCESS).accepted()
        assertIs<SurfaceStream.Bound>(registry.openStream(SURFACE))
        respawned.pushTree(tree("after-respawn"))

        assertEquals(listOf("after-respawn"), host.trees.map { it.label() })
        assertEquals(listOf(false, true, false, true), host.connections)
    }

    @Test
    fun `an event emitted with no plugin behind the surface is reported undeliverable, not thrown`() {
        assertFalse(registry.emit(SURFACE, textChange("nobody-home")))
    }

    @Test
    fun `an event emitted after the surface closes is dropped`() {
        val surface = registry.register(SURFACE, PROCESS).accepted()
        assertTrue(registry.emit(SURFACE, textChange("in-time")))

        assertTrue(registry.unregister(SURFACE))

        assertFalse(registry.emit(SURFACE, textChange("too-late")))
        assertFalse(surface.emit(textChange("too-late-direct")), "a closed surface refuses directly too")
        assertNull(registry.surfaceOf(SURFACE))
    }

    @Test
    fun `a graceful shutdown reports the disconnection once, not once per teardown path`() {
        // UnregisterUI closes the surface, which ends the event queue, which ends the StreamUI call, whose
        // own teardown closes the surface again. The component must not be told twice.
        val host = RecordingHost()
        registry.attach(SURFACE, host)
        val surface = registry.register(SURFACE, PROCESS).accepted()
        assertIs<SurfaceStream.Bound>(registry.openStream(SURFACE))

        assertTrue(registry.unregister(SURFACE))
        registry.closeStream(surface)

        assertEquals(listOf(false, true, false), host.connections)
    }

    @Test
    fun `detaching is scoped to the component that owns the attachment`() {
        val replaced = RecordingHost()
        val current = RecordingHost()
        registry.attach(SURFACE, replaced)
        registry.attach(SURFACE, current)

        // A component disposed after its successor attached must not take the successor's slot with it.
        registry.detach(SURFACE, replaced)
        registry.register(SURFACE, PROCESS).accepted().pushTree(tree("still-delivered"))

        assertEquals(listOf("still-delivered"), current.trees.map { it.label() })
        assertTrue(replaced.trees.isEmpty())
    }

    @Test
    fun `a plugin that stops reading sheds the oldest events and keeps the newest in order`() =
        runBlocking {
            // Bounded, not unlimited: the reader is in another process and can stop reading, and the
            // queue lives in the host. Shedding from the head keeps last-write-wins pointing at the
            // current value — dropping the newest would leave the plugin holding a stale one forever.
            val surface = registry.register(SURFACE, PROCESS).accepted()
            val overflow = 5
            val sent = (1..RemoteUiSurface.OUTGOING_BUFFER + overflow).map { "value-$it" }

            sent.forEach { value -> assertTrue(surface.emit(textChange(value))) }

            assertEquals(overflow.toLong(), surface.shedEventCount)
            val drained = surface.events().take(RemoteUiSurface.OUTGOING_BUFFER).toList()
            assertEquals(sent.drop(overflow), drained.map { it.textChange.newValue })
        }

    @Test
    fun `a full tree replaces the surface's tree`() {
        val host = RecordingHost()
        registry.attach(SURFACE, host)
        val surface = registry.register(SURFACE, PROCESS).accepted()

        surface.applyUpdate(
            WidgetUpdate
                .newBuilder()
                .setSurfaceId(SURFACE)
                .setFullTree(tree("from-wire").toProto())
                .build(),
        )

        assertEquals("from-wire", surface.tree?.label())
        assertEquals(listOf("from-wire"), host.trees.map { it.label() })
    }

    @Test
    fun `a diff applies on top of the surface's current tree`() {
        val host = RecordingHost()
        registry.attach(SURFACE, host)
        val surface = registry.register(SURFACE, PROCESS).accepted()
        surface.pushTree(tree("before").copy(version = 4))

        surface.applyUpdate(
            WidgetUpdate
                .newBuilder()
                .setSurfaceId(SURFACE)
                .setDiff(
                    listOf<DiffOperation>(
                        DiffOperation.NodeUpdated(NODE, mapOf("label" to "after"), null),
                    ).toProtoDiff(baseVersion = 4, newVersion = 5),
                ).build(),
        )

        assertEquals("after", surface.tree?.label())
        assertEquals(5L, surface.tree?.version, "the sender's version must be kept so the next base check works")
        assertEquals(listOf("before", "after"), host.trees.map { it.label() })
    }

    @Test
    fun `a diff arriving before any full tree is dropped rather than applied to nothing`() {
        val host = RecordingHost()
        registry.attach(SURFACE, host)
        val surface = registry.register(SURFACE, PROCESS).accepted()

        surface.applyUpdate(
            WidgetUpdate
                .newBuilder()
                .setSurfaceId(SURFACE)
                .setDiff(
                    listOf<DiffOperation>(DiffOperation.NodeRemoved(NODE)).toProtoDiff(baseVersion = 1, newVersion = 2),
                ).build(),
        )

        assertNull(surface.tree)
        assertTrue(host.trees.isEmpty(), "nothing to render, so nothing is published")
    }

    @Test
    fun `an update with neither a tree nor a diff leaves the surface alone`() {
        val surface = registry.register(SURFACE, PROCESS).accepted()
        surface.pushTree(tree("kept"))

        surface.applyUpdate(WidgetUpdate.newBuilder().setSurfaceId(SURFACE).build())

        assertEquals("kept", surface.tree?.label())
    }

    @Test
    fun `a stream for an unregistered surface is refused with an actionable reason`() {
        val refused = assertIs<SurfaceStream.Refused>(registry.openStream("never-registered"))

        assertContains(refused.reason, "RegisterUI")
    }

    @Test
    fun `only one stream may hold a surface at a time`() {
        registry.register(SURFACE, PROCESS).accepted()
        assertIs<SurfaceStream.Bound>(registry.openStream(SURFACE))

        val refused = assertIs<SurfaceStream.Refused>(registry.openStream(SURFACE))

        assertContains(refused.reason, "StreamUI")
    }

    @Test
    fun `unregistering a surface nobody registered is not an error`() {
        assertFalse(registry.unregister("never-registered"))
    }

    // ---- Helpers ----

    private class RecordingHost : RemoteUiSurfaceHost {
        val trees = mutableListOf<WidgetTree>()
        val connections = mutableListOf<Boolean>()

        override fun onTreeUpdated(tree: WidgetTree) {
            trees += tree
        }

        override fun onConnectionChanged(connected: Boolean) {
            connections += connected
        }
    }

    private fun SurfaceRegistration.accepted(): RemoteUiSurface = assertIs<SurfaceRegistration.Accepted>(this).surface

    private fun tree(label: String): WidgetTree =
        WidgetTree(
            rootId = NODE,
            nodes = mapOf(NODE to WidgetNode(NODE, WidgetType.TEXT, mapOf("label" to label))),
        )

    private fun WidgetTree.label(): String? = nodes[NODE]?.properties?.get("label")

    private fun textChange(value: String): UIEvent =
        UIEvent
            .newBuilder()
            .setSurfaceId(SURFACE)
            .setTargetNodeId(NODE)
            .setTextChange(TextChangeEvent.newBuilder().setNewValue(value))
            .build()

    private companion object {
        const val SURFACE = "panel-1"
        const val PROCESS = "plugin-a"
        const val NODE = "node-1"
    }
}
