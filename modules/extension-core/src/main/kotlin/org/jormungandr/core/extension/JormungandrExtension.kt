package org.jormungandr.core.extension

import com.intellij.openapi.Disposable

/**
 * Base contract for all modular extensions in Jörmungandr.
 *
 * Implements [Disposable] to ensure deterministic garbage collection
 * and teardown through the IntelliJ Platform [com.intellij.openapi.util.Disposer] hierarchy.
 */
interface JormungandrExtension : Disposable {
    /** Unique extension identifier */
    val id: ExtensionId

    /** Metadata, versioning, author, and resource limits */
    val metadata: ExtensionMetadata

    /** Current lifecycle state */
    val state: ExtensionState

    /**
     * Initializes the extension within the given execution context.
     * Called asynchronously from a background dispatcher.
     */
    suspend fun initialize(context: ExtensionContext)

    /**
     * Activates the extension when the containing project becomes active or
     * when the tool window is accessed by the user.
     */
    suspend fun activate()

    /**
     * Temporarily suspends background polling, timers, and non-critical workers.
     */
    suspend fun pause()

    /**
     * Resumes execution after a pause.
     */
    suspend fun resume()

    /**
     * Reacts to JVM/off-heap memory pressure by evicting in-memory caches,
     * closing idle connections, or unmapping unused Arrow buffers.
     */
    suspend fun trimMemory(level: MemoryPressureLevel)

    /**
     * Initiates graceful shutdown: drains active query queues, cancels coroutines,
     * terminates worker threads, and releases network/ZeroMQ sockets.
     */
    suspend fun deactivate()

    /**
     * Mandatory cleanup callback from IntelliJ [Disposable].
     * Must guarantee zero dangling threads, processes, or native handles.
     */
    override fun dispose()
}
