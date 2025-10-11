package ai.rever.boss.components.plugin.panels.bottom.terminal

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight

data class TerminalCell(
    val char: Char = ' ',
    val foregroundColor: Color = Color.Unspecified,
    val backgroundColor: Color = Color.Unspecified,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false
)

class TerminalEmulator(
    private var columns: Int = 120,
    private var rows: Int = 24
) {
    // Screen buffer - array of lines, each line is array of cells
    private var buffer = Array(rows) { Array(columns) { TerminalCell() } }
    
    // Alternate screen buffer support
    private var primaryBuffer = buffer
    private var alternateBuffer = Array(rows) { Array(columns) { TerminalCell() } }
    private var usingAlternateBuffer = false
    
    // Saved primary buffer cursor position
    private var savedPrimaryCursorRow = 0
    private var savedPrimaryCursorCol = 0
    
    // Scrolling region
    private var scrollTop = 0
    private var scrollBottom = rows - 1
    
    // Cursor position (0-based)
    private var cursorRow = 0
    private var cursorCol = 0
    
    // Cursor visibility
    private var cursorVisible = true
    
    // Saved cursor position for save/restore operations
    private var savedCursorRow = 0
    private var savedCursorCol = 0
    
    // Current attributes for new characters
    private var currentForeground = Color.Unspecified
    private var currentBackground = Color.Unspecified
    private var currentBold = false
    private var currentItalic = false
    private var currentUnderline = false
    
    // Character sets (G0, G1, G2, G3)
    private enum class CharacterSet {
        ASCII,      // US ASCII
        GRAPHICS,   // Line drawing/special graphics
        UK,         // UK charset
        UNCHANGED   // Keep as-is
    }
    
    private var g0CharSet = CharacterSet.ASCII
    private var g1CharSet = CharacterSet.ASCII
    private var g2CharSet = CharacterSet.ASCII
    private var g3CharSet = CharacterSet.ASCII
    
    // Currently active character set (GL)
    private var currentCharSet = 0 // 0=G0, 1=G1, 2=G2, 3=G3
    
    // Single shift state
    private var singleShift = -1 // -1=none, 2=SS2(G2), 3=SS3(G3)
    
    // Parser state
    private val escapeSequence = StringBuilder()
    private var inEscapeSequence = false
    
    // Scrollback buffer with limit
    private val scrollbackLines = mutableListOf<Array<TerminalCell>>()
    private val maxBufferSize = 2000 // Total lines limit (scrollback + visible)
    private val maxScrollback: Int
        get() = maxBufferSize - rows // Dynamically calculate based on current rows
    
    // Window title
    private var windowTitle = ""
    private var iconTitle = ""
    
    fun processInput(input: String) {
        for (char in input) {
            processChar(char)
        }
    }
    
    private fun processChar(char: Char) {
        when {
            inEscapeSequence -> handleEscapeSequence(char)
            char == '\u001B' -> {
                inEscapeSequence = true
                escapeSequence.clear()
            }
            else -> handleRegularChar(char)
        }
    }
    
    private fun handleEscapeSequence(char: Char) {
        escapeSequence.append(char)
        
        // Check if we have a complete sequence
        when {
            // CSI sequences: ESC [ ... letter (@ through ~)
            escapeSequence.startsWith("[") && escapeSequence.length > 1 && char >= '@' && char <= '~' -> {
                // Extract parameters (everything between [ and the command char)
                val params = if (escapeSequence.length > 2) {
                    escapeSequence.substring(1, escapeSequence.length - 1)
                } else {
                    "" // No parameters, just ESC[<command>
                }
                processCsiSequence(params, char)
                inEscapeSequence = false
            }
            // OSC sequences: ESC ] ... BEL or ESC \
            escapeSequence.startsWith("]") && (char == '\u0007' || 
                (escapeSequence.length > 1 && escapeSequence.endsWith("\\"))) -> {
                val content = if (escapeSequence.length > 1) {
                    escapeSequence.substring(1)
                } else {
                    ""
                }
                processOscSequence(content)
                inEscapeSequence = false
            }
            // DCS sequences: ESC P ... ESC \
            escapeSequence.startsWith("P") && escapeSequence.length > 1 && 
                escapeSequence.endsWith("\\") && escapeSequence[escapeSequence.length - 2] == '\u001B' -> {
                // Device Control String - ignore for now
                inEscapeSequence = false
            }
            // PM sequences: ESC ^ ... ESC \
            escapeSequence.startsWith("^") && escapeSequence.length > 1 && 
                escapeSequence.endsWith("\\") && escapeSequence[escapeSequence.length - 2] == '\u001B' -> {
                // Privacy Message - ignore for now
                inEscapeSequence = false
            }
            // APC sequences: ESC _ ... ESC \
            escapeSequence.startsWith("_") && escapeSequence.length > 1 && 
                escapeSequence.endsWith("\\") && escapeSequence[escapeSequence.length - 2] == '\u001B' -> {
                // Application Program Command - ignore for now
                inEscapeSequence = false
            }
            // Two-character sequences (ESC followed by a single character)
            escapeSequence.length == 1 -> {
                when (char) {
                    '[', ']', 'P', '^', '_' -> {
                        // Start of multi-character sequence, continue building
                    }
                    '(', ')', '*', '+' -> {
                        // Character set selection - start of three-character sequence, continue building
                    }
                    else -> {
                        // Other two-character sequences
                        processTwoCharSequence(char)
                        inEscapeSequence = false
                    }
                }
            }
            // Character set designation: ESC ( A, ESC ) 0, etc.
            (escapeSequence.length == 2 && 
                (escapeSequence[0] == '(' || escapeSequence[0] == ')' || 
                 escapeSequence[0] == '*' || escapeSequence[0] == '+')) -> {
                // Character set designation complete
                processCharacterSetDesignation(escapeSequence[0], char)
                inEscapeSequence = false
            }
            // Handle malformed sequences - if we get too long, just abort
            escapeSequence.length > 100 -> {
                // Malformed sequence, just ignore it
                inEscapeSequence = false
            }
        }
    }
    
    private fun processCsiSequence(params: String, command: Char) {
        // Extract intermediate characters (?, >, =, etc.) from the beginning of params
        var intermediates = ""
        var actualParams = params
        
        if (params.isNotEmpty()) {
            // Check for intermediate characters at the start
            val firstChar = params[0]
            if (firstChar == '?' || firstChar == '>' || firstChar == '=' || firstChar == '<' || firstChar == '!') {
                intermediates = firstChar.toString()
                actualParams = params.substring(1)
            }
        }
        
        // Parse parameters more carefully - handle empty params and non-numeric values
        val args = if (actualParams.isEmpty()) {
            emptyList()
        } else {
            actualParams.split(';').map { param ->
                param.toIntOrNull() ?: 0  // Default to 0 for invalid params
            }
        }
        
        when (command) {
            'A' -> moveCursorUp(args.getOrElse(0) { 1 }) // Cursor up
            'B' -> moveCursorDown(args.getOrElse(0) { 1 }) // Cursor down
            'C' -> moveCursorRight(args.getOrElse(0) { 1 }) // Cursor forward
            'D' -> moveCursorLeft(args.getOrElse(0) { 1 }) // Cursor back
            'E' -> { // Cursor next line
                cursorCol = 0
                moveCursorDown(args.getOrElse(0) { 1 })
            }
            'F' -> { // Cursor previous line
                cursorCol = 0
                moveCursorUp(args.getOrElse(0) { 1 })
            }
            'G' -> cursorCol = minOf(columns - 1, maxOf(0, args.getOrElse(0) { 1 } - 1)) // Cursor horizontal absolute
            'H', 'f' -> setCursorPosition(args.getOrElse(0) { 1 }, args.getOrElse(1) { 1 }) // Cursor position
            'J' -> clearScreen(args.getOrElse(0) { 0 }) // Clear screen
            'K' -> clearLine(args.getOrElse(0) { 0 }) // Clear line
            'L' -> insertLines(args.getOrElse(0) { 1 }) // Insert lines
            'M' -> deleteLines(args.getOrElse(0) { 1 }) // Delete lines
            'P' -> deleteCharacters(args.getOrElse(0) { 1 }) // Delete characters
            'S' -> scrollUp(args.getOrElse(0) { 1 }) // Scroll up
            'T' -> scrollDown(args.getOrElse(0) { 1 }) // Scroll down
            'X' -> eraseCharacters(args.getOrElse(0) { 1 }) // Erase characters
            'd' -> cursorRow = minOf(rows - 1, maxOf(0, args.getOrElse(0) { 1 } - 1)) // Line position absolute
            'm' -> processColorAndStyle(args) // SGR - Select Graphic Rendition
            'n' -> {} // Device status report - ignore for now
            'r' -> setScrollingRegion(args.getOrElse(0) { 1 }, args.getOrElse(1) { rows }) // Set scrolling region
            's' -> saveCursor() // Save cursor position
            'u' -> restoreCursor() // Restore cursor position
            'h' -> handleModeSet(params) // Set mode - pass original params with intermediates
            'l' -> handleModeReset(params) // Reset mode - pass original params with intermediates
            '!' -> {} // Soft reset - ignore for now
            'c' -> {} // Device attributes - ignore for now
            else -> {
                // Unknown CSI sequence, ignore
            }
        }
    }
    
    private fun processOscSequence(content: String) {
        // Handle OSC sequences (e.g., setting window title)
        // OSC sequences have the format: OSC number ; text ST
        // Where ST can be BEL (\u0007) or ESC \
        
        // Remove the terminator if present
        val cleanContent = content.removeSuffix("\\").removeSuffix("\u0007")
        
        // Split into command number and parameters
        val parts = cleanContent.split(';', limit = 2)
        if (parts.isEmpty()) return
        
        val command = parts[0].toIntOrNull() ?: return
        val text = parts.getOrElse(1) { "" }
        
        when (command) {
            0 -> {
                // Set icon name and window title
                iconTitle = text
                windowTitle = text
            }
            1 -> {
                // Set icon name
                iconTitle = text
            }
            2 -> {
                // Set window title
                windowTitle = text
            }
            4 -> {
                // Set/change color palette
                // Format: OSC 4 ; index ; color ST
                val colorParts = text.split(';', limit = 2)
                if (colorParts.size == 2) {
                    val index = colorParts[0].toIntOrNull()
                    val colorSpec = colorParts[1]
                    if (index != null) {
                        setColorPalette(index, colorSpec)
                    }
                }
            }
            7 -> {
                // Set current working directory (used by some terminals)
                // We'll store but not use this for now
            }
            8 -> {
                // Hyperlink
                // Format: OSC 8 ; params ; uri ST
                // For now, we'll ignore hyperlinks
            }
            10 -> {
                // Set foreground color
                setDefaultForeground(text)
            }
            11 -> {
                // Set background color
                setDefaultBackground(text)
            }
            12 -> {
                // Set cursor color
                // We could implement this by adding a cursor color state
            }
            52 -> {
                // Clipboard operations
                // Format: OSC 52 ; clipboard ; base64-data ST
                // For security reasons, we'll ignore clipboard operations for now
            }
            104 -> {
                // Reset color palette
                // Format: OSC 104 ; index ST or OSC 104 ST (reset all)
                if (text.isEmpty()) {
                    resetColorPalette()
                } else {
                    val index = text.toIntOrNull()
                    if (index != null) {
                        resetColorPaletteEntry(index)
                    }
                }
            }
            110 -> {
                // Reset foreground color
                resetDefaultForeground()
            }
            111 -> {
                // Reset background color
                resetDefaultBackground()
            }
            112 -> {
                // Reset cursor color
            }
        }
    }
    
    // Color palette for custom colors
    private val customColorPalette = mutableMapOf<Int, Color>()
    private var defaultForegroundColor: Color? = null
    private var defaultBackgroundColor: Color? = null
    
    private fun setColorPalette(index: Int, colorSpec: String) {
        // Parse color specification (e.g., "rgb:ff/00/00" or "#ff0000")
        val color = parseColorSpec(colorSpec)
        if (color != null && index in 0..255) {
            customColorPalette[index] = color
        }
    }
    
    private fun setDefaultForeground(colorSpec: String) {
        defaultForegroundColor = parseColorSpec(colorSpec)
    }
    
    private fun setDefaultBackground(colorSpec: String) {
        defaultBackgroundColor = parseColorSpec(colorSpec)
    }
    
    private fun resetColorPalette() {
        customColorPalette.clear()
    }
    
    private fun resetColorPaletteEntry(index: Int) {
        customColorPalette.remove(index)
    }
    
    private fun resetDefaultForeground() {
        defaultForegroundColor = null
    }
    
    private fun resetDefaultBackground() {
        defaultBackgroundColor = null
    }
    
    private fun parseColorSpec(spec: String): Color? {
        return when {
            // RGB format: rgb:rr/gg/bb or rgb:rrrr/gggg/bbbb
            spec.startsWith("rgb:") -> {
                val rgb = spec.substring(4).split('/')
                if (rgb.size == 3) {
                    try {
                        val r = rgb[0].padEnd(4, rgb[0].last()).substring(0, 2).toInt(16)
                        val g = rgb[1].padEnd(4, rgb[1].last()).substring(0, 2).toInt(16)
                        val b = rgb[2].padEnd(4, rgb[2].last()).substring(0, 2).toInt(16)
                        Color(r, g, b)
                    } catch (e: Exception) {
                        null
                    }
                } else null
            }
            // Hex format: #rrggbb
            spec.startsWith("#") && spec.length == 7 -> {
                try {
                    val r = spec.substring(1, 3).toInt(16)
                    val g = spec.substring(3, 5).toInt(16)
                    val b = spec.substring(5, 7).toInt(16)
                    Color(r, g, b)
                } catch (e: Exception) {
                    null
                }
            }
            else -> null
        }
    }
    
    private fun handleRegularChar(char: Char) {
        when (char) {
            '\n' -> {
                cursorCol = 0
                cursorRow++
                if (cursorRow >= rows) {
                    scrollUp()
                    cursorRow = rows - 1
                }
            }
            '\r' -> cursorCol = 0
            '\b' -> if (cursorCol > 0) cursorCol--
            '\t' -> {
                // Move to next tab stop (every 8 columns)
                val nextTab = ((cursorCol / 8) + 1) * 8
                cursorCol = minOf(nextTab, columns - 1)
            }
            '\u000E' -> currentCharSet = 1 // SO - Shift Out (use G1)
            '\u000F' -> currentCharSet = 0 // SI - Shift In (use G0)
            else -> {
                if (char.code >= 32) {
                    // Translate character based on active character set
                    val displayChar = translateCharacter(char)
                    
                    // Place character at cursor position
                    if (cursorCol < columns) {
                        buffer[cursorRow][cursorCol] = TerminalCell(
                            char = displayChar,
                            foregroundColor = currentForeground,
                            backgroundColor = currentBackground,
                            bold = currentBold,
                            italic = currentItalic,
                            underline = currentUnderline
                        )
                        cursorCol++
                        if (cursorCol >= columns) {
                            cursorCol = 0
                            cursorRow++
                            if (cursorRow >= rows) {
                                scrollUp()
                                cursorRow = rows - 1
                            }
                        }
                    }
                    
                    // Reset single shift if used
                    if (singleShift >= 0) {
                        singleShift = -1
                    }
                }
            }
        }
    }
    
    private fun translateCharacter(char: Char): Char {
        // Determine which character set to use
        val activeSet = when {
            singleShift >= 0 -> singleShift
            else -> currentCharSet
        }
        
        val charSet = when (activeSet) {
            0 -> g0CharSet
            1 -> g1CharSet
            2 -> g2CharSet
            3 -> g3CharSet
            else -> CharacterSet.ASCII
        }
        
        // Translate character based on character set
        return when (charSet) {
            CharacterSet.GRAPHICS -> translateGraphicsChar(char)
            CharacterSet.UK -> translateUKChar(char)
            else -> char // ASCII or UNCHANGED
        }
    }
    
    private fun translateGraphicsChar(char: Char): Char {
        // DEC Special Graphics character set (line drawing)
        return when (char) {
            '`' -> '◆' // Diamond
            'a' -> '▒' // Checkerboard
            'b' -> '␉' // HT
            'c' -> '␌' // FF
            'd' -> '␍' // CR
            'e' -> '␊' // LF
            'f' -> '°' // Degree
            'g' -> '±' // Plus/minus
            'h' -> '␤' // NL
            'i' -> '␋' // VT
            'j' -> '┘' // Lower right corner
            'k' -> '┐' // Upper right corner
            'l' -> '┌' // Upper left corner
            'm' -> '└' // Lower left corner
            'n' -> '┼' // Crossing lines
            'o' -> '⎺' // Scan line 1
            'p' -> '⎻' // Scan line 3
            'q' -> '─' // Horizontal line
            'r' -> '⎼' // Scan line 5
            's' -> '⎽' // Scan line 7
            't' -> '├' // T pointing right
            'u' -> '┤' // T pointing left
            'v' -> '┴' // T pointing up
            'w' -> '┬' // T pointing down
            'x' -> '│' // Vertical line
            'y' -> '≤' // Less than or equal
            'z' -> '≥' // Greater than or equal
            '{' -> 'π' // Pi
            '|' -> '≠' // Not equal
            '}' -> '£' // UK pound
            '~' -> '·' // Centered dot
            else -> char // Keep unchanged
        }
    }
    
    private fun translateUKChar(char: Char): Char {
        // UK character set - only difference from US ASCII is # -> £
        return when (char) {
            '#' -> '£' // UK pound symbol
            else -> char
        }
    }
    
    private fun processColorAndStyle(args: List<Int>) {
        for (arg in args) {
            when (arg) {
                0 -> resetAttributes()
                1 -> currentBold = true
                3 -> currentItalic = true
                4 -> currentUnderline = true
                22 -> currentBold = false
                23 -> currentItalic = false
                24 -> currentUnderline = false
                in 30..37 -> currentForeground = ansiColorToCompose(arg - 30)
                38 -> {} // Extended color, needs more parsing
                39 -> currentForeground = Color.Unspecified
                in 40..47 -> currentBackground = ansiColorToCompose(arg - 40)
                48 -> {} // Extended background color
                49 -> currentBackground = Color.Unspecified
                in 90..97 -> currentForeground = ansiColorToCompose(arg - 90, bright = true)
                in 100..107 -> currentBackground = ansiColorToCompose(arg - 100, bright = true)
            }
        }
        
        // Handle 256-color mode: ESC[38;5;nnn or ESC[48;5;nnn
        if (args.size >= 3 && args[0] == 38 && args[1] == 5) {
            currentForeground = xterm256ColorToCompose(args[2])
        } else if (args.size >= 3 && args[0] == 48 && args[1] == 5) {
            currentBackground = xterm256ColorToCompose(args[2])
        }
    }
    
    private fun ansiColorToCompose(code: Int, bright: Boolean = false): Color {
        return if (bright) {
            when (code) {
                0 -> Color(0xFF808080) // Bright black (gray)
                1 -> Color(0xFFFF5555) // Bright red
                2 -> Color(0xFF55FF55) // Bright green
                3 -> Color(0xFFFFFF55) // Bright yellow
                4 -> Color(0xFF5555FF) // Bright blue - Much brighter like IntelliJ
                5 -> Color(0xFFFF55FF) // Bright magenta
                6 -> Color(0xFF55FFFF) // Bright cyan
                7 -> Color(0xFFFFFFFF) // Bright white
                else -> Color.Unspecified
            }
        } else {
            when (code) {
                0 -> Color(0xFF000000) // Black
                1 -> Color(0xFFAA0000) // Red
                2 -> Color(0xFF00AA00) // Green
                3 -> Color(0xFFAA5500) // Yellow/Brown
                4 -> Color(0xFF5555FF) // Blue - Using bright blue for better visibility
                5 -> Color(0xFFAA00AA) // Magenta
                6 -> Color(0xFF00AAAA) // Cyan
                7 -> Color(0xFFAAAAAA) // Light gray
                else -> Color.Unspecified
            }
        }
    }
    
    private fun xterm256ColorToCompose(code: Int): Color {
        // Check custom palette first
        customColorPalette[code]?.let { return it }
        
        // Implement 256-color palette
        return when (code) {
            in 0..15 -> {
                // Standard 16 colors
                if (code < 8) ansiColorToCompose(code) 
                else ansiColorToCompose(code - 8, bright = true)
            }
            in 16..231 -> {
                // 216-color cube
                val index = code - 16
                val r = (index / 36) * 51
                val g = ((index % 36) / 6) * 51
                val b = (index % 6) * 51
                Color((r shl 16) or (g shl 8) or b or 0xFF000000.toInt())
            }
            in 232..255 -> {
                // Grayscale
                val gray = 8 + (code - 232) * 10
                Color((gray shl 16) or (gray shl 8) or gray or 0xFF000000.toInt())
            }
            else -> Color.Unspecified
        }
    }
    
    private fun resetAttributes() {
        currentForeground = Color.Unspecified
        currentBackground = Color.Unspecified
        currentBold = false
        currentItalic = false
        currentUnderline = false
    }
    
    private fun moveCursorUp(n: Int) {
        cursorRow = maxOf(0, cursorRow - n)
    }
    
    private fun moveCursorDown(n: Int) {
        cursorRow = minOf(rows - 1, cursorRow + n)
    }
    
    private fun moveCursorRight(n: Int) {
        cursorCol = minOf(columns - 1, cursorCol + n)
    }
    
    private fun moveCursorLeft(n: Int) {
        cursorCol = maxOf(0, cursorCol - n)
    }
    
    private fun setCursorPosition(row: Int, col: Int) {
        cursorRow = minOf(rows - 1, maxOf(0, row - 1))
        cursorCol = minOf(columns - 1, maxOf(0, col - 1))
    }
    
    private fun clearScreen(mode: Int) {
        when (mode) {
            0 -> {
                // Clear from cursor to end
                for (row in cursorRow until rows) {
                    val startCol = if (row == cursorRow) cursorCol else 0
                    for (col in startCol until columns) {
                        buffer[row][col] = TerminalCell()
                    }
                }
            }
            1 -> {
                // Clear from beginning to cursor
                for (row in 0..cursorRow) {
                    val endCol = if (row == cursorRow) cursorCol else columns - 1
                    for (col in 0..endCol) {
                        buffer[row][col] = TerminalCell()
                    }
                }
            }
            2 -> {
                // Clear entire screen
                for (row in 0 until rows) {
                    for (col in 0 until columns) {
                        buffer[row][col] = TerminalCell()
                    }
                }
            }
        }
    }
    
    private fun clearLine(mode: Int) {
        when (mode) {
            0 -> {
                // Clear from cursor to end of line
                for (col in cursorCol until columns) {
                    buffer[cursorRow][col] = TerminalCell()
                }
            }
            1 -> {
                // Clear from beginning to cursor
                for (col in 0..cursorCol) {
                    buffer[cursorRow][col] = TerminalCell()
                }
            }
            2 -> {
                // Clear entire line
                for (col in 0 until columns) {
                    buffer[cursorRow][col] = TerminalCell()
                }
            }
        }
    }
    
    private fun saveCursor() {
        savedCursorRow = cursorRow
        savedCursorCol = cursorCol
    }
    
    private fun restoreCursor() {
        cursorRow = savedCursorRow
        cursorCol = savedCursorCol
    }
    
    private fun scrollUp(count: Int = 1) {
        repeat(count) {
            if (usingAlternateBuffer || scrollTop > 0 || scrollBottom < rows - 1) {
                // In alternate buffer or with custom scroll region, only scroll within the region
                if (scrollBottom > scrollTop) {
                    // Shift lines up within scroll region
                    for (row in scrollTop until scrollBottom) {
                        buffer[row] = buffer[row + 1]
                    }
                    // Clear the bottom line of scroll region
                    buffer[scrollBottom] = Array(columns) { TerminalCell() }
                }
            } else {
                // Normal scrolling in primary buffer - save to scrollback
                scrollbackLines.add(buffer[0].copyOf())
                if (scrollbackLines.size > maxScrollback) {
                    scrollbackLines.removeAt(0)
                }
                
                // Ensure total buffer doesn't exceed limit
                trimBuffer()
                
                // Shift all lines up
                for (row in 0 until rows - 1) {
                    buffer[row] = buffer[row + 1]
                }
                
                // Clear the bottom line
                buffer[rows - 1] = Array(columns) { TerminalCell() }
            }
        }
    }
    
    private fun scrollDown(count: Int = 1) {
        repeat(count) {
            if (scrollBottom > scrollTop) {
                // Shift lines down within scroll region
                for (row in scrollBottom downTo scrollTop + 1) {
                    buffer[row] = buffer[row - 1]
                }
                
                // Clear the top line of scroll region
                buffer[scrollTop] = Array(columns) { TerminalCell() }
            }
        }
    }
    
    private fun insertLines(count: Int) {
        for (i in 0 until count) {
            // Shift lines down from cursor position
            for (row in rows - 1 downTo cursorRow + 1) {
                buffer[row] = buffer[row - 1]
            }
            // Clear the line at cursor position
            buffer[cursorRow] = Array(columns) { TerminalCell() }
        }
    }
    
    private fun deleteLines(count: Int) {
        for (i in 0 until count) {
            // Shift lines up from cursor position
            for (row in cursorRow until rows - 1) {
                buffer[row] = buffer[row + 1]
            }
            // Clear the bottom line
            buffer[rows - 1] = Array(columns) { TerminalCell() }
        }
    }
    
    private fun deleteCharacters(count: Int) {
        // Delete characters at cursor position, shift rest of line left
        minOf(cursorCol + count, columns)
        for (col in cursorCol until columns - count) {
            buffer[cursorRow][col] = if (col + count < columns) {
                buffer[cursorRow][col + count]
            } else {
                TerminalCell()
            }
        }
    }
    
    private fun eraseCharacters(count: Int) {
        // Erase characters at cursor position without shifting
        val endCol = minOf(cursorCol + count, columns)
        for (col in cursorCol until endCol) {
            buffer[cursorRow][col] = TerminalCell()
        }
    }
    
    fun getAnnotatedLines(): List<AnnotatedString> {
        val allLines = mutableListOf<AnnotatedString>()
        
        // Ensure we don't exceed buffer limit
        trimBuffer()
        
        // Add scrollback lines
        scrollbackLines.forEach { row ->
            allLines.add(buildAnnotatedString {
                var hasContent = false
                for (cell in row) {
                    hasContent = true
                    val style = SpanStyle(
                        color = if (cell.foregroundColor == Color.Unspecified) 
                            defaultForegroundColor ?: Color(0xFFE0E0E0) else cell.foregroundColor,
                        background = if (cell.backgroundColor != Color.Unspecified) 
                            cell.backgroundColor else defaultBackgroundColor ?: Color.Unspecified,
                        fontWeight = if (cell.bold) FontWeight.Bold else null,
                        fontStyle = if (cell.italic) androidx.compose.ui.text.font.FontStyle.Italic else null,
                        textDecoration = if (cell.underline) 
                            androidx.compose.ui.text.style.TextDecoration.Underline else null
                    )
                    
                    append(AnnotatedString(cell.char.toString(), style))
                }
                // Ensure line has at least one character for proper rendering
                if (!hasContent || length == 0) {
                    append(" ")
                }
            })
        }
        
        // Add current buffer lines
        buffer.forEach { row ->
            allLines.add(buildAnnotatedString {
                var hasContent = false
                for (cell in row) {
                    hasContent = true
                    val style = SpanStyle(
                        color = if (cell.foregroundColor == Color.Unspecified) 
                            defaultForegroundColor ?: Color(0xFFE0E0E0) else cell.foregroundColor,
                        background = if (cell.backgroundColor != Color.Unspecified) 
                            cell.backgroundColor else defaultBackgroundColor ?: Color.Unspecified,
                        fontWeight = if (cell.bold) FontWeight.Bold else null,
                        fontStyle = if (cell.italic) androidx.compose.ui.text.font.FontStyle.Italic else null,
                        textDecoration = if (cell.underline) 
                            androidx.compose.ui.text.style.TextDecoration.Underline else null
                    )
                    
                    append(AnnotatedString(cell.char.toString(), style))
                }
                // Ensure line has at least one character for proper rendering
                if (!hasContent || length == 0) {
                    append(" ")
                }
            })
        }
        
        return allLines
    }
    
    fun getCursorPosition(): Pair<Int, Int> = (scrollbackLines.size + cursorRow) to cursorCol
    
    fun isCursorVisible(): Boolean = cursorVisible
    
    private fun trimBuffer() {
        // Ensure total lines don't exceed maxBufferSize
        val totalLines = scrollbackLines.size + rows
        if (totalLines > maxBufferSize) {
            val linesToRemove = totalLines - maxBufferSize
            repeat(linesToRemove) {
                if (scrollbackLines.isNotEmpty()) {
                    scrollbackLines.removeAt(0)
                }
            }
        }
    }
    
    fun resize(newColumns: Int, newRows: Int) {
        if (newColumns == columns && newRows == rows) {
            return // No change needed
        }
        
        // println("[TerminalEmulator] Resizing from ${columns}x${rows} to ${newColumns}x${newRows}")
        
        // Resize both buffers
        val newPrimaryBuffer = Array(newRows) { Array(newColumns) { TerminalCell() } }
        val newAlternateBuffer = Array(newRows) { Array(newColumns) { TerminalCell() } }
        
        // Copy existing content to new buffers
        val rowsToCopy = minOf(rows, newRows)
        val colsToCopy = minOf(columns, newColumns)
        
        // Copy primary buffer
        if (usingAlternateBuffer) alternateBuffer else primaryBuffer
        for (row in 0 until rowsToCopy) {
            for (col in 0 until colsToCopy) {
                newPrimaryBuffer[row][col] = primaryBuffer[row][col]
                newAlternateBuffer[row][col] = alternateBuffer[row][col]
            }
        }
        
        // Update buffers and dimensions
        primaryBuffer = newPrimaryBuffer
        alternateBuffer = newAlternateBuffer
        buffer = if (usingAlternateBuffer) alternateBuffer else primaryBuffer
        columns = newColumns
        rows = newRows
        
        // Adjust cursor position if necessary
        cursorRow = minOf(cursorRow, newRows - 1)
        cursorCol = minOf(cursorCol, newColumns - 1)
        
        // Adjust saved cursor position
        savedCursorRow = minOf(savedCursorRow, newRows - 1)
        savedCursorCol = minOf(savedCursorCol, newColumns - 1)
        savedPrimaryCursorRow = minOf(savedPrimaryCursorRow, newRows - 1)
        savedPrimaryCursorCol = minOf(savedPrimaryCursorCol, newColumns - 1)
        
        // Adjust scrolling region
        scrollBottom = minOf(scrollBottom, newRows - 1)
        scrollTop = minOf(scrollTop, scrollBottom)
        
        // Update max scrollback based on new rows
        // Scrollback only applies to primary buffer
        val newMaxScrollback = maxBufferSize - newRows
        
        // Trim scrollback if necessary
        while (scrollbackLines.size > newMaxScrollback && scrollbackLines.isNotEmpty()) {
            scrollbackLines.removeAt(0)
        }
    }
    
    private fun handleModeSet(params: String) {
        when (params) {
            "?25" -> cursorVisible = true // Show cursor
            "?1049" -> switchToAlternateBuffer() // Use alternate screen buffer
            "?47" -> switchToAlternateBuffer() // Alternate screen (older version)
            "?1047" -> switchToAlternateBuffer() // Alternate screen
            else -> {} // Other modes - ignore
        }
    }
    
    private fun handleModeReset(params: String) {
        when (params) {
            "?25" -> cursorVisible = false // Hide cursor
            "?1049" -> switchToPrimaryBuffer() // Use normal screen buffer
            "?47" -> switchToPrimaryBuffer() // Normal screen (older version)
            "?1047" -> switchToPrimaryBuffer() // Normal screen
            else -> {} // Other modes - ignore
        }
    }
    
    private fun switchToAlternateBuffer() {
        if (!usingAlternateBuffer) {
            // Save current cursor position in primary buffer
            savedPrimaryCursorRow = cursorRow
            savedPrimaryCursorCol = cursorCol
            
            // Save primary buffer and switch to alternate
            primaryBuffer = buffer
            buffer = alternateBuffer
            usingAlternateBuffer = true
            
            // Clear the alternate buffer
            for (row in 0 until rows) {
                for (col in 0 until columns) {
                    buffer[row][col] = TerminalCell()
                }
            }
            
            // Reset cursor position
            cursorRow = 0
            cursorCol = 0
            
            // Reset scrolling region
            scrollTop = 0
            scrollBottom = rows - 1
        }
    }
    
    private fun switchToPrimaryBuffer() {
        if (usingAlternateBuffer) {
            // Save alternate buffer
            alternateBuffer = buffer
            
            // Switch back to primary buffer
            buffer = primaryBuffer
            usingAlternateBuffer = false
            
            // Restore cursor position
            cursorRow = savedPrimaryCursorRow
            cursorCol = savedPrimaryCursorCol
            
            // Reset scrolling region
            scrollTop = 0
            scrollBottom = rows - 1
        }
    }
    
    private fun setScrollingRegion(top: Int, bottom: Int) {
        // Convert 1-based to 0-based and validate
        scrollTop = maxOf(0, minOf(rows - 1, top - 1))
        scrollBottom = maxOf(scrollTop, minOf(rows - 1, bottom - 1))
        
        // Move cursor to home position when scrolling region is set
        cursorRow = scrollTop
        cursorCol = 0
    }
    
    private fun processCharacterSetDesignation(intermediate: Char, final: Char) {
        // Character set designation sequences like ESC(B, ESC)0, etc.
        val charSet = when (final) {
            'A' -> CharacterSet.UK
            'B' -> CharacterSet.ASCII
            '0' -> CharacterSet.GRAPHICS
            '1' -> CharacterSet.ASCII // Alternate character ROM - treat as ASCII
            '2' -> CharacterSet.GRAPHICS // Alternate character ROM special graphics
            else -> CharacterSet.UNCHANGED
        }
        
        if (charSet != CharacterSet.UNCHANGED) {
            when (intermediate) {
                '(' -> g0CharSet = charSet // Designate G0
                ')' -> g1CharSet = charSet // Designate G1
                '*' -> g2CharSet = charSet // Designate G2
                '+' -> g3CharSet = charSet // Designate G3
            }
        }
    }
    
    private fun performFullReset() {
        // Reset to initial state
        
        // Switch to primary buffer if in alternate
        if (usingAlternateBuffer) {
            switchToPrimaryBuffer()
        }
        
        // Clear the entire screen
        for (row in 0 until rows) {
            for (col in 0 until columns) {
                buffer[row][col] = TerminalCell()
            }
        }
        
        // Reset cursor position
        cursorRow = 0
        cursorCol = 0
        savedCursorRow = 0
        savedCursorCol = 0
        savedPrimaryCursorRow = 0
        savedPrimaryCursorCol = 0
        
        // Reset attributes
        resetAttributes()
        
        // Reset cursor visibility
        cursorVisible = true
        
        // Reset scrolling region
        scrollTop = 0
        scrollBottom = rows - 1
        
        // Clear scrollback in primary buffer
        scrollbackLines.clear()
        
        // Reset character sets to defaults
        g0CharSet = CharacterSet.ASCII
        g1CharSet = CharacterSet.ASCII
        g2CharSet = CharacterSet.ASCII
        g3CharSet = CharacterSet.ASCII
        currentCharSet = 0
        singleShift = -1
        
        // Reset window titles
        windowTitle = ""
        iconTitle = ""
        
        // Reset custom colors
        customColorPalette.clear()
        defaultForegroundColor = null
        defaultBackgroundColor = null
    }
    
    fun getWindowTitle(): String = windowTitle
    fun getIconTitle(): String = iconTitle
    
    private fun processTwoCharSequence(char: Char) {
        when (char) {
            '7' -> saveCursor() // Save cursor
            '8' -> restoreCursor() // Restore cursor
            'D' -> scrollUp() // Index
            'M' -> scrollDown() // Reverse index
            'E' -> { // Next line
                cursorCol = 0
                moveCursorDown(1)
            }
            'H' -> {} // Tab set - ignore for now
            '=' -> {} // Application keypad mode - ignore for now
            '>' -> {} // Normal keypad mode - ignore for now
            'c' -> performFullReset() // Reset
            'N' -> singleShift = 2 // SS2 - Single shift to G2
            'O' -> singleShift = 3 // SS3 - Single shift to G3
            'n' -> currentCharSet = 2 // LS2 - Locking shift to G2
            'o' -> currentCharSet = 3 // LS3 - Locking shift to G3
            else -> {
                // Unknown two-char sequence - silently ignore
            }
        }
    }
} 