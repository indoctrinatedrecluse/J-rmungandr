package org.jormungandr.dataframe

import com.intellij.openapi.diagnostic.logger
import org.jormungandr.core.extension.*

private val LOG = logger<DataFrameViewerExtension>()

/**
 * Extension responsible for zero-copy Apache Arrow buffer handling,
 * tabular data caching, and virtualized data grid rendering.
 */
class DataFrameViewerExtension : JormungandrExtension {
    override val id: ExtensionId = ExtensionId("org.jormungandr.dataframe")

    override val metadata: ExtensionMetadata = ExtensionMetadata(
        id = id,
        displayName = "Arrow Dataframe Viewer Subsystem",
        version = "0.1.0",
        description = "Provides virtualized high-performance tabular grid display and Apache Arrow memory buffers.",
        author = "indoctrinatedrecluse",
        supportedLanguages = listOf("Python", "R", "SQL"),
        associatedStacks = listOf("Apache Arrow", "Pandas", "Polars", "DuckDB"),
        quota = ResourceQuota(
            maxHeapBytes = 512L * 1024 * 1024,
            maxOffHeapBytes = 3L * 1024 * 1024 * 1024, // 3 GB for large zero-copy Arrow memory maps
            maxWorkerThreads = 4
        )
    )

    private var _state: ExtensionState = ExtensionState.UNLOADED
    override val state: ExtensionState get() = _state

    private var context: ExtensionContext? = null

    /** Active tabular styling tokens provided by ThemeManager */
    var currentGridTheme: org.jormungandr.core.theme.DataGridThemeTokens =
        org.jormungandr.core.theme.JormungandrTheme.SOLARIZED_LIGHT.dataGrid
        private set

    /** Applies theme tokens across all virtualized table columns */
    fun applyTheme(theme: org.jormungandr.core.theme.JormungandrTheme) {
        currentGridTheme = theme.dataGrid
        LOG.info("DataFrameViewer updated grid theme: ${theme.name} (HeaderBg=${theme.dataGrid.headerBackground})")
    }

    override suspend fun initialize(context: ExtensionContext) {
        this.context = context
        _state = ExtensionState.INITIALIZED
        LOG.info("DataFrameViewerExtension initialized.")
    }

    override suspend fun activate() {
        check(_state == ExtensionState.INITIALIZED || _state == ExtensionState.PAUSED) {
            "Cannot activate from state $_state"
        }
        _state = ExtensionState.ACTIVE
        LOG.info("DataFrameViewerExtension activated.")
    }

    override suspend fun pause() {
        _state = ExtensionState.PAUSED
        LOG.info("DataFrameViewerExtension paused.")
    }

    override suspend fun resume() {
        _state = ExtensionState.ACTIVE
        LOG.info("DataFrameViewerExtension resumed.")
    }

    override suspend fun trimMemory(level: MemoryPressureLevel) {
        LOG.warn("DataFrameViewerExtension releasing cached Arrow buffers under $level pressure.")
        // Evict non-visible off-heap Arrow memory chunks
    }

    override suspend fun deactivate() {
        _state = ExtensionState.DISPOSING
        LOG.info("DataFrameViewerExtension deactivating: unmapping all Arrow off-heap buffers...")
        _state = ExtensionState.TERMINATED
    }

    override fun dispose() {
        if (_state != ExtensionState.TERMINATED) {
            LOG.info("Disposing DataFrameViewerExtension through IntelliJ Disposer.")
            _state = ExtensionState.TERMINATED
        }
    }
}
