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

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.jormungandr.jupyter.kernel.KernelStatus
import org.jormungandr.jupyter.model.JupyterKernelSpec
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress

class RemoteJupyterGatewayClientTest {

    companion object {
        private var mockServer: HttpServer? = null
        private var serverPort: Int = 0

        @BeforeAll
        @JvmStatic
        fun setupServer() {
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            serverPort = server.address.port

            // /api/status
            server.createContext("/api/status") { exchange ->
                val body = """{"started": "2026-10-07T12:00:00Z", "last_activity": "2026-10-07T12:05:00Z", "kernels": 1, "connections": 2}"""
                val bytes = body.toByteArray(Charsets.UTF_8)
                exchange.responseHeaders.set("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }

            // /api/kernels
            server.createContext("/api/kernels") { exchange ->
                if (exchange.requestMethod == "GET") {
                    val body = """[{"id": "k-12345", "name": "python3", "execution_state": "idle", "connections": 2}]"""
                    val bytes = body.toByteArray(Charsets.UTF_8)
                    exchange.responseHeaders.set("Content-Type", "application/json")
                    exchange.sendResponseHeaders(200, bytes.size.toLong())
                    exchange.responseBody.use { it.write(bytes) }
                } else if (exchange.requestMethod == "POST") {
                    val body = """{"id": "k-99999", "name": "python3", "execution_state": "starting", "connections": 1}"""
                    val bytes = body.toByteArray(Charsets.UTF_8)
                    exchange.responseHeaders.set("Content-Type", "application/json")
                    exchange.sendResponseHeaders(201, bytes.size.toLong())
                    exchange.responseBody.use { it.write(bytes) }
                } else {
                    exchange.sendResponseHeaders(405, -1)
                }
            }

            // /api/kernelspecs
            server.createContext("/api/kernelspecs") { exchange ->
                val body = """{"default": "python3", "kernelspecs": {"python3": {"name": "python3", "spec": {"display_name": "Python 3", "language": "python"}}}}"""
                val bytes = body.toByteArray(Charsets.UTF_8)
                exchange.responseHeaders.set("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }

            server.start()
            mockServer = server
        }

        @AfterAll
        @JvmStatic
        fun tearDownServer() {
            mockServer?.stop(0)
        }
    }

    @Test
    fun `test generateSshTunnelCommand produces standard OpenSSH command`() {
        val config = RemoteGatewayConfig(
            name = "Test SSH Cluster",
            baseUrl = "http://localhost:8888",
            useSshTunnel = true,
            sshHost = "gpu-node-01.company.internal",
            sshPort = 2222,
            sshUser = "researcher",
            sshKeyPath = "/home/user/.ssh/id_ed25519",
            localForwardPort = 8888,
            remotePort = 8888
        )

        val client = RemoteJupyterGatewayClient(config)
        val cmd = client.generateSshTunnelCommand()

        assertTrue(cmd.startsWith("ssh -N -L 8888:localhost:8888 -p 2222"))
        assertTrue(cmd.contains("-i \"/home/user/.ssh/id_ed25519\""))
        assertTrue(cmd.contains("researcher@gpu-node-01.company.internal"))
    }

    @Test
    fun `test testConnection succeeds against live server`() = runBlocking {
        val config = RemoteGatewayConfig(
            baseUrl = "http://127.0.0.1:$serverPort"
        )
        val client = RemoteJupyterGatewayClient(config)
        val res = client.testConnection()

        assertTrue(res.isSuccess)
        val msg = res.getOrNull()
        assertNotNull(msg)
        assertTrue(msg!!.contains("Connected successfully (HTTP 200)"))
    }

    @Test
    fun `test listRunningKernels returns parsed kernel list`() = runBlocking {
        val config = RemoteGatewayConfig(
            baseUrl = "http://127.0.0.1:$serverPort"
        )
        val client = RemoteJupyterGatewayClient(config)
        val kernels = client.listRunningKernels()

        assertEquals(1, kernels.size)
        val k = kernels[0]
        assertEquals("k-12345", k.id)
        assertEquals("python3", k.name)
        assertEquals("idle", k.executionState)
        assertEquals(2, k.connections)
    }

    @Test
    fun `test listKernelSpecs returns available specifications`() = runBlocking {
        val config = RemoteGatewayConfig(
            baseUrl = "http://127.0.0.1:$serverPort"
        )
        val client = RemoteJupyterGatewayClient(config)
        val specs = client.listKernelSpecs()

        assertTrue(specs.isNotEmpty())
        assertTrue(specs.any { it.name == "python3" })
    }

    @Test
    fun `test startRemoteKernel spawns new kernel`() = runBlocking {
        val config = RemoteGatewayConfig(
            baseUrl = "http://127.0.0.1:$serverPort"
        )
        val client = RemoteJupyterGatewayClient(config)
        val res = client.startRemoteKernel("python3")

        assertTrue(res.isSuccess)
        val info = res.getOrNull()
        assertNotNull(info)
        assertEquals("k-99999", info!!.id)
    }

    @Test
    fun `test RemoteKernelSession lifecycle`() = runBlocking {
        val config = RemoteGatewayConfig(
            baseUrl = "http://127.0.0.1:$serverPort"
        )
        val client = RemoteJupyterGatewayClient(config)
        val spec = JupyterKernelSpec("python3", "Python 3", "python", emptyList())
        val session = RemoteKernelSession(client, spec)

        assertEquals(KernelStatus.DISCONNECTED, session.status.value)
        val started = session.start()
        assertTrue(started)
        assertEquals(KernelStatus.IDLE, session.status.value)

        val execResult = session.execute("print('Hello from remote!')")
        assertTrue(execResult.isSuccess)
        assertTrue(execResult.outputs.isNotEmpty())

        session.shutdown()
        assertEquals(KernelStatus.DISCONNECTED, session.status.value)
    }
}
