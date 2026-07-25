package ai.rever.boss.ui.sdk

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WidgetDiffEngineTest {
    private fun simpleTree(): WidgetTree {
        val colId = "col1"
        val textId = "text1"
        return WidgetTree(
            rootId = colId,
            nodes =
                mapOf(
                    colId to WidgetNode(colId, WidgetType.COLUMN, childIds = listOf(textId)),
                    textId to WidgetNode(textId, WidgetType.TEXT, properties = mapOf("value" to "Hello")),
                ),
        )
    }

    @Test
    fun `diff identical trees produces no operations`() {
        val tree = simpleTree()
        val ops = WidgetDiffEngine.diff(tree, tree)
        assertTrue(ops.isEmpty(), "Expected empty diff for identical trees, got: $ops")
    }

    @Test
    fun `add node produces NodeAdded`() {
        val base = simpleTree()
        val colId = "col1"
        val textId = "text1"
        val text2Id = "text2"
        val newTree =
            WidgetTree(
                rootId = colId,
                nodes =
                    mapOf(
                        colId to WidgetNode(colId, WidgetType.COLUMN, childIds = listOf(textId, text2Id)),
                        textId to WidgetNode(textId, WidgetType.TEXT, properties = mapOf("value" to "Hello")),
                        text2Id to WidgetNode(text2Id, WidgetType.TEXT, properties = mapOf("value" to "World")),
                    ),
            )

        val ops = WidgetDiffEngine.diff(base, newTree)
        val added = ops.filterIsInstance<DiffOperation.NodeAdded>()
        assertEquals(1, added.size)
        assertEquals(text2Id, added[0].node.id)
        assertEquals(colId, added[0].parentId)
        assertEquals(1, added[0].index)
    }

    @Test
    fun `remove node produces NodeRemoved`() {
        val base = simpleTree()
        val colId = "col1"
        val textId = "text1"
        val newTree =
            WidgetTree(
                rootId = colId,
                nodes =
                    mapOf(
                        colId to WidgetNode(colId, WidgetType.COLUMN, childIds = emptyList()),
                    ),
            )

        val ops = WidgetDiffEngine.diff(base, newTree)
        val removed = ops.filterIsInstance<DiffOperation.NodeRemoved>()
        assertEquals(1, removed.size)
        assertEquals(textId, removed[0].nodeId)
    }

    @Test
    fun `change property produces NodeUpdated`() {
        val base = simpleTree()
        val colId = "col1"
        val textId = "text1"
        val newTree =
            WidgetTree(
                rootId = colId,
                nodes =
                    mapOf(
                        colId to WidgetNode(colId, WidgetType.COLUMN, childIds = listOf(textId)),
                        textId to WidgetNode(textId, WidgetType.TEXT, properties = mapOf("value" to "Changed")),
                    ),
            )

        val ops = WidgetDiffEngine.diff(base, newTree)
        val updated = ops.filterIsInstance<DiffOperation.NodeUpdated>()
        assertEquals(1, updated.size)
        assertEquals(textId, updated[0].nodeId)
        assertEquals("Changed", updated[0].changedProperties["value"])
    }

    @Test
    fun `change modifier produces NodeUpdated with newModifier`() {
        val base = simpleTree()
        val colId = "col1"
        val textId = "text1"
        val newModifier = WidgetModifier(width = 100, height = 50)
        val newTree =
            WidgetTree(
                rootId = colId,
                nodes =
                    mapOf(
                        colId to WidgetNode(colId, WidgetType.COLUMN, childIds = listOf(textId)),
                        textId to
                            WidgetNode(
                                textId,
                                WidgetType.TEXT,
                                properties = mapOf("value" to "Hello"),
                                modifier = newModifier,
                            ),
                    ),
            )

        val ops = WidgetDiffEngine.diff(base, newTree)
        val updated = ops.filterIsInstance<DiffOperation.NodeUpdated>()
        assertEquals(1, updated.size)
        assertEquals(textId, updated[0].nodeId)
        assertEquals(newModifier, updated[0].newModifier)
    }

    @Test
    fun `apply NodeAdded matches expected tree`() {
        val base = simpleTree()
        val colId = "col1"
        val textId = "text1"
        val text2Id = "text2"
        val newNode = WidgetNode(text2Id, WidgetType.TEXT, properties = mapOf("value" to "World"))

        val ops = listOf(DiffOperation.NodeAdded(newNode, colId, 1))
        val result = WidgetDiffEngine.apply(base, ops)

        assertEquals(3, result.nodes.size)
        val col = result.nodes[colId]!!
        assertEquals(listOf(textId, text2Id), col.childIds)
        assertEquals("World", result.nodes[text2Id]!!.properties["value"])
    }

    @Test
    fun `apply NodeRemoved matches expected tree`() {
        val colId = "col1"
        val text1Id = "text1"
        val text2Id = "text2"
        val base =
            WidgetTree(
                rootId = colId,
                nodes =
                    mapOf(
                        colId to WidgetNode(colId, WidgetType.COLUMN, childIds = listOf(text1Id, text2Id)),
                        text1Id to WidgetNode(text1Id, WidgetType.TEXT, properties = mapOf("value" to "A")),
                        text2Id to WidgetNode(text2Id, WidgetType.TEXT, properties = mapOf("value" to "B")),
                    ),
            )

        val ops = listOf(DiffOperation.NodeRemoved(text1Id))
        val result = WidgetDiffEngine.apply(base, ops)

        assertEquals(2, result.nodes.size)
        assertEquals(listOf(text2Id), result.nodes[colId]!!.childIds)
    }

    @Test
    fun `apply NodeUpdated changes properties`() {
        val base = simpleTree()
        val textId = "text1"

        val ops = listOf(DiffOperation.NodeUpdated(textId, mapOf("value" to "Updated"), null))
        val result = WidgetDiffEngine.apply(base, ops)

        assertEquals("Updated", result.nodes[textId]!!.properties["value"])
    }

    // ---- Subtree adds must not double-link children (issue #34 item 7) ----

    /**
     * A `NodeAdded` payload carries the added node's own `childIds`, so a subtree add links the
     * child from the parent's payload *and* from the child's own op. Applying them parent-first used
     * to link the child twice (and the renderer drew the subtree twice); insertion is now idempotent,
     * so both orders converge.
     */
    private fun subtreeAddOps(): List<DiffOperation> {
        val rowId = "row1"
        val innerId = "inner1"
        val row = WidgetNode(rowId, WidgetType.ROW, childIds = listOf(innerId))
        val inner = WidgetNode(innerId, WidgetType.TEXT, properties = mapOf("value" to "Nested"))
        return listOf(
            DiffOperation.NodeAdded(row, "col1", 1),
            DiffOperation.NodeAdded(inner, rowId, 0),
        )
    }

    @Test
    fun `apply subtree add parent-first does not duplicate child links`() {
        val result = WidgetDiffEngine.apply(simpleTree(), subtreeAddOps())

        assertEquals(listOf("inner1"), result.nodes["row1"]!!.childIds)
        assertEquals(listOf("text1", "row1"), result.nodes["col1"]!!.childIds)
    }

    @Test
    fun `apply subtree add child-first matches parent-first`() {
        val parentFirst = WidgetDiffEngine.apply(simpleTree(), subtreeAddOps())
        val childFirst = WidgetDiffEngine.apply(simpleTree(), subtreeAddOps().reversed())

        assertEquals(parentFirst.nodes, childFirst.nodes)
    }

    @Test
    fun `diff then apply of a subtree add matches the new tree exactly`() {
        val base = simpleTree()
        val expected =
            WidgetTree(
                rootId = "col1",
                nodes =
                    mapOf(
                        "col1" to WidgetNode("col1", WidgetType.COLUMN, childIds = listOf("text1", "row1")),
                        "text1" to WidgetNode("text1", WidgetType.TEXT, properties = mapOf("value" to "Hello")),
                        "row1" to WidgetNode("row1", WidgetType.ROW, childIds = listOf("inner1")),
                        "inner1" to WidgetNode("inner1", WidgetType.TEXT, properties = mapOf("value" to "Nested")),
                    ),
            )

        val result = WidgetDiffEngine.apply(base, WidgetDiffEngine.diff(base, expected))

        assertEquals(expected.nodes, result.nodes)
    }

    @Test
    fun `apply NodeMoved within the same parent re-indexes without duplicating`() {
        val base =
            WidgetTree(
                rootId = "col1",
                nodes =
                    mapOf(
                        "col1" to WidgetNode("col1", WidgetType.COLUMN, childIds = listOf("a", "b")),
                        "a" to WidgetNode("a", WidgetType.TEXT),
                        "b" to WidgetNode("b", WidgetType.TEXT),
                    ),
            )

        val result = WidgetDiffEngine.apply(base, listOf(DiffOperation.NodeMoved("a", "col1", 1)))

        assertEquals(listOf("b", "a"), result.nodes["col1"]!!.childIds)
    }

    @Test
    fun `apply diff round-trip matches new tree`() {
        val base = simpleTree()
        val colId = "col1"
        val textId = "text1"
        val text2Id = "text2"
        val expected =
            WidgetTree(
                rootId = colId,
                nodes =
                    mapOf(
                        colId to WidgetNode(colId, WidgetType.COLUMN, childIds = listOf(textId, text2Id)),
                        textId to WidgetNode(textId, WidgetType.TEXT, properties = mapOf("value" to "Hello")),
                        text2Id to WidgetNode(text2Id, WidgetType.TEXT, properties = mapOf("value" to "World")),
                    ),
            )

        val ops = WidgetDiffEngine.diff(base, expected)
        val result = WidgetDiffEngine.apply(base, ops)

        assertEquals(expected.nodes.keys, result.nodes.keys)
        assertEquals(expected.nodes[colId]!!.childIds, result.nodes[colId]!!.childIds)
        assertEquals("World", result.nodes[text2Id]!!.properties["value"])
    }
}
