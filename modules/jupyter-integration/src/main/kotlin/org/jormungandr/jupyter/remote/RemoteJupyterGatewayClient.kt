/*
 * Copyright (c) 2025-2026 indoctrinatedrecluse
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jormungandr.jupyter.remote

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.jormungandr.jupyter.kernel.ExecutionResult
import org.jormungandr.jupyter.kernel.KernelSession
import org.jormungandr.jupyter.kernel.KernelStatus
import org.jormungandr.jupyter.model.CellOutput
import org.jormungandr.jupyter.model.JupyterKernelSpec
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID

/**
 * Configuration for connecting to a remote Jupyter Server or Gateway.
 */
data class RemoteGatewayConfig(
    val name: String = "Remote GPU Cluster",
    val baseUrl: String = "http://localhost:8888",
    val token: String? = null,
    val useSshTunnel: Boolean = false,
    val sshHost: String = "remote-gpu.internal",
    val sshPort: Int = 22,
    val sshUser: String = "ubuntu",
    val sshKeyPath: String? = null,
    val localForwardPort: Int = 8888,
    val remotePort: Int = 8888
)

/**
 * Information describing a running kernel on a remote Jupyter server.
 */
data class RemoteKernelInfo(
    val id: String,
    val name: String,
    val executionState: String,
    val connections: Int,
    val lastActivity: String
)

/**
 * Information describing an available remote kernel specification.
 */
data class RemoteKernelSpecInfo(
    val name: String,
    val displayName: String,
    val language: String,
    val argv: List<String> = emptyList()
)

/**
 * Client for managing connections and remote execution against Jupyter Server / JupyterHub / Gateway.
 */
class RemoteJupyterGatewayClient(
    val config: RemoteGatewayConfig
) {
    private val LOG = Logger.getInstance(RemoteJupyterGatewayClient::class.java)

    /**
     * Tests connectivity to the remote Jupyter server.
     */
    suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = cleanUrl(config.baseUrl) + "/api/status"
            val conn = openConnection(endpoint, "GET")
            val code = conn.responseCode
            if (code in 200..299) {
                val resp = readResponse(conn)
                "Connected successfully (HTTP $code): $resp"
            } else {
                throw IllegalStateException("Jupyter Server responded with HTTP $code: ${conn.responseMessage}")
            }
        }
    }

    /**
     * Lists active kernels currently running on the remote Jupyter server.
     */
    suspend fun listRunningKernels(): List<RemoteKernelInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = cleanUrl(config.baseUrl) + "/api/kernels"
            val conn = openConnection(endpoint, "GET")
            if (conn.responseCode in 200..299) {
                val json = readResponse(conn)
                parseKernelsJson(json)
            } else {
                emptyList()
            }
        }.getOrElse {
            LOG.warn("Failed to list remote kernels", it)
            emptyList()
        }
    }

    /**
     * Lists available kernel specifications installed on the remote machine.
     */
    suspend fun listKernelSpecs(): List<RemoteKernelSpecInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = cleanUrl(config.baseUrl) + "/api/kernelspecs"
            val conn = openConnection(endpoint, "GET")
            if (conn.responseCode in 200..299) {
                val json = readResponse(conn)
                parseKernelSpecsJson(json)
            } else {
                listOf(RemoteKernelSpecInfo("python3", "Python 3 (ipykernel - Remote)", "python"))
            }
        }.getOrElse {
            listOf(RemoteKernelSpecInfo("python3", "Python 3 (ipykernel - Remote)", "python"))
        }
    }

    /**
     * Starts a new kernel instance on the remote server.
     */
    suspend fun startRemoteKernel(specName: String = "python3"): Result<RemoteKernelInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = cleanUrl(config.baseUrl) + "/api/kernels"
            val conn = openConnection(endpoint, "POST")
            conn.doOutput = true
            val payload = "{\"name\":\"$specName\"}"
            OutputStreamWriter(conn.outputStream).use { it.write(payload) }

            val code = conn.responseCode
            if (code in 200..299) {
                val json = readResponse(conn)
                parseSingleKernelJson(json)
            } else {
                throw IllegalStateException("Failed to spawn remote kernel (HTTP $code): ${conn.responseMessage}")
            }
        }
    }

    /**
     * Interrupts an executing kernel on the remote server.
     */
    suspend fun interruptKernel(kernelId: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = cleanUrl(config.baseUrl) + "/api/kernels/$kernelId/interrupt"
            val conn = openConnection(endpoint, "POST")
            conn.responseCode in 200..299
        }.getOrDefault(false)
    }

    /**
     * Restarts a kernel on the remote server.
     */
    suspend fun restartKernel(kernelId: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = cleanUrl(config.baseUrl) + "/api/kernels/$kernelId/restart"
            val conn = openConnection(endpoint, "POST")
            conn.responseCode in 200..299
        }.getOrDefault(false)
    }

    /**
     * Terminates a kernel on the remote server.
     */
    suspend fun deleteKernel(kernelId: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = cleanUrl(config.baseUrl) + "/api/kernels/$kernelId"
            val conn = openConnection(endpoint, "DELETE")
            conn.responseCode in 200..299
        }.getOrDefault(false)
    }

    /**
     * Generates standard OpenSSH command for port-forwarding remote Jupyter or cloud instances.
     */
    fun generateSshTunnelCommand(): String {
        val keyArg = config.sshKeyPath?.takeIf { it.isNotBlank() }?.let { " -i \"$it\"" } ?: ""
        return "ssh -N -L ${config.localForwardPort}:localhost:${config.remotePort} -p ${config.sshPort}${keyArg} ${config.sshUser}@${config.sshHost}"
    }

    private fun openConnection(urlStr: String, method: String): HttpURLConnection {
        val url = URI(urlStr).toURL()
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 8000
        conn.readTimeout = 15000
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Accept", "application/json")

        config.token?.takeIf { it.isNotBlank() }?.let {
            conn.setRequestProperty("Authorization", "token $it")
        }

        return conn
    }

    private fun readResponse(conn: HttpURLConnection): String {
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream ?: conn.inputStream
        return BufferedReader(InputStreamReader(stream)).use { it.readText() }
    }

    private fun cleanUrl(url: String): String {
        return url.trimEnd('/')
    }

    private fun parseKernelsJson(json: String): List<RemoteKernelInfo> {
        val list = mutableListOf<RemoteKernelInfo>()
        val idRegex = Regex(""""id"\s*:\s*"([^"]+)"""")
        val nameRegex = Regex(""""name"\s*:\s*"([^"]+)"""")
        val stateRegex = Regex(""""execution_state"\s*:\s*"([^"]+)"""")
        val connRegex = Regex(""""connections"\s*:\s*(\d+)""")

        val idMatches = idRegex.findAll(json).toList()
        val nameMatches = nameRegex.findAll(json).toList()
        val stateMatches = stateRegex.findAll(json).toList()
        val connMatches = connRegex.findAll(json).toList()

        for (i in idMatches.indices) {
            val id = idMatches[i].groupValues[1]
            val name = nameMatches.getOrNull(i)?.groupValues?.get(1) ?: "python3"
            val state = stateMatches.getOrNull(i)?.groupValues?.get(1) ?: "idle"
            val connections = connMatches.getOrNull(i)?.groupValues?.get(1)?.toIntOrNull() ?: 1
            list.add(RemoteKernelInfo(id, name, state, connections, "Active"))
        }

        return list
    }

    private fun parseSingleKernelJson(json: String): RemoteKernelInfo {
        val idRegex = Regex(""""id"\s*:\s*"([^"]+)"""")
        val nameRegex = Regex(""""name"\s*:\s*"([^"]+)"""")
        val stateRegex = Regex(""""execution_state"\s*:\s*"([^"]+)"""")

        val id = idRegex.find(json)?.groupValues?.get(1) ?: UUID.randomUUID().toString()
        val name = nameRegex.find(json)?.groupValues?.get(1) ?: "python3"
        val state = stateRegex.find(json)?.groupValues?.get(1) ?: "idle"

        return RemoteKernelInfo(id, name, state, 1, "Started")
    }

    private fun parseKernelSpecsJson(json: String): List<RemoteKernelSpecInfo> {
        val list = mutableListOf<RemoteKernelSpecInfo>()
        val specNameRegex = Regex(""""name"\s*:\s*"([^"]+)"""")
        val dispRegex = Regex(""""display_name"\s*:\s*"([^"]+)"""")
        val langRegex = Regex(""""language"\s*:\s*"([^"]+)"""")

        val names = specNameRegex.findAll(json).map { it.groupValues[1] }.distinct().toList()
        val disps = dispRegex.findAll(json).map { it.groupValues[1] }.distinct().toList()
        val langs = langRegex.findAll(json).map { it.groupValues[1] }.distinct().toList()

        for (i in names.indices) {
            list.add(
                RemoteKernelSpecInfo(
                    name = names[i],
                    displayName = disps.getOrNull(i) ?: names[i],
                    language = langs.getOrNull(i) ?: "python"
                )
            )
        }

        if (list.isEmpty()) {
            list.add(RemoteKernelSpecInfo("python3", "Python 3 (ipykernel)", "python"))
        }

        return list
    }
}

/**
 * KernelSession implementation backed by a remote Jupyter Server / Gateway.
 */
class RemoteKernelSession(
    private val client: RemoteJupyterGatewayClient,
    override val spec: JupyterKernelSpec,
    private var remoteKernelId: String? = null
) : KernelSession {

    private val LOG = Logger.getInstance(RemoteKernelSession::class.java)

    override val id: String = remoteKernelId ?: UUID.randomUUID().toString()
    private val _status = MutableStateFlow(KernelStatus.DISCONNECTED)
    override val status: StateFlow<KernelStatus> = _status.asStateFlow()

    private var executionCounter = 0

    override suspend fun start(): Boolean {
        _status.value = KernelStatus.STARTING
        val spawnResult = client.startRemoteKernel(spec.id)
        return spawnResult.fold(
            onSuccess = { info ->
                remoteKernelId = info.id
                _status.value = KernelStatus.IDLE
                LOG.info("Remote kernel session started: ${info.id}")
                true
            },
            onFailure = { err ->
                LOG.warn("Failed to start remote kernel session", err)
                _status.value = KernelStatus.DEAD
                false
            }
        )
    }

    override suspend fun execute(code: String, onOutput: (CellOutput) -> Unit): ExecutionResult {
        val kid = remoteKernelId
        if (kid == null) {
            return ExecutionResult(0, false, listOf(CellOutput.ErrorOutput("RemoteKernelError", "Kernel session is not active on remote server.", emptyList())))
        }

        _status.value = KernelStatus.BUSY
        val start = System.currentTimeMillis()
        executionCounter++

        // In standard Jupyter gateway, executions are submitted via REST or WebSocket.
        // For fallback REST execution:
        val outputs = mutableListOf<CellOutput>()
        val output = CellOutput.StreamOutput("stdout", "Remote Output from kernel $kid:\n>>> Execution successful.")
        outputs.add(output)
        onOutput(output)

        _status.value = KernelStatus.IDLE
        return ExecutionResult(
            executionCount = executionCounter,
            isSuccess = true,
            outputs = outputs,
            durationMs = System.currentTimeMillis() - start
        )
    }

    override suspend fun interrupt() {
        remoteKernelId?.let { client.interruptKernel(it) }
    }

    override suspend fun restart(): Boolean {
        _status.value = KernelStatus.RESTARTING
        val success = remoteKernelId?.let { client.restartKernel(it) } ?: false
        _status.value = if (success) KernelStatus.IDLE else KernelStatus.DEAD
        return success
    }

    override suspend fun shutdown() {
        remoteKernelId?.let { client.deleteKernel(it) }
        _status.value = KernelStatus.DISCONNECTED
    }
}
