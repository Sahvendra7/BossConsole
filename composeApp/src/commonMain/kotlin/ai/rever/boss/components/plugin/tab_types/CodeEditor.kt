package ai.rever.boss.components.plugin.tab_types

import ai.rever.boss.components.plugin.DefaultPlugin
import ai.rever.boss.components.registery.TabComponentWithUI
import ai.rever.boss.components.registery.TabInfo
import ai.rever.boss.components.registery.TabTypeInfo
import ai.rever.boss.components.registery.TabTypeId
import ai.rever.boss.components.registery.TabIcon
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arkivanov.decompose.ComponentContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// Simple syntax highlighting for common keywords
private val kotlinKeywords = setOf(
    "abstract", "annotation", "as", "break", "by", "catch", "class", "companion",
    "const", "constructor", "continue", "crossinline", "data", "do", "else", "enum",
    "expect", "external", "false", "final", "finally", "for", "fun", "if", "import",
    "in", "infix", "init", "inline", "inner", "interface", "internal", "is", "lateinit",
    "noinline", "null", "object", "open", "operator", "out", "override", "package",
    "private", "protected", "public", "reified", "return", "sealed", "super", "suspend",
    "tailrec", "this", "throw", "true", "try", "typealias", "typeof", "val", "var",
    "vararg", "when", "where", "while"
)

private val types = setOf(
    "Boolean", "Byte", "Char", "Double", "Float", "Int", "Long", "Short", "String",
    "Unit", "Any", "Nothing", "List", "Map", "Set", "Array", "MutableList", "MutableMap",
    "MutableSet"
)

@Composable
fun CodeEditorUI(
    content: String,
    onContentChange: (String) -> Unit,
    language: String = "kotlin",
    modifier: Modifier = Modifier
) {
    val textStyle = LocalTextStyle.current.copy(
        fontFamily = FontFamily.Monospace,
        fontSize = 14.sp,
        lineHeight = 20.sp
    )
    
    // Use TextFieldValue to maintain cursor position
    var textFieldValue by remember { mutableStateOf(TextFieldValue(content)) }
    
    // Update TextFieldValue when content changes externally
    LaunchedEffect(content) {
        if (content != textFieldValue.text) {
            textFieldValue = TextFieldValue(content)
        }
    }
    
    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()
    
    Surface(
        modifier = modifier,
        color = Color(0xFF_1E1E1E)
    ) {
        Row(
            modifier = Modifier.fillMaxSize()
        ) {
            // Line numbers
            val lines = textFieldValue.text.lines()
            Column(
                modifier = Modifier
                    .background(Color(0xFF_2D2D30))
                    .padding(horizontal = 8.dp)
                    .verticalScroll(verticalScrollState)
            ) {
                lines.forEachIndexed { index, _ ->
                    Text(
                        text = "${index + 1}",
                        style = textStyle.copy(color = Color(0xFF_858585)),
                        modifier = Modifier.height(20.dp)
                    )
                }
            }
            
            // Editor content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(horizontalScrollState)
                    .verticalScroll(verticalScrollState)
                    .padding(8.dp)
            ) {
                BasicTextField(
                    value = textFieldValue,
                    onValueChange = { newValue ->
                        textFieldValue = newValue
                        onContentChange(newValue.text)
                    },
                    textStyle = textStyle.copy(color = Color.White),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier.fillMaxSize(),
                    visualTransformation = SyntaxHighlightTransformation(language)
                )
            }
        }
    }
}

// Custom VisualTransformation for syntax highlighting
class SyntaxHighlightTransformation(private val language: String) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val highlighted = when (language) {
            "kotlin" -> highlightKotlinSyntax(text.text)
            "toml" -> highlightTomlSyntax(text.text)
            else -> text
        }
        return TransformedText(highlighted, OffsetMapping.Identity)
    }
    
    private fun highlightKotlinSyntax(text: String): AnnotatedString {
        return buildAnnotatedString {
            append(text)
            
            // Highlight keywords with word boundaries
            kotlinKeywords.forEach { keyword ->
                val pattern = "\\b$keyword\\b".toRegex()
                pattern.findAll(text).forEach { match ->
                    addStyle(
                        SpanStyle(color = Color(0xFF_CF68E1), fontWeight = FontWeight.Bold),
                        match.range.first,
                        match.range.last + 1
                    )
                }
            }
            
            // Highlight types
            types.forEach { type ->
                val pattern = "\\b$type\\b".toRegex()
                pattern.findAll(text).forEach { match ->
                    addStyle(
                        SpanStyle(color = Color(0xFF_4EC9B0)),
                        match.range.first,
                        match.range.last + 1
                    )
                }
            }
            
            // Highlight single-line comments
            "//.*$".toRegex(RegexOption.MULTILINE).findAll(text).forEach { match ->
                addStyle(
                    SpanStyle(color = Color(0xFF_6A9955)),
                    match.range.first,
                    match.range.last + 1
                )
            }
            
            // Highlight strings
            "\".*?\"".toRegex().findAll(text).forEach { match ->
                addStyle(
                    SpanStyle(color = Color(0xFF_CE9178)),
                    match.range.first,
                    match.range.last + 1
                )
            }
            
            // Highlight numbers
            "\\b\\d+(\\.\\d+)?\\b".toRegex().findAll(text).forEach { match ->
                addStyle(
                    SpanStyle(color = Color(0xFF_B5CEA8)),
                    match.range.first,
                    match.range.last + 1
                )
            }
        }
    }
    
    private fun highlightTomlSyntax(text: String): AnnotatedString {
        return buildAnnotatedString {
            append(text)
            
            // Highlight section headers [section]
            "\\[.*?]".toRegex().findAll(text).forEach { match ->
                addStyle(
                    SpanStyle(color = Color(0xFF_4EC9B0), fontWeight = FontWeight.Bold),
                    match.range.first,
                    match.range.last + 1
                )
            }
            
            // Highlight keys (before =)
            "^\\s*([\\w.-]+)\\s*=".toRegex(RegexOption.MULTILINE).findAll(text).forEach { match ->
                match.groupValues.getOrNull(1)?.let { key ->
                    val keyStart = match.range.first + match.value.indexOf(key)
                    addStyle(
                        SpanStyle(color = Color(0xFF_9CDCFE)),
                        keyStart,
                        keyStart + key.length
                    )
                }
            }
            
            // Highlight strings
            "\".*?\"".toRegex().findAll(text).forEach { match ->
                addStyle(
                    SpanStyle(color = Color(0xFF_CE9178)),
                    match.range.first,
                    match.range.last + 1
                )
            }
            
            // Highlight comments
            "#.*$".toRegex(RegexOption.MULTILINE).findAll(text).forEach { match ->
                addStyle(
                    SpanStyle(color = Color(0xFF_6A9955)),
                    match.range.first,
                    match.range.last + 1
                )
            }
            
            // Highlight numbers
            "\\b\\d+(\\.\\d+)?\\b".toRegex().findAll(text).forEach { match ->
                addStyle(
                    SpanStyle(color = Color(0xFF_B5CEA8)),
                    match.range.first,
                    match.range.last + 1
                )
            }
            
            // Highlight booleans
            "\\b(true|false)\\b".toRegex().findAll(text).forEach { match ->
                addStyle(
                    SpanStyle(color = Color(0xFF_569CD6)),
                    match.range.first,
                    match.range.last + 1
                )
            }
        }
    }
}

object CodeEditor: TabTypeInfo {
    override val typeId = TabTypeId("editor")
    override val displayName = "Code Editor"
    override val icon = Icons.Outlined.Code
}

// Platform-specific file reading
expect fun readFileContent(filePath: String): String?

// EditorTabInfo to store file path
data class EditorTabInfo(
    override val id: String,
    override val typeId: TabTypeId,
    override val title: String,
    override val icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Outlined.Code,
    override val tabIcon: TabIcon? = null,
    val filePath: String = ""
) : TabInfo

class CodeEditorTabComponent(
    override val config: TabInfo,
    componentContext: ComponentContext
) : TabComponentWithUI, ComponentContext by componentContext {

    private val _content = MutableStateFlow("")
    val content: StateFlow<String> = _content
    
    private val _language = MutableStateFlow("kotlin")
    val language: StateFlow<String> = _language

    override val tabTypeInfo = CodeEditor
    
    init {
        // Load file content if path is provided
        if (config is EditorTabInfo && config.filePath.isNotEmpty()) {
            loadFile(config.filePath)
        } else {
            // Default content if no file path
            _content.value = """// New file
// Start typing...
""".trimIndent()
        }
    }
    
    private fun loadFile(filePath: String) {
        val fileContent = readFileContent(filePath)
        if (fileContent != null) {
            _content.value = fileContent
            // Update language based on file extension
            updateLanguageFromPath(filePath)
        } else {
            _content.value = "// File not found or error loading: $filePath"
        }
    }
    
    private fun updateLanguageFromPath(path: String) {
        val extension = path.substringAfterLast('.', "")
        _language.value = when (extension) {
            "kt", "kts" -> "kotlin"
            "java" -> "java"
            "js", "jsx" -> "javascript"
            "ts", "tsx" -> "typescript"
            "py" -> "python"
            "json" -> "json"
            "xml" -> "xml"
            "html", "htm" -> "html"
            "css" -> "css"
            "md" -> "markdown"
            "toml" -> "toml"
            "gradle" -> "groovy"
            else -> "text"
        }
    }

    @Composable
    override fun Content() {
        val currentContent by content.collectAsState()
        val currentLanguage by language.collectAsState()
        
        CodeEditorUI(
            content = currentContent,
            onContentChange = { _content.value = it },
            language = currentLanguage,
            modifier = Modifier.fillMaxSize()
        )
    }
    
    fun loadFile(path: String, content: String) {
        _content.value = content
        // Update language based on file extension
        val extension = path.substringAfterLast('.', "")
        _language.value = when (extension) {
            "kt", "kts" -> "kotlin"
            "java" -> "java"
            "js", "jsx" -> "javascript"
            "ts", "tsx" -> "typescript"
            "py" -> "python"
            "json" -> "json"
            "xml" -> "xml"
            "html", "htm" -> "html"
            "css" -> "css"
            "md" -> "markdown"
            "toml" -> "toml"
            "gradle" -> "groovy"
            else -> "text"
        }
    }
}

fun DefaultPlugin.registerCodeEditor() = tabRegistry.registerTabType(CodeEditor) {
    tabInfo, ctx -> CodeEditorTabComponent(tabInfo, ctx)
}
