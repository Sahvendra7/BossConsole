package ai.rever.boss.components.plugin.tab_types

import ai.rever.boss.editor.SemanticAdapterFactory
import ai.rever.boss.psi.NavigationEvent
import ai.rever.boss.run.DetectedMainFunction
import ai.rever.boss.run.Language
import ai.rever.boss.run.MainFunctionDetectorProvider
import ai.rever.bosseditor.compose.BossEditor
import ai.rever.bosseditor.core.EditorPosition
import ai.rever.bosseditor.core.EditorRange
import ai.rever.bosseditor.core.EditorState
import ai.rever.bosseditor.highlight.Token
import ai.rever.bosseditor.highlight.TokenCache
import ai.rever.bosseditor.highlight.lexers.*
import ai.rever.bosseditor.rendering.EditorToken
import ai.rever.bosseditor.theme.EditorTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * BossEditor integration layer for BOSS application.
 *
 * This composable provides a drop-in replacement for RSyntaxEditorWithGutter,
 * using the native Compose Canvas-based BossEditor instead of RSyntaxTextArea.
 *
 * ## Features
 * - Native Compose rendering (no Swing interop)
 * - Syntax highlighting via BossEditor lexers
 * - Semantic highlighting for Kotlin (via PSI integration)
 * - Run gutter icons for detected main functions
 * - Theme integration with BOSS themes
 *
 * @param content The file content to display
 * @param onContentChange Callback when content changes
 * @param language The programming language for syntax highlighting
 * @param filePath The path to the file being edited
 * @param projectPath The project root path (for running detected functions)
 * @param modifier Modifier for the editor
 * @param isReadOnly Whether the editor is read-only
 * @param fontSize Font size in pixels
 * @param fontFamily Font family name
 * @param theme BOSS theme name
 * @param onCursorPositionChange Callback for cursor position changes (line, column)
 * @param onModifiedStateChange Callback when modification state changes
 * @param onRun Callback when a run icon is clicked
 * @param onNavigate Callback when Cmd+Click navigation is triggered (go-to-definition)
 */
@Composable
fun BossEditorIntegration(
    content: String,
    onContentChange: (String) -> Unit,
    language: String,
    filePath: String,
    projectPath: String = "",
    modifier: Modifier = Modifier,
    isReadOnly: Boolean = false,
    fontSize: Int = CodeEditorSettings.fontSize,
    fontFamily: String = CodeEditorSettings.fontFamily,
    theme: String = CodeEditorSettings.theme,
    onCursorPositionChange: (line: Int, column: Int) -> Unit = { _, _ -> },
    onModifiedStateChange: (Boolean) -> Unit = { },
    onRun: (DetectedMainFunction) -> Unit = { },
    onNavigate: (NavigationEvent) -> Unit = { }
) {
    val coroutineScope = rememberCoroutineScope()

    // Create editor state that persists across recompositions
    val editorState = remember(filePath) {
        EditorState(content)
    }

    // Track original content for modification detection
    var originalContent by remember(filePath) { mutableStateOf(content) }

    // State for detected main functions
    var detectedMainFunctions by remember { mutableStateOf<List<DetectedMainFunction>>(emptyList()) }

    // Create lexer based on language
    val lexer = remember(language) {
        getLexerForLanguage(language.lowercase())
    }

    // Create token cache for multi-line state tracking (only if lexer is available)
    val tokenCache = remember(lexer, editorState.document) {
        lexer?.let { TokenCache(editorState.document, it) }
    }

    // Dispose token cache when composable is disposed
    DisposableEffect(tokenCache) {
        onDispose {
            tokenCache?.dispose()
        }
    }

    // Create semantic adapter for Kotlin files
    val semanticAdapter = remember(filePath, editorState.document) {
        SemanticAdapterFactory.create(editorState.document, filePath)
    }

    // Map theme name to EditorTheme
    val editorTheme = remember(theme) {
        mapBossThemeToEditorTheme(theme)
    }

    // Map font family name to FontFamily
    val composeFontFamily = remember(fontFamily) {
        when (fontFamily.lowercase()) {
            "jetbrains mono", "jetbrainsmono" -> FontFamily.Monospace
            "fira code", "firacode" -> FontFamily.Monospace
            "source code pro" -> FontFamily.Monospace
            else -> FontFamily.Monospace
        }
    }

    // Update content from external source
    LaunchedEffect(content) {
        if (editorState.document.getText() != content) {
            editorState.document.setText(content)
        }
    }

    // Detect main functions when content changes
    LaunchedEffect(content, filePath) {
        if (filePath.isNotEmpty() && content.isNotEmpty()) {
            val currentFilePath = filePath
            withContext(Dispatchers.IO) {
                try {
                    val detector = MainFunctionDetectorProvider.get()
                    val langEnum = Language.fromFileName(currentFilePath)
                    val detected = detector.detectInFile(currentFilePath, content, langEnum)
                    withContext(Dispatchers.Main) {
                        if (filePath == currentFilePath) {
                            detectedMainFunctions = detected
                        }
                    }
                } catch (e: Exception) {
                    println("[BossEditorIntegration] Error detecting main functions: ${e.message}")
                    withContext(Dispatchers.Main) {
                        if (filePath == currentFilePath) {
                            detectedMainFunctions = emptyList()
                        }
                    }
                }
            }
        } else {
            detectedMainFunctions = emptyList()
        }
    }

    // Token provider combining lexer and semantic highlighting
    // Uses TokenCache for proper multi-line state tracking (block comments, raw strings, etc.)
    val tokenProvider: (Int) -> List<EditorToken> = remember(tokenCache, semanticAdapter) {
        { lineNumber ->
            // First try semantic tokens (higher priority)
            val semanticTokens = semanticAdapter?.getLineTokens(lineNumber)

            if (!semanticTokens.isNullOrEmpty()) {
                EditorToken.fromTokens(semanticTokens)
            } else {
                // Fall back to lexer-based highlighting via TokenCache
                // TokenCache handles multi-line state tracking automatically
                val lexerTokens: List<Token> = tokenCache?.getLineTokens(lineNumber) ?: emptyList()
                EditorToken.fromTokens(lexerTokens)
            }
        }
    }

    // Layout: Run gutter | Editor
    Row(modifier = modifier.fillMaxSize()) {
        // Run gutter (for detected main functions)
        if (detectedMainFunctions.isNotEmpty()) {
            BossEditorRunGutter(
                detectedMainFunctions = detectedMainFunctions,
                editorState = editorState,
                fontSize = fontSize.toFloat(),
                fontFamily = composeFontFamily,
                onRun = onRun,
                modifier = Modifier
                    .width(24.dp)
                    .fillMaxHeight()
                    .background(editorTheme.colors.gutterBackground)
            )
        }

        // Main editor
        BossEditor(
            state = editorState,
            modifier = Modifier.fillMaxSize(),
            theme = editorTheme,
            fontFamily = composeFontFamily,
            fontSize = fontSize.toFloat(),
            showLineNumbers = true,
            highlightCurrentLine = true,
            readOnly = isReadOnly,
            tokenProvider = tokenProvider,
            onTextChanged = {
                val newContent = editorState.document.getText()
                onContentChange(newContent)
                val isModified = newContent != originalContent
                onModifiedStateChange(isModified)
            },
            onCaretPositionChanged = { position ->
                // Convert to 1-based line/column for compatibility
                onCursorPositionChange(position.line + 1, position.column + 1)
            },
            onSelectionChanged = { _ ->
                // Selection changed - could integrate with mark occurrences
            }
        )
    }
}

/**
 * Maps BOSS theme name to BossEditor EditorTheme.
 */
private fun mapBossThemeToEditorTheme(themeName: String): EditorTheme {
    return when (themeName.lowercase()) {
        "dark", "boss dark" -> EditorTheme.Dark
        "light", "boss light" -> EditorTheme.Light
        "dracula" -> EditorTheme.Dracula
        "monokai" -> EditorTheme.Monokai
        "solarized dark" -> EditorTheme.SolarizedDark
        "solarized light" -> EditorTheme.SolarizedLight
        else -> EditorTheme.Dark
    }
}

/**
 * Returns the appropriate lexer for the given language.
 * Supports language names, file extensions, and common aliases.
 */
private fun getLexerForLanguage(language: String): BaseLexer? {
    return when (language) {
        // Kotlin
        "kotlin", "kt", "kts" -> KotlinLexer()

        // Java
        "java" -> JavaLexer()

        // JavaScript
        "javascript", "js", "jsx", "mjs", "cjs" -> JavaScriptLexer()

        // TypeScript
        "typescript", "ts", "tsx", "mts", "cts" -> TypeScriptLexer()

        // Python
        "python", "py", "pyw", "pyi", "pyx" -> PythonLexer()

        // JSON
        "json", "jsonc", "json5" -> JsonLexer()

        // XML
        "xml", "xsd", "xsl", "xslt", "svg", "plist", "wsdl" -> XmlLexer()

        // HTML
        "html", "htm", "xhtml", "vue", "svelte" -> HtmlLexer()

        // CSS
        "css", "scss", "sass", "less" -> CssLexer()

        // Shell/Bash
        "shell", "sh", "bash", "zsh", "fish", "ksh", "csh", "tcsh" -> ShellLexer()

        // Markdown
        "markdown", "md", "mdx", "mkd", "mkdn" -> MarkdownLexer()

        // SQL
        "sql", "mysql", "pgsql", "plsql", "sqlite" -> SqlLexer()

        // YAML
        "yaml", "yml" -> YamlLexer()

        // Groovy
        "groovy", "gradle", "gvy", "gy", "gsh" -> GroovyLexer()

        // C/C++
        "c", "h", "cpp", "hpp", "cc", "hh", "cxx", "hxx", "c++", "h++", "ino" -> CLexer()

        // Rust
        "rust", "rs" -> RustLexer()

        // Go
        "go", "golang" -> GoLexer()

        // Swift
        "swift" -> SwiftLexer()

        // Ruby
        "ruby", "rb", "rake", "gemspec", "ru", "erb", "podspec" -> RubyLexer()

        // PHP
        "php", "phtml", "php3", "php4", "php5", "php7", "phps" -> PHPLexer()

        // Dockerfile
        "dockerfile", "docker" -> DockerfileLexer()

        // Makefile
        "makefile", "mk", "mak", "make" -> MakefileLexer()

        // TOML
        "toml" -> TomlLexer()

        // Properties/INI
        "properties", "env", "cfg", "conf", "ini" -> PropertiesLexer()

        // Scala
        "scala", "sc", "sbt" -> ScalaLexer()

        // Perl
        "perl", "pl", "pm", "pod", "t", "psgi" -> PerlLexer()

        // Lua
        "lua" -> LuaLexer()

        // C#
        "csharp", "cs", "csx" -> CSharpLexer()

        // Clojure
        "clojure", "clj", "cljs", "cljc", "edn" -> ClojureLexer()

        // LaTeX
        "latex", "tex", "sty", "cls", "bib", "bst", "ltx" -> LaTeXLexer()

        // Batch/CMD (Windows)
        "batch", "bat", "cmd" -> BatchLexer()

        // Visual Basic
        "vb", "vbs", "bas", "frm", "vba" -> VisualBasicLexer()

        // Tcl/Tk
        "tcl", "tk", "itcl", "itk" -> TclLexer()

        // Lisp/Scheme/Racket
        "lisp", "lsp", "cl", "el", "elc", "scm", "ss", "rkt", "scheme", "racket", "elisp", "emacs-lisp" -> LispLexer()

        // D
        "d", "di" -> DLexer()

        // Pascal/Delphi
        "pascal", "pas", "dpr", "dpk", "pp", "inc", "lpr", "lfm", "dfm", "delphi" -> DelphiLexer()

        // ActionScript
        "actionscript", "as", "mxml" -> ActionScriptLexer()

        // Fortran
        "fortran", "f", "for", "f77", "f90", "f95", "f03", "f08", "f18" -> FortranLexer()

        // JSP
        "jsp", "jspf", "jspx", "tag", "tagx", "tld" -> JspLexer()

        // Diff/Patch
        "diff", "patch", "rej" -> DiffLexer()

        // Default: no syntax highlighting
        else -> null
    }
}

/**
 * Run gutter for BossEditor showing detected main functions.
 *
 * This is a pure Compose implementation that directly uses EditorState's
 * scroll offset, avoiding the Swing synchronization issues in RSyntaxGutterOverlay.
 */
@Composable
private fun BossEditorRunGutter(
    detectedMainFunctions: List<DetectedMainFunction>,
    editorState: EditorState,
    fontSize: Float,
    fontFamily: FontFamily = FontFamily.Monospace,
    onRun: (DetectedMainFunction) -> Unit,
    modifier: Modifier = Modifier
) {
    // Collect scroll offset from editor state
    val scrollOffset by editorState.scrollOffset.collectAsState()
    val density = LocalDensity.current

    // Measure line height to match EditorCanvas exactly
    // EditorCanvas uses textMeasurer.measure("M", style).size.height
    val textMeasurer = rememberTextMeasurer()
    val lineHeightPx = remember(fontSize, fontFamily) {
        val style = TextStyle(
            fontFamily = fontFamily,
            fontSize = fontSize.sp
        )
        textMeasurer.measure("M", style).size.height.toFloat()
    }

    // Convert pixel height to dp for sizing
    val lineHeightDp = with(density) { lineHeightPx.toDp() }

    // Create a map for fast lookup
    val runnableLines = remember(detectedMainFunctions) {
        detectedMainFunctions.associateBy { it.lineNumber }
    }

    // Calculate visible range with buffer
    val firstVisibleLine = (scrollOffset.y / lineHeightPx).toInt().coerceAtLeast(0)
    val visibleLineCount = 50 // Generous buffer for smooth scrolling
    val visibleRange = remember(firstVisibleLine, visibleLineCount, editorState.document.lineCount) {
        val start = (firstVisibleLine - 2).coerceAtLeast(0)
        val end = (firstVisibleLine + visibleLineCount + 2).coerceAtMost(editorState.document.lineCount)
        start until end
    }

    Box(modifier = modifier) {
        // Render run icons for detected main functions in visible range
        detectedMainFunctions
            .filter { it.lineNumber in visibleRange }
            .forEach { detected ->
                // Calculate Y position in pixels
                // lineNumber from detector is 1-based, editor lines are 0-based internally
                val yOffsetPx = (detected.lineNumber * lineHeightPx) - scrollOffset.y.toFloat()

                // Only render if within viewport
                if (yOffsetPx >= -lineHeightPx && yOffsetPx < 2000f) {
                    Box(
                        modifier = Modifier
                            // Use pixel-based offset to match EditorCanvas rendering
                            .offset { IntOffset(0, yOffsetPx.toInt()) }
                            .height(lineHeightDp)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        GutterRunIcon(
                            detected = detected,
                            onRun = onRun,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
    }
}
