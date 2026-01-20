package ai.rever.bosseditor.largefile

object LargeFileConstants {
    const val PAGE_SIZE = 8 * 1024              // 8KB per page
    const val MAX_PAGE_BORDER_SHIFT = 128       // Max bytes to shift for char boundary
    const val LARGE_FILE_THRESHOLD = 10_000_000L // 10MB threshold
}
