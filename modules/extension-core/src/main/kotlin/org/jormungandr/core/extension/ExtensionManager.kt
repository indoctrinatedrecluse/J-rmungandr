package org.jormungandr.core.extension

import com.intellij.openapi.Disposable
import kotlinx.coroutines.flow.StateFlow

/**
 * Service managing registration, state transitions, memory governance,
 * and deterministic teardown for all Jörmungandr extensions.
 */
interface ExtensionManager : Disposable {
    /** Observable stream of all registered extensions and their live instances */
    val loadedExtensions: StateFlow<Map<ExtensionId, JormungandrExtension>>

    /** Registers a new extension instance */
    fun registerExtension(extension: JormungandrExtension)

    /** Asynchronously initializes an extension with the specified context */
    suspend fun initializeExtension(id: ExtensionId, context: ExtensionContext): Result<Unit>

    /** Activates a previously initialized extension */
    suspend fun activateExtension(id: ExtensionId): Result<Unit>

    /** Pauses an active extension */
    suspend fun pauseExtension(id: ExtensionId): Result<Unit>

    /** Resumes a paused extension */
    suspend fun resumeExtension(id: ExtensionId): Result<Unit>

    /** Deactivates and cleanly tears down an extension */
    suspend fun deactivateExtension(id: ExtensionId): Result<Unit>

    /** Triggers memory trimming across all registered active extensions */
    suspend fun trimAllMemory(level: MemoryPressureLevel)

    /** Retrieves an extension by its identifier */
    fun getExtension(id: ExtensionId): JormungandrExtension?

    /** Retrieves current lifecycle state of an extension */
    fun getExtensionState(id: ExtensionId): ExtensionState
}
