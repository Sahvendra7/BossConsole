package ai.rever.boss.plugin.dependency

import ai.rever.boss.plugin.api.PluginDependency
import ai.rever.boss.plugin.api.PluginManifest
import ai.rever.boss.plugin.logging.BossLogger
import ai.rever.boss.plugin.logging.LogCategory

/**
 * Result of dependency resolution.
 */
sealed class DependencyResolutionResult {
    /**
     * All dependencies resolved successfully.
     */
    data class Resolved(
        /**
         * Ordered list of plugins to load (topological order).
         */
        val loadOrder: List<String>,

        /**
         * Map of plugin ID to resolved version.
         */
        val resolvedVersions: Map<String, String>
    ) : DependencyResolutionResult()

    /**
     * Dependencies could not be resolved.
     */
    data class Failed(
        /**
         * Missing dependencies.
         */
        val missingDependencies: List<MissingDependency>,

        /**
         * Version conflicts.
         */
        val conflicts: List<DependencyConflict>,

        /**
         * Circular dependencies detected.
         */
        val circularDependencies: List<List<String>>
    ) : DependencyResolutionResult()

    val isResolved: Boolean get() = this is Resolved
}

/**
 * Information about a missing dependency.
 */
data class MissingDependency(
    val pluginId: String,
    val requiredBy: String,
    val requiredVersion: String,
    val isOptional: Boolean
)

/**
 * Information about a version conflict.
 */
data class DependencyConflict(
    val pluginId: String,
    val requestedVersions: List<RequestedVersion>
) {
    data class RequestedVersion(
        val version: String,
        val requiredBy: String
    )
}

/**
 * Dependency graph node.
 */
data class DependencyNode(
    val pluginId: String,
    val version: String,
    val dependencies: List<PluginDependency>
)

/**
 * Resolves plugin dependencies and determines load order.
 *
 * Features:
 * - Version range matching
 * - Topological sorting for load order
 * - Circular dependency detection
 * - Optional dependency handling
 */
class PluginDependencyResolver {
    private val logger = BossLogger.forComponent("PluginDependencyResolver")

    /**
     * Resolve dependencies for a set of plugins.
     *
     * @param plugins Plugins to resolve (ID to manifest)
     * @param installedPlugins Already installed plugins (ID to version)
     * @param availablePlugins Available plugins for dependency resolution (ID to versions)
     * @return Resolution result
     */
    fun resolve(
        plugins: Map<String, PluginManifest>,
        installedPlugins: Map<String, String> = emptyMap(),
        availablePlugins: Map<String, List<String>> = emptyMap()
    ): DependencyResolutionResult {
        val missingDependencies = mutableListOf<MissingDependency>()
        val conflicts = mutableListOf<DependencyConflict>()
        val resolvedVersions = mutableMapOf<String, String>()

        // Track which version each plugin requests for dependencies
        val versionRequests = mutableMapOf<String, MutableList<RequestedVersion>>()

        // Build dependency graph
        val graph = mutableMapOf<String, MutableSet<String>>()
        val nodes = mutableMapOf<String, DependencyNode>()

        // Add all plugins to resolve
        for ((pluginId, manifest) in plugins) {
            nodes[pluginId] = DependencyNode(
                pluginId = pluginId,
                version = manifest.version,
                dependencies = manifest.dependencies
            )
            graph[pluginId] = mutableSetOf()
            resolvedVersions[pluginId] = manifest.version
        }

        // Add installed plugins
        for ((pluginId, version) in installedPlugins) {
            if (pluginId !in nodes) {
                resolvedVersions[pluginId] = version
            }
        }

        // Resolve dependencies
        for ((pluginId, node) in nodes) {
            for (dep in node.dependencies) {
                val depId = dep.pluginId
                val versionRange = VersionRange.parse(dep.version)

                // Check if dependency is satisfied
                val satisfiedBy = when {
                    // Check in plugins being loaded
                    plugins.containsKey(depId) -> {
                        val depVersion = plugins[depId]!!.version
                        if (versionRange.satisfiedBy(depVersion)) depVersion else null
                    }
                    // Check in installed plugins
                    installedPlugins.containsKey(depId) -> {
                        val depVersion = installedPlugins[depId]!!
                        if (versionRange.satisfiedBy(depVersion)) depVersion else null
                    }
                    // Check in available plugins
                    availablePlugins.containsKey(depId) -> {
                        versionRange.bestMatch(availablePlugins[depId]!!)
                    }
                    else -> null
                }

                if (satisfiedBy != null) {
                    // Add to graph
                    graph.getOrPut(pluginId) { mutableSetOf() }.add(depId)

                    // Track version request
                    versionRequests.getOrPut(depId) { mutableListOf() }.add(
                        RequestedVersion(satisfiedBy, pluginId)
                    )

                    // Update resolved version
                    resolvedVersions[depId] = satisfiedBy
                } else if (!dep.optional) {
                    missingDependencies.add(MissingDependency(
                        pluginId = depId,
                        requiredBy = pluginId,
                        requiredVersion = dep.version,
                        isOptional = false
                    ))
                } else {
                    logger.debug(LogCategory.SYSTEM, "Optional dependency not available", mapOf(
                        "plugin" to pluginId,
                        "dependency" to depId
                    ))
                }
            }
        }

        // Check for version conflicts
        for ((depId, requests) in versionRequests) {
            val uniqueVersions = requests.map { it.version }.distinct()
            if (uniqueVersions.size > 1) {
                conflicts.add(DependencyConflict(
                    pluginId = depId,
                    requestedVersions = requests.distinctBy { it.version }
                ))
            }
        }

        // Detect circular dependencies
        val circularDependencies = detectCircularDependencies(graph)

        // Return failure if there are issues
        if (missingDependencies.any { !it.isOptional } ||
            conflicts.isNotEmpty() ||
            circularDependencies.isNotEmpty()
        ) {
            return DependencyResolutionResult.Failed(
                missingDependencies = missingDependencies,
                conflicts = conflicts,
                circularDependencies = circularDependencies
            )
        }

        // Compute topological order for loading
        val loadOrder = topologicalSort(graph, nodes.keys)

        return DependencyResolutionResult.Resolved(
            loadOrder = loadOrder,
            resolvedVersions = resolvedVersions
        )
    }

    /**
     * Detect circular dependencies in the graph.
     *
     * @param graph Dependency graph
     * @return List of circular dependency chains
     */
    private fun detectCircularDependencies(
        graph: Map<String, Set<String>>
    ): List<List<String>> {
        val cycles = mutableListOf<List<String>>()
        val visited = mutableSetOf<String>()
        val recursionStack = mutableSetOf<String>()
        val path = mutableListOf<String>()

        fun dfs(node: String): Boolean {
            visited.add(node)
            recursionStack.add(node)
            path.add(node)

            for (neighbor in graph[node] ?: emptySet()) {
                if (neighbor !in visited) {
                    if (dfs(neighbor)) {
                        return true
                    }
                } else if (neighbor in recursionStack) {
                    // Found a cycle
                    val cycleStart = path.indexOf(neighbor)
                    val cycle = path.subList(cycleStart, path.size).toList() + neighbor
                    cycles.add(cycle)
                }
            }

            path.removeLast()
            recursionStack.remove(node)
            return false
        }

        for (node in graph.keys) {
            if (node !in visited) {
                dfs(node)
            }
        }

        return cycles
    }

    /**
     * Perform topological sort for load order.
     *
     * @param graph Dependency graph
     * @param nodes All nodes to include
     * @return Topologically sorted list (dependencies first)
     */
    private fun topologicalSort(
        graph: Map<String, Set<String>>,
        nodes: Set<String>
    ): List<String> {
        val result = mutableListOf<String>()
        val visited = mutableSetOf<String>()
        val temp = mutableSetOf<String>()

        fun visit(node: String) {
            if (node in temp) return // Skip cycles
            if (node in visited) return

            temp.add(node)

            // Visit dependencies first
            for (dep in graph[node] ?: emptySet()) {
                visit(dep)
            }

            temp.remove(node)
            visited.add(node)
            result.add(node)
        }

        for (node in nodes) {
            visit(node)
        }

        return result
    }

    /**
     * Get the dependency tree for a plugin.
     *
     * @param pluginId The plugin ID
     * @param plugins Map of plugin ID to manifest
     * @param depth Maximum depth to traverse (0 = unlimited)
     * @return Dependency tree
     */
    fun getDependencyTree(
        pluginId: String,
        plugins: Map<String, PluginManifest>,
        installedPlugins: Map<String, String> = emptyMap(),
        depth: Int = 0
    ): DependencyTreeNode? {
        val manifest = plugins[pluginId] ?: return null

        return buildTreeNode(
            pluginId = pluginId,
            version = manifest.version,
            dependencies = manifest.dependencies,
            plugins = plugins,
            installedPlugins = installedPlugins,
            currentDepth = 0,
            maxDepth = depth,
            visited = mutableSetOf()
        )
    }

    private fun buildTreeNode(
        pluginId: String,
        version: String,
        dependencies: List<PluginDependency>,
        plugins: Map<String, PluginManifest>,
        installedPlugins: Map<String, String>,
        currentDepth: Int,
        maxDepth: Int,
        visited: MutableSet<String>
    ): DependencyTreeNode {
        val children = mutableListOf<DependencyTreeNode>()

        if (maxDepth == 0 || currentDepth < maxDepth) {
            if (pluginId !in visited) {
                visited.add(pluginId)

                for (dep in dependencies) {
                    val depManifest = plugins[dep.pluginId]
                    val depVersion = depManifest?.version ?: installedPlugins[dep.pluginId]

                    if (depVersion != null || !dep.optional) {
                        children.add(
                            buildTreeNode(
                                pluginId = dep.pluginId,
                                version = depVersion ?: "missing",
                                dependencies = depManifest?.dependencies ?: emptyList(),
                                plugins = plugins,
                                installedPlugins = installedPlugins,
                                currentDepth = currentDepth + 1,
                                maxDepth = maxDepth,
                                visited = visited
                            )
                        )
                    }
                }
            }
        }

        return DependencyTreeNode(
            pluginId = pluginId,
            version = version,
            children = children
        )
    }
}

/**
 * Node in a dependency tree.
 */
data class DependencyTreeNode(
    val pluginId: String,
    val version: String,
    val children: List<DependencyTreeNode>
) {
    /**
     * Pretty print the tree.
     */
    fun prettyPrint(indent: String = ""): String {
        val sb = StringBuilder()
        sb.appendLine("$indent$pluginId@$version")
        for ((index, child) in children.withIndex()) {
            val isLast = index == children.lastIndex
            val prefix = if (isLast) "└── " else "├── "
            val childIndent = if (isLast) "    " else "│   "
            sb.append(child.prettyPrint("$indent$childIndent").replaceFirst(childIndent, prefix))
        }
        return sb.toString()
    }
}

typealias RequestedVersion = DependencyConflict.RequestedVersion
