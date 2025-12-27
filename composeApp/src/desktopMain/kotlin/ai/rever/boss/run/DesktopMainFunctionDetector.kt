package ai.rever.boss.run

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

actual fun createMainFunctionDetector(): MainFunctionDetector = DesktopMainFunctionDetector()

/**
 * Desktop implementation of MainFunctionDetector.
 * Detects main functions in Kotlin, Java, Python, JavaScript, TypeScript, Go, and Rust.
 */
class DesktopMainFunctionDetector : MainFunctionDetector {

    companion object {
        // Regex patterns for main function detection
        private val KOTLIN_MAIN_PATTERN = Regex(
            """^\s*(?:@JvmStatic\s+)?fun\s+main\s*\(""",
            RegexOption.MULTILINE
        )
        private val KOTLIN_PACKAGE_PATTERN = Regex(
            """^\s*package\s+([\w.]+)""",
            RegexOption.MULTILINE
        )

        private val JAVA_MAIN_PATTERN = Regex(
            """^\s*public\s+static\s+void\s+main\s*\(\s*String\s*\[?\s*\]?\s*\w*\s*\)""",
            RegexOption.MULTILINE
        )
        private val JAVA_CLASS_PATTERN = Regex(
            """^\s*(?:public\s+)?class\s+(\w+)""",
            RegexOption.MULTILINE
        )
        private val JAVA_PACKAGE_PATTERN = Regex(
            """^\s*package\s+([\w.]+)\s*;""",
            RegexOption.MULTILINE
        )

        private val PYTHON_MAIN_PATTERN = Regex(
            """^if\s+__name__\s*==\s*['""]__main__['""]""",
            RegexOption.MULTILINE
        )

        private val GO_MAIN_PATTERN = Regex(
            """^\s*func\s+main\s*\(\s*\)""",
            RegexOption.MULTILINE
        )
        private val GO_PACKAGE_MAIN_PATTERN = Regex(
            """^\s*package\s+main\b""",
            RegexOption.MULTILINE
        )

        private val RUST_MAIN_PATTERN = Regex(
            """^\s*fn\s+main\s*\(\s*\)""",
            RegexOption.MULTILINE
        )

        // File extensions to scan
        private val SCANNABLE_EXTENSIONS = setOf(
            "kt", "kts", "java", "py", "js", "jsx", "mjs", "ts", "tsx", "go", "rs"
        )

        // Directories to skip
        private val SKIP_DIRECTORIES = setOf(
            "build", "node_modules", ".git", ".gradle", ".idea", "target",
            "__pycache__", "venv", ".venv", "dist", "out", "bin"
        )
    }

    override suspend fun scanProject(projectPath: String): List<RunConfiguration> =
        withContext(Dispatchers.IO) {
            val projectDir = File(projectPath)
            if (!projectDir.exists() || !projectDir.isDirectory) {
                return@withContext emptyList()
            }

            val configurations = mutableListOf<RunConfiguration>()
            scanDirectory(projectDir, projectPath, configurations)
            configurations
        }

    private fun scanDirectory(
        directory: File,
        projectPath: String,
        configurations: MutableList<RunConfiguration>
    ) {
        directory.listFiles()?.forEach { file ->
            when {
                file.isDirectory && !SKIP_DIRECTORIES.contains(file.name) && !file.name.startsWith(".") -> {
                    scanDirectory(file, projectPath, configurations)
                }
                file.isFile && SCANNABLE_EXTENSIONS.contains(file.extension.lowercase()) -> {
                    try {
                        val content = file.readText()
                        val detected = detectInFile(file.absolutePath, content)
                        detected.forEach { mainFunc ->
                            configurations.add(
                                RunConfiguration(
                                    id = UUID.randomUUID().toString(),
                                    name = mainFunc.toShortName(),
                                    type = RunConfigurationType.MAIN_FUNCTION,
                                    filePath = file.absolutePath,
                                    lineNumber = mainFunc.lineNumber,
                                    language = mainFunc.language,
                                    command = generateCommand(mainFunc, projectPath),
                                    workingDirectory = projectPath,
                                    isAutoDetected = true
                                )
                            )
                        }
                    } catch (e: Exception) {
                        println("Error scanning file ${file.absolutePath}: ${e.message}")
                    }
                }
            }
        }
    }

    override fun detectInFile(
        filePath: String,
        content: String,
        language: Language?
    ): List<DetectedMainFunction> {
        val detectedLanguage = language ?: Language.fromFileName(filePath)
        return when (detectedLanguage) {
            Language.KOTLIN -> detectKotlinMain(filePath, content)
            Language.JAVA -> detectJavaMain(filePath, content)
            Language.PYTHON -> detectPythonMain(filePath, content)
            Language.JAVASCRIPT, Language.TYPESCRIPT -> detectJsMain(filePath, content, detectedLanguage)
            Language.GO -> detectGoMain(filePath, content)
            Language.RUST -> detectRustMain(filePath, content)
            Language.UNKNOWN -> emptyList()
        }
    }

    private fun detectKotlinMain(filePath: String, content: String): List<DetectedMainFunction> {
        val results = mutableListOf<DetectedMainFunction>()
        val lines = content.lines()

        // Extract package name
        val packageMatch = KOTLIN_PACKAGE_PATTERN.find(content)
        val packageName = packageMatch?.groupValues?.get(1)

        // Track if we're inside a multi-line string (triple quotes)
        var inMultiLineString = false

        // Find main functions
        lines.forEachIndexed { index, line ->
            // Count triple quotes to track multi-line string state
            val tripleQuoteCount = line.windowed(3).count { it == "\"\"\"" }
            if (tripleQuoteCount % 2 == 1) {
                inMultiLineString = !inMultiLineString
            }

            // Only detect main if not inside a multi-line string
            if (!inMultiLineString && KOTLIN_MAIN_PATTERN.containsMatchIn(line)) {
                // Also skip if the line looks like it's inside a regular string
                val trimmedLine = line.trim()
                if (!trimmedLine.startsWith("\"") && !trimmedLine.startsWith("text =")) {
                    results.add(
                        DetectedMainFunction(
                            lineNumber = index,
                            functionName = "main",
                            className = null,
                            packageName = packageName,
                            language = Language.KOTLIN,
                            filePath = filePath
                        )
                    )
                }
            }
        }

        return results
    }

    private fun detectJavaMain(filePath: String, content: String): List<DetectedMainFunction> {
        val results = mutableListOf<DetectedMainFunction>()
        val lines = content.lines()

        // Extract package and class name
        val packageMatch = JAVA_PACKAGE_PATTERN.find(content)
        val packageName = packageMatch?.groupValues?.get(1)

        val classMatch = JAVA_CLASS_PATTERN.find(content)
        val className = classMatch?.groupValues?.get(1)

        // Find main methods
        lines.forEachIndexed { index, line ->
            if (JAVA_MAIN_PATTERN.containsMatchIn(line)) {
                results.add(
                    DetectedMainFunction(
                        lineNumber = index,
                        functionName = "main",
                        className = className,
                        packageName = packageName,
                        language = Language.JAVA,
                        filePath = filePath
                    )
                )
            }
        }

        return results
    }

    private fun detectPythonMain(filePath: String, content: String): List<DetectedMainFunction> {
        val results = mutableListOf<DetectedMainFunction>()
        val lines = content.lines()

        lines.forEachIndexed { index, line ->
            if (PYTHON_MAIN_PATTERN.containsMatchIn(line)) {
                results.add(
                    DetectedMainFunction(
                        lineNumber = index,
                        functionName = "__main__",
                        className = null,
                        packageName = null,
                        language = Language.PYTHON,
                        filePath = filePath
                    )
                )
            }
        }

        return results
    }

    private fun detectJsMain(
        filePath: String,
        content: String,
        language: Language
    ): List<DetectedMainFunction> {
        // For JS/TS, we consider files with certain patterns as entry points
        // This is a simplified detection - real detection would check package.json
        val results = mutableListOf<DetectedMainFunction>()

        // Check if it's a typical entry point file
        val fileName = File(filePath).name.lowercase()
        val isEntryPoint = fileName in listOf(
            "index.js", "index.ts", "index.jsx", "index.tsx",
            "main.js", "main.ts", "app.js", "app.ts",
            "server.js", "server.ts"
        )

        if (isEntryPoint) {
            results.add(
                DetectedMainFunction(
                    lineNumber = 0,
                    functionName = "entry",
                    className = null,
                    packageName = null,
                    language = language,
                    filePath = filePath
                )
            )
        }

        return results
    }

    private fun detectGoMain(filePath: String, content: String): List<DetectedMainFunction> {
        val results = mutableListOf<DetectedMainFunction>()

        // Go requires both package main and func main()
        if (!GO_PACKAGE_MAIN_PATTERN.containsMatchIn(content)) {
            return results
        }

        val lines = content.lines()
        lines.forEachIndexed { index, line ->
            if (GO_MAIN_PATTERN.containsMatchIn(line)) {
                results.add(
                    DetectedMainFunction(
                        lineNumber = index,
                        functionName = "main",
                        className = null,
                        packageName = "main",
                        language = Language.GO,
                        filePath = filePath
                    )
                )
            }
        }

        return results
    }

    private fun detectRustMain(filePath: String, content: String): List<DetectedMainFunction> {
        val results = mutableListOf<DetectedMainFunction>()
        val lines = content.lines()

        lines.forEachIndexed { index, line ->
            if (RUST_MAIN_PATTERN.containsMatchIn(line)) {
                results.add(
                    DetectedMainFunction(
                        lineNumber = index,
                        functionName = "main",
                        className = null,
                        packageName = null,
                        language = Language.RUST,
                        filePath = filePath
                    )
                )
            }
        }

        return results
    }

    override fun generateCommand(detected: DetectedMainFunction, projectPath: String): String {
        val projectDir = File(projectPath)

        return when (detected.language) {
            Language.KOTLIN -> generateKotlinCommand(detected, projectDir)
            Language.JAVA -> generateJavaCommand(detected, projectDir)
            Language.PYTHON -> generatePythonCommand(detected)
            Language.JAVASCRIPT -> generateJavaScriptCommand(detected)
            Language.TYPESCRIPT -> generateTypeScriptCommand(detected)
            Language.GO -> generateGoCommand(detected)
            Language.RUST -> generateRustCommand(projectDir)
            Language.UNKNOWN -> "echo 'Unknown language'"
        }
    }

    private fun generateKotlinCommand(detected: DetectedMainFunction, projectDir: File): String {
        // Check for Gradle project
        if (hasGradleWrapper(projectDir)) {
            return "./gradlew run"
        }
        // Fallback to kotlin command
        return "kotlin ${detected.filePath}"
    }

    private fun generateJavaCommand(detected: DetectedMainFunction, projectDir: File): String {
        // Check for Gradle project
        if (hasGradleWrapper(projectDir)) {
            return "./gradlew run"
        }
        // Fallback to javac + java
        val className = if (detected.packageName != null && detected.className != null) {
            "${detected.packageName}.${detected.className}"
        } else {
            detected.className ?: "Main"
        }
        return "javac ${detected.filePath} && java $className"
    }

    private fun generatePythonCommand(detected: DetectedMainFunction): String {
        return "python3 ${detected.filePath}"
    }

    private fun generateJavaScriptCommand(detected: DetectedMainFunction): String {
        return "node ${detected.filePath}"
    }

    private fun generateTypeScriptCommand(detected: DetectedMainFunction): String {
        return "npx ts-node ${detected.filePath}"
    }

    private fun generateGoCommand(detected: DetectedMainFunction): String {
        return "go run ${detected.filePath}"
    }

    private fun generateRustCommand(projectDir: File): String {
        // Check for Cargo project
        if (File(projectDir, "Cargo.toml").exists()) {
            return "cargo run"
        }
        return "rustc main.rs && ./main"
    }

    private fun hasGradleWrapper(projectDir: File): Boolean {
        return File(projectDir, "gradlew").exists() ||
               File(projectDir, "gradlew.bat").exists()
    }
}
