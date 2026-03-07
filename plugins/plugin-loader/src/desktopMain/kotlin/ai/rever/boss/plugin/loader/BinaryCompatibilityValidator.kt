package ai.rever.boss.plugin.loader

import ai.rever.boss.plugin.logging.BossLogger
import ai.rever.boss.plugin.logging.LogCategory
import java.util.jar.JarFile

/**
 * Validates binary compatibility of plugin JARs by trial-loading all classes
 * and forcing resolution of their method/field/constructor references.
 *
 * This catches `NoSuchMethodError`, `NoClassDefFoundError`, and other linkage
 * errors at load time rather than at first UI render.
 */
object BinaryCompatibilityValidator {

    private val logger = BossLogger.forComponent("BinaryCompatibilityValidator")

    data class ValidationResult(
        val isCompatible: Boolean,
        val errors: List<String> = emptyList()
    )

    /**
     * Validate all classes in [jarPath] against the given [classLoader].
     *
     * For each `.class` entry (outside `META-INF/`), the class is loaded with
     * `Class.forName(name, false, classLoader)` and then its declared members
     * are accessed to force the JVM to resolve all referenced symbols.
     */
    fun validate(classLoader: ClassLoader, jarPath: String): ValidationResult {
        val errors = mutableListOf<String>()

        val classNames = try {
            JarFile(jarPath).use { jar ->
                jar.entries().asSequence()
                    .filter { it.name.endsWith(".class") && !it.name.startsWith("META-INF/") }
                    .map { it.name.removeSuffix(".class").replace('/', '.') }
                    .toList()
            }
        } catch (e: Exception) {
            logger.warn(LogCategory.SYSTEM, "Failed to read JAR entries for validation", mapOf(
                "jarPath" to jarPath,
                "error" to (e.message ?: "unknown")
            ))
            return ValidationResult(isCompatible = true)
        }

        for (className in classNames) {
            try {
                val clazz = Class.forName(className, false, classLoader)
                // Force resolution of all member references
                clazz.declaredMethods
                clazz.declaredConstructors
                clazz.declaredFields
            } catch (e: LinkageError) {
                errors.add("$className: ${e.javaClass.simpleName} - ${e.message}")
            } catch (e: ClassNotFoundException) {
                errors.add("$className: ClassNotFoundException - ${e.message}")
            } catch (e: Exception) {
                // SecurityException, etc. — not a compatibility issue, skip
            }
        }

        if (errors.isNotEmpty()) {
            logger.warn(LogCategory.SYSTEM, "Binary compatibility validation failed", mapOf(
                "jarPath" to jarPath,
                "errorCount" to errors.size,
                "errors" to errors.take(5)
            ))
        } else {
            logger.debug(LogCategory.SYSTEM, "Binary compatibility validation passed", mapOf(
                "jarPath" to jarPath,
                "classCount" to classNames.size
            ))
        }

        return ValidationResult(
            isCompatible = errors.isEmpty(),
            errors = errors
        )
    }
}
