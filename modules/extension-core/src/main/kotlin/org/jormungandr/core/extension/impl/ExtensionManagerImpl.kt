package org.jormungandr.core.extension.impl

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jormungandr.core.extension.*
import java.util.concurrent.ConcurrentHashMap

private val LOG = logger<ExtensionManagerImpl>()

/**
 * Production implementation of [ExtensionManager].
 *
 * Integrates directly with IntelliJ Platform lifecycle services and the [Disposer]
 * hierarchy for deterministic resource disposal and leak prevention.
 */
@Service(Service.Level.APP)
class ExtensionManagerImpl(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : ExtensionManager {

    private val mutex = Mutex()
    private val extensionMap = ConcurrentHashMap<ExtensionId, JormungandrExtension>()
    private val _loadedExtensions = MutableStateFlow<Map<ExtensionId, JormungandrExtension>>(emptyMap())
    override val loadedExtensions: StateFlow<Map<ExtensionId, JormungandrExtension>> = _loadedExtensions.asStateFlow()

    @Volatile
    private var isDisposed = false

    override fun registerExtension(extension: JormungandrExtension) {
        check(!isDisposed) { "ExtensionManager is already disposed." }
        extensionMap[extension.id] = extension
        // Register extension as child Disposable of ExtensionManager
        Disposer.register(this, extension)
        _loadedExtensions.value = extensionMap.toMap()
        LOG.info("Registered extension: ${extension.id.value} (v${extension.metadata.version})")
    }

    override suspend fun initializeExtension(id: ExtensionId, context: ExtensionContext): Result<Unit> =
        mutex.withLock {
            val ext = extensionMap[id] ?: return Result.failure(IllegalArgumentException("Extension not found: $id"))
            return runCatching {
                LOG.info("Initializing extension: ${id.value}")
                ext.initialize(context)
            }.onFailure { err ->
                LOG.error("Failed to initialize extension: ${id.value}", err)
            }
        }

    override suspend fun activateExtension(id: ExtensionId): Result<Unit> =
        mutex.withLock {
            val ext = extensionMap[id] ?: return Result.failure(IllegalArgumentException("Extension not found: $id"))
            return runCatching {
                LOG.info("Activating extension: ${id.value}")
                ext.activate()
            }.onFailure { err ->
                LOG.error("Failed to activate extension: ${id.value}", err)
            }
        }

    override suspend fun pauseExtension(id: ExtensionId): Result<Unit> =
        mutex.withLock {
            val ext = extensionMap[id] ?: return Result.failure(IllegalArgumentException("Extension not found: $id"))
            return runCatching {
                LOG.info("Pausing extension: ${id.value}")
                ext.pause()
            }
        }

    override suspend fun resumeExtension(id: ExtensionId): Result<Unit> =
        mutex.withLock {
            val ext = extensionMap[id] ?: return Result.failure(IllegalArgumentException("Extension not found: $id"))
            return runCatching {
                LOG.info("Resuming extension: ${id.value}")
                ext.resume()
            }
        }

    override suspend fun deactivateExtension(id: ExtensionId): Result<Unit> =
        mutex.withLock {
            val ext = extensionMap[id] ?: return Result.failure(IllegalArgumentException("Extension not found: $id"))
            return runCatching {
                LOG.info("Deactivating extension: ${id.value}")
                ext.deactivate()
            }.onFailure { err ->
                LOG.error("Error during deactivation of extension: ${id.value}", err)
            }
        }

    override suspend fun trimAllMemory(level: MemoryPressureLevel) {
        LOG.warn("Memory pressure detected ($level). Trimming active extension memory buffers...")
        for ((id, ext) in extensionMap) {
            if (ext.state == ExtensionState.ACTIVE || ext.state == ExtensionState.PAUSED) {
                runCatching {
                    ext.trimMemory(level)
                }.onFailure { err ->
                    LOG.error("Failed to trim memory for extension: ${id.value}", err)
                }
            }
        }
    }

    override fun getExtension(id: ExtensionId): JormungandrExtension? = extensionMap[id]

    override fun getExtensionState(id: ExtensionId): ExtensionState =
        extensionMap[id]?.state ?: ExtensionState.UNLOADED

    override fun dispose() {
        if (isDisposed) return
        isDisposed = true
        LOG.info("Disposing ExtensionManager and unmounting all child extensions...")

        // Cancel background coroutine scope
        scope.cancel()

        // Individual extensions registered via Disposer.register(this, ext)
        // are automatically disposed by IntelliJ's Disposer engine.
        extensionMap.clear()
        _loadedExtensions.value = emptyMap()
    }
}
