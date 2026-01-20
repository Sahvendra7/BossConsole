package ai.rever.bosseditor.largefile

data class Page(
    val pageNumber: Long,
    val text: String,
    val byteStart: Long,
    val byteEnd: Long,
    val isLastPage: Boolean
) {
    val lineCount: Int = text.count { it == '\n' } + 1
}
