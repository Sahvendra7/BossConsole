package ai.rever.bosseditor.largefile

import java.io.RandomAccessFile

object CharsetBoundaryDetector {

    /**
     * Checks if a byte is a UTF-8 continuation byte (10xxxxxx pattern).
     * Continuation bytes are in the range 0x80-0xBF.
     */
    fun isUtf8ContinuationByte(byte: Byte): Boolean {
        // UTF-8 continuation bytes have the pattern 10xxxxxx
        // This means (byte & 0xC0) == 0x80
        return (byte.toInt() and 0xC0) == 0x80
    }

    /**
     * Finds the next UTF-8 character boundary starting from the given position.
     * Returns the position of the first byte that is NOT a continuation byte.
     *
     * @param raf The RandomAccessFile to read from
     * @param position The starting position to search from
     * @param maxShift Maximum number of bytes to shift forward
     * @return The position of the next character boundary, or the original position if already at boundary
     */
    fun findNextUtf8Boundary(raf: RandomAccessFile, position: Long, maxShift: Int): Long {
        if (position >= raf.length()) {
            return raf.length()
        }

        raf.seek(position)

        var currentPosition = position
        var shift = 0

        while (shift < maxShift && currentPosition < raf.length()) {
            val byte = raf.readByte()
            if (!isUtf8ContinuationByte(byte)) {
                // Found a non-continuation byte - this is a character start
                return currentPosition
            }
            currentPosition++
            shift++
        }

        // If we've exhausted maxShift or reached EOF, return current position
        return currentPosition
    }

    /**
     * Finds the previous UTF-8 character boundary starting from the given position.
     * Returns the position of the first byte that is NOT a continuation byte,
     * searching backwards.
     *
     * @param raf The RandomAccessFile to read from
     * @param position The starting position to search from (exclusive)
     * @param maxShift Maximum number of bytes to shift backward
     * @return The position of the previous character boundary
     */
    fun findPreviousUtf8Boundary(raf: RandomAccessFile, position: Long, maxShift: Int): Long {
        if (position <= 0) {
            return 0
        }

        var currentPosition = position - 1
        var shift = 0

        while (shift < maxShift && currentPosition >= 0) {
            raf.seek(currentPosition)
            val byte = raf.readByte()
            if (!isUtf8ContinuationByte(byte)) {
                // Found a non-continuation byte - this is a character start
                return currentPosition
            }
            currentPosition--
            shift++
        }

        // If we've exhausted maxShift or reached start, return current position
        return maxOf(0, currentPosition)
    }
}
