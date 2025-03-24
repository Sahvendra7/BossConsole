package ai.rever.boss

fun String.truncate(length: Int): String {
    return if (length > 0 && this.length > length) {
        this.take(length) + "..."
    } else {
        this
    }
}