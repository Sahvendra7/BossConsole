package ai.rever.boss

interface FileSelector {
    suspend fun selectFile(): String?
}

expect fun getFileSelector(): FileSelector 