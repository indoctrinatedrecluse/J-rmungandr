package org.jormungandr.jupyter.kernel

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import org.jormungandr.jupyter.model.JupyterKernelSpec
import java.util.concurrent.ConcurrentHashMap

private val LOG = logger<JupyterKernelService>()

/**
 * Application service managing live Jupyter kernel sessions across all open notebooks.
 */
@Service(Service.Level.APP)
class JupyterKernelService : Disposable {

    private val sessions = ConcurrentHashMap<String, KernelSession>()

    /**
     * Obtains or creates a running kernel session for the given notebook key and kernel specification.
     */
    suspend fun getOrCreateSession(
        notebookKey: String,
        spec: JupyterKernelSpec = KernelDiscovery.discoverKernels().firstOrNull() ?: JupyterKernelSpec("python3", "Python 3", "python", listOf("python", "-m", "ipykernel_launcher", "-f", "{connection_file}")),
        preferZmq: Boolean = true
    ): KernelSession {
        val existing = sessions[notebookKey]
        if (existing != null && existing.status.value.isRunning) {
            return existing
        }

        var session: KernelSession? = null
        if (preferZmq && KernelDiscovery.hasIpykernel()) {
            try {
                LOG.info("Attempting ZmqKernelSession for notebook: $notebookKey")
                val zmq = ZmqKernelSession(spec)
                zmq.start()
                session = zmq
            } catch (e: Exception) {
                LOG.warn("Failed to start ZmqKernelSession, falling back to SubprocessPythonSession: ${e.message}", e)
            }
        }

        if (session == null) {
            LOG.info("Creating SubprocessPythonSession for notebook: $notebookKey")
            val fallback = SubprocessPythonSession(spec)
            fallback.start()
            session = fallback
        }

        sessions[notebookKey] = session
        return session
    }

    /**
     * Retrieves an active session if one exists.
     */
    fun getSession(notebookKey: String): KernelSession? = sessions[notebookKey]

    /**
     * Lists all currently managed kernel sessions.
     */
    fun getActiveSessions(): List<KernelSession> = sessions.values.toList()

    /**
     * Closes and cleans up a specific notebook kernel session.
     */
    suspend fun shutdownSession(notebookKey: String) {
        val session = sessions.remove(notebookKey) ?: return
        LOG.info("Shutting down kernel session for notebook: $notebookKey")
        session.shutdown()
    }

    override fun dispose() {
        LOG.info("Disposing JupyterKernelService: terminating all active kernel sessions...")
        for ((key, session) in sessions) {
            runCatching {
                kotlinx.coroutines.runBlocking {
                    session.shutdown()
                }
            }
        }
        sessions.clear()
    }
}
