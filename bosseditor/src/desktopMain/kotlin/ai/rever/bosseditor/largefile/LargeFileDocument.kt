package ai.rever.bosseditor.largefile

import ai.rever.bosseditor.core.DocumentListener
import ai.rever.bosseditor.core.EditorPosition
import ai.rever.bosseditor.core.ITextModel
import java.io.Closeable
import java.io.File

/**
 * Read-only text model for large files using page-based lazy loading.
 *
 * This implementation wraps [LargeFileAdapter] to provide efficient access to
 * large files without loading the entire content into memory.
 *
 * Key features:
 * - Page-based lazy loading (8KB pages)
 * - LRU page cache for frequently accessed regions
 * - Line start byte offsets cached for fast line lookup
 * - Thread-safe concurrent access
 *
 * Limitations:
 * - Read-only (all mutation operations throw UnsupportedOperationException)
 * - getText() without parameters may be slow or partial for very large files
 * - Maximum file size is 2GB due to Int offset limitations
 *
 * Resource management:
 * - This class holds a [RandomAccessFile] handle that must be closed.
 * - Always use with try-finally or Kotlin's [use] extension to ensure [close] is called.
 *
 * Example:
 * ```kotlin
 * LargeFileDocument(file).use { doc ->
 *     // Use the document
 * }
 * ```
 */
class LargeFileDocument(
    private val file: File,
    private val maxCachedPages: Int = 20
) : ITextModel, Closeable {

    init {
        // Validate file size to prevent integer overflow (max 2GB)
        require(file.length() <= Int.MAX_VALUE) {
            "File size ${file.length()} exceeds maximum supported size of ${Int.MAX_VALUE} bytes (2GB)"
        }
    }

    private val adapter = LargeFileAdapter(file)

    // Page cache with LRU eviction
    private val pageCache = object : LinkedHashMap<Long, Page>(maxCachedPages, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Page>?): Boolean {
            return size > maxCachedPages
        }
    }

    // Line start byte offsets - built on first access
    // lineStartOffsets[i] = byte offset where line i starts
    // @Volatile required for thread-safe double-checked locking in ensureLineOffsetsBuilt()
    @Volatile
    private var lineStartOffsets: LongArray? = null

    // Document version (always 0 for read-only documents)
    override val documentVersion: Long = 0L

    override val isReadOnly: Boolean = true

    override val length: Int
        get() = adapter.fileSize.toInt()

    override val lineCount: Int
        get() {
            ensureLineOffsetsBuilt()
            return lineStartOffsets!!.size
        }

    /**
     * Returns the full text. Warning: For large files, this loads everything into memory.
     * Consider using getLineText() for viewport-based rendering instead.
     */
    override fun getText(): String {
        val builder = StringBuilder()
        for (pageNum in 0 until adapter.pagesAmount) {
            val page = getPage(pageNum)
            if (page != null) {
                builder.append(page.text)
            }
        }
        return builder.toString()
    }

    override fun getText(startOffset: Int, endOffset: Int): String {
        require(startOffset >= 0) { "startOffset must be non-negative" }
        require(endOffset <= length) { "endOffset must be <= length" }
        require(startOffset <= endOffset) { "startOffset must be <= endOffset" }

        if (startOffset == endOffset) return ""

        val result = StringBuilder(endOffset - startOffset)

        // Find starting page
        var currentOffset = startOffset.toLong()
        while (currentOffset < endOffset) {
            val pageNum = findPageForOffset(currentOffset)
            val page = getPage(pageNum) ?: break

            val pageRelativeStart = (currentOffset - page.byteStart).toInt().coerceAtLeast(0)
            val pageRelativeEnd = (endOffset - page.byteStart).toInt().coerceAtMost(page.text.length)

            if (pageRelativeStart < page.text.length && pageRelativeEnd > pageRelativeStart) {
                result.append(page.text.substring(pageRelativeStart, pageRelativeEnd))
            }

            currentOffset = page.byteEnd
        }

        return result.toString()
    }

    override fun charAt(offset: Int): Char {
        require(offset in 0 until length) { "Offset out of bounds: $offset" }

        val pageNum = findPageForOffset(offset.toLong())
        val page = getPage(pageNum) ?: throw IndexOutOfBoundsException("Cannot read page for offset $offset")

        val pageRelativeOffset = (offset - page.byteStart).toInt()
        if (pageRelativeOffset < 0 || pageRelativeOffset >= page.text.length) {
            throw IndexOutOfBoundsException("Offset $offset not in page $pageNum")
        }
        return page.text[pageRelativeOffset]
    }

    override fun getLineText(lineNumber: Int): String {
        ensureLineOffsetsBuilt()
        val offsets = lineStartOffsets!!
        require(lineNumber in 0 until offsets.size) { "Line number out of bounds: $lineNumber" }

        val startOffset = offsets[lineNumber]
        val endOffset = if (lineNumber + 1 < offsets.size) {
            // End before the newline of this line (which is at offsets[lineNumber+1] - 1)
            offsets[lineNumber + 1] - 1
        } else {
            // Last line - end at file end
            adapter.fileSize
        }

        if (startOffset >= endOffset) {
            return ""
        }

        return readTextRange(startOffset, endOffset)
    }

    override fun getLineLength(lineNumber: Int): Int {
        ensureLineOffsetsBuilt()
        val offsets = lineStartOffsets!!
        require(lineNumber in 0 until offsets.size) { "Line number out of bounds: $lineNumber" }

        val startOffset = offsets[lineNumber]
        val endOffset = if (lineNumber + 1 < offsets.size) {
            offsets[lineNumber + 1] - 1 // Exclude the newline
        } else {
            adapter.fileSize
        }

        return (endOffset - startOffset).toInt().coerceAtLeast(0)
    }

    override fun getLineStartOffset(lineNumber: Int): Int {
        ensureLineOffsetsBuilt()
        val offsets = lineStartOffsets!!
        require(lineNumber in 0 until offsets.size) { "Line number out of bounds: $lineNumber" }
        return offsets[lineNumber].toInt()
    }

    override fun getLineEndOffset(lineNumber: Int): Int {
        ensureLineOffsetsBuilt()
        val offsets = lineStartOffsets!!
        require(lineNumber in 0 until offsets.size) { "Line number out of bounds: $lineNumber" }

        return if (lineNumber + 1 < offsets.size) {
            offsets[lineNumber + 1].toInt() // Include newline
        } else {
            adapter.fileSize.toInt()
        }
    }

    override fun offsetToPosition(offset: Int): EditorPosition {
        require(offset in 0..length) { "Offset out of bounds: $offset" }
        ensureLineOffsetsBuilt()
        val offsets = lineStartOffsets!!

        // Binary search for the line containing this offset
        var low = 0
        var high = offsets.size - 1

        while (low < high) {
            val mid = (low + high + 1) / 2
            if (offsets[mid] <= offset) {
                low = mid
            } else {
                high = mid - 1
            }
        }

        val line = low
        val column = (offset - offsets[line].toInt()).coerceAtLeast(0)
        return EditorPosition(line, column)
    }

    override fun positionToOffset(position: EditorPosition): Int {
        return positionToOffset(position.line, position.column)
    }

    override fun positionToOffset(line: Int, column: Int): Int {
        ensureLineOffsetsBuilt()
        val offsets = lineStartOffsets!!
        require(line in 0 until offsets.size) { "Line out of bounds: $line" }

        val lineStart = offsets[line].toInt()
        val lineLen = getLineLength(line)
        val clampedColumn = column.coerceIn(0, lineLen)

        return lineStart + clampedColumn
    }

    override fun addDocumentListener(listener: DocumentListener) {
        // No-op for read-only document
    }

    override fun removeDocumentListener(listener: DocumentListener) {
        // No-op for read-only document
    }

    // --- Mutation operations (not supported) ---

    override fun insert(offset: Int, text: String) {
        throw UnsupportedOperationException("Large file documents are read-only")
    }

    override fun delete(startOffset: Int, endOffset: Int) {
        throw UnsupportedOperationException("Large file documents are read-only")
    }

    override fun replace(startOffset: Int, endOffset: Int, newText: String) {
        throw UnsupportedOperationException("Large file documents are read-only")
    }

    override fun setText(text: String) {
        throw UnsupportedOperationException("Large file documents are read-only")
    }

    override fun close() {
        adapter.close()
        synchronized(pageCache) {
            pageCache.clear()
        }
    }

    // --- Private helpers ---

    private fun getPage(pageNumber: Long): Page? {
        synchronized(pageCache) {
            // Check cache first
            pageCache[pageNumber]?.let { return it }

            // Load page
            val page = adapter.getPageText(pageNumber) ?: return null

            // Add to cache (LRU eviction handled by LinkedHashMap)
            pageCache[pageNumber] = page

            return page
        }
    }

    private fun findPageForOffset(offset: Long): Long {
        // Each page is approximately PAGE_SIZE bytes
        // But due to UTF-8 boundary alignment, actual page boundaries vary
        // Start with estimate and adjust if needed
        val estimate = (offset / LargeFileConstants.PAGE_SIZE).coerceIn(0, adapter.pagesAmount - 1)

        // Check if estimate is correct
        val page = getPage(estimate)
        if (page != null && offset >= page.byteStart && offset < page.byteEnd) {
            return estimate
        }

        // Search nearby pages
        for (delta in 1..3) {
            if (estimate - delta >= 0) {
                val prevPage = getPage(estimate - delta)
                if (prevPage != null && offset >= prevPage.byteStart && offset < prevPage.byteEnd) {
                    return estimate - delta
                }
            }
            if (estimate + delta < adapter.pagesAmount) {
                val nextPage = getPage(estimate + delta)
                if (nextPage != null && offset >= nextPage.byteStart && offset < nextPage.byteEnd) {
                    return estimate + delta
                }
            }
        }

        return estimate
    }

    /**
     * Builds the line start offsets array by scanning the entire file.
     * This is done once on first access to line-related methods.
     */
    private fun ensureLineOffsetsBuilt() {
        if (lineStartOffsets != null) return

        synchronized(this) {
            if (lineStartOffsets != null) return

            val offsets = mutableListOf<Long>()
            offsets.add(0L) // Line 0 always starts at offset 0

            var byteOffset = 0L
            for (pageNum in 0 until adapter.pagesAmount) {
                val page = getPage(pageNum) ?: continue

                // Scan page for newlines
                for ((index, char) in page.text.withIndex()) {
                    if (char == '\n') {
                        // Next line starts after this newline
                        val newlineByteOffset = page.byteStart + index
                        offsets.add(newlineByteOffset + 1)
                    }
                }

                byteOffset = page.byteEnd
            }

            lineStartOffsets = offsets.toLongArray()
        }
    }

    /**
     * Reads text from the file between two byte offsets.
     */
    private fun readTextRange(startOffset: Long, endOffset: Long): String {
        if (startOffset >= endOffset) return ""

        val result = StringBuilder()
        var currentOffset = startOffset

        while (currentOffset < endOffset) {
            val pageNum = findPageForOffset(currentOffset)
            val page = getPage(pageNum) ?: break

            val pageRelativeStart = (currentOffset - page.byteStart).toInt().coerceAtLeast(0)
            val pageRelativeEnd = (endOffset - page.byteStart).toInt().coerceAtMost(page.text.length)

            if (pageRelativeStart < page.text.length && pageRelativeEnd > pageRelativeStart) {
                result.append(page.text.substring(pageRelativeStart, pageRelativeEnd))
            }

            currentOffset = page.byteEnd
        }

        return result.toString()
    }

    companion object {
        /**
         * Checks if a file should be opened with the large file adapter.
         */
        fun shouldUseLargeFileAdapter(file: File): Boolean {
            return file.exists() && file.length() > LargeFileConstants.LARGE_FILE_THRESHOLD
        }

        /**
         * Checks if a file path should be opened with the large file adapter.
         */
        fun shouldUseLargeFileAdapter(filePath: String): Boolean {
            return shouldUseLargeFileAdapter(File(filePath))
        }
    }
}
