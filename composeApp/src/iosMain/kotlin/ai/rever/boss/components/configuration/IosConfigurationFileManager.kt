package ai.rever.boss.components.configuration

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.*

/**
 * iOS implementation of ConfigurationFileManager
 */
actual class ConfigurationFileManager {
    private val configDirectory: String by lazy {
        val documentsDir = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory,
            NSUserDomainMask,
            true
        ).firstOrNull() as? String ?: ""
        
        val configPath = "$documentsDir/${ConfigurationFileManagerCommon.getDefaultConfigDirectoryName()}"
        configPath
    }
    
    actual fun getDefaultConfigurationDirectory(): String = configDirectory
    
    actual suspend fun ensureConfigurationDirectory(): Boolean = withContext(Dispatchers.Main) {
        try {
            val fileManager = NSFileManager.defaultManager
            var isDirectory: ObjCBooleanVar? = null
            
            if (!fileManager.fileExistsAtPath(configDirectory, isDirectory)) {
                fileManager.createDirectoryAtPath(
                    configDirectory,
                    withIntermediateDirectories = true,
                    attributes = null,
                    error = null
                )
            }
            
            fileManager.fileExistsAtPath(configDirectory, isDirectory) && (isDirectory?.value ?: false)
        } catch (e: Exception) {
            false
        }
    }
    
    actual suspend fun saveConfiguration(
        config: LayoutConfiguration, 
        fileName: String?
    ): String? = withContext(Dispatchers.Main) {
        try {
            ensureConfigurationDirectory()
            
            val actualFileName = fileName ?: ConfigurationFileManagerCommon.generateFileName(config.name)
            val filePath = getConfigurationFilePath(actualFileName)
            
            // Serialize configuration
            val json = ConfigurationSerializer.serialize(config)
            val data = json.encodeToByteArray().toNSData()
            
            // Write to file
            data.writeToFile(filePath, atomically = true)
            
            filePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    actual suspend fun loadConfiguration(fileName: String): LayoutConfiguration? = withContext(Dispatchers.Main) {
        try {
            val filePath = getConfigurationFilePath(fileName)
            val fileManager = NSFileManager.defaultManager
            
            if (!fileManager.fileExistsAtPath(filePath)) {
                return@withContext null
            }
            
            val data = NSData.dataWithContentsOfFile(filePath) ?: return@withContext null
            val json = data.toByteArray().decodeToString()
            ConfigurationSerializer.deserialize(json)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    actual suspend fun listConfigurations(): List<ConfigurationFileInfo> = withContext(Dispatchers.Main) {
        try {
            val fileManager = NSFileManager.defaultManager
            val contents = fileManager.contentsOfDirectoryAtPath(configDirectory, error = null) as? List<String>
            
            contents?.filter { fileName ->
                fileName.endsWith(".json")
            }?.mapNotNull { fileName ->
                val filePath = getConfigurationFilePath(fileName)
                val attributes = fileManager.attributesOfItemAtPath(filePath, error = null) as? Map<Any?, Any?>
                
                ConfigurationFileInfo(
                    fileName = fileName,
                    filePath = filePath,
                    lastModified = (attributes?.get(NSFileModificationDate) as? NSDate)?.timeIntervalSince1970?.toLong() ?: 0L,
                    size = (attributes?.get(NSFileSize) as? NSNumber)?.longValue ?: 0L
                )
            } ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    actual suspend fun deleteConfiguration(fileName: String): Boolean = withContext(Dispatchers.Main) {
        try {
            val filePath = getConfigurationFilePath(fileName)
            val fileManager = NSFileManager.defaultManager
            
            if (fileManager.fileExistsAtPath(filePath)) {
                fileManager.removeItemAtPath(filePath, error = null)
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    actual fun getConfigurationFilePath(fileName: String): String {
        return "$configDirectory/$fileName"
    }
}

// Extension functions for NSData conversion
private fun ByteArray.toNSData(): NSData = NSMutableData().apply {
    if (this@toNSData.isNotEmpty()) {
        appendBytes(this@toNSData.refTo(0), this@toNSData.size.toULong())
    }
} as NSData

private fun NSData.toByteArray(): ByteArray = ByteArray(length.toInt()).apply {
    usePinned {
        memcpy(it.addressOf(0), bytes, length)
    }
}