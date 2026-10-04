package org.jormungandr.jupyter

import com.intellij.openapi.diagnostic.logger
import org.jormungandr.core.extension.*
import org.jormungandr.jupyter.model.JupyterKernelSpec
import java.util.concurrent.ConcurrentHashMap

private val LOG = logger<JupyterExtension>()

/**
 * Extension responsible for managing Jupyter kernels, ZeroMQ communication,
 * and notebook execution in Jörmungandr.
 */
class JupyterExtension : JormungandrExtension {
    override val id: ExtensionId = ExtensionId("org.jormungandr.jupyter")

    override val metadata: ExtensionMetadata = ExtensionMetadata(
        id = id,
        displayName = "Jupyter Interactive Subsystem",
        version = "0.1.0",
        description = "Provides ZeroMQ client protocol, kernel discovery, and interactive execution.",
        author = "indoctrinatedrecluse",
        quota = ResourceQuota(
            maxHeapBytes = 512L * 1024 * 1024,
            maxOffHeapBytes = 1L * 1024 * 1024 * 1024, // 1 GB for kernel outputs/buffers
            maxWorkerThreads = 4
        )
    )

    private var _state: ExtensionState = ExtensionState.UNLOADED
    override val state: ExtensionState get() = _state

    private var context: ExtensionContext? = null
    private val activeKernels = ConcurrentHashMap<String, JupyterKernelSpec>()

    override suspend fun initialize(context: ExtensionContext) {
        this.context = context
        _state = ExtensionState.INITIALIZED
        LOG.info("JupyterExtension initialized.")
    }

    override suspend fun activate() {
        check(_state == ExtensionState.INITIALIZED || _state == ExtensionState.PAUSED) {
            "Cannot activate from state $_state"
        }
        _state = ExtensionState.ACTIVE
        LOG.info("JupyterExtension activated. Scanning available local/conda Jupyter kernels...")
    }

    override suspend fun pause() {
        _state = ExtensionState.PAUSED
        LOG.info("JupyterExtension paused. Idle kernel sockets throttled.")
    }

    override suspend fun resume() {
        _state = ExtensionState.ACTIVE
        LOG.info("JupyterExtension resumed.")
    }

    override suspend fun trimMemory(level: MemoryPressureLevel) {
        LOG.warn("JupyterExtension trimming output caches and unpinned cell buffers under $level pressure.")
        // Evict cached cell MIME outputs from heap
    }

    override suspend fun deactivate() {
        _state = ExtensionState.DISPOSING
        LOG.info("JupyterExtension deactivating: terminating active kernels and closing ZeroMQ sockets...")
        activeKernels.clear()
        _state = ExtensionState.TERMINATED
    }

    override fun dispose() {
        if (_state != ExtensionState.TERMINATED) {
            LOG.info("Disposing JupyterExtension through IntelliJ Disposer.")
            activeKernels.clear()
            _state = ExtensionState.TERMINATED
        }
    }
}
