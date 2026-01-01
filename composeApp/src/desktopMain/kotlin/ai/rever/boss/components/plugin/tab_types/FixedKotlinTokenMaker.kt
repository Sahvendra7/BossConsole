package ai.rever.boss.components.plugin.tab_types

import ai.rever.boss.psi.SemanticCache
import ai.rever.boss.psi.SemanticType
import org.fife.ui.rsyntaxtextarea.Token
import org.fife.ui.rsyntaxtextarea.TokenImpl
import org.fife.ui.rsyntaxtextarea.TokenMaker
import org.fife.ui.rsyntaxtextarea.TokenMakerFactory
import org.fife.ui.rsyntaxtextarea.TokenTypes
import javax.swing.text.Segment

/**
 * Fixed Kotlin TokenMaker that properly recognizes Kotlin keywords.
 *
 * RSyntaxTextArea 3.5.4's built-in KotlinTokenMaker has a bug where it
 * returns IDENTIFIER for all keywords instead of RESERVED_WORD.
 * This class wraps the original and creates new tokens with correct types.
 */
class FixedKotlinTokenMaker : TokenMaker {

    companion object {
        /**
         * Current file path for semantic highlighting lookup.
         * Set this before tokenizing to enable PSI-based semantic highlighting.
         */
        @Volatile
        var currentFilePath: String = ""

        /**
         * Line offset for the current tokenization.
         * Used to calculate absolute offsets for semantic lookup.
         */
        @Volatile
        var currentLineOffset: Int = 0

        // Kotlin hard keywords (always reserved)
        private val KOTLIN_KEYWORDS = setOf(
            "as", "break", "class", "continue", "do", "else", "false", "for",
            "fun", "if", "in", "interface", "is", "null", "object", "package",
            "return", "super", "this", "throw", "true", "try", "typealias",
            "typeof", "val", "var", "when", "while"
        )

        // Kotlin soft keywords (reserved in specific contexts)
        private val KOTLIN_SOFT_KEYWORDS = setOf(
            "by", "catch", "constructor", "delegate", "dynamic", "field",
            "file", "finally", "get", "import", "init", "param", "property",
            "receiver", "set", "setparam", "where"
        )

        // Kotlin modifier keywords
        private val KOTLIN_MODIFIERS = setOf(
            "abstract", "actual", "annotation", "companion", "const",
            "crossinline", "data", "enum", "expect", "external", "final",
            "infix", "inline", "inner", "internal", "lateinit", "noinline",
            "open", "operator", "out", "override", "private", "protected",
            "public", "reified", "sealed", "suspend", "tailrec", "vararg"
        )

        // Built-in types
        private val KOTLIN_TYPES = setOf(
            "Any", "Boolean", "Byte", "Char", "Double", "Float", "Int",
            "Long", "Nothing", "Short", "String", "Unit", "Array",
            "List", "Map", "Set", "MutableList", "MutableMap", "MutableSet"
        )

        // Compose UI types (classes)
        private val COMPOSE_TYPES = setOf(
            "Color", "Modifier", "Alignment", "Arrangement", "ContentScale",
            "TextStyle", "FontWeight", "FontStyle", "TextAlign", "TextOverflow",
            "Shape", "RoundedCornerShape", "CircleShape", "CutCornerShape",
            "Dp", "Sp", "Size", "Offset", "IntOffset", "IntSize",
            "PaddingValues", "WindowInsets", "DpSize", "DpOffset",
            "CornerSize", "BorderStroke", "Brush", "SolidColor",
            "State", "MutableState", "SnapshotStateList", "SnapshotStateMap",
            "Animatable", "AnimationSpec", "TweenSpec", "SpringSpec",
            "KeyboardOptions", "KeyboardActions", "ImeAction", "KeyboardType",
            "FocusRequester", "FocusManager", "FocusState",
            "ScrollState", "LazyListState", "LazyGridState",
            "DrawScope", "Canvas", "Path", "ImageBitmap", "ImageVector",
            "Painter", "BitmapPainter", "VectorPainter"
        )

        // Compose runtime functions (remember, effects, state)
        private val COMPOSE_FUNCTIONS = setOf(
            // State & remember
            "remember", "rememberSaveable", "rememberCoroutineScope",
            "rememberUpdatedState", "rememberScrollState", "rememberLazyListState",
            "rememberWindowState", "rememberPagerState", "rememberDraggableState",
            "rememberSwipeableState", "rememberModalBottomSheetState",
            "rememberDrawerState", "rememberScaffoldState", "rememberBackgroundScope",
            // Effects
            "LaunchedEffect", "DisposableEffect", "SideEffect",
            "derivedStateOf", "produceState", "snapshotFlow",
            // State creation
            "mutableStateOf", "mutableStateListOf", "mutableStateMapOf",
            "mutableIntStateOf", "mutableLongStateOf", "mutableFloatStateOf",
            "mutableDoubleStateOf",
            // Composable callbacks
            "key", "CompositionLocalProvider",
            // Animation
            "animate", "animateFloatAsState", "animateColorAsState",
            "animateDpAsState", "animateIntAsState", "animateOffsetAsState",
            "animateSizeAsState", "animateContentSize", "updateTransition",
            "rememberInfiniteTransition", "Animatable"
        )

        // Compose UI composables (layout, material)
        private val COMPOSE_COMPOSABLES = setOf(
            // Layout
            "Column", "Row", "Box", "Surface", "Scaffold", "Card",
            "LazyColumn", "LazyRow", "LazyVerticalGrid", "LazyHorizontalGrid",
            "FlowRow", "FlowColumn", "BoxWithConstraints",
            "Spacer", "Divider",
            // Material
            "Text", "Button", "IconButton", "TextButton", "OutlinedButton",
            "FloatingActionButton", "ExtendedFloatingActionButton",
            "TextField", "OutlinedTextField", "BasicTextField",
            "Checkbox", "RadioButton", "Switch", "Slider",
            "Icon", "Image", "AsyncImage",
            "TopAppBar", "BottomAppBar", "NavigationBar", "NavigationRail",
            "TabRow", "Tab", "ScrollableTabRow",
            "AlertDialog", "Dialog", "ModalBottomSheet", "BottomSheet",
            "DropdownMenu", "DropdownMenuItem", "ExposedDropdownMenuBox",
            "CircularProgressIndicator", "LinearProgressIndicator",
            "Snackbar", "SnackbarHost",
            "ListItem", "NavigationDrawerItem",
            // Foundation
            "BasicText", "ClickableText", "Canvas", "AndroidView",
            "HorizontalPager", "VerticalPager"
        )

        // Compose annotations
        private val COMPOSE_ANNOTATIONS = setOf(
            "Composable", "Preview", "Immutable", "Stable",
            "NonRestartableComposable", "ReadOnlyComposable", "ExplicitGroupsComposable",
            "OptIn", "Suppress", "Deprecated"
        )

        // Common Kotlin stdlib functions (for yellow highlighting like IntelliJ)
        private val KOTLIN_STDLIB_FUNCTIONS = setOf(
            // Collection operations
            "filter", "filterNot", "filterNotNull", "filterIsInstance",
            "map", "mapNotNull", "mapIndexed", "flatMap",
            "forEach", "forEachIndexed", "onEach", "onEachIndexed",
            "reduce", "reduceOrNull", "fold", "foldIndexed",
            "any", "all", "none", "count",
            "first", "firstOrNull", "last", "lastOrNull",
            "find", "findLast", "single", "singleOrNull",
            "take", "takeLast", "takeWhile", "takeLastWhile",
            "drop", "dropLast", "dropWhile", "dropLastWhile",
            "sorted", "sortedBy", "sortedDescending", "sortedByDescending",
            "reversed", "shuffled", "distinct", "distinctBy",
            "groupBy", "groupingBy", "partition", "associate",
            "associateBy", "associateWith", "zip", "unzip",
            "flatten", "chunked", "windowed",
            "plus", "minus", "union", "intersect", "subtract",
            "contains", "containsKey", "containsValue",
            "isEmpty", "isNotEmpty", "isBlank", "isNotBlank",
            "isNullOrEmpty", "isNullOrBlank",
            "getOrNull", "getOrDefault", "getOrElse", "getOrPut",
            "toList", "toSet", "toMap", "toMutableList", "toMutableSet",
            "toTypedArray", "toIntArray", "toLongArray", "toDoubleArray",
            "joinToString", "joinTo",
            // String operations
            "lowercase", "uppercase", "capitalize", "decapitalize",
            "trim", "trimStart", "trimEnd", "padStart", "padEnd",
            "split", "replace", "replaceFirst", "replaceBefore", "replaceAfter",
            "substring", "substringBefore", "substringAfter",
            "startsWith", "endsWith", "matches", "toRegex",
            "toInt", "toLong", "toDouble", "toFloat", "toBoolean",
            "toIntOrNull", "toLongOrNull", "toDoubleOrNull",
            // Scope functions
            "let", "run", "with", "apply", "also",
            // Null safety
            "takeIf", "takeUnless", "requireNotNull", "checkNotNull",
            "require", "check", "error", "assert",
            // Type operations
            "toString", "hashCode", "equals", "compareTo",
            // Coroutines
            "launch", "async", "await", "runBlocking", "withContext",
            "delay", "yield", "coroutineScope", "supervisorScope",
            // Flow
            "collect", "collectLatest", "emit", "emitAll",
            "stateIn", "shareIn", "launchIn",
            "combine", "merge", "zip", "flatMapConcat", "flatMapMerge",
            "debounce", "throttle", "sample", "distinctUntilChanged",
            "catch", "retry", "retryWhen", "onStart", "onCompletion",
            // Common utility
            "println", "print", "readLine", "readln",
            "repeat", "buildString", "buildList", "buildSet", "buildMap",
            "listOf", "setOf", "mapOf", "mutableListOf", "mutableSetOf", "mutableMapOf",
            "arrayOf", "intArrayOf", "longArrayOf", "doubleArrayOf",
            "emptyList", "emptySet", "emptyMap", "emptyArray",
            "sequenceOf", "generateSequence", "sequence",
            "lazy", "lazyOf", "synchronized"
        )

        // All keywords combined
        private val ALL_KEYWORDS = KOTLIN_KEYWORDS + KOTLIN_SOFT_KEYWORDS + KOTLIN_MODIFIERS

        // All Compose types combined
        private val ALL_COMPOSE_TYPES = COMPOSE_TYPES

        // All functions combined (Compose + stdlib)
        private val ALL_COMPOSE_FUNCTIONS = COMPOSE_FUNCTIONS + COMPOSE_COMPOSABLES + KOTLIN_STDLIB_FUNCTIONS

        /**
         * Gets the correct token type for a lexeme (lexical analysis only).
         */
        fun getTokenType(lexeme: String): Int {
            return when {
                lexeme in ALL_KEYWORDS -> Token.RESERVED_WORD
                lexeme in KOTLIN_TYPES -> Token.DATA_TYPE
                lexeme in ALL_COMPOSE_TYPES -> Token.DATA_TYPE
                lexeme in ALL_COMPOSE_FUNCTIONS -> Token.FUNCTION
                lexeme in COMPOSE_ANNOTATIONS -> Token.ANNOTATION
                lexeme == "true" || lexeme == "false" -> Token.LITERAL_BOOLEAN
                lexeme == "null" -> Token.RESERVED_WORD
                else -> -1 // Not a special token
            }
        }

        /**
         * Gets the token type using semantic analysis from PSI cache.
         * Falls back to lexical analysis if no semantic info available.
         */
        fun getSemanticTokenType(lexeme: String, absoluteOffset: Int, length: Int): Int {
            // First check semantic cache if file path is set
            if (currentFilePath.isNotEmpty()) {
                val semanticType = SemanticCache.findSemanticType(currentFilePath, absoluteOffset, length)
                if (semanticType != null) {
                    return semanticType.tokenType
                }
            }

            // Fall back to lexical analysis
            return getTokenType(lexeme)
        }
    }

    // Delegate to the original KotlinTokenMaker for basic tokenization
    private val delegate: TokenMaker by lazy {
        TokenMakerFactory.getDefaultInstance().getTokenMaker("text/kotlin")
    }

    override fun getTokenList(text: Segment, initialTokenType: Int, startOffset: Int): Token {
        // Store the line offset for semantic lookup
        currentLineOffset = startOffset

        // Get tokens from the delegate (original KotlinTokenMaker)
        val originalFirstToken = delegate.getTokenList(text, initialTokenType, startOffset)

        // Create a new token chain with corrected types
        var resultHead: TokenImpl? = null
        var resultTail: TokenImpl? = null

        var current: Token? = originalFirstToken
        while (current != null) {
            val tokenType = current.type

            // Determine the correct type
            val newType = if (tokenType == Token.IDENTIFIER) {
                val lexeme = current.getLexeme()
                if (lexeme != null) {
                    // Calculate absolute offset for semantic lookup
                    val absoluteOffset = startOffset + current.textOffset - text.offset

                    // Use semantic analysis if available, fall back to lexical
                    val correctType = getSemanticTokenType(lexeme, absoluteOffset, lexeme.length)
                    if (correctType != -1) correctType else tokenType
                } else {
                    tokenType
                }
            } else {
                tokenType
            }

            // Create a new token with the correct type
            val newToken = TokenImpl(
                current.textArray,
                current.textOffset,
                current.textOffset + current.length() - 1,
                startOffset + current.textOffset - text.offset,
                newType,
                current.languageIndex
            )

            // Build the linked list
            if (resultHead == null) {
                resultHead = newToken
                resultTail = newToken
            } else {
                resultTail?.setNextToken(newToken)
                resultTail = newToken
            }

            // Move to next token - check for NULL type to terminate
            if (current.type == Token.NULL) break
            current = current.nextToken
        }

        return resultHead ?: TokenImpl()
    }

    // Delegate all other methods to the original
    override fun getLastTokenTypeOnLine(text: Segment, initialTokenType: Int): Int {
        return delegate.getLastTokenTypeOnLine(text, initialTokenType)
    }

    override fun getLineCommentStartAndEnd(languageIndex: Int): Array<String>? {
        return delegate.getLineCommentStartAndEnd(languageIndex)
    }

    override fun getMarkOccurrencesOfTokenType(type: Int): Boolean {
        return delegate.getMarkOccurrencesOfTokenType(type)
    }

    override fun getOccurrenceMarker(): org.fife.ui.rsyntaxtextarea.OccurrenceMarker? {
        return delegate.occurrenceMarker
    }

    override fun getShouldIndentNextLineAfter(token: Token?): Boolean {
        return delegate.getShouldIndentNextLineAfter(token)
    }

    override fun getCurlyBracesDenoteCodeBlocks(languageIndex: Int): Boolean {
        return delegate.getCurlyBracesDenoteCodeBlocks(languageIndex)
    }

    override fun isMarkupLanguage(): Boolean {
        return delegate.isMarkupLanguage
    }

    override fun isIdentifierChar(languageIndex: Int, ch: Char): Boolean {
        return delegate.isIdentifierChar(languageIndex, ch)
    }

    override fun getClosestStandardTokenTypeForInternalType(type: Int): Int {
        return type // Just return the type as-is
    }

    override fun getInsertBreakAction(): javax.swing.Action? {
        return null // Use default behavior
    }

    override fun addNullToken() {
        // No-op - handled in getTokenList
    }

    override fun addToken(array: CharArray, start: Int, end: Int, tokenType: Int, startOffset: Int) {
        // No-op - we use getTokenList approach
    }
}

/**
 * Utility to post-process tokens from the original KotlinTokenMaker.
 * Use this when you can't replace the TokenMaker but want to fix keyword types.
 */
object KotlinTokenFixer {
    /**
     * Creates a corrected token list from an existing one.
     * Returns a new linked list of tokens with IDENTIFIER tokens for keywords
     * converted to RESERVED_WORD.
     */
    fun fixTokenTypes(firstToken: Token?, startOffset: Int, textOffset: Int): Token? {
        if (firstToken == null) return null

        var resultHead: TokenImpl? = null
        var resultTail: TokenImpl? = null

        var current: Token? = firstToken
        while (current != null) {
            val tokenType = current.type

            // Determine the correct type
            val newType = if (tokenType == Token.IDENTIFIER) {
                val lexeme = current.getLexeme()
                if (lexeme != null) {
                    val correctType = FixedKotlinTokenMaker.getTokenType(lexeme)
                    if (correctType != -1) correctType else tokenType
                } else {
                    tokenType
                }
            } else {
                tokenType
            }

            // Create a new token with the correct type
            val newToken = TokenImpl(
                current.textArray,
                current.textOffset,
                current.textOffset + current.length() - 1,
                startOffset + current.textOffset - textOffset,
                newType,
                current.languageIndex
            )

            // Build the linked list
            if (resultHead == null) {
                resultHead = newToken
                resultTail = newToken
            } else {
                resultTail?.setNextToken(newToken)
                resultTail = newToken
            }

            // Move to next token - check for NULL type to terminate
            if (current.type == Token.NULL) break
            current = current.nextToken
        }

        return resultHead
    }
}
