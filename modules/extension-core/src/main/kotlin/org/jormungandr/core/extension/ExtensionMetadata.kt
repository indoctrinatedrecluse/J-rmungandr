package org.jormungandr.core.extension

/**
 * Declared resource quotas for a modular extension.
 */
data class ResourceQuota(
    /** Maximum heap allocation limit in bytes before triggering proactive GC/trim */
    val maxHeapBytes: Long = 512L * 1024 * 1024, // 512 MB

    /** Maximum off-heap memory (e.g., Apache Arrow buffers, DuckDB cache) in bytes */
    val maxOffHeapBytes: Long = 2L * 1024 * 1024 * 1024, // 2 GB

    /** Maximum dedicated background worker threads */
    val maxWorkerThreads: Int = 4
)

/**
 * Descriptive metadata for extension identification, dependencies, and limits.
 */
data class ExtensionMetadata(
    val id: ExtensionId,
    val displayName: String,
    val version: String,
    val description: String,
    val author: String = "indoctrinatedrecluse",
    val supportedLanguages: List<String> = emptyList(),
    val associatedStacks: List<String> = emptyList(),
    val dependencies: Set<ExtensionId> = emptySet(),
    val quota: ResourceQuota = ResourceQuota()
)
