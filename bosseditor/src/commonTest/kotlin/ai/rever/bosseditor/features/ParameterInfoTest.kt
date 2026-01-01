package ai.rever.bosseditor.features

import ai.rever.bosseditor.core.EditorPosition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParameterInfoTest {

    @Test
    fun testParameterDefinitionCreation() {
        val param = ParameterDefinition(
            name = "count",
            type = "Int",
            defaultValue = "0",
            documentation = "The count of items"
        )

        assertEquals("count", param.name)
        assertEquals("Int", param.type)
        assertEquals("0", param.defaultValue)
        assertEquals("The count of items", param.documentation)
        assertTrue(param.isOptional)
    }

    @Test
    fun testParameterDefinitionRequired() {
        val param = ParameterDefinition(
            name = "name",
            type = "String"
        )

        assertFalse(param.isOptional)
        assertNull(param.defaultValue)
    }

    @Test
    fun testParameterDefinitionDisplayString() {
        val param1 = ParameterDefinition(
            name = "x",
            type = "Int"
        )
        assertEquals("x: Int", param1.displayString)

        val param2 = ParameterDefinition(
            name = "y",
            type = "String",
            defaultValue = "\"hello\""
        )
        assertEquals("y: String = \"hello\"", param2.displayString)

        val param3 = ParameterDefinition(name = "z")
        assertEquals("z", param3.displayString)
    }

    @Test
    fun testFunctionSignatureCreation() {
        val signature = FunctionSignature(
            name = "myFunction",
            parameters = listOf(
                ParameterDefinition("a", "Int"),
                ParameterDefinition("b", "String")
            ),
            returnType = "Unit",
            documentation = "A test function"
        )

        assertEquals("myFunction", signature.name)
        assertEquals(2, signature.parameters.size)
        assertEquals("Unit", signature.returnType)
        assertEquals("A test function", signature.documentation)
    }

    @Test
    fun testFunctionSignatureDisplayString() {
        val signature = FunctionSignature(
            name = "greet",
            parameters = listOf(
                ParameterDefinition("name", "String"),
                ParameterDefinition("count", "Int", "1")
            ),
            returnType = "String"
        )

        assertEquals("greet(name: String, count: Int = 1): String", signature.displayString)
    }

    @Test
    fun testFunctionSignatureEmptyParams() {
        val signature = FunctionSignature(
            name = "noParams",
            parameters = emptyList(),
            returnType = "Unit"
        )

        assertEquals("noParams(): Unit", signature.displayString)
    }

    @Test
    fun testFunctionSignatureNoReturnType() {
        val signature = FunctionSignature(
            name = "voidFunc",
            parameters = listOf(ParameterDefinition("x", "Int"))
        )

        assertEquals("voidFunc(x: Int)", signature.displayString)
    }

    @Test
    fun testFunctionSignatureGetParameter() {
        val signature = FunctionSignature(
            name = "test",
            parameters = listOf(
                ParameterDefinition("a", "Int"),
                ParameterDefinition("b", "String"),
                ParameterDefinition("c", "Boolean")
            )
        )

        assertEquals("a", signature.getParameter(0)?.name)
        assertEquals("b", signature.getParameter(1)?.name)
        assertEquals("c", signature.getParameter(2)?.name)
        assertNull(signature.getParameter(3))
        assertNull(signature.getParameter(-1))
    }

    @Test
    fun testFunctionSignatureParseSimple() {
        val signature = FunctionSignature.parse("myFunc(x: Int, y: String): Boolean")

        assertNotNull(signature)
        assertEquals("myFunc", signature.name)
        assertEquals(2, signature.parameters.size)
        assertEquals("x", signature.parameters[0].name)
        assertEquals("Int", signature.parameters[0].type)
        assertEquals("y", signature.parameters[1].name)
        assertEquals("String", signature.parameters[1].type)
        assertEquals("Boolean", signature.returnType)
    }

    @Test
    fun testFunctionSignatureParseNoParams() {
        val signature = FunctionSignature.parse("noArgs(): Unit")

        assertNotNull(signature)
        assertEquals("noArgs", signature.name)
        assertTrue(signature.parameters.isEmpty())
        assertEquals("Unit", signature.returnType)
    }

    @Test
    fun testFunctionSignatureParseNoReturn() {
        val signature = FunctionSignature.parse("voidFunc(a: Int)")

        assertNotNull(signature)
        assertEquals("voidFunc", signature.name)
        assertEquals(1, signature.parameters.size)
        assertNull(signature.returnType)
    }

    @Test
    fun testFunctionSignatureParseInvalid() {
        assertNull(FunctionSignature.parse(""))
        assertNull(FunctionSignature.parse("noParens"))
        assertNull(FunctionSignature.parse("(x: Int)")) // No name
        assertNull(FunctionSignature.parse("func(")) // Unclosed paren
    }

    @Test
    fun testParameterInfoStateCreation() {
        val signatures = listOf(
            FunctionSignature("func", listOf(ParameterDefinition("x", "Int"))),
            FunctionSignature("func", listOf(
                ParameterDefinition("x", "Int"),
                ParameterDefinition("y", "String")
            ))
        )

        val state = ParameterInfoState(
            signatures = signatures,
            activeSignatureIndex = 0,
            activeParameterIndex = 0,
            position = EditorPosition(5, 10)
        )

        assertEquals(2, state.overloadCount)
        assertTrue(state.hasOverloads)
        assertEquals(signatures[0], state.activeSignature)
        assertEquals("x", state.activeParameter?.name)
    }

    @Test
    fun testParameterInfoStateSingleSignature() {
        val state = ParameterInfoState(
            signatures = listOf(
                FunctionSignature("single", listOf(ParameterDefinition("a", "Int")))
            ),
            activeSignatureIndex = 0,
            activeParameterIndex = 0,
            position = EditorPosition(0, 0)
        )

        assertFalse(state.hasOverloads)
        assertEquals(1, state.overloadCount)
    }

    @Test
    fun testParameterInfoStateNavigation() {
        val signatures = listOf(
            FunctionSignature("f1", listOf(ParameterDefinition("a", "Int"))),
            FunctionSignature("f2", listOf(ParameterDefinition("b", "String"))),
            FunctionSignature("f3", listOf(ParameterDefinition("c", "Boolean")))
        )

        var state = ParameterInfoState(
            signatures = signatures,
            activeSignatureIndex = 0,
            activeParameterIndex = 0,
            position = EditorPosition(0, 0)
        )

        assertEquals(0, state.activeSignatureIndex)

        state = state.nextOverload()
        assertEquals(1, state.activeSignatureIndex)

        state = state.nextOverload()
        assertEquals(2, state.activeSignatureIndex)

        // Should wrap around
        state = state.nextOverload()
        assertEquals(0, state.activeSignatureIndex)

        state = state.previousOverload()
        assertEquals(2, state.activeSignatureIndex)
    }

    @Test
    fun testParameterInfoStateWithActiveSignature() {
        val signatures = listOf(
            FunctionSignature("f1", listOf(ParameterDefinition("a", "Int"))),
            FunctionSignature("f2", listOf(ParameterDefinition("b", "String")))
        )

        var state = ParameterInfoState(
            signatures = signatures,
            activeSignatureIndex = 0,
            activeParameterIndex = 0,
            position = EditorPosition(0, 0)
        )

        state = state.withActiveSignature(1)
        assertEquals(1, state.activeSignatureIndex)

        // Should clamp to valid range
        state = state.withActiveSignature(10)
        assertEquals(1, state.activeSignatureIndex)

        state = state.withActiveSignature(-5)
        assertEquals(0, state.activeSignatureIndex)
    }

    @Test
    fun testParameterInfoStateWithActiveParameter() {
        val signature = FunctionSignature(
            "test",
            listOf(
                ParameterDefinition("a", "Int"),
                ParameterDefinition("b", "String"),
                ParameterDefinition("c", "Boolean")
            )
        )

        var state = ParameterInfoState(
            signatures = listOf(signature),
            activeSignatureIndex = 0,
            activeParameterIndex = 0,
            position = EditorPosition(0, 0)
        )

        assertEquals("a", state.activeParameter?.name)

        state = state.withActiveParameter(1)
        assertEquals("b", state.activeParameter?.name)

        state = state.withActiveParameter(2)
        assertEquals("c", state.activeParameter?.name)

        // Should clamp
        state = state.withActiveParameter(10)
        assertEquals("c", state.activeParameter?.name)
    }

    @Test
    fun testStaticParameterInfoProvider() {
        val signatures = mapOf(
            "println" to listOf(
                FunctionSignature("println", listOf(ParameterDefinition("message", "Any?")))
            )
        )

        val provider = StaticParameterInfoProvider(signatures)
        assertNotNull(provider)

        val printlnSignatures = provider.getSignaturesFor("println")
        assertEquals(1, printlnSignatures?.size)

        assertNull(provider.getSignaturesFor("nonexistent"))
    }

    @Test
    fun testEmptySignatureList() {
        val state = ParameterInfoState(
            signatures = emptyList(),
            activeSignatureIndex = 0,
            activeParameterIndex = 0,
            position = EditorPosition(0, 0)
        )

        assertFalse(state.hasOverloads)
        assertEquals(0, state.overloadCount)
        assertNull(state.activeSignature)
        assertNull(state.activeParameter)
    }

    @Test
    fun testParameterDefinitionWithoutType() {
        val param = ParameterDefinition(name = "varargs")
        assertEquals("varargs", param.displayString)
        assertNull(param.type)
    }

    @Test
    fun testFunctionSignatureWithManyParameters() {
        val params = (1..10).map { ParameterDefinition("p$it", "Int") }
        val signature = FunctionSignature("manyParams", params)

        assertEquals(10, signature.parameters.size)
        assertTrue(signature.displayString.contains("p1: Int"))
        assertTrue(signature.displayString.contains("p10: Int"))
    }
}
