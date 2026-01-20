package ai.rever.bosseditor.largefile

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.Charset
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Page-based adapter for reading large files without loading the entire file into memory.
 * Follows IntelliJ's largeFilesEditor approach for efficient memory usage.
 *
 * @param file The file to read
 * @param pageSize Size of each page in bytes (default: 8KB)
 * @param maxPageBorderShift Maximum bytes to shift page boundaries for UTF-8 alignment
 * @param charset Character encoding of the file (default: UTF-8)
 */
class LargeFileAdapter(
    file: File,
    private val pageSize: Int = LargeFileConstants.PAGE_SIZE,
    private val maxPageBorderShift: Int = LargeFileConstants.MAX_PAGE_BORDER_SHIFT,
    private val charset: Charset = Charsets.UTF_8
) : Closeable {

    private val randomAccessFile: RandomAccessFile = RandomAccessFile(file, "r")
    private val lock = ReentrantLock()

    /**
     * Total size of the file in bytes.
     */
    val fileSize: Long = randomAccessFile.length()

    /**
     * Total number of pages in the file.
     * Note: Actual page boundaries may shift due to UTF-8 alignment.
     */
    val pagesAmount: Long = if (fileSize == 0L) {
        0L
    } else {
        (fileSize + pageSize - 1) / pageSize
    }

    /**
     * Reads a page from the file.
     *
     * @param pageNumber Zero-based page number
     * @return The Page data, or null if pageNumber is invalid
     */
    fun getPageText(pageNumber: Long): Page? = lock.withLock {
        if (pageNumber < 0 || pageNumber >= pagesAmount) {
            return null
        }

        val nominalStart = pageNumber * pageSize
        val nominalEnd = minOf((pageNumber + 1) * pageSize.toLong(), fileSize)

        // Adjust start position to UTF-8 boundary (for pages after first)
        val byteStart = if (pageNumber == 0L) {
            0L
        } else {
            CharsetBoundaryDetector.findNextUtf8Boundary(
                randomAccessFile,
                nominalStart,
                maxPageBorderShift
            )
        }

        // Adjust end position to UTF-8 boundary (for pages before last)
        val isLastPage = pageNumber == pagesAmount - 1
        val byteEnd = if (isLastPage) {
            fileSize
        } else {
            CharsetBoundaryDetector.findNextUtf8Boundary(
                randomAccessFile,
                nominalEnd,
                maxPageBorderShift
            )
        }

        // Read the bytes
        val length = (byteEnd - byteStart).toInt()
        if (length <= 0) {
            return Page(
                pageNumber = pageNumber,
                text = "",
                byteStart = byteStart,
                byteEnd = byteEnd,
                isLastPage = isLastPage
            )
        }

        val buffer = ByteArray(length)
        randomAccessFile.seek(byteStart)
        randomAccessFile.readFully(buffer)

        val text = String(buffer, charset)

        Page(
            pageNumber = pageNumber,
            text = text,
            byteStart = byteStart,
            byteEnd = byteEnd,
            isLastPage = isLastPage
        )
    }

    /**
     * Closes the underlying file handle.
     */
    override fun close() = lock.withLock {
        randomAccessFile.close()
    }
}
