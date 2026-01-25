package ai.rever.boss.psi

import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.openapi.project.Project
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.psi.PsiFile
import org.jetbrains.kotlin.com.intellij.psi.PsiFileFactory
import org.jetbrains.kotlin.idea.KotlinLanguage
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * PSI Bootstrap Service - Initializes the IntelliJ Platform infrastructure for code navigation.
 *
 * This service provides:
 * - Kotlin PSI parsing via kotlin-compiler-embeddable
 * - Project environment for symbol resolution
 *
 * Thread Safety: This object must be initialized from the main thread before use.
 * PSI operations must use ReadAction wrappers (see PSIThreadBridge).
 *
 * NOTE: This uses the Kotlin compiler's embedded IntelliJ classes (org.jetbrains.kotlin.com.intellij)
 * rather than standalone IntelliJ libraries to avoid class shadowing conflicts.
 */
object PSIBootstrap {
    private val logger = BossLogger.forComponent("PSIBootstrap")

    private val initialized = AtomicBoolean(false)
    private var rootDisposable: Disposable? = null
    private var kotlinEnvironment: KotlinCoreEnvironment? = null
    private var ktPsiFactory: KtPsiFactory? = null

    /**
     * The IntelliJ Project instance for PSI operations.
     * Must be accessed only after initialize() is called.
     */
    val project: Project
        get() = kotlinEnvironment?.project
            ?: throw IllegalStateException("PSI not initialized. Call initialize() first.")

    /**
     * Check if PSI is initialized and ready for use.
     */
    val isInitialized: Boolean
        get() = initialized.get()

    /**
     * Initialize the PSI infrastructure.
     *
     * This sets up:
     * - Kotlin compiler environment for Kotlin PSI
     * - Extension points for language support
     *
     * @throws IllegalStateException if already initialized
     */
    @Synchronized
    fun initialize() {
        if (initialized.get()) {
            return
        }

        try {
            // Create root disposable for cleanup
            rootDisposable = Disposer.newDisposable("BOSS-PSI")

            // Configure Kotlin compiler
            val configuration = CompilerConfiguration()

            // Create Kotlin core environment
            kotlinEnvironment = KotlinCoreEnvironment.createForProduction(
                rootDisposable!!,
                configuration,
                EnvironmentConfigFiles.JVM_CONFIG_FILES
            )

            // Create PSI factory for file parsing
            ktPsiFactory = KtPsiFactory(project)

            initialized.set(true)

        } catch (e: Exception) {
            logger.error(LogCategory.EDITOR, "Failed to initialize PSI", error = e)
            shutdown()
            throw RuntimeException("Failed to initialize PSI", e)
        }
    }

    /**
     * Shutdown the PSI infrastructure and release resources.
     */
    @Synchronized
    fun shutdown() {
        if (!initialized.get() && rootDisposable == null) {
            return
        }

        try {
            ktPsiFactory = null
            kotlinEnvironment = null

            rootDisposable?.let { disposable ->
                Disposer.dispose(disposable)
            }
            rootDisposable = null

            initialized.set(false)

        } catch (e: Exception) {
            logger.warn(LogCategory.EDITOR, "Error during shutdown", error = e)
        }
    }

    /**
     * Parse a Kotlin file from text content.
     *
     * @param fileName Name of the file (used for error messages)
     * @param content The Kotlin source code
     * @return KtFile PSI representation
     */
    fun parseKotlinFile(fileName: String, content: String): KtFile {
        ensureInitialized()

        val factory = ktPsiFactory
            ?: throw IllegalStateException("PSI factory not initialized")

        return factory.createFile(fileName, content)
    }

    /**
     * Parse a file from disk.
     *
     * @param file The file to parse
     * @return KtFile representation, or null if parsing fails or not a Kotlin file
     */
    fun parseFile(file: File): KtFile? {
        ensureInitialized()

        if (!file.exists() || !file.isFile) {
            return null
        }

        // Only support Kotlin files for now
        if (file.extension !in listOf("kt", "kts")) {
            return null
        }

        val content = try {
            file.readText()
        } catch (e: Exception) {
            logger.warn(LogCategory.EDITOR, "Failed to read file", mapOf("fileName" to file.name), error = e)
            return null
        }

        return parseKotlinFile(file.name, content)
    }

    /**
     * Ensure PSI is initialized before operations.
     */
    private fun ensureInitialized() {
        if (!initialized.get()) {
            throw IllegalStateException("PSI not initialized. Call PSIBootstrap.initialize() first.")
        }
    }
}
