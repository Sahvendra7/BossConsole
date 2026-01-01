package ai.rever.bosseditor.features

import ai.rever.bosseditor.core.EditorPosition

/**
 * Represents a single parameter in a function signature.
 *
 * @property name The parameter name
 * @property type The parameter type (if available)
 * @property defaultValue Default value if parameter is optional
 * @property documentation Optional documentation for this parameter
 */
data class ParameterDefinition(
    val name: String,
    val type: String? = null,
    val defaultValue: String? = null,
    val documentation: String? = null
) {
    /** Whether this parameter has a default value (is optional) */
    val isOptional: Boolean get() = defaultValue != null

    /** Display string for the parameter */
    val displayString: String get() = buildString {
        append(name)
        type?.let { append(": $it") }
        defaultValue?.let { append(" = $it") }
    }
}

/**
 * Represents a function signature for parameter info display.
 *
 * @property name The function name
 * @property parameters List of parameters
 * @property returnType Return type (if available)
 * @property documentation Optional documentation for the function
 */
data class FunctionSignature(
    val name: String,
    val parameters: List<ParameterDefinition>,
    val returnType: String? = null,
    val documentation: String? = null
) {
    /** Display string for the full signature */
    val displayString: String get() = buildString {
        append(name)
        append("(")
        append(parameters.joinToString(", ") { it.displayString })
        append(")")
        returnType?.let { append(": $it") }
    }

    /** Gets the parameter at the given index, or null if out of bounds */
    fun getParameter(index: Int): ParameterDefinition? = parameters.getOrNull(index)

    companion object {
        /** Creates a signature from a simple string like "fun(a: Int, b: String): Unit" */
        fun parse(signature: String): FunctionSignature? {
            // Simple parser - real implementation would use PSI
            val nameEnd = signature.indexOf('(')
            if (nameEnd < 0) return null

            val name = signature.substring(0, nameEnd).trim()
            if (name.isEmpty()) return null // Require a function name

            val paramsStart = nameEnd + 1
            val paramsEnd = signature.indexOf(')', paramsStart)
            if (paramsEnd < 0) return null

            val paramsStr = signature.substring(paramsStart, paramsEnd)
            val parameters = if (paramsStr.isBlank()) {
                emptyList()
            } else {
                paramsStr.split(",").map { param ->
                    val parts = param.trim().split(":")
                    if (parts.size >= 2) {
                        ParameterDefinition(parts[0].trim(), parts[1].trim())
                    } else {
                        ParameterDefinition(parts[0].trim())
                    }
                }
            }

            val returnType = if (paramsEnd + 1 < signature.length) {
                val rest = signature.substring(paramsEnd + 1).trim()
                if (rest.startsWith(":")) {
                    rest.substring(1).trim()
                } else null
            } else null

            return FunctionSignature(name, parameters, returnType)
        }
    }
}

/**
 * State for parameter info display.
 *
 * @property signatures List of overloaded signatures (multiple if function is overloaded)
 * @property activeSignatureIndex Index of the currently active signature
 * @property activeParameterIndex Index of the parameter being typed
 * @property position Position where parameter info should be displayed
 */
data class ParameterInfoState(
    val signatures: List<FunctionSignature>,
    val activeSignatureIndex: Int = 0,
    val activeParameterIndex: Int = 0,
    val position: EditorPosition
) {
    /** The currently active signature */
    val activeSignature: FunctionSignature?
        get() = signatures.getOrNull(activeSignatureIndex)

    /** The currently active parameter */
    val activeParameter: ParameterDefinition?
        get() = activeSignature?.getParameter(activeParameterIndex)

    /** Whether there are multiple overloaded signatures */
    val hasOverloads: Boolean get() = signatures.size > 1

    /** Total number of overloads */
    val overloadCount: Int get() = signatures.size

    /** Updates the active signature index */
    fun withActiveSignature(index: Int): ParameterInfoState {
        val newIndex = index.coerceIn(0, (signatures.size - 1).coerceAtLeast(0))
        return copy(activeSignatureIndex = newIndex)
    }

    /** Updates the active parameter index */
    fun withActiveParameter(index: Int): ParameterInfoState {
        val paramCount = activeSignature?.parameters?.size ?: 0
        val newIndex = index.coerceIn(0, (paramCount - 1).coerceAtLeast(0))
        return copy(activeParameterIndex = newIndex)
    }

    /** Moves to next overload */
    fun nextOverload(): ParameterInfoState =
        withActiveSignature((activeSignatureIndex + 1) % signatures.size)

    /** Moves to previous overload */
    fun previousOverload(): ParameterInfoState =
        withActiveSignature((activeSignatureIndex - 1 + signatures.size) % signatures.size)
}

/**
 * Provider interface for parameter information.
 */
interface ParameterInfoProvider {
    /**
     * Gets parameter info at the given position.
     *
     * @param position The cursor position
     * @return Parameter info state, or null if not inside a function call
     */
    suspend fun getParameterInfo(position: EditorPosition): ParameterInfoState?
}

/**
 * Simple static parameter info provider for testing.
 */
class StaticParameterInfoProvider(
    private val signatures: Map<String, List<FunctionSignature>>
) : ParameterInfoProvider {
    override suspend fun getParameterInfo(position: EditorPosition): ParameterInfoState? {
        // This is a placeholder - real implementation would analyze code
        return null
    }

    fun getSignaturesFor(functionName: String): List<FunctionSignature>? {
        return signatures[functionName]
    }
}
