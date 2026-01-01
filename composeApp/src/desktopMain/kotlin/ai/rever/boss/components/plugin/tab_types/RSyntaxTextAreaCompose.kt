package ai.rever.boss.components.plugin.tab_types

import ai.rever.boss.font.FontManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rsyntaxtextarea.SyntaxConstants
import org.fife.ui.rtextarea.RTextScrollPane
import java.awt.Font
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities
import javax.swing.event.CaretEvent
import javax.swing.event.CaretListener
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * A Compose wrapper for RSyntaxTextArea that provides syntax highlighting,
 * code folding, bracket matching, and other advanced editor features.
 *
 * This composable embeds the Swing-based RSyntaxTextArea into a Compose UI
 * using SwingPanel for interoperability.
 */
@Composable
fun RSyntaxTextAreaCompose(
    content: String,
    onContentChange: (String) -> Unit,
    language: String,
    filePath: String,
    modifier: Modifier = Modifier,
    isReadOnly: Boolean = false,
    fontSize: Int = CodeEditorSettings.fontSize,
    fontFamily: String = CodeEditorSettings.fontFamily,
    theme: String = CodeEditorSettings.theme,
    onCursorPositionChange: (line: Int, column: Int) -> Unit = { _, _ -> },
    onModifiedStateChange: (Boolean) -> Unit = { }
) {
    val coroutineScope = rememberCoroutineScope()

    // Thread-safe flag to prevent update loops between Compose and Swing
    // Uses AtomicBoolean for thread-safe compareAndSet operations across EDT and Main dispatcher
    val isInternalUpdate = remember { AtomicBoolean(false) }

    // Track original content for modification detection
    var originalContent by remember { mutableStateOf(content) }

    // Create and remember RSyntaxTextArea instance
    val textAreaState = remember {
        RSyntaxTextAreaState().also { state ->
            // CRITICAL: ALL Swing component configuration must happen on EDT
            // This includes setting syntaxEditingStyle, text content, and theme
            // Previously, these were set off-EDT which caused tokenization issues
            val configureOnEdt = {
                state.textArea.apply {
                    // Configure editor features first
                    isCodeFoldingEnabled = true
                    isAutoIndentEnabled = true
                    tabSize = 4
                    isEditable = !isReadOnly
                    antiAliasingEnabled = true
                    markOccurrences = true
                    paintMatchedBracketPair = true
                    isBracketMatchingEnabled = true
                    highlightCurrentLine = true
                    fadeCurrentLineHighlight = true
                    isWhitespaceVisible = false
                    eolMarkersVisible = false
                    paintTabLines = true

                    // Set initial font
                    font = createEditorFont(fontFamily, fontSize)

                    // IMPORTANT ORDER: Set syntax style BEFORE content
                    // This ensures the correct TokenMaker is in place when text is set
                    syntaxEditingStyle = mapLanguageToSyntaxStyle(language)

                    // Set initial content (tokenization happens during this call)
                    text = content
                }

                // Apply theme (which also ensures TokenMaker is correctly attached)
                RSyntaxThemeMapper.applyTheme(state.textArea, theme)
                state.textArea.revalidate()
                state.textArea.repaint()
            }

            // Use invokeAndWait to ensure configuration is complete before returning
            // This prevents "flash of unstyled content" and tokenization issues
            if (SwingUtilities.isEventDispatchThread()) {
                configureOnEdt()
            } else {
                SwingUtilities.invokeAndWait {
                    configureOnEdt()
                }
            }
        }
    }

    val textArea = textAreaState.textArea
    val scrollPane = textAreaState.scrollPane

    // Update content when external changes occur
    LaunchedEffect(content) {
        // Thread-safe check: only proceed if we atomically set the flag
        if (textArea.text != content && isInternalUpdate.compareAndSet(false, true)) {
            SwingUtilities.invokeLater {
                try {
                    // Save caret position
                    val caretPos = textArea.caretPosition.coerceIn(0, content.length)
                    textArea.text = content
                    // Restore caret position
                    textArea.caretPosition = caretPos.coerceIn(0, textArea.document.length)
                    // Force re-tokenization after content change to ensure syntax highlighting
                    textArea.forceReparsing(0)
                    textArea.revalidate()
                    textArea.repaint()
                } finally {
                    // Post back to Main dispatcher to reset flag (avoid modifying from EDT)
                    coroutineScope.launch(Dispatchers.Main) {
                        isInternalUpdate.set(false)
                    }
                }
            }
        }
    }

    // Update language syntax highlighting
    LaunchedEffect(language) {
        SwingUtilities.invokeLater {
            textArea.syntaxEditingStyle = mapLanguageToSyntaxStyle(language)
            // Force re-tokenization and repaint after language change
            textArea.forceReparsing(0)
            textArea.revalidate()
            textArea.repaint()
        }
    }

    // Update theme
    LaunchedEffect(theme) {
        SwingUtilities.invokeLater {
            RSyntaxThemeMapper.applyTheme(textArea, theme)
            textArea.revalidate()
            textArea.repaint()
        }
    }

    // Update font settings
    LaunchedEffect(fontSize, fontFamily) {
        SwingUtilities.invokeLater {
            textArea.font = createEditorFont(fontFamily, fontSize)
            textArea.revalidate()
            textArea.repaint()
        }
    }

    // Update read-only state
    LaunchedEffect(isReadOnly) {
        SwingUtilities.invokeLater {
            textArea.isEditable = !isReadOnly
        }
    }

    // Set up document listener for content changes
    DisposableEffect(textArea) {
        val documentListener = object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = notifyChange()
            override fun removeUpdate(e: DocumentEvent) = notifyChange()
            override fun changedUpdate(e: DocumentEvent) = notifyChange()

            private fun notifyChange() {
                // Thread-safe check: only proceed if we atomically set the flag
                if (isInternalUpdate.compareAndSet(false, true)) {
                    coroutineScope.launch(Dispatchers.Main) {
                        try {
                            val newContent = textArea.text
                            onContentChange(newContent)
                            // Check if content differs from original
                            val isModified = newContent != originalContent
                            onModifiedStateChange(isModified)
                        } finally {
                            isInternalUpdate.set(false)
                        }
                    }
                }
            }
        }
        textArea.document.addDocumentListener(documentListener)

        // Caret listener for cursor position updates
        val caretListener = CaretListener { e: CaretEvent ->
            try {
                val dot = e.dot
                val line = textArea.getLineOfOffset(dot) + 1 // 1-based line numbers
                val lineStart = textArea.getLineStartOffset(line - 1)
                val column = dot - lineStart + 1 // 1-based column numbers
                coroutineScope.launch(Dispatchers.Main) {
                    onCursorPositionChange(line, column)
                }
            } catch (ex: Exception) {
                // Ignore invalid offsets during rapid updates
            }
        }
        textArea.addCaretListener(caretListener)

        onDispose {
            textArea.document.removeDocumentListener(documentListener)
            textArea.removeCaretListener(caretListener)
        }
    }

    // Update original content reference when filePath changes (new file loaded)
    LaunchedEffect(filePath) {
        originalContent = content
        onModifiedStateChange(false)
    }

    // Embed Swing component in Compose
    SwingPanel(
        factory = { scrollPane },
        modifier = modifier.fillMaxSize(),
        update = { pane ->
            // Handle any updates that need to occur after factory
            pane.revalidate()
            pane.repaint()
        }
    )
}

/**
 * Holds the RSyntaxTextArea and its scroll pane.
 * Created once and reused across recompositions.
 */
private class RSyntaxTextAreaState {
    val textArea: RSyntaxTextArea = RSyntaxTextArea(40, 100).apply {
        // Configure keyboard bindings to remove BOSS global shortcut conflicts
        configureBossKeyBindings()
    }

    val scrollPane: RTextScrollPane = RTextScrollPane(textArea).apply {
        lineNumbersEnabled = true
        isFoldIndicatorEnabled = true
    }
}

/**
 * Creates a font for the editor with the specified family and size.
 * Uses FontManager for intelligent font loading with bundled fallbacks.
 */
private fun createEditorFont(fontFamily: String, fontSize: Int): Font {
    return FontManager.createEditorFont(fontFamily, fontSize)
}

/**
 * Maps file language/extension to RSyntaxTextArea syntax style constant.
 */
fun mapLanguageToSyntaxStyle(language: String): String {
    val lang = language.lowercase().trim()

    val result = when {
        // Kotlin
        lang in listOf("kotlin", "kt", "kts") -> SyntaxConstants.SYNTAX_STYLE_KOTLIN

        // Java
        lang == "java" -> SyntaxConstants.SYNTAX_STYLE_JAVA

        // JavaScript family
        lang in listOf("javascript", "js", "mjs", "cjs") -> SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT
        lang in listOf("typescript", "ts") -> SyntaxConstants.SYNTAX_STYLE_TYPESCRIPT
        lang in listOf("jsx") -> SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT // JSX uses JavaScript highlighting
        lang in listOf("tsx") -> SyntaxConstants.SYNTAX_STYLE_TYPESCRIPT // TSX uses TypeScript highlighting

        // Python
        lang in listOf("python", "py", "pyw") -> SyntaxConstants.SYNTAX_STYLE_PYTHON

        // Web technologies
        lang in listOf("html", "htm", "xhtml") -> SyntaxConstants.SYNTAX_STYLE_HTML
        lang in listOf("css") -> SyntaxConstants.SYNTAX_STYLE_CSS
        lang in listOf("less") -> SyntaxConstants.SYNTAX_STYLE_LESS
        lang in listOf("scss", "sass") -> SyntaxConstants.SYNTAX_STYLE_CSS // No native SCSS support

        // Data formats
        lang in listOf("json", "jsonc") -> SyntaxConstants.SYNTAX_STYLE_JSON
        lang in listOf("json5") -> SyntaxConstants.SYNTAX_STYLE_JSON_WITH_COMMENTS
        lang in listOf("xml", "xsl", "xslt", "xsd") -> SyntaxConstants.SYNTAX_STYLE_XML
        lang in listOf("yaml", "yml") -> SyntaxConstants.SYNTAX_STYLE_YAML
        lang in listOf("toml") -> SyntaxConstants.SYNTAX_STYLE_INI // No native TOML support
        lang in listOf("properties", "ini", "cfg", "conf") -> SyntaxConstants.SYNTAX_STYLE_INI
        lang in listOf("csv") -> SyntaxConstants.SYNTAX_STYLE_CSV

        // Markdown & Documentation
        lang in listOf("markdown", "md", "mkd", "mdx") -> SyntaxConstants.SYNTAX_STYLE_MARKDOWN

        // Shell scripts
        lang in listOf("shell", "bash", "sh", "zsh", "ksh") -> SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL
        lang in listOf("batch", "bat", "cmd") -> SyntaxConstants.SYNTAX_STYLE_WINDOWS_BATCH
        lang in listOf("powershell", "ps1", "psm1") -> SyntaxConstants.SYNTAX_STYLE_WINDOWS_BATCH // Approximate

        // Systems programming
        lang == "c" -> SyntaxConstants.SYNTAX_STYLE_C
        lang in listOf("cpp", "c++", "cc", "cxx", "hpp", "h") -> SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS
        lang in listOf("csharp", "cs") -> SyntaxConstants.SYNTAX_STYLE_CSHARP
        lang in listOf("rust", "rs") -> SyntaxConstants.SYNTAX_STYLE_RUST
        lang == "go" -> SyntaxConstants.SYNTAX_STYLE_GO
        lang in listOf("swift") -> SyntaxConstants.SYNTAX_STYLE_NONE // No native Swift support
        lang in listOf("objectivec", "m", "mm") -> SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS // Approximate

        // JVM languages
        lang in listOf("groovy", "gradle") -> SyntaxConstants.SYNTAX_STYLE_GROOVY
        lang == "scala" -> SyntaxConstants.SYNTAX_STYLE_SCALA
        lang == "clojure" -> SyntaxConstants.SYNTAX_STYLE_CLOJURE

        // Other scripting languages
        lang in listOf("ruby", "rb") -> SyntaxConstants.SYNTAX_STYLE_RUBY
        lang in listOf("perl", "pl", "pm") -> SyntaxConstants.SYNTAX_STYLE_PERL
        lang == "php" -> SyntaxConstants.SYNTAX_STYLE_PHP
        lang == "lua" -> SyntaxConstants.SYNTAX_STYLE_LUA
        lang == "r" -> SyntaxConstants.SYNTAX_STYLE_NONE // No native R support
        lang in listOf("dart") -> SyntaxConstants.SYNTAX_STYLE_DART

        // Database
        lang == "sql" -> SyntaxConstants.SYNTAX_STYLE_SQL

        // Lisp family
        lang in listOf("lisp", "el", "emacs") -> SyntaxConstants.SYNTAX_STYLE_LISP

        // Build tools
        lang == "makefile" -> SyntaxConstants.SYNTAX_STYLE_MAKEFILE
        lang == "cmake" -> SyntaxConstants.SYNTAX_STYLE_MAKEFILE // Approximate CMAKE with MAKEFILE
        lang in listOf("dockerfile", "docker") -> SyntaxConstants.SYNTAX_STYLE_DOCKERFILE

        // Text & Config
        lang in listOf("text", "txt", "log") -> SyntaxConstants.SYNTAX_STYLE_NONE
        lang == "latex" -> SyntaxConstants.SYNTAX_STYLE_LATEX
        lang == "htaccess" -> SyntaxConstants.SYNTAX_STYLE_HTACCESS
        lang == "hosts" -> SyntaxConstants.SYNTAX_STYLE_HOSTS

        // Default - no syntax highlighting
        else -> SyntaxConstants.SYNTAX_STYLE_NONE
    }
    return result
}

/**
 * Extracts language from file path based on extension.
 */
fun getLanguageFromFilePath(filePath: String): String {
    val extension = filePath.substringAfterLast('.', "").lowercase()

    return when (extension) {
        // Special cases where extension doesn't match language name
        "kt", "kts" -> "kotlin"
        "js", "mjs", "cjs" -> "javascript"
        "ts" -> "typescript"
        "py", "pyw" -> "python"
        "rb" -> "ruby"
        "pl", "pm" -> "perl"
        "rs" -> "rust"
        "cs" -> "csharp"
        "cc", "cxx", "hpp", "h" -> "cpp"
        "m", "mm" -> "objectivec"
        "sh", "zsh", "ksh" -> "shell"
        "bat", "cmd" -> "batch"
        "ps1", "psm1" -> "powershell"
        "md", "mkd", "mdx" -> "markdown"
        "yml" -> "yaml"
        "el" -> "lisp"
        "" -> "text"
        else -> extension
    }
}
