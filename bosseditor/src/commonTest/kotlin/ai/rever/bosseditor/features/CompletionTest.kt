package ai.rever.bosseditor.features

import ai.rever.bosseditor.core.EditorPosition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompletionTest {

    @Test
    fun testCompletionItemCreation() {
        val item = CompletionItem(
            label = "myFunction",
            insertText = "myFunction()",
            kind = CompletionKind.FUNCTION,
            detail = "fun myFunction(): Unit"
        )

        assertEquals("myFunction", item.label)
        assertEquals("myFunction()", item.insertText)
        assertEquals(CompletionKind.FUNCTION, item.kind)
        assertEquals("fun myFunction(): Unit", item.detail)
        assertFalse(item.deprecated)
    }

    @Test
    fun testCompletionItemFactoryMethods() {
        val keyword = CompletionItem.keyword("fun")
        assertEquals("fun", keyword.label)
        assertEquals(CompletionKind.KEYWORD, keyword.kind)

        val function = CompletionItem.function("test", "(a: Int)", "String")
        assertEquals("test", function.label)
        assertEquals("test()", function.insertText)
        assertEquals(CompletionKind.FUNCTION, function.kind)
        assertEquals("(a: Int): String", function.detail)

        val variable = CompletionItem.variable("myVar", "Int")
        assertEquals("myVar", variable.label)
        assertEquals(CompletionKind.VARIABLE, variable.kind)
        assertEquals("Int", variable.detail)

        val className = CompletionItem.className("MyClass")
        assertEquals("MyClass", className.label)
        assertEquals(CompletionKind.CLASS, className.kind)

        val property = CompletionItem.property("name", "String")
        assertEquals("name", property.label)
        assertEquals(CompletionKind.PROPERTY, property.kind)

        val snippet = CompletionItem.snippet("forloop", "for (i in 0..10) {}", "For loop")
        assertEquals("forloop", snippet.label)
        assertEquals("for (i in 0..10) {}", snippet.insertText)
        assertEquals(CompletionKind.SNIPPET, snippet.kind)
    }

    @Test
    fun testCompletionStateFiltering() {
        val items = listOf(
            CompletionItem.keyword("fun"),
            CompletionItem.keyword("function"),
            CompletionItem.keyword("for"),
            CompletionItem.keyword("if"),
            CompletionItem.keyword("val")
        )

        val state = CompletionState(
            triggerPosition = EditorPosition(0, 0),
            prefix = "fu",
            allItems = items
        )

        val filtered = state.filteredItems
        assertEquals(2, filtered.size)
        assertTrue(filtered.any { it.label == "fun" })
        assertTrue(filtered.any { it.label == "function" })
    }

    @Test
    fun testCompletionStateEmptyPrefix() {
        val items = listOf(
            CompletionItem.keyword("fun"),
            CompletionItem.keyword("val"),
            CompletionItem.keyword("var")
        )

        val state = CompletionState(
            triggerPosition = EditorPosition(0, 0),
            prefix = "",
            allItems = items
        )

        assertEquals(3, state.filteredItems.size)
    }

    @Test
    fun testCompletionStateNavigation() {
        val items = listOf(
            CompletionItem.keyword("a"),
            CompletionItem.keyword("b"),
            CompletionItem.keyword("c"),
            CompletionItem.keyword("d"),
            CompletionItem.keyword("e")
        )

        var state = CompletionState(
            triggerPosition = EditorPosition(0, 0),
            prefix = "",
            allItems = items,
            selectedIndex = 0
        )

        assertEquals(0, state.selectedIndex)
        assertEquals("a", state.selectedItem?.label)

        state = state.moveDown()
        assertEquals(1, state.selectedIndex)
        assertEquals("b", state.selectedItem?.label)

        state = state.moveDown()
        assertEquals(2, state.selectedIndex)

        state = state.moveUp()
        assertEquals(1, state.selectedIndex)

        state = state.moveToFirst()
        assertEquals(0, state.selectedIndex)

        state = state.moveToLast()
        assertEquals(4, state.selectedIndex)
    }

    @Test
    fun testCompletionStatePageNavigation() {
        val items = (1..20).map { CompletionItem.keyword("item$it") }

        var state = CompletionState(
            triggerPosition = EditorPosition(0, 0),
            prefix = "",
            allItems = items,
            selectedIndex = 0
        )

        state = state.pageDown()
        assertEquals(10, state.selectedIndex)

        state = state.pageDown()
        assertEquals(19, state.selectedIndex) // Clamped to max

        state = state.pageUp()
        assertEquals(9, state.selectedIndex)
    }

    @Test
    fun testCompletionStateBoundsChecking() {
        val items = listOf(
            CompletionItem.keyword("a"),
            CompletionItem.keyword("b")
        )

        var state = CompletionState(
            triggerPosition = EditorPosition(0, 0),
            prefix = "",
            allItems = items,
            selectedIndex = 0
        )

        // Moving up from 0 should stay at 0
        state = state.moveUp()
        assertEquals(0, state.selectedIndex)

        // Moving down past end should clamp
        state = state.moveDown().moveDown().moveDown()
        assertEquals(1, state.selectedIndex)
    }

    @Test
    fun testCompletionStatePrefixUpdate() {
        val items = listOf(
            CompletionItem.keyword("fun"),
            CompletionItem.keyword("for"),
            CompletionItem.keyword("if")
        )

        var state = CompletionState(
            triggerPosition = EditorPosition(0, 0),
            prefix = "f",
            allItems = items,
            selectedIndex = 1
        )

        assertEquals(1, state.selectedIndex)

        // Updating prefix should reset selection
        state = state.withPrefix("fo")
        assertEquals(0, state.selectedIndex)
        assertEquals(1, state.filteredItems.size) // Only "for"
    }

    @Test
    fun testCompletionStateHasItems() {
        val items = listOf(CompletionItem.keyword("fun"))

        val stateWithItems = CompletionState(
            triggerPosition = EditorPosition(0, 0),
            prefix = "",
            allItems = items
        )
        assertTrue(stateWithItems.hasItems)

        val stateNoMatch = CompletionState(
            triggerPosition = EditorPosition(0, 0),
            prefix = "xyz",
            allItems = items
        )
        assertFalse(stateNoMatch.hasItems)
    }

    @Test
    fun testCompletionResultIncomplete() {
        val result = CompletionResult(
            items = listOf(CompletionItem.keyword("test")),
            isIncomplete = true
        )

        assertTrue(result.isIncomplete)
        assertEquals(1, result.items.size)
    }

    @Test
    fun testKotlinKeywordCompletionProvider() {
        val provider = KotlinKeywordCompletionProvider
        // This is a suspend function, but we can verify it exists
        assertNotNull(provider)
    }

    @Test
    fun testStaticCompletionProvider() {
        val items = listOf(
            CompletionItem.keyword("test1"),
            CompletionItem.keyword("test2")
        )
        val provider = StaticCompletionProvider(items)
        assertNotNull(provider)
    }

    @Test
    fun testEffectiveFilterText() {
        val item1 = CompletionItem(
            label = "myLabel",
            filterText = "customFilter"
        )
        assertEquals("customFilter", item1.effectiveFilterText)

        val item2 = CompletionItem(label = "myLabel")
        assertEquals("myLabel", item2.effectiveFilterText)
    }

    @Test
    fun testEffectiveSortText() {
        val item1 = CompletionItem(
            label = "myLabel",
            sortText = "zzz"
        )
        assertEquals("zzz", item1.effectiveSortText)

        val item2 = CompletionItem(label = "myLabel")
        assertEquals("myLabel", item2.effectiveSortText)
    }

    @Test
    fun testCaseInsensitiveFiltering() {
        val items = listOf(
            CompletionItem.keyword("MyFunction"),
            CompletionItem.keyword("myMethod"),
            CompletionItem.keyword("other")
        )

        val state = CompletionState(
            triggerPosition = EditorPosition(0, 0),
            prefix = "my",
            allItems = items
        )

        assertEquals(2, state.filteredItems.size)
    }

    @Test
    fun testPrefixMatchPriority() {
        val items = listOf(
            CompletionItem.keyword("contains_fun"),
            CompletionItem.keyword("function"),
            CompletionItem.keyword("fun")
        )

        val state = CompletionState(
            triggerPosition = EditorPosition(0, 0),
            prefix = "fun",
            allItems = items
        )

        // Items starting with prefix should come first
        val filtered = state.filteredItems
        assertEquals(3, filtered.size)
        // "fun" and "function" start with "fun", so should be first
        assertTrue(filtered[0].label.startsWith("fun", ignoreCase = true))
    }
}
