package ai.rever.bosseditor.psi

import kotlinx.coroutines.*
import java.io.File
import java.nio.file.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.jar.JarFile
import ai.rever.bosseditor.utils.extractFileName

/**
 * Indexing progress callback.
 */
typealias IndexingProgressCallback = (indexed: Int, total: Int, currentFile: String) -> Unit

/**
 * Project indexer that manages the declaration index.
 *
 * This indexer:
 * - Scans the project for Kotlin and Java source files
 * - Builds an index of all declarations
 * - Watches for file changes and updates the index incrementally
 * - Provides progress reporting during initial indexing
 *
 * @property projectPath The root path of the project to index
 */
class ProjectIndexer(val projectPath: String) {

    /**
     * The declaration index.
     */
    val index = DeclarationIndex()

    /**
     * Coroutine scope for background indexing.
     */
    private val indexScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Flag indicating whether initial indexing is complete.
     */
    private val indexingComplete = AtomicBoolean(false)

    /**
     * Flag indicating whether indexing is currently in progress.
     */
    private val indexingInProgress = AtomicBoolean(false)

    /**
     * Progress counters.
     */
    private val filesIndexed = AtomicInteger(0)
    private val totalFiles = AtomicInteger(0)

    /**
     * File watcher for incremental updates.
     */
    private var watchService: WatchService? = null
    private var watchJob: Job? = null

    /**
     * Check if initial indexing is complete.
     */
    val isIndexingComplete: Boolean
        get() = indexingComplete.get()

    /**
     * Check if indexing is currently in progress.
     */
    val isIndexing: Boolean
        get() = indexingInProgress.get()

    /**
     * Get indexing progress (0.0 to 1.0).
     */
    val progress: Float
        get() {
            val total = totalFiles.get()
            return if (total > 0) filesIndexed.get().toFloat() / total else 0f
        }

    /**
     * Start indexing the project.
     *
     * @param progressCallback Optional callback for progress updates
     * @return Job for the indexing operation
     */
    fun startIndexing(progressCallback: IndexingProgressCallback? = null): Job {
        if (indexingInProgress.getAndSet(true)) {
            return indexScope.launch { } // Return empty job
        }

        return indexScope.launch {
            try {
                // Mark this directory as indexed
                indexedDirectories.add(File(projectPath).canonicalPath)

                // Find all source files
                val sourceFiles = findSourceFiles()
                totalFiles.set(sourceFiles.size)
                filesIndexed.set(0)

                // Index each file
                for ((idx, file) in sourceFiles.withIndex()) {
                    try {
                        indexFile(file)
                        filesIndexed.incrementAndGet()

                        // Report progress
                        progressCallback?.invoke(idx + 1, sourceFiles.size, file.name)

                        // Yield to allow other coroutines to run
                        if (idx % 10 == 0) {
                            yield()
                        }
                    } catch (_: Exception) {
                        // Skip problematic files silently
                    }
                }

                indexingComplete.set(true)

            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Indexing failed silently
            } finally {
                indexingInProgress.set(false)
            }
        }
    }

    /**
     * Index a single file.
     *
     * @param file The file to index
     */
    suspend fun indexFile(file: File) {
        if (!file.exists() || !file.isFile) return

        val extension = file.extension.lowercase()
        if (extension !in listOf("kt", "kts", "java")) return

        PSIThreadBridge.readAction {
            val psiFile = PSIBootstrap.parseFile(file)
            if (psiFile != null) {
                index.indexFile(psiFile, file.absolutePath)
            }
        }
    }

    /**
     * Re-index a single file (for incremental updates).
     *
     * @param filePath Path to the file to re-index
     */
    suspend fun invalidateFile(filePath: String) {
        val file = File(filePath)
        if (file.exists()) {
            indexFile(file)
        } else {
            index.removeFile(filePath)
        }
    }

    /**
     * Start watching for file changes.
     */
    fun startWatching() {
        if (watchJob?.isActive == true) return

        watchJob = indexScope.launch {
            try {
                watchService = FileSystems.getDefault().newWatchService()

                // Register project directory and subdirectories
                val projectDir = Paths.get(projectPath)
                registerDirectoryTree(projectDir)

                // Process events
                while (isActive) {
                    val key = withContext(Dispatchers.IO) {
                        watchService?.poll(1, java.util.concurrent.TimeUnit.SECONDS)
                    }

                    key?.let { processWatchKey(it) }
                }

            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // File watcher error - ignore
            }
        }
    }

    /**
     * Stop watching for file changes.
     */
    fun stopWatching() {
        watchJob?.cancel()
        watchJob = null
        watchService?.close()
        watchService = null
    }

    /**
     * Shutdown the indexer.
     */
    fun shutdown() {
        stopWatching()
        indexScope.cancel()
        index.clear()
        indexedDirectories.clear()
        indexedLibraryJars.clear()
        libraryIndexingComplete.set(false)
    }

    /**
     * Track which directories have been indexed to avoid re-indexing.
     */
    private val indexedDirectories = mutableSetOf<String>()

    /**
     * Track pending indexing jobs per directory for awaiting completion.
     */
    private val pendingIndexingJobs = mutableMapOf<String, Job>()

    /**
     * Lock for thread-safe access to pendingIndexingJobs.
     */
    private val jobsLock = Any()

    /**
     * Extend the index with files from an additional directory.
     * This allows multi-project support by indexing files from projects
     * that weren't in the original index path.
     *
     * @param directoryPath Path to the directory to add
     * @param progressCallback Optional progress callback
     * @return Job for the indexing operation, or null if already indexed
     */
    fun addDirectory(directoryPath: String, progressCallback: IndexingProgressCallback? = null): Job? {
        // Normalize the path
        val normalizedPath = File(directoryPath).canonicalPath

        // Check if already indexed or indexing in progress
        synchronized(jobsLock) {
            if (normalizedPath in indexedDirectories) {
                // Return existing job if still running
                return pendingIndexingJobs[normalizedPath]?.takeIf { it.isActive }
            }
        }

        val job = indexScope.launch {
            try {
                synchronized(jobsLock) {
                    indexedDirectories.add(normalizedPath)
                }

                // Find source files in this directory
                val dir = File(normalizedPath)
                if (!dir.exists() || !dir.isDirectory) {
                    return@launch
                }

                val sourceFiles = dir.walkTopDown()
                    .filter { file ->
                        file.isFile && file.extension.lowercase() in listOf("kt", "kts", "java")
                    }
                    .filter { file ->
                        val relativePath = file.relativeTo(dir).path
                        !relativePath.contains("/build/") &&
                        !relativePath.contains("/.") &&
                        !relativePath.startsWith("build/") &&
                        !relativePath.startsWith(".")
                    }
                    .toList()

                for ((idx, file) in sourceFiles.withIndex()) {
                    try {
                        indexFile(file)
                        progressCallback?.invoke(idx + 1, sourceFiles.size, file.name)
                        if (idx % 10 == 0) yield()
                    } catch (_: Exception) {
                        // Skip problematic files
                    }
                }

            } catch (_: Exception) {
                // Error adding directory - ignore
            } finally {
                // Remove from pending jobs when done
                synchronized(jobsLock) {
                    pendingIndexingJobs.remove(normalizedPath)
                }
            }
        }

        // Track the job
        synchronized(jobsLock) {
            pendingIndexingJobs[normalizedPath] = job
        }

        return job
    }

    /**
     * Await completion of indexing for a specific directory.
     *
     * @param directoryPath Path to the directory
     * @return true if indexing completed, false if no indexing was in progress
     */
    suspend fun awaitDirectoryIndexing(directoryPath: String): Boolean {
        val normalizedPath = File(directoryPath).canonicalPath
        val job = synchronized(jobsLock) {
            pendingIndexingJobs[normalizedPath]
        }
        return if (job != null) {
            job.join()
            true
        } else {
            false
        }
    }

    /**
     * Check if indexing is in progress for a directory.
     *
     * @param directoryPath Path to the directory
     * @return true if indexing is in progress
     */
    fun isDirectoryIndexing(directoryPath: String): Boolean {
        val normalizedPath = File(directoryPath).canonicalPath
        return synchronized(jobsLock) {
            pendingIndexingJobs[normalizedPath]?.isActive == true
        }
    }

    /**
     * Check if a directory has been indexed (either completed or in progress).
     *
     * @param directoryPath Path to the directory
     * @return true if the directory is indexed or being indexed
     */
    fun isDirectoryIndexed(directoryPath: String): Boolean {
        val normalizedPath = File(directoryPath).canonicalPath
        return synchronized(jobsLock) {
            normalizedPath in indexedDirectories
        }
    }

    /**
     * Get all indexed directories including the main project path.
     *
     * @return Set of all indexed directory paths
     */
    fun getIndexedDirectories(): Set<String> {
        return synchronized(jobsLock) {
            indexedDirectories.toSet()
        }
    }

    /**
     * Index a file if it's from a project not yet indexed.
     * Automatically detects the project root and indexes the entire project.
     *
     * @param filePath Path to a file that might be from an unindexed project
     * @return Job for the indexing operation, or null if already indexed or no project found
     */
    fun ensureFileProjectIndexed(filePath: String): Job? {
        val file = File(filePath)
        if (!file.exists()) {
            return null
        }

        // Find the project root (look for build.gradle.kts, settings.gradle.kts, or src directory)
        var current = file.parentFile
        var projectRoot: File? = null

        while (current != null) {
            if (File(current, "build.gradle.kts").exists() ||
                File(current, "build.gradle").exists() ||
                File(current, "settings.gradle.kts").exists() ||
                File(current, "settings.gradle").exists()) {
                projectRoot = current
                // Don't break - keep going up to find the root project
            }
            current = current.parentFile
        }

        if (projectRoot != null) {
            val normalizedRoot = projectRoot.canonicalPath
            val mainProjectPath = File(projectPath).canonicalPath

            if (!isDirectoryIndexed(normalizedRoot) && normalizedRoot != mainProjectPath) {
                return addDirectory(normalizedRoot)
            }
        }
        return null
    }

    /**
     * Find all source files in the project.
     */
    private fun findSourceFiles(): List<File> {
        val projectDir = File(projectPath)
        if (!projectDir.exists() || !projectDir.isDirectory) {
            return emptyList()
        }

        val sourceFiles = mutableListOf<File>()

        // Walk the directory tree
        projectDir.walkTopDown()
            .filter { file ->
                // Filter for source files
                file.isFile && file.extension.lowercase() in listOf("kt", "kts", "java")
            }
            .filter { file ->
                // Skip build directories and hidden files
                val relativePath = file.relativeTo(projectDir).path
                !relativePath.contains("/build/") &&
                !relativePath.contains("/.") &&
                !relativePath.startsWith("build/") &&
                !relativePath.startsWith(".")
            }
            .forEach { sourceFiles.add(it) }

        return sourceFiles
    }

    /**
     * Register a directory tree for watching.
     */
    private fun registerDirectoryTree(root: Path) {
        val service = watchService ?: return

        Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: java.nio.file.attribute.BasicFileAttributes): FileVisitResult {
                // Skip build directories and hidden directories
                val name = dir.fileName?.toString() ?: ""
                if (name.startsWith(".") || name == "build" || name == "out") {
                    return FileVisitResult.SKIP_SUBTREE
                }

                try {
                    dir.register(service,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_DELETE,
                        StandardWatchEventKinds.ENTRY_MODIFY
                    )
                } catch (e: Exception) {
                    // Ignore registration errors for inaccessible directories
                }

                return FileVisitResult.CONTINUE
            }
        })
    }

    /**
     * Process a watch key event.
     */
    private suspend fun processWatchKey(key: WatchKey) {
        val dir = key.watchable() as? Path ?: return

        for (event in key.pollEvents()) {
            val kind = event.kind()
            if (kind == StandardWatchEventKinds.OVERFLOW) continue

            @Suppress("UNCHECKED_CAST")
            val filename = (event as WatchEvent<Path>).context()
            val filePath = dir.resolve(filename)
            val file = filePath.toFile()

            // Only process source files
            if (file.extension.lowercase() !in listOf("kt", "kts", "java")) continue

            when (kind) {
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY -> {
                    // Small delay to let the file system settle
                    delay(100)
                    invalidateFile(file.absolutePath)
                }
                StandardWatchEventKinds.ENTRY_DELETE -> {
                    index.removeFile(file.absolutePath)
                }
            }
        }

        key.reset()
    }

    /**
     * Flag indicating whether library indexing is complete.
     */
    private val libraryIndexingComplete = AtomicBoolean(false)

    /**
     * Track which library JARs have been indexed.
     */
    private val indexedLibraryJars = mutableSetOf<String>()

    /**
     * Check if library indexing is complete.
     */
    val isLibraryIndexingComplete: Boolean
        get() = libraryIndexingComplete.get()

    /**
     * Index library source JARs from the Gradle cache.
     * This enables navigation to Compose, stdlib, and other library symbols.
     *
     * @param progressCallback Optional progress callback
     */
    fun indexLibrarySources(progressCallback: IndexingProgressCallback? = null): Job {
        return indexScope.launch {
            try {
                val gradleCache = File(System.getProperty("user.home"), ".gradle/caches/modules-2/files-2.1")
                if (!gradleCache.exists()) {
                    return@launch
                }

                // Find source JARs for key libraries
                val sourceJars = findLibrarySourceJars(gradleCache)

                var indexed = 0
                for (jar in sourceJars) {
                    if (jar.absolutePath in indexedLibraryJars) continue

                    try {
                        extractAndIndexJar(jar)
                        indexedLibraryJars.add(jar.absolutePath)
                        indexed++
                        progressCallback?.invoke(indexed, sourceJars.size, jar.name)

                        if (indexed % 10 == 0) {
                            yield()
                        }
                    } catch (_: Exception) {
                        // Skip problematic JARs
                    }
                }

                libraryIndexingComplete.set(true)

            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Library indexing error - ignore
            }
        }
    }

    /**
     * Find library source JARs to index.
     * Focuses on key libraries: Compose, Kotlin stdlib, AndroidX.
     */
    private fun findLibrarySourceJars(gradleCache: File): List<File> {
        val sourceJars = mutableListOf<File>()

        // Libraries to index: group/artifactPrefix (artifactPrefix is optional)
        // Gradle cache structure: files-2.1/{group}/{artifact}/{version}/{hash}/{file}
        // Note: group directory uses dots (e.g., "androidx.compose.runtime"), NOT slashes
        val libraryPatterns = listOf(
            // Compose runtime and core (AndroidX)
            "androidx.compose.runtime",
            "androidx.compose.ui",
            "androidx.compose.foundation",
            "androidx.compose.material",
            "androidx.compose.material3",
            // JetBrains Compose (desktop-specific)
            "org.jetbrains.compose.runtime",
            "org.jetbrains.compose.ui",
            "org.jetbrains.compose.foundation",
            "org.jetbrains.compose.material",
            "org.jetbrains.compose.material3",
            // Kotlin stdlib
            "org.jetbrains.kotlin/kotlin-stdlib",
            // Kotlinx coroutines
            "org.jetbrains.kotlinx/kotlinx-coroutines"
        )

        for (pattern in libraryPatterns) {
            val parts = pattern.split("/")
            val group = parts[0]
            val artifactPrefix = parts.getOrNull(1)

            // Find the group directory (group uses dots, not slashes!)
            val groupDir = File(gradleCache, group)
            if (!groupDir.exists()) {
                continue
            }

            // Find matching artifacts
            groupDir.listFiles()?.forEach { artifactDir ->
                if (artifactPrefix == null || artifactDir.name.startsWith(artifactPrefix)) {
                    // Find the latest version's source JAR
                    val sourceJar = findLatestSourceJar(artifactDir)
                    if (sourceJar != null) {
                        sourceJars.add(sourceJar)
                    }
                }
            }
        }

        return sourceJars
    }

    /**
     * Find the latest source JAR for an artifact.
     * Prefers desktop-specific sources when available.
     */
    private fun findLatestSourceJar(artifactDir: File): File? {
        // Get all version directories
        val versionDirs = artifactDir.listFiles()?.filter { it.isDirectory } ?: return null

        // Sort by version (simple string sort works for semantic versions)
        val sortedVersions = versionDirs.sortedByDescending { it.name }

        for (versionDir in sortedVersions) {
            // Find source JARs in this version's hash directories
            val sourceJars = versionDir.walkTopDown()
                .filter { it.isFile && it.name.endsWith("-sources.jar") }
                .toList()

            if (sourceJars.isNotEmpty()) {
                // Prefer desktop-specific sources
                val desktopJar = sourceJars.find { it.name.contains("desktop") }
                return desktopJar ?: sourceJars.first()
            }
        }

        return null
    }

    /**
     * Extract and index Kotlin files from a source JAR.
     *
     * @param jarFile The source JAR to process
     * @return Number of files indexed
     */
    private suspend fun extractAndIndexJar(jarFile: File): Int {
        var filesIndexed = 0

        try {
            JarFile(jarFile).use { jar ->
                val entries = jar.entries()

                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()

                    // Only process Kotlin files
                    if (!entry.name.endsWith(".kt")) continue

                    // Skip test files
                    if (entry.name.contains("/test/") || entry.name.contains("Test.kt")) continue

                    try {
                        val content = jar.getInputStream(entry).bufferedReader().readText()
                        val fileName = entry.name.extractFileName()

                        // Create a virtual path for the library file
                        val virtualPath = "jar://${jarFile.absolutePath}!/${entry.name}"

                        // Parse and index the file
                        PSIThreadBridge.readAction {
                            val psiFile = PSIBootstrap.parseKotlinFile(fileName, content)
                            index.indexFile(psiFile, virtualPath, isLibrary = true)
                            filesIndexed++
                        }

                        // Yield periodically to avoid blocking
                        if (filesIndexed % 20 == 0) {
                            yield()
                        }
                    } catch (e: Exception) {
                        // Skip problematic files
                    }
                }
            }
        } catch (_: Exception) {
            // Error reading JAR - ignore
        }

        return filesIndexed
    }

    companion object {
        /**
         * Global singleton instance for cross-file navigation.
         */
        @Volatile
        private var instance: ProjectIndexer? = null

        /**
         * Get the current indexer instance (if initialized).
         */
        val current: ProjectIndexer?
            get() = instance

        /**
         * Initialize the global indexer for a project.
         * Returns existing instance if already initialized for the same path.
         *
         * @param projectPath The project root path to index
         * @return The indexer instance
         */
        fun initialize(projectPath: String): ProjectIndexer {
            return synchronized(this) {
                val existing = instance
                if (existing != null && existing.projectPath == projectPath) {
                    existing
                } else {
                    existing?.shutdown()
                    ProjectIndexer(projectPath).also { instance = it }
                }
            }
        }

        /**
         * Shutdown and clear the global instance.
         */
        fun shutdownGlobal() {
            synchronized(this) {
                instance?.shutdown()
                instance = null
            }
        }

        /**
         * Create an indexer for the current working directory.
         */
        fun forCurrentDirectory(): ProjectIndexer {
            return ProjectIndexer(System.getProperty("user.dir"))
        }
    }
}
