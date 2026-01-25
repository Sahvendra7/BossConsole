package ai.rever.bosseditor.highlight.lexers

import ai.rever.bosseditor.highlight.*

/**
 * Kotlin syntax highlighting lexer.
 *
 * Provides comprehensive tokenization for Kotlin code including:
 * - Hard keywords (if, for, when, etc.)
 * - Soft keywords (by, catch, get, set, etc.)
 * - Modifiers (private, public, suspend, etc.)
 * - Built-in types (Int, String, List, etc.)
 * - Compose types and functions
 * - Kotlin stdlib functions
 * - Comments (single-line, block, doc)
 * - Strings (regular, raw/multiline)
 * - String templates ($var, ${expr})
 * - Numbers (int, float, hex, binary)
 * - Annotations (@Composable, etc.)
 */
class KotlinLexer : BaseLexer() {

    override val languageId: String = "kotlin"
    override val fileExtensions: List<String> = listOf("kt", "kts")

    companion object {
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
            "List", "Map", "Set", "MutableList", "MutableMap", "MutableSet",
            "Sequence", "Pair", "Triple", "Result", "Throwable", "Exception",
            "Error", "Number", "Comparable", "Iterable", "Collection"
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
            "OptIn", "Suppress", "Deprecated", "JvmStatic", "JvmOverloads",
            "JvmName", "JvmField", "JvmInline", "JvmRecord"
        )

        // Common Kotlin stdlib functions
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
            "combine", "merge", "flatMapConcat", "flatMapMerge",
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

        // All types combined
        private val ALL_TYPES = KOTLIN_TYPES + COMPOSE_TYPES

        // All functions combined
        private val ALL_FUNCTIONS = COMPOSE_FUNCTIONS + COMPOSE_COMPOSABLES + KOTLIN_STDLIB_FUNCTIONS

        // Operators
        private val OPERATORS = setOf(
            '+', '-', '*', '/', '%', '=', '<', '>', '!', '&', '|', '^', '~', '?', ':'
        )

        // Punctuation
        private val PUNCTUATION = setOf(
            '(', ')', '[', ']', '{', '}', ',', '.', ';', '@'
        )
    }

    override fun tokenizeLine(
        line: String,
        lineNumber: Int,
        startState: LexerState
    ): LineTokens {
        val tokens = mutableListOf<Token>()
        var pos = 0
        var state = startState

        while (pos < line.length) {
            val char = line[pos]

            when (state) {
                LexerState.IN_BLOCK_COMMENT -> {
                    val (endPos, complete) = readBlockComment(line, pos)
                    tokens.add(Token(pos, endPos, TokenType.COMMENT_BLOCK))
                    pos = endPos
                    if (complete) {
                        state = LexerState.NORMAL
                    }
                }

                LexerState.IN_DOC_COMMENT -> {
                    val (endPos, complete) = readBlockComment(line, pos)
                    // Check for doc comment tags
                    val commentText = line.substring(pos, endPos)
                    tokens.addAll(tokenizeDocComment(pos, commentText))
                    pos = endPos
                    if (complete) {
                        state = LexerState.NORMAL
                    }
                }

                LexerState.IN_MULTILINE_STRING, LexerState.IN_RAW_STRING -> {
                    val (endPos, tokens2, complete) = continueRawString(line, pos)
                    tokens.addAll(tokens2)
                    pos = endPos
                    if (complete) {
                        state = LexerState.NORMAL
                    }
                }

                LexerState.NORMAL -> {
                    when {
                        // Whitespace
                        char.isWhitespace() -> {
                            val end = skipWhitespace(line, pos)
                            // Don't create tokens for whitespace (optional)
                            pos = end
                        }

                        // Single-line comment
                        matchesAt(line, pos, "//") -> {
                            tokens.add(Token(pos, line.length, TokenType.COMMENT))
                            pos = line.length
                        }

                        // Doc comment start
                        matchesAt(line, pos, "/**") -> {
                            val (endPos, complete) = readBlockComment(line, pos + 3)
                            val fullEnd = endPos
                            val commentText = line.substring(pos, fullEnd)
                            tokens.addAll(tokenizeDocComment(pos, commentText))
                            pos = fullEnd
                            if (!complete) {
                                state = LexerState.IN_DOC_COMMENT
                            }
                        }

                        // Block comment start
                        matchesAt(line, pos, "/*") -> {
                            val (endPos, complete) = readBlockComment(line, pos + 2)
                            tokens.add(Token(pos, endPos, TokenType.COMMENT_BLOCK))
                            pos = endPos
                            if (!complete) {
                                state = LexerState.IN_BLOCK_COMMENT
                            }
                        }

                        // Raw string (triple-quoted)
                        matchesAt(line, pos, "\"\"\"") -> {
                            val (endPos, stringTokens, complete) = tokenizeRawString(line, pos)
                            tokens.addAll(stringTokens)
                            pos = endPos
                            if (!complete) {
                                state = LexerState.IN_RAW_STRING
                            }
                        }

                        // Regular string
                        char == '"' -> {
                            val (endPos, stringTokens) = tokenizeString(line, pos)
                            tokens.addAll(stringTokens)
                            pos = endPos
                        }

                        // Character literal
                        char == '\'' -> {
                            val endPos = readCharLiteral(line, pos)
                            if (endPos > pos) {
                                tokens.add(Token(pos, endPos, TokenType.CHAR))
                            }
                            pos = endPos.coerceAtLeast(pos + 1)
                        }

                        // Annotation
                        char == '@' -> {
                            val nameStart = pos + 1
                            val nameEnd = readIdentifier(line, nameStart)
                            if (nameEnd > nameStart) {
                                val annotationName = line.substring(nameStart, nameEnd)
                                val tokenType = if (annotationName in COMPOSE_ANNOTATIONS) {
                                    TokenType.ANNOTATION
                                } else {
                                    TokenType.ANNOTATION
                                }
                                tokens.add(Token(pos, nameEnd, tokenType))
                                pos = nameEnd
                            } else {
                                tokens.add(Token(pos, pos + 1, TokenType.PUNCTUATION))
                                pos++
                            }
                        }

                        // Number
                        char.isDigit() || (char == '.' && pos + 1 < line.length && line[pos + 1].isDigit()) -> {
                            val endPos = readNumber(line, pos)
                            if (endPos > pos) {
                                tokens.add(Token(pos, endPos, TokenType.NUMBER))
                                pos = endPos
                            } else {
                                pos++
                            }
                        }

                        // Identifier or keyword
                        isIdentifierStart(char) -> {
                            val endPos = readIdentifier(line, pos)
                            val identifier = line.substring(pos, endPos)
                            val tokenType = classifyIdentifier(identifier)
                            tokens.add(Token(pos, endPos, tokenType))
                            pos = endPos
                        }

                        // Multi-character operators
                        matchesAt(line, pos, "?.") || matchesAt(line, pos, "?:") ||
                        matchesAt(line, pos, "::") || matchesAt(line, pos, "..") ||
                        matchesAt(line, pos, "->") || matchesAt(line, pos, "=>") ||
                        matchesAt(line, pos, "==") || matchesAt(line, pos, "!=") ||
                        matchesAt(line, pos, "===") || matchesAt(line, pos, "!==") ||
                        matchesAt(line, pos, "<=") || matchesAt(line, pos, ">=") ||
                        matchesAt(line, pos, "&&") || matchesAt(line, pos, "||") ||
                        matchesAt(line, pos, "++") || matchesAt(line, pos, "--") ||
                        matchesAt(line, pos, "+=") || matchesAt(line, pos, "-=") ||
                        matchesAt(line, pos, "*=") || matchesAt(line, pos, "/=") ||
                        matchesAt(line, pos, "%=") || matchesAt(line, pos, "?") -> {
                            val opLen = when {
                                matchesAt(line, pos, "===") || matchesAt(line, pos, "!==") -> 3
                                matchesAt(line, pos, "?.") || matchesAt(line, pos, "?:") ||
                                matchesAt(line, pos, "::") || matchesAt(line, pos, "..") ||
                                matchesAt(line, pos, "->") || matchesAt(line, pos, "=>") ||
                                matchesAt(line, pos, "==") || matchesAt(line, pos, "!=") ||
                                matchesAt(line, pos, "<=") || matchesAt(line, pos, ">=") ||
                                matchesAt(line, pos, "&&") || matchesAt(line, pos, "||") ||
                                matchesAt(line, pos, "++") || matchesAt(line, pos, "--") ||
                                matchesAt(line, pos, "+=") || matchesAt(line, pos, "-=") ||
                                matchesAt(line, pos, "*=") || matchesAt(line, pos, "/=") ||
                                matchesAt(line, pos, "%=") -> 2
                                else -> 1
                            }
                            val opType = when {
                                matchesAt(line, pos, "&&") || matchesAt(line, pos, "||") ||
                                char == '!' -> TokenType.OPERATOR_LOGICAL
                                matchesAt(line, pos, "==") || matchesAt(line, pos, "!=") ||
                                matchesAt(line, pos, "===") || matchesAt(line, pos, "!==") ||
                                matchesAt(line, pos, "<=") || matchesAt(line, pos, ">=") ||
                                char == '<' || char == '>' -> TokenType.OPERATOR_COMPARISON
                                else -> TokenType.OPERATOR
                            }
                            tokens.add(Token(pos, pos + opLen, opType))
                            pos += opLen
                        }

                        // Single-character operators
                        char in OPERATORS -> {
                            val opType = when (char) {
                                '!' -> TokenType.OPERATOR_LOGICAL
                                '<', '>' -> TokenType.OPERATOR_COMPARISON
                                else -> TokenType.OPERATOR
                            }
                            tokens.add(Token(pos, pos + 1, opType))
                            pos++
                        }

                        // Brackets
                        char == '{' || char == '}' || char == '[' || char == ']' -> {
                            tokens.add(Token(pos, pos + 1, TokenType.BRACKET))
                            pos++
                        }

                        // Parentheses
                        char == '(' || char == ')' -> {
                            tokens.add(Token(pos, pos + 1, TokenType.PARENTHESIS))
                            pos++
                        }

                        // Other punctuation
                        char in PUNCTUATION -> {
                            tokens.add(Token(pos, pos + 1, TokenType.PUNCTUATION))
                            pos++
                        }

                        // Unknown character
                        else -> {
                            tokens.add(Token(pos, pos + 1, TokenType.DEFAULT))
                            pos++
                        }
                    }
                }
            }
        }

        return LineTokens(tokens, state)
    }

    override fun classifyIdentifier(identifier: String): TokenType {
        return when {
            identifier in ALL_KEYWORDS -> TokenType.KEYWORD
            identifier == "true" || identifier == "false" -> TokenType.BOOLEAN
            identifier == "null" -> TokenType.NULL
            identifier in ALL_TYPES -> TokenType.TYPE
            identifier in COMPOSE_ANNOTATIONS -> TokenType.ANNOTATION
            identifier in ALL_FUNCTIONS -> TokenType.FUNCTION_CALL
            // Check if it looks like a type (starts with uppercase)
            identifier.firstOrNull()?.isUpperCase() == true -> TokenType.TYPE
            else -> TokenType.IDENTIFIER
        }
    }

    /**
     * Tokenizes a regular string with escape sequences and string templates.
     */
    private fun tokenizeString(line: String, start: Int): Pair<Int, List<Token>> {
        val tokens = mutableListOf<Token>()
        var pos = start + 1 // Skip opening quote
        var tokenStart = start

        while (pos < line.length) {
            val char = line[pos]
            when {
                // End of string
                char == '"' -> {
                    tokens.add(Token(tokenStart, pos + 1, TokenType.STRING))
                    return (pos + 1) to tokens
                }

                // Escape sequence
                char == '\\' && pos + 1 < line.length -> {
                    if (tokenStart < pos) {
                        tokens.add(Token(tokenStart, pos, TokenType.STRING))
                    }
                    tokens.add(Token(pos, pos + 2, TokenType.STRING_ESCAPE))
                    pos += 2
                    tokenStart = pos
                }

                // String template: $identifier or ${expression}
                char == '$' && pos + 1 < line.length -> {
                    if (tokenStart < pos) {
                        tokens.add(Token(tokenStart, pos, TokenType.STRING))
                    }

                    val nextChar = line[pos + 1]
                    if (nextChar == '{') {
                        // ${expression} - find matching }
                        val exprEnd = findMatchingBrace(line, pos + 2)
                        tokens.add(Token(pos, exprEnd, TokenType.STRING_TEMPLATE))
                        pos = exprEnd
                    } else if (nextChar.isLetter() || nextChar == '_') {
                        // $identifier
                        val idEnd = readIdentifier(line, pos + 1)
                        tokens.add(Token(pos, idEnd, TokenType.STRING_TEMPLATE))
                        pos = idEnd
                    } else {
                        pos++
                    }
                    tokenStart = pos
                }

                else -> pos++
            }
        }

        // Unterminated string
        if (tokenStart < line.length) {
            tokens.add(Token(tokenStart, line.length, TokenType.STRING))
        }
        return line.length to tokens
    }

    /**
     * Tokenizes a raw (triple-quoted) string.
     */
    private fun tokenizeRawString(line: String, start: Int): Triple<Int, List<Token>, Boolean> {
        val tokens = mutableListOf<Token>()
        var pos = start + 3 // Skip opening """
        var tokenStart = start

        while (pos + 2 < line.length) {
            val char = line[pos]
            when {
                // End of raw string
                line[pos] == '"' && line[pos + 1] == '"' && line[pos + 2] == '"' -> {
                    tokens.add(Token(tokenStart, pos + 3, TokenType.STRING))
                    return Triple(pos + 3, tokens, true)
                }

                // String template in raw string
                char == '$' && pos + 1 < line.length -> {
                    if (tokenStart < pos) {
                        tokens.add(Token(tokenStart, pos, TokenType.STRING))
                    }

                    val nextChar = line[pos + 1]
                    if (nextChar == '{') {
                        val exprEnd = findMatchingBrace(line, pos + 2)
                        tokens.add(Token(pos, exprEnd, TokenType.STRING_TEMPLATE))
                        pos = exprEnd
                    } else if (nextChar.isLetter() || nextChar == '_') {
                        val idEnd = readIdentifier(line, pos + 1)
                        tokens.add(Token(pos, idEnd, TokenType.STRING_TEMPLATE))
                        pos = idEnd
                    } else {
                        pos++
                    }
                    tokenStart = pos
                }

                else -> pos++
            }
        }

        // Check for closing at end of line
        if (pos + 3 <= line.length &&
            line[pos] == '"' && line[pos + 1] == '"' && line[pos + 2] == '"') {
            tokens.add(Token(tokenStart, pos + 3, TokenType.STRING))
            return Triple(pos + 3, tokens, true)
        }

        // Continues on next line
        if (tokenStart < line.length) {
            tokens.add(Token(tokenStart, line.length, TokenType.STRING))
        }
        return Triple(line.length, tokens, false)
    }

    /**
     * Continues parsing a raw string from a previous line.
     */
    private fun continueRawString(line: String, start: Int): Triple<Int, List<Token>, Boolean> {
        val tokens = mutableListOf<Token>()
        var pos = start
        var tokenStart = start

        while (pos + 2 < line.length) {
            val char = line[pos]
            when {
                // End of raw string
                line[pos] == '"' && line[pos + 1] == '"' && line[pos + 2] == '"' -> {
                    tokens.add(Token(tokenStart, pos + 3, TokenType.STRING))
                    return Triple(pos + 3, tokens, true)
                }

                // String template
                char == '$' && pos + 1 < line.length -> {
                    if (tokenStart < pos) {
                        tokens.add(Token(tokenStart, pos, TokenType.STRING))
                    }

                    val nextChar = line[pos + 1]
                    if (nextChar == '{') {
                        val exprEnd = findMatchingBrace(line, pos + 2)
                        tokens.add(Token(pos, exprEnd, TokenType.STRING_TEMPLATE))
                        pos = exprEnd
                    } else if (nextChar.isLetter() || nextChar == '_') {
                        val idEnd = readIdentifier(line, pos + 1)
                        tokens.add(Token(pos, idEnd, TokenType.STRING_TEMPLATE))
                        pos = idEnd
                    } else {
                        pos++
                    }
                    tokenStart = pos
                }

                else -> pos++
            }
        }

        // Check for closing at end of line
        if (pos + 3 <= line.length &&
            line[pos] == '"' && line[pos + 1] == '"' && line[pos + 2] == '"') {
            tokens.add(Token(tokenStart, pos + 3, TokenType.STRING))
            return Triple(pos + 3, tokens, true)
        }

        // Continues on next line
        if (tokenStart < line.length) {
            tokens.add(Token(tokenStart, line.length, TokenType.STRING))
        }
        return Triple(line.length, tokens, false)
    }

    /**
     * Finds the matching closing brace for string template expressions.
     */
    private fun findMatchingBrace(line: String, start: Int): Int {
        var depth = 1
        var pos = start

        while (pos < line.length && depth > 0) {
            when (line[pos]) {
                '{' -> depth++
                '}' -> depth--
            }
            pos++
        }
        return pos
    }

    /**
     * Tokenizes a doc comment, highlighting doc tags.
     */
    private fun tokenizeDocComment(start: Int, text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var pos = 0
        var tokenStart = 0

        // Doc comment tags
        val docTags = setOf(
            "@param", "@return", "@throws", "@exception", "@see", "@since",
            "@author", "@version", "@deprecated", "@sample", "@suppress",
            "@property", "@constructor", "@receiver"
        )

        while (pos < text.length) {
            if (text[pos] == '@' && pos + 1 < text.length) {
                // Check for doc tag
                val tagEnd = (pos + 1 until text.length).firstOrNull {
                    !text[it].isLetter()
                } ?: text.length

                val potentialTag = text.substring(pos, tagEnd)
                if (potentialTag in docTags) {
                    // Add comment before tag
                    if (tokenStart < pos) {
                        tokens.add(Token(start + tokenStart, start + pos, TokenType.COMMENT_DOC))
                    }
                    // Add tag
                    tokens.add(Token(start + pos, start + tagEnd, TokenType.COMMENT_DOC_TAG))
                    pos = tagEnd
                    tokenStart = pos
                    continue
                }
            }
            pos++
        }

        // Add remaining comment
        if (tokenStart < text.length) {
            tokens.add(Token(start + tokenStart, start + text.length, TokenType.COMMENT_DOC))
        }

        return tokens
    }
}
