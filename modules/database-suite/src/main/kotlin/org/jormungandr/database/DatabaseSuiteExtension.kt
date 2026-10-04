package org.jormungandr.database

import com.intellij.openapi.diagnostic.logger
import org.jormungandr.core.extension.*
import org.jormungandr.database.model.ConnectionConfig
import java.util.concurrent.ConcurrentHashMap

private val LOG = logger<DatabaseSuiteExtension>()

/**
 * Extension providing SQL & NoSQL connectivity, schema introspection,
 * and query execution for Jörmungandr.
 */
class DatabaseSuiteExtension : JormungandrExtension {
    override val id: ExtensionId = ExtensionId("org.jormungandr.database")

    override val metadata: ExtensionMetadata = ExtensionMetadata(
        id = id,
        displayName = "Database & Query Studio",
        version = "0.1.0",
        description = "Provides unified SQL & NoSQL connection management, schema browsing, and query runners.",
        author = "indoctrinatedrecluse",
        quota = ResourceQuota(
            maxHeapBytes = 512L * 1024 * 1024,
            maxOffHeapBytes = 2L * 1024 * 1024 * 1024, // 2 GB for DuckDB / query result cache
            maxWorkerThreads = 8
        )
    )

    private var _state: ExtensionState = ExtensionState.UNLOADED
    override val state: ExtensionState get() = _state

    private var context: ExtensionContext? = null
    private val activeConnections = ConcurrentHashMap<String, ConnectionConfig>()

    override suspend fun initialize(context: ExtensionContext) {
        this.context = context
        _state = ExtensionState.INITIALIZED
        LOG.info("DatabaseSuiteExtension initialized.")
    }

    override suspend fun activate() {
        check(_state == ExtensionState.INITIALIZED || _state == ExtensionState.PAUSED) {
            "Cannot activate from state $_state"
        }
        _state = ExtensionState.ACTIVE
        LOG.info("DatabaseSuiteExtension activated. Ready to connect to data sources.")
    }

    override suspend fun pause() {
        _state = ExtensionState.PAUSED
        LOG.info("DatabaseSuiteExtension paused. Connection pools suspended to idle.")
    }

    override suspend fun resume() {
        _state = ExtensionState.ACTIVE
        LOG.info("DatabaseSuiteExtension resumed.")
    }

    override suspend fun trimMemory(level: MemoryPressureLevel) {
        LOG.warn("DatabaseSuiteExtension trimming cached schema metadata and query buffers under $level pressure.")
        // Clear cached schemas and close idle connection pool handles
    }

    override suspend fun deactivate() {
        _state = ExtensionState.DISPOSING
        LOG.info("DatabaseSuiteExtension deactivating: closing all active database connections...")
        activeConnections.clear()
        _state = ExtensionState.TERMINATED
    }

    override fun dispose() {
        if (_state != ExtensionState.TERMINATED) {
            LOG.info("Disposing DatabaseSuiteExtension through IntelliJ Disposer.")
            activeConnections.clear()
            _state = ExtensionState.TERMINATED
        }
    }
}
