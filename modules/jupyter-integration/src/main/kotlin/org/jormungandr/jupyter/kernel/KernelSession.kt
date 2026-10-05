package org.jormungandr.jupyter.kernel

import org.jormungandr.jupyter.model.CellOutput
import org.jormungandr.jupyter.model.JupyterKernelSpec
import kotlinx.coroutines.flow.StateFlow

/**
 * Execution and connection status of a Jupyter kernel.
 */
enum class KernelStatus(val displayName: String, val isRunning: Boolean) {
    DISCONNECTED("Disconnected", false),
    STARTING("Starting...", true),
    IDLE("Idle", true),
    BUSY("Busy", true),
    RESTARTING("Restarting...", true),
    DEAD("Dead / Error", false)
}

/**
 * Result of a single cell execution.
 */
data class ExecutionResult(
    val executionCount: Int,
    val isSuccess: Boolean,
    val outputs: List<CellOutput> = emptyList(),
    val durationMs: Long = 0L
)

/**
 * Contract for managing live execution against a running Jupyter kernel.
 */
interface KernelSession {
    val id: String
    val spec: JupyterKernelSpec
    val status: StateFlow<KernelStatus>

    suspend fun start(): Boolean
    suspend fun execute(code: String, onOutput: (CellOutput) -> Unit = {}): ExecutionResult
    suspend fun interrupt()
    suspend fun restart(): Boolean
    suspend fun shutdown()
}
