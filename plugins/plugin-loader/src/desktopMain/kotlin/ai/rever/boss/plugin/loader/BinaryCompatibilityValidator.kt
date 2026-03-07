package ai.rever.boss.plugin.loader

import ai.rever.boss.plugin.logging.BossLogger
import ai.rever.boss.plugin.logging.LogCategory
import java.io.DataInputStream
import java.util.jar.JarFile

/**
 * Validates binary compatibility of plugin JARs by loading all classes and
 * verifying that every method, field, and constructor reference in their
 * constant pools can be resolved against the current API.
 *
 * This catches:
 * - `NoClassDefFoundError` — a referenced class is entirely missing
 * - `NoSuchMethodError` — a method/constructor signature changed (e.g. parameter reorder)
 * - `NoSuchFieldError` — a field was removed or renamed
 *
 * The JVM resolves these lazily at first use, so without this check they
 * would surface as crashes during first UI render.
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
     * For each `.class` entry (outside `META-INF/`), the constant pool is
     * parsed to extract all `MethodRef`, `FieldRef`, and `InterfaceMethodRef`
     * entries. Each referenced class is loaded and its members are checked
     * via reflection. This forces resolution of all symbolic references that
     * the JVM would otherwise defer until first execution.
     */
    fun validate(classLoader: ClassLoader, jarPath: String): ValidationResult {
        val errors = mutableListOf<String>()

        val classEntries = try {
            JarFile(jarPath).use { jar ->
                jar.entries().asSequence()
                    .filter { it.name.endsWith(".class") && !it.name.startsWith("META-INF/") }
                    .map { entry ->
                        val className = entry.name.removeSuffix(".class").replace('/', '.')
                        val bytes = jar.getInputStream(entry).use { it.readBytes() }
                        className to bytes
                    }
                    .toList()
            }
        } catch (e: Exception) {
            logger.error(LogCategory.SYSTEM, "Failed to read JAR for validation", mapOf(
                "jarPath" to jarPath,
                "error" to (e.message ?: "unknown")
            ))
            return ValidationResult(isCompatible = false, errors = listOf(
                "Failed to read JAR: ${e.message}"
            ))
        }

        // Collect all class names in this JAR — references between them are
        // self-consistent (compiled together) and don't need cross-validation.
        val jarClassNames = classEntries.map { it.first }.toSet()

        for ((className, bytes) in classEntries) {
            // First, ensure the class itself can be loaded
            try {
                Class.forName(className, false, classLoader)
            } catch (e: LinkageError) {
                errors.add("$className: ${e.javaClass.simpleName} - ${e.message}")
                continue
            } catch (e: ClassNotFoundException) {
                errors.add("$className: ClassNotFoundException - ${e.message}")
                continue
            }

            // Parse constant pool and verify all symbolic references
            try {
                val refs = ConstantPoolParser.extractReferences(bytes)
                for (ref in refs) {
                    // Skip references to classes within the same JAR — they were
                    // compiled together and are guaranteed to be consistent.
                    if (ref.ownerClassName in jarClassNames) continue
                    verifyReference(ref, classLoader, className, errors)
                }
            } catch (e: Exception) {
                // Malformed class file — not a compatibility issue per se, skip
                logger.debug(LogCategory.SYSTEM, "Failed to parse constant pool", mapOf(
                    "className" to className,
                    "error" to (e.message ?: "unknown")
                ))
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
                "classCount" to classEntries.size
            ))
        }

        return ValidationResult(
            isCompatible = errors.isEmpty(),
            errors = errors
        )
    }

    private fun verifyReference(
        ref: ConstantPoolParser.MemberRef,
        classLoader: ClassLoader,
        sourceClass: String,
        errors: MutableList<String>
    ) {
        // Skip references to the plugin's own classes (they're already loaded above)
        // and primitive/array types
        if (ref.ownerClassName.startsWith("[") || ref.ownerClassName.isEmpty()) return

        val ownerClass = try {
            Class.forName(ref.ownerClassName, false, classLoader)
        } catch (e: LinkageError) {
            errors.add("$sourceClass -> ${ref.ownerClassName}: ${e.javaClass.simpleName} - ${e.message}")
            return
        } catch (e: ClassNotFoundException) {
            // Only flag missing classes from the shared API packages, not JDK/Kotlin stdlib
            if (ref.ownerClassName.startsWith("ai.rever.boss.plugin.")) {
                errors.add("$sourceClass -> ${ref.ownerClassName}: class not found")
            }
            return
        }

        // Verify the specific member exists
        when (ref.type) {
            ConstantPoolParser.RefType.METHOD,
            ConstantPoolParser.RefType.INTERFACE_METHOD -> {
                if (ref.name == "<init>") {
                    // Constructor — verify parameter types match
                    val paramTypes = ref.parseParameterTypes(classLoader) ?: return
                    try {
                        ownerClass.getDeclaredConstructor(*paramTypes)
                    } catch (_: NoSuchMethodException) {
                        errors.add("$sourceClass -> ${ref.ownerClassName}.<init>(${ref.descriptor}): constructor not found")
                    }
                } else if (ref.name != "<clinit>") {
                    // Regular method — check name + parameter types
                    val paramTypes = ref.parseParameterTypes(classLoader) ?: return
                    if (!hasMethod(ownerClass, ref.name, paramTypes)) {
                        errors.add("$sourceClass -> ${ref.ownerClassName}.${ref.name}(${ref.descriptor}): method not found")
                    }
                }
            }
            ConstantPoolParser.RefType.FIELD -> {
                if (!hasField(ownerClass, ref.name)) {
                    errors.add("$sourceClass -> ${ref.ownerClassName}.${ref.name}: field not found")
                }
            }
        }
    }

    /** Check the class and its superclasses/interfaces for the method. */
    private fun hasMethod(clazz: Class<*>, name: String, paramTypes: Array<Class<*>>): Boolean {
        return try {
            clazz.getMethod(name, *paramTypes)
            true
        } catch (_: NoSuchMethodException) {
            // getMethod only finds public methods; try declared on the hierarchy
            var current: Class<*>? = clazz
            while (current != null) {
                try {
                    current.getDeclaredMethod(name, *paramTypes)
                    return true
                } catch (_: NoSuchMethodException) {
                    // continue
                }
                current = current.superclass
            }
            false
        }
    }

    /** Check the class and its superclasses for the field. */
    private fun hasField(clazz: Class<*>, name: String): Boolean {
        return try {
            clazz.getField(name)
            true
        } catch (_: NoSuchFieldException) {
            var current: Class<*>? = clazz
            while (current != null) {
                try {
                    current.getDeclaredField(name)
                    return true
                } catch (_: NoSuchFieldException) {
                    // continue
                }
                current = current.superclass
            }
            false
        }
    }
}

/**
 * Minimal JVM constant pool parser that extracts MethodRef, FieldRef,
 * and InterfaceMethodRef entries from class file bytes.
 *
 * Follows the JVM Class File Format specification (JVMS §4.4).
 * Only parses enough to resolve symbolic references — skips attributes,
 * methods, and other sections.
 */
internal object ConstantPoolParser {

    enum class RefType { METHOD, FIELD, INTERFACE_METHOD }

    data class MemberRef(
        val type: RefType,
        val ownerClassName: String,
        val name: String,
        val descriptor: String
    ) {
        /**
         * Parse JVM method descriptor parameter types into Class objects.
         * Returns null if any type cannot be resolved (e.g. plugin-internal types).
         */
        fun parseParameterTypes(classLoader: ClassLoader): Array<Class<*>>? {
            return try {
                parseDescriptorParams(descriptor, classLoader)
            } catch (_: ClassNotFoundException) {
                null
            }
        }
    }

    // Constant pool tag values (JVMS §4.4)
    private const val TAG_UTF8 = 1
    private const val TAG_INTEGER = 3
    private const val TAG_FLOAT = 4
    private const val TAG_LONG = 5
    private const val TAG_DOUBLE = 6
    private const val TAG_CLASS = 7
    private const val TAG_STRING = 8
    private const val TAG_FIELDREF = 9
    private const val TAG_METHODREF = 10
    private const val TAG_INTERFACE_METHODREF = 11
    private const val TAG_NAME_AND_TYPE = 12
    private const val TAG_METHOD_HANDLE = 15
    private const val TAG_METHOD_TYPE = 16
    private const val TAG_DYNAMIC = 17
    private const val TAG_INVOKE_DYNAMIC = 18
    private const val TAG_MODULE = 19
    private const val TAG_PACKAGE = 20

    fun extractReferences(classBytes: ByteArray): List<MemberRef> {
        val dis = DataInputStream(classBytes.inputStream())

        // Magic number
        val magic = dis.readInt()
        if (magic != 0xCAFEBABE.toInt()) return emptyList()

        // Version
        dis.readUnsignedShort() // minor
        dis.readUnsignedShort() // major

        // Constant pool
        val cpCount = dis.readUnsignedShort()
        val utf8s = mutableMapOf<Int, String>()
        val classInfos = mutableMapOf<Int, Int>() // index -> nameIndex
        val nameAndTypes = mutableMapOf<Int, Pair<Int, Int>>() // index -> (nameIndex, descriptorIndex)
        val memberRefs = mutableListOf<Triple<RefType, Int, Int>>() // (type, classIndex, natIndex)

        var i = 1
        while (i < cpCount) {
            val tag = dis.readUnsignedByte()
            when (tag) {
                TAG_UTF8 -> {
                    utf8s[i] = dis.readUTF()
                }
                TAG_INTEGER, TAG_FLOAT -> dis.readInt()
                TAG_LONG, TAG_DOUBLE -> {
                    dis.readLong()
                    i++ // 8-byte constants take two slots
                }
                TAG_CLASS -> classInfos[i] = dis.readUnsignedShort()
                TAG_STRING -> dis.readUnsignedShort()
                TAG_FIELDREF -> {
                    val classIdx = dis.readUnsignedShort()
                    val natIdx = dis.readUnsignedShort()
                    memberRefs.add(Triple(RefType.FIELD, classIdx, natIdx))
                }
                TAG_METHODREF -> {
                    val classIdx = dis.readUnsignedShort()
                    val natIdx = dis.readUnsignedShort()
                    memberRefs.add(Triple(RefType.METHOD, classIdx, natIdx))
                }
                TAG_INTERFACE_METHODREF -> {
                    val classIdx = dis.readUnsignedShort()
                    val natIdx = dis.readUnsignedShort()
                    memberRefs.add(Triple(RefType.INTERFACE_METHOD, classIdx, natIdx))
                }
                TAG_NAME_AND_TYPE -> {
                    val nameIdx = dis.readUnsignedShort()
                    val descIdx = dis.readUnsignedShort()
                    nameAndTypes[i] = nameIdx to descIdx
                }
                TAG_METHOD_HANDLE -> {
                    dis.readUnsignedByte()
                    dis.readUnsignedShort()
                }
                TAG_METHOD_TYPE -> dis.readUnsignedShort()
                TAG_DYNAMIC, TAG_INVOKE_DYNAMIC -> {
                    dis.readUnsignedShort()
                    dis.readUnsignedShort()
                }
                TAG_MODULE, TAG_PACKAGE -> dis.readUnsignedShort()
                else -> return emptyList() // Unknown tag, bail out safely
            }
            i++
        }

        // Resolve references
        return memberRefs.mapNotNull { (refType, classIdx, natIdx) ->
            val classNameIdx = classInfos[classIdx] ?: return@mapNotNull null
            val ownerInternal = utf8s[classNameIdx] ?: return@mapNotNull null
            val ownerClassName = ownerInternal.replace('/', '.')

            val (nameIdx, descIdx) = nameAndTypes[natIdx] ?: return@mapNotNull null
            val name = utf8s[nameIdx] ?: return@mapNotNull null
            val descriptor = utf8s[descIdx] ?: return@mapNotNull null

            MemberRef(refType, ownerClassName, name, descriptor)
        }
    }

    /**
     * Parse JVM method descriptor parameter section into Class objects.
     * e.g. "(Ljava/lang/String;IZ)V" -> [String::class.java, Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType]
     */
    fun parseDescriptorParams(descriptor: String, classLoader: ClassLoader): Array<Class<*>> {
        val params = mutableListOf<Class<*>>()
        val paramSection = descriptor.substringAfter('(').substringBefore(')')
        var idx = 0
        while (idx < paramSection.length) {
            when (paramSection[idx]) {
                'B' -> { params.add(Byte::class.javaPrimitiveType!!); idx++ }
                'C' -> { params.add(Char::class.javaPrimitiveType!!); idx++ }
                'D' -> { params.add(Double::class.javaPrimitiveType!!); idx++ }
                'F' -> { params.add(Float::class.javaPrimitiveType!!); idx++ }
                'I' -> { params.add(Int::class.javaPrimitiveType!!); idx++ }
                'J' -> { params.add(Long::class.javaPrimitiveType!!); idx++ }
                'S' -> { params.add(Short::class.javaPrimitiveType!!); idx++ }
                'Z' -> { params.add(Boolean::class.javaPrimitiveType!!); idx++ }
                'V' -> { params.add(Void::class.javaPrimitiveType!!); idx++ }
                'L' -> {
                    val end = paramSection.indexOf(';', idx)
                    val className = paramSection.substring(idx + 1, end).replace('/', '.')
                    params.add(Class.forName(className, false, classLoader))
                    idx = end + 1
                }
                '[' -> {
                    // Array type — find the element type and use Class.forName with JVM array notation
                    val start = idx
                    while (idx < paramSection.length && paramSection[idx] == '[') idx++
                    val arrayDesc = if (paramSection[idx] == 'L') {
                        val end = paramSection.indexOf(';', idx)
                        val desc = paramSection.substring(start, end + 1)
                        idx = end + 1
                        desc
                    } else {
                        val desc = paramSection.substring(start, idx + 1)
                        idx++
                        desc
                    }
                    params.add(Class.forName(arrayDesc.replace('/', '.'), false, classLoader))
                }
                else -> idx++ // Skip unknown
            }
        }
        return params.toTypedArray()
    }
}
