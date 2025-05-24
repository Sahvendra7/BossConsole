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
    
    // Cursor position (0-based)
    private var cursorRow = 0
    private var cursorCol = 0
    
    // Saved cursor position for save/restore operations
    private var savedCursorRow = 0
    private var savedCursorCol = 0
    
    // Current attributes for new characters
    private var currentForeground = Color.Unspecified
    private var currentBackground = Color.Unspecified
    private var currentBold = false
    private var currentItalic = false
    private var currentUnderline = false
    
    // Parser state
    private val escapeSequence = StringBuilder()
    private var inEscapeSequence = false
    
    // Scrollback buffer with limit
    private val scrollbackLines = mutableListOf<Array<TerminalCell>>()
    private val maxBufferSize = 2000 // Total lines limit (scrollback + visible)
    private val maxScrollback: Int
        get() = maxBufferSize - rows // Dynamically calculate based on current rows
    
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
            'r' -> {} // Set scrolling region - TODO
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
        // For now, we'll ignore these
    }
    
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
            'c' -> {} // Reset - TODO: implement full reset
            else -> {
                // Unknown two-char sequence - silently ignore
            }
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
            else -> {
                if (char.code >= 32) {
                    // Place character at cursor position
                    if (cursorCol < columns) {
                        buffer[cursorRow][cursorCol] = TerminalCell(
                            char = char,
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
                }
            }
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
                1 -> Color(0xFFFF0000) // Bright red
                2 -> Color(0xFF00FF00) // Bright green
                3 -> Color(0xFFFFFF00) // Bright yellow
                4 -> Color(0xFF0080FF) // Bright blue
                5 -> Color(0xFFFF00FF) // Bright magenta
                6 -> Color(0xFF00FFFF) // Bright cyan
                7 -> Color(0xFFFFFFFF) // Bright white
                else -> Color.Unspecified
            }
        } else {
            when (code) {
                0 -> Color(0xFF000000) // Black
                1 -> Color(0xFF800000) // Red
                2 -> Color(0xFF008000) // Green
                3 -> Color(0xFF808000) // Yellow
                4 -> Color(0xFF000080) // Blue
                5 -> Color(0xFF800080) // Magenta
                6 -> Color(0xFF008080) // Cyan
                7 -> Color(0xFFC0C0C0) // White
                else -> Color.Unspecified
            }
        }
    }
    
    private fun xterm256ColorToCompose(code: Int): Color {
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
            // Save the top line to scrollback
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
    
    private fun scrollDown(count: Int = 1) {
        repeat(count) {
            // Shift all lines down
            for (row in rows - 1 downTo 1) {
                buffer[row] = buffer[row - 1]
            }
            
            // Clear the top line
            buffer[0] = Array(columns) { TerminalCell() }
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
        val endCol = minOf(cursorCol + count, columns)
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
                for (cell in row) {
                    val style = SpanStyle(
                        color = if (cell.foregroundColor == Color.Unspecified) 
                            Color(0xFFD4D4D4) else cell.foregroundColor,
                        background = if (cell.backgroundColor != Color.Unspecified) 
                            cell.backgroundColor else Color.Unspecified,
                        fontWeight = if (cell.bold) FontWeight.Bold else null,
                        fontStyle = if (cell.italic) androidx.compose.ui.text.font.FontStyle.Italic else null,
                        textDecoration = if (cell.underline) 
                            androidx.compose.ui.text.style.TextDecoration.Underline else null
                    )
                    
                    append(AnnotatedString(cell.char.toString(), style))
                }
            })
        }
        
        // Add current buffer lines
        buffer.forEach { row ->
            allLines.add(buildAnnotatedString {
                for (cell in row) {
                    val style = SpanStyle(
                        color = if (cell.foregroundColor == Color.Unspecified) 
                            Color(0xFFD4D4D4) else cell.foregroundColor,
                        background = if (cell.backgroundColor != Color.Unspecified) 
                            cell.backgroundColor else Color.Unspecified,
                        fontWeight = if (cell.bold) FontWeight.Bold else null,
                        fontStyle = if (cell.italic) androidx.compose.ui.text.font.FontStyle.Italic else null,
                        textDecoration = if (cell.underline) 
                            androidx.compose.ui.text.style.TextDecoration.Underline else null
                    )
                    
                    append(AnnotatedString(cell.char.toString(), style))
                }
            })
        }
        
        return allLines
    }
    
    fun getCursorPosition(): Pair<Int, Int> = (scrollbackLines.size + cursorRow) to cursorCol
    
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
        
        // Create new buffer with new dimensions
        val newBuffer = Array(newRows) { Array(newColumns) { TerminalCell() } }
        
        // Copy existing content to new buffer
        val rowsToCopy = minOf(rows, newRows)
        for (row in 0 until rowsToCopy) {
            val colsToCopy = minOf(columns, newColumns)
            for (col in 0 until colsToCopy) {
                newBuffer[row][col] = buffer[row][col]
            }
            // If new buffer is wider, the extra cells are already initialized with empty TerminalCell()
        }
        
        // Update buffer and dimensions
        buffer = newBuffer
        columns = newColumns
        rows = newRows
        
        // Adjust cursor position if necessary
        cursorRow = minOf(cursorRow, newRows - 1)
        cursorCol = minOf(cursorCol, newColumns - 1)
        
        // Adjust saved cursor position
        savedCursorRow = minOf(savedCursorRow, newRows - 1)
        savedCursorCol = minOf(savedCursorCol, newColumns - 1)
        
        // Update max scrollback based on new rows
        val newMaxScrollback = maxBufferSize - newRows
        
        // Trim scrollback if necessary
        while (scrollbackLines.size > newMaxScrollback && scrollbackLines.isNotEmpty()) {
            scrollbackLines.removeAt(0)
        }
    }
    
    private fun handleModeSet(params: String) {
        when (params) {
            "?25" -> {} // Show cursor - TODO
            "?1049" -> {} // Use alternate screen buffer - TODO
            else -> {} // Other modes - ignore
        }
    }
    
    private fun handleModeReset(params: String) {
        when (params) {
            "?25" -> {} // Hide cursor - TODO
            "?1049" -> {} // Use normal screen buffer - TODO
            else -> {} // Other modes - ignore
        }
    }
    
    private fun processCharacterSetDesignation(intermediate: Char, final: Char) {
        // Character set designation sequences like ESC(B, ESC)0, etc.
        // For now, we'll ignore these as they affect character rendering
        // which we don't fully support yet
    }
} 