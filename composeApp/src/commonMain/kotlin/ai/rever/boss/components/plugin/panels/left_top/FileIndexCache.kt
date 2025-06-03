package ai.rever.boss.components.plugin.panels.left_top

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

/**
 * LRU cache for file system nodes with dynamic loading
 */
class FileIndexCache(
    private val maxSize: Int = 1000,
    private val maxDepthInitial: Int = 2,
    private val maxDepthExpanded: Int = 5
) {
    private val cache = mutableMapOf<String, CachedNode>()
    private val accessOrder = mutableListOf<String>()
    private val mutex = Mutex()
    
    data class CachedNode(
        val node: FileNode,
        var lastAccessed: Long = Clock.System.now().epochSeconds,
        var isFullyLoaded: Boolean = false,
        var loadDepth: Int = 0
    )
    
    suspend fun getNode(path: String, forceReload: Boolean = false): FileNode? = mutex.withLock {
        if (!forceReload) {
            cache[path]?.let { cached ->
                // Update access order
                accessOrder.remove(path)
                accessOrder.add(0, path)
                cached.lastAccessed = Clock.System.now().epochSeconds
                return cached.node
            }
        }
        
        // Load node from file system
        val node = scanDirectory(path)
        
        node?.let {
            addToCache(path, it, maxDepthInitial)
        }
        
        return node
    }
    
    suspend fun expandNode(path: String): FileNode? = mutex.withLock {
        val cached = cache[path] ?: return null
        
        // If already fully loaded, return
        if (cached.isFullyLoaded || cached.loadDepth >= maxDepthExpanded) {
            return cached.node
        }
        
        // Reload with deeper scan
        val expandedNode = scanDirectoryWithDepth(path, maxDepth = maxDepthExpanded, startDepth = 0)
        
        expandedNode?.let {
            // Replace the cached node with the expanded one
            cache[path] = CachedNode(
                node = it,
                lastAccessed = Clock.System.now().epochSeconds,
                isFullyLoaded = true,
                loadDepth = maxDepthExpanded
            )
            return it
        }
        
        return cached.node
    }
    
    private fun addToCache(path: String, node: FileNode, depth: Int) {
        // Evict old entries if needed
        while (cache.size >= maxSize && accessOrder.isNotEmpty()) {
            val oldestPath = accessOrder.removeLast()
            cache.remove(oldestPath)
        }
        
        cache[path] = CachedNode(node, loadDepth = depth)
        accessOrder.add(0, path)
        
        // Also cache child directories for quick access
        if (node.isDirectory && depth > 0) {
            node.children.forEach { child ->
                if (child.isDirectory && !cache.containsKey(child.path)) {
                    addToCache(child.path, child, depth - 1)
                }
            }
        }
    }
    
    suspend fun clearCache() = mutex.withLock {
        cache.clear()
        accessOrder.clear()
    }
    
    suspend fun getCacheStats(): CacheStats = mutex.withLock {
        CacheStats(
            totalNodes = cache.size,
            fullyLoadedNodes = cache.values.count { it.isFullyLoaded },
            averageDepth = cache.values.map { it.loadDepth }.average().toFloat()
        )
    }
    
    data class CacheStats(
        val totalNodes: Int,
        val fullyLoadedNodes: Int,
        val averageDepth: Float
    )
}


// Platform-specific implementation with depth control
expect suspend fun scanDirectoryWithDepth(path: String, maxDepth: Int, startDepth: Int): FileNode?