package ai.rever.bosseditor.logging

import ai.rever.bosseditor.lsp.logging.LogCategory
import ai.rever.bosseditor.lsp.logging.LspLogger
import ai.rever.bosseditor.lsp.logging.ComponentLogger as LspComponentLogger

/**
 * Desktop implementation of EditorLogger.
 * Delegates to LspLogger for full SLF4J integration.
 */
actual object EditorLogger {
    actual fun forComponent(componentName: String): EditorComponentLogger {
        return EditorComponentLogger(LspLogger.forComponent(componentName))
    }
}

/**
 * Desktop implementation of EditorComponentLogger.
 * Wraps LspLogger's ComponentLogger.
 */
actual class EditorComponentLogger(
    private val delegate: LspComponentLogger
) {
    actual fun trace(
        category: EditorLogCategory,
        message: String,
        data: Map<String, Any?>?
    ) {
        delegate.trace(category.toLspCategory(), message, data = data)
    }

    actual fun debug(
        category: EditorLogCategory,
        message: String,
        data: Map<String, Any?>?
    ) {
        delegate.debug(category.toLspCategory(), message, data = data)
    }

    actual fun info(
        category: EditorLogCategory,
        message: String,
        data: Map<String, Any?>?
    ) {
        delegate.info(category.toLspCategory(), message, data = data)
    }

    actual fun warn(
        category: EditorLogCategory,
        message: String,
        data: Map<String, Any?>?,
        error: Throwable?
    ) {
        delegate.warn(category.toLspCategory(), message, data = data, error = error)
    }

    actual fun error(
        category: EditorLogCategory,
        message: String,
        data: Map<String, Any?>?,
        error: Throwable?
    ) {
        delegate.error(category.toLspCategory(), message, data = data, error = error)
    }
}

/**
 * Map EditorLogCategory to LspLogger's LogCategory.
 */
private fun EditorLogCategory.toLspCategory(): LogCategory {
    return when (this) {
        EditorLogCategory.TRANSPORT -> LogCategory.TRANSPORT
        EditorLogCategory.PROTOCOL -> LogCategory.PROTOCOL
        EditorLogCategory.SERVER -> LogCategory.SERVER
        EditorLogCategory.DOCUMENT -> LogCategory.DOCUMENT
        EditorLogCategory.COMPLETION -> LogCategory.COMPLETION
        EditorLogCategory.DIAGNOSTICS -> LogCategory.DIAGNOSTICS
        EditorLogCategory.NAVIGATION -> LogCategory.NAVIGATION
        EditorLogCategory.SEMANTIC -> LogCategory.SEMANTIC
        EditorLogCategory.EDITOR -> LogCategory.GENERAL
        EditorLogCategory.GENERAL -> LogCategory.GENERAL
    }
}
