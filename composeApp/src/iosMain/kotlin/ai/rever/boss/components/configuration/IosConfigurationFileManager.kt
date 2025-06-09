package ai.rever.boss.components.configuration

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.*
import kotlinx.cinterop.ExperimentalForeignApi

/**
 * iOS implementation of ConfigurationFileManager
 */
@OptIn(ExperimentalForeignApi::class)
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
            
            if (!fileManager.fileExistsAtPath(configDirectory)) {
                fileManager.createDirectoryAtPath(
                    configDirectory,
                    withIntermediateDirectories = true,
                    attributes = null,
                    error = null
                )
            }
            
            fileManager.fileExistsAtPath(configDirectory)
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
            val nsString = NSString.create(string = json)
            
            // Write to file
            nsString.writeToFile(filePath, atomically = true, encoding = NSUTF8StringEncoding, error = null)
            
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
            
            val nsString = NSString.stringWithContentsOfFile(filePath, encoding = NSUTF8StringEncoding, error = null) ?: return@withContext null
            ConfigurationSerializer.deserialize(nsString.toString())
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

