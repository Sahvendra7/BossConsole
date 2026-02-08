package ai.rever.boss.plugin.loader

/**
 * Exception thrown when plugin loading fails.
 */
open class PluginLoadException(
    message: String,
    val pluginId: String? = null,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Exception thrown when a plugin manifest is invalid or missing.
 */
class PluginManifestException(
    message: String,
    pluginId: String? = null,
    cause: Throwable? = null
) : PluginLoadException(message, pluginId, cause)

/**
 * Exception thrown when a plugin's main class cannot be found or instantiated.
 */
class PluginClassException(
    message: String,
    pluginId: String? = null,
    val className: String? = null,
    cause: Throwable? = null
) : PluginLoadException(message, pluginId, cause)

/**
 * Exception thrown when a plugin dependency cannot be resolved.
 */
class PluginDependencyException(
    message: String,
    pluginId: String? = null,
    val missingDependencies: List<String> = emptyList(),
    cause: Throwable? = null
) : PluginLoadException(message, pluginId, cause)

/**
 * Exception thrown when a plugin cannot be unloaded.
 */
class PluginUnloadException(
    message: String,
    pluginId: String? = null,
    val reasons: List<String> = emptyList(),
    cause: Throwable? = null
) : PluginLoadException(message, pluginId, cause)

/**
 * Exception thrown when a plugin's API version is incompatible.
 */
class PluginApiVersionException(
    message: String,
    pluginId: String? = null,
    val requiredVersion: String? = null,
    val currentVersion: String? = null,
    cause: Throwable? = null
) : PluginLoadException(message, pluginId, cause)

/**
 * Exception thrown when a plugin JAR signature verification fails.
 */
class PluginSignatureException(
    message: String,
    pluginId: String? = null,
    cause: Throwable? = null
) : PluginLoadException(message, pluginId, cause)

/**
 * Exception thrown when a plugin requires a newer BOSS version.
 */
class PluginBossVersionException(
    message: String,
    pluginId: String? = null,
    val requiredVersion: String? = null,
    val currentVersion: String? = null,
    cause: Throwable? = null
) : PluginLoadException(message, pluginId, cause)
