package ai.rever.boss.psi

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Thread bridge for PSI operations with Compose.
 *
 * Since we're using kotlin-compiler-embeddable which has shaded IntelliJ classes,
 * we don't have access to ApplicationManager. Instead, we use a custom read/write
 * lock to ensure thread-safe access to PSI operations.
 *
 * This bridge provides coroutine-friendly wrappers that:
 * - Execute PSI read operations with proper locking
 * - Allow non-blocking navigation from Compose UI
 * - Handle threading coordination between PSI thread pool and Compose Main dispatcher
 *
 * Usage:
 * ```kotlin
 * val target = PSIThreadBridge.readAction {
 *     navigationService.goToDefinition(file, offset)
 * }
 * ```
 */
object PSIThreadBridge {

    /**
     * Dedicated thread pool for PSI operations.
     * Uses multiple threads to allow concurrent read operations.
     */
    private val psiExecutor = Executors.newFixedThreadPool(4).asCoroutineDispatcher()

    /**
     * Read/write lock for PSI operations.
     * Multiple readers can run concurrently, but writers have exclusive access.
     */
    private val psiLock = ReentrantReadWriteLock()

    /**
     * Dispatcher for PSI read operations.
     */
    val psiDispatcher: CoroutineDispatcher
        get() = psiExecutor

    /**
     * Execute a PSI read action with proper locking.
     *
     * Read actions can run concurrently with other read actions,
     * but will block if a write action is in progress.
     *
     * @param block The PSI operation to execute
     * @return The result of the operation
     */
    suspend fun <T> readAction(block: () -> T): T {
        return withContext(psiExecutor) {
            psiLock.read {
                block()
            }
        }
    }

    /**
     * Execute a PSI read action synchronously (for use in non-coroutine contexts).
     *
     * WARNING: This may block the calling thread. Prefer the suspend version.
     *
     * @param block The PSI operation to execute
     * @return The result of the operation
     */
    fun <T> readActionSync(block: () -> T): T {
        return psiLock.read {
            block()
        }
    }

    /**
     * Execute a PSI write action with exclusive locking.
     *
     * Write actions have exclusive access - they block all read and write operations.
     * Use sparingly and keep the block as short as possible.
     *
     * @param block The PSI modification to execute
     * @return The result of the operation
     */
    suspend fun <T> writeAction(block: () -> T): T {
        return withContext(psiExecutor) {
            psiLock.write {
                block()
            }
        }
    }

    /**
     * Check if we're currently holding a read lock.
     */
    fun isInReadAction(): Boolean {
        return psiLock.readLockCount > 0
    }

    /**
     * Check if we're currently holding a write lock.
     */
    fun isInWriteAction(): Boolean {
        return psiLock.isWriteLocked
    }

    /**
     * Shutdown the PSI thread pool.
     * Call this when the application is closing.
     */
    fun shutdown() {
        try {
            psiExecutor.close()
        } catch (e: Exception) {
            println("[PSI] Error shutting down thread pool: ${e.message}")
        }
    }
}
