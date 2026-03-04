package ai.rever.boss.plugin.sandbox.ui

import ai.rever.boss.plugin.logging.BossLogger
import ai.rever.boss.plugin.logging.LogCategory
import java.util.concurrent.ConcurrentHashMap

/**
 * Intercepts uncaught exceptions on the AWT/Compose thread and attributes them to plugins.
 *
 * When a plugin has a binary incompatibility (e.g., `NoSuchMethodError` from a mismatched
 * dependency version), the error occurs during Compose composition and cannot be caught by
 * normal try-catch or [LocalPluginErrorHandler]. This interceptor hooks into the global
 * [Thread.UncaughtExceptionHandler] chain to catch these errors and route them to the
 * appropriate plugin's error handler.
 *
 * ## How it works
 * 1. The [PluginErrorBoundary] registers an interceptor callback for its plugin ID
 * 2. When an uncaught exception occurs, this interceptor inspects the stack trace
 * 3. If the stack trace contains frames from a plugin's classloader or sandbox thread,
 *    the error is attributed to that plugin
 * 4. The registered callback is invoked to set the error state in the boundary's composable
 *
 * ## Thread Safety
 * Uses [ConcurrentHashMap] for the interceptor registry. The exception handler runs on
 * the crashing thread (usually AWT EDT for composition errors).
 */
object PluginCrashInterceptor {

    private val logger = BossLogger.forComponent("PluginCrashInterceptor")

    /**
     * Registered interceptors: pluginId -> error callback.
     * When multiple composables for the same plugin are active, the most recent
     * registration wins (last-writer-wins via ConcurrentHashMap).
     */
    private val interceptors = ConcurrentHashMap<String, (Throwable) -> Unit>()

    /**
     * Cached classloader-to-pluginId mapping, populated at registration time.
     * Avoids calling Class.forName() during exception handling (which could
     * trigger class loading and mask the original error).
     */
    private val classLoaderToPluginId = ConcurrentHashMap<ClassLoader, String>()

    /**
     * The original uncaught exception handler we chain to.
     */
    @Volatile
    private var originalHandler: Thread.UncaughtExceptionHandler? = null

    @Volatile
    private var isInstalled = false

    /**
     * Install the crash interceptor by chaining into the global uncaught exception handler.
     *
     * Safe to call multiple times - only installs once. Should be called early in app startup,
     * after [CrashHandler.install()] so we chain properly.
     */
    fun install() {
        if (isInstalled) return
        synchronized(this) {
            if (isInstalled) return

            originalHandler = Thread.getDefaultUncaughtExceptionHandler()

            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                val handled = tryHandlePluginCrash(thread, throwable)
                if (!handled) {
                    // Not a plugin crash (or no interceptor registered) - chain to original handler
                    originalHandler?.uncaughtException(thread, throwable)
                }
            }

            isInstalled = true
            logger.debug(LogCategory.SYSTEM, "Plugin crash interceptor installed")
        }
    }

    /**
     * Register an error callback for a plugin.
     *
     * @param pluginId The plugin ID to intercept errors for
     * @param onError Callback invoked when an error is attributed to this plugin
     * @return A [Registration] that should be disposed when the composable leaves composition
     */
    fun register(pluginId: String, onError: (Throwable) -> Unit): Registration {
        interceptors[pluginId] = onError

        // Cache classloader mapping at registration time so we don't need
        // Class.forName() during exception handling (which risks masking errors).
        try {
            val contextLoader = Thread.currentThread().contextClassLoader
            if (contextLoader != null) {
                // Check if this is a PluginClassLoader by reflection
                try {
                    val field = contextLoader.javaClass.getField("pluginId")
                    val loaderId = field.get(contextLoader) as? String
                    if (loaderId == pluginId) {
                        classLoaderToPluginId[contextLoader] = pluginId
                    }
                } catch (_: NoSuchFieldException) {
                    // Not a PluginClassLoader, skip
                } catch (_: Throwable) {
                    // Ignore reflection errors
                }
            }
        } catch (_: Throwable) {
            // Don't let caching fail registration
        }

        logger.debug(LogCategory.SYSTEM, "Registered crash interceptor", mapOf(
            "pluginId" to pluginId,
            "activeInterceptors" to interceptors.size
        ))
        return Registration(pluginId)
    }

    /**
     * Attempt to handle an uncaught exception as a plugin crash.
     *
     * @return true if the error was attributed to a plugin and handled
     */
    private fun tryHandlePluginCrash(thread: Thread, throwable: Throwable): Boolean {
        val pluginId = attributeToPlugin(throwable, thread) ?: return false
        val callback = interceptors[pluginId] ?: return false

        logger.warn(LogCategory.SYSTEM, "Intercepted plugin crash during composition", mapOf(
            "pluginId" to pluginId,
            "errorType" to throwable.javaClass.simpleName,
            "thread" to thread.name,
            "message" to (throwable.message ?: "no message")
        ))

        return try {
            callback(throwable)
            true
        } catch (e: Exception) {
            logger.error(LogCategory.SYSTEM, "Error in plugin crash callback", mapOf(
                "pluginId" to pluginId
            ), e)
            false
        }
    }

    /**
     * Handle a crash attributed to a specific plugin.
     *
     * Called by the custom [WindowExceptionHandlerFactory] in main.kt when Compose catches
     * a composition error and [attributeToPlugin] identifies the responsible plugin.
     *
     * @return true if the callback was invoked successfully
     */
    fun tryHandle(pluginId: String, throwable: Throwable): Boolean {
        val callback = interceptors[pluginId] ?: return false

        logger.warn(LogCategory.SYSTEM, "Handling plugin crash via WindowExceptionHandler", mapOf(
            "pluginId" to pluginId,
            "errorType" to throwable.javaClass.simpleName,
            "message" to (throwable.message ?: "no message")
        ))

        return try {
            callback(throwable)
            true
        } catch (e: Exception) {
            logger.error(LogCategory.SYSTEM, "Error in plugin crash callback", mapOf(
                "pluginId" to pluginId
            ), e)
            false
        }
    }

    /**
     * Register a classloader-to-pluginId mapping.
     *
     * Called by plugin loading infrastructure to pre-cache the mapping,
     * avoiding [Class.forName] during exception handling.
     */
    fun registerClassLoader(classLoader: ClassLoader, pluginId: String) {
        classLoaderToPluginId[classLoader] = pluginId
    }

    /**
     * Attribute an exception to a plugin by inspecting the stack trace and thread name.
     *
     * Checks for:
     * 1. Thread name containing plugin sandbox identifier (`plugin-sandbox-<pluginId>`)
     * 2. Stack frame class names matching registered plugin package prefixes
     * 3. Stack frame classloaders resolved via cached plugin classloaders
     *
     * @return The plugin ID if attributed, null otherwise
     */
    fun attributeToPlugin(throwable: Throwable, thread: Thread? = null): String? {
        // Strategy 1: Check thread name for plugin sandbox threads
        val threadName = thread?.name ?: Thread.currentThread().name
        for (pluginId in interceptors.keys) {
            if (threadName.contains("plugin-sandbox-$pluginId")) {
                return pluginId
            }
        }

        // Strategy 2: Match stack trace class names against registered plugin package prefixes.
        // Plugin classes follow the convention ai.rever.boss.plugin.dynamic.<shortId>.* where
        // the pluginId is ai.rever.boss.plugin.dynamic.<shortId>. This is a fast string
        // comparison that avoids class loading entirely.
        try {
            for (element in throwable.stackTrace) {
                for (pluginId in interceptors.keys) {
                    if (element.className.startsWith("$pluginId.")) {
                        return pluginId
                    }
                }
            }
        } catch (_: Throwable) {
            // Don't let attribution itself crash
        }

        // Strategy 3: Resolve stack trace classes via cached plugin classloaders.
        // Tries each registered plugin classloader to find which one loaded the class,
        // attributing the error to that plugin.
        if (classLoaderToPluginId.isNotEmpty()) {
            try {
                for (element in throwable.stackTrace) {
                    for ((loader, pId) in classLoaderToPluginId) {
                        if (!interceptors.containsKey(pId)) continue
                        val clazz = try {
                            Class.forName(element.className, false, loader)
                        } catch (_: Throwable) {
                            null
                        }
                        if (clazz != null && clazz.classLoader == loader) {
                            return pId
                        }
                    }
                }
            } catch (_: Throwable) {
                // Don't let attribution itself crash
            }
        }

        return null
    }

    /**
     * Handle representing a registered interceptor. Call [unregister] to remove.
     */
    class Registration(private val pluginId: String) {
        fun unregister() {
            interceptors.remove(pluginId)
            // Clean up cached classloader entries for this plugin
            classLoaderToPluginId.entries.removeIf { it.value == pluginId }
            logger.debug(LogCategory.SYSTEM, "Unregistered crash interceptor", mapOf(
                "pluginId" to pluginId,
                "activeInterceptors" to interceptors.size
            ))
        }
    }
}
