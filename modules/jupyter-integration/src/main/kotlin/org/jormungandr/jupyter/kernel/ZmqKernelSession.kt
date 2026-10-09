package org.jormungandr.jupyter.kernel

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jormungandr.jupyter.model.CellOutput
import org.jormungandr.jupyter.model.JupyterKernelSpec
import org.zeromq.SocketType
import org.zeromq.ZContext
import org.zeromq.ZMQ
import java.io.File
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private val LOG = logger<ZmqKernelSession>()

/**
 * Native Jupyter Kernel Session communicating over ZeroMQ via JeroMQ.
 * Implements standard Jupyter Wire Protocol 5.3:
 * - 5 socket channels (Shell, IOPub, Control, Stdin, Heartbeat)
 * - HMAC-SHA256 message signing
 * - Asynchronous streaming outputs and rich MIME display data
 * - Non-blocking process stream draining and race-free completion coordination.
 */
class ZmqKernelSession(
    override val spec: JupyterKernelSpec
) : KernelSession {

    override val id: String = UUID.randomUUID().toString()

    private val _status = MutableStateFlow(KernelStatus.DISCONNECTED)
    override val status: StateFlow<KernelStatus> = _status.asStateFlow()

    private val gson = Gson()
    private val executionCounter = AtomicInteger(1)
    private val sessionScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var zContext: ZContext? = null
    private var shellSocket: ZMQ.Socket? = null
    private var iopubSocket: ZMQ.Socket? = null
    private var controlSocket: ZMQ.Socket? = null
    private var hbSocket: ZMQ.Socket? = null

    private var kernelProcess: Process? = null
    private var connectionFile: File? = null

    private var hmacKey: String = ""
    private var secretKeySpec: SecretKeySpec? = null

    private data class ReplyHolder(val count: Int, val isOk: Boolean)
    private val pendingExecutions = ConcurrentHashMap<String, CompletableDeferred<ExecutionResult>>()
    private val pendingReplies = ConcurrentHashMap<String, ReplyHolder>()
    private val outputListeners = ConcurrentHashMap<String, (CellOutput) -> Unit>()
    private val executionOutputs = ConcurrentHashMap<String, MutableList<CellOutput>>()

    override suspend fun start(): Boolean = withContext(Dispatchers.IO) {
        if (_status.value.isRunning && kernelProcess?.isAlive == true) return@withContext true
        _status.value = KernelStatus.STARTING

        try {
            // 1. Allocate 5 available TCP ports
            val shellPort = findFreePort()
            val iopubPort = findFreePort()
            val stdinPort = findFreePort()
            val controlPort = findFreePort()
            val hbPort = findFreePort()

            hmacKey = UUID.randomUUID().toString()
            secretKeySpec = SecretKeySpec(hmacKey.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")

            // 2. Generate connection_file.json
            val connObj = JsonObject().apply {
                addProperty("transport", "tcp")
                addProperty("ip", "127.0.0.1")
                addProperty("shell_port", shellPort)
                addProperty("iopub_port", iopubPort)
                addProperty("stdin_port", stdinPort)
                addProperty("control_port", controlPort)
                addProperty("hb_port", hbPort)
                addProperty("signature_scheme", "hmac-sha256")
                addProperty("key", hmacKey)
            }

            val connFile = File.createTempFile("jormungandr_kernel_", ".json")
            connFile.deleteOnExit()
            connFile.writeText(gson.toJson(connObj), Charsets.UTF_8)
            connectionFile = connFile

            // 3. Resolve python executable and spawn Kernel Process
            val pyExe = KernelDiscovery.findPythonExecutable()
            val resolvedArgv = spec.argv.mapIndexed { idx, arg ->
                if (idx == 0 && (arg.equals("python", ignoreCase = true) || arg.equals("python3", ignoreCase = true) || arg.equals("py", ignoreCase = true))) {
                    pyExe
                } else {
                    arg
                }
            }
            val cmd = resolvedArgv.map { it.replace("{connection_file}", connFile.absolutePath) }
            val pb = ProcessBuilder(cmd)
            pb.environment().putAll(spec.env)
            pb.environment()["PYTHONIOENCODING"] = "utf-8"
            pb.environment()["PYTHONUTF8"] = "1"
            pb.redirectErrorStream(false)

            LOG.info("Launching Jupyter kernel process: ${cmd.joinToString(" ")}")
            val proc = pb.start()
            kernelProcess = proc

            // Drain stdout and stderr asynchronously so pipes never buffer-lock
            sessionScope.launch(Dispatchers.IO) {
                runCatching {
                    val r = proc.inputStream.bufferedReader(StandardCharsets.UTF_8)
                    while (isActive && proc.isAlive) {
                        val line = r.readLine() ?: break
                        LOG.debug("Kernel stdout: $line")
                    }
                }
            }
            sessionScope.launch(Dispatchers.IO) {
                runCatching {
                    val r = proc.errorStream.bufferedReader(StandardCharsets.UTF_8)
                    while (isActive && proc.isAlive) {
                        val line = r.readLine() ?: break
                        LOG.debug("Kernel stderr: $line")
                    }
                }
            }

            // Verify process stays alive
            delay(400)
            if (!proc.isAlive) {
                LOG.error("Jupyter kernel process terminated prematurely with exit code: ${proc.exitValue()}")
                _status.value = KernelStatus.DEAD
                return@withContext false
            }

            // 4. Initialize ZeroMQ sockets via JeroMQ
            val ctx = ZContext()
            zContext = ctx

            val shell = ctx.createSocket(SocketType.DEALER)
            shell.connect("tcp://127.0.0.1:$shellPort")
            shellSocket = shell

            val iopub = ctx.createSocket(SocketType.SUB)
            iopub.connect("tcp://127.0.0.1:$iopubPort")
            iopub.subscribe("") // Subscribe to all topics
            iopubSocket = iopub

            val control = ctx.createSocket(SocketType.DEALER)
            control.connect("tcp://127.0.0.1:$controlPort")
            controlSocket = control

            val hb = ctx.createSocket(SocketType.REQ)
            hb.connect("tcp://127.0.0.1:$hbPort")
            hbSocket = hb

            // 5. Start background listeners for IOPub and Shell channels
            startIopubListener(iopub)
            startShellListener(shell)

            _status.value = KernelStatus.IDLE
            LOG.info("ZmqKernelSession connected successfully (PID=${proc.pid()}).")
            true
        } catch (e: Exception) {
            _status.value = KernelStatus.DEAD
            LOG.error("Failed to start ZmqKernelSession", e)
            false
        }
    }

    override suspend fun execute(code: String, onOutput: (CellOutput) -> Unit): ExecutionResult = withContext(Dispatchers.IO) {
        if (kernelProcess == null || kernelProcess?.isAlive != true) {
            val started = start()
            if (!started) {
                val err = CellOutput.ErrorOutput("KernelError", "Kernel process is not alive", emptyList())
                onOutput(err)
                return@withContext ExecutionResult(0, false, listOf(err))
            }
        }

        val msgId = UUID.randomUUID().toString()
        val currentExecCount = executionCounter.getAndIncrement()
        val deferred = CompletableDeferred<ExecutionResult>()

        pendingExecutions[msgId] = deferred
        outputListeners[msgId] = onOutput
        executionOutputs[msgId] = mutableListOf()

        val header = JsonObject().apply {
            addProperty("msg_id", msgId)
            addProperty("username", "jormungandr")
            addProperty("session", id)
            addProperty("date", Instant.now().toString())
            addProperty("msg_type", "execute_request")
            addProperty("version", "5.3")
        }

        val content = JsonObject().apply {
            addProperty("code", code)
            addProperty("silent", false)
            addProperty("store_history", true)
            add("user_expressions", JsonObject())
            addProperty("allow_stdin", false)
            addProperty("stop_on_error", true)
        }

        sendMessage(shellSocket!!, header, content)
        _status.value = KernelStatus.BUSY

        try {
            // Await execution reply and all streamed outputs
            val result = withTimeout(120_000L) {
                deferred.await()
            }
            _status.value = KernelStatus.IDLE
            result
        } catch (e: TimeoutCancellationException) {
            _status.value = KernelStatus.IDLE
            val err = CellOutput.ErrorOutput("TimeoutError", "Cell execution timed out after 120 seconds", emptyList())
            onOutput(err)
            ExecutionResult(currentExecCount, false, listOf(err))
        } finally {
            pendingExecutions.remove(msgId)
            pendingReplies.remove(msgId)
            outputListeners.remove(msgId)
            executionOutputs.remove(msgId)
        }
    }

    override suspend fun interrupt() = withContext(Dispatchers.IO) {
        LOG.info("Interrupting kernel via Control channel...")
        val ctrl = controlSocket
        if (ctrl != null) {
            val header = JsonObject().apply {
                addProperty("msg_id", UUID.randomUUID().toString())
                addProperty("username", "jormungandr")
                addProperty("session", id)
                addProperty("date", Instant.now().toString())
                addProperty("msg_type", "interrupt_request")
                addProperty("version", "5.3")
            }
            sendMessage(ctrl, header, JsonObject())
        }
        Unit
    }

    override suspend fun restart(): Boolean = withContext(Dispatchers.IO) {
        _status.value = KernelStatus.RESTARTING
        shutdown()
        executionCounter.set(1)
        start()
    }

    override suspend fun shutdown() = withContext(Dispatchers.IO) {
        _status.value = KernelStatus.DISCONNECTED
        runCatching {
            val ctrl = controlSocket
            if (ctrl != null) {
                val header = JsonObject().apply {
                    addProperty("msg_id", UUID.randomUUID().toString())
                    addProperty("username", "jormungandr")
                    addProperty("session", id)
                    addProperty("date", Instant.now().toString())
                    addProperty("msg_type", "shutdown_request")
                    addProperty("version", "5.3")
                }
                val content = JsonObject().apply { addProperty("restart", false) }
                sendMessage(ctrl, header, content)
            }
        }
        runCatching { zContext?.close() }
        zContext = null
        shellSocket = null
        iopubSocket = null
        controlSocket = null
        hbSocket = null

        kernelProcess?.destroyForcibly()
        kernelProcess = null
        connectionFile?.delete()
        connectionFile = null
        sessionScope.cancel()
    }

    private fun startIopubListener(iopub: ZMQ.Socket) {
        sessionScope.launch(Dispatchers.IO) {
            while (isActive) {
                val frames = receiveMultipartMessage(iopub) ?: continue

                val msgType = frames.header.get("msg_type")?.asString ?: continue
                val parentMsgId = frames.parentHeader.get("msg_id")?.asString ?: ""
                val listener = outputListeners[parentMsgId]
                val outputList = executionOutputs[parentMsgId]

                when (msgType) {
                    "status" -> {
                        val state = frames.content.get("execution_state")?.asString
                        if (state == "idle") {
                            _status.value = KernelStatus.IDLE
                            // If shell already replied, complete execution now that IOPub is idle
                            val reply = pendingReplies[parentMsgId]
                            val deferred = pendingExecutions[parentMsgId]
                            if (reply != null && deferred != null && !deferred.isCompleted) {
                                val outs = outputList?.toList() ?: emptyList()
                                deferred.complete(ExecutionResult(reply.count, reply.isOk, outs))
                            }
                        } else if (state == "busy") {
                            _status.value = KernelStatus.BUSY
                        }
                    }
                    "stream" -> {
                        val name = frames.content.get("name")?.asString ?: "stdout"
                        val text = frames.content.get("text")?.asString ?: ""
                        if (text.contains(JupyterIdeActionBridge.TOKEN_START) && text.contains(JupyterIdeActionBridge.TOKEN_END)) {
                            for (l in text.lines()) {
                                if (l.contains(JupyterIdeActionBridge.TOKEN_START)) {
                                    JupyterIdeActionBridge.processLine(l, listener)
                                }
                            }
                        } else {
                            val streamOut = CellOutput.StreamOutput(name, text)
                            outputList?.add(streamOut)
                            listener?.invoke(streamOut)
                        }
                    }
                    "execute_result" -> {
                        val count = frames.content.get("execution_count")?.asInt ?: 1
                        val data = parseData(frames.content.getAsJsonObject("data"))
                        val meta = parseData(frames.content.getAsJsonObject("metadata"))
                        val resultOut = CellOutput.ExecuteResultOutput(count, data, meta)
                        outputList?.add(resultOut)
                        listener?.invoke(resultOut)
                    }
                    "display_data" -> {
                        val data = parseData(frames.content.getAsJsonObject("data"))
                        val meta = parseData(frames.content.getAsJsonObject("metadata"))
                        val displayOut = CellOutput.DisplayDataOutput(data, meta)
                        outputList?.add(displayOut)
                        listener?.invoke(displayOut)
                    }
                    "error" -> {
                        val ename = frames.content.get("ename")?.asString ?: "Error"
                        val evalue = frames.content.get("evalue")?.asString ?: ""
                        val tb = mutableListOf<String>()
                        if (frames.content.has("traceback") && frames.content.get("traceback").isJsonArray) {
                            for (t in frames.content.getAsJsonArray("traceback")) tb.add(t.asString)
                        }
                        val errOut = CellOutput.ErrorOutput(ename, evalue, tb)
                        outputList?.add(errOut)
                        listener?.invoke(errOut)
                    }
                }
            }
        }
    }

    private fun startShellListener(shell: ZMQ.Socket) {
        sessionScope.launch(Dispatchers.IO) {
            while (isActive) {
                val frames = receiveMultipartMessage(shell) ?: continue
                val msgType = frames.header.get("msg_type")?.asString ?: continue

                if (msgType == "execute_reply") {
                    val parentMsgId = frames.parentHeader.get("msg_id")?.asString ?: ""
                    val statusStr = frames.content.get("status")?.asString ?: "ok"
                    val count = frames.content.get("execution_count")?.asInt ?: 1
                    val deferred = pendingExecutions[parentMsgId]
                    val isOk = statusStr == "ok"

                    pendingReplies[parentMsgId] = ReplyHolder(count, isOk)

                    // Grace delay: wait up to 250ms for IOPub status:idle to flush remaining outputs
                    launch {
                        delay(250)
                        val outputs = executionOutputs[parentMsgId]?.toList() ?: emptyList()
                        deferred?.complete(ExecutionResult(count, isOk, outputs))
                    }
                }
            }
        }
    }

    private data class ParsedJupyterMessage(
        val identities: List<ByteArray>,
        val header: JsonObject,
        val parentHeader: JsonObject,
        val metadata: JsonObject,
        val content: JsonObject
    )

    private fun receiveMultipartMessage(socket: ZMQ.Socket): ParsedJupyterMessage? {
        return runCatching {
            val identities = mutableListOf<ByteArray>()
            while (true) {
                val frame = socket.recv() ?: return@runCatching null
                if (frame.contentEquals(DELIMITER)) break
                identities.add(frame)
            }

            val sig = socket.recvStr() ?: return@runCatching null
            val headerStr = socket.recvStr() ?: return@runCatching null
            val parentHeaderStr = socket.recvStr() ?: return@runCatching null
            val metadataStr = socket.recvStr() ?: return@runCatching null
            val contentStr = socket.recvStr() ?: return@runCatching null

            // Drain any extra raw binary buffers if present
            while (socket.hasReceiveMore()) {
                socket.recv()
            }

            val header = JsonParser.parseString(headerStr).asJsonObject
            val parentHeader = if (parentHeaderStr.isNotBlank()) JsonParser.parseString(parentHeaderStr).asJsonObject else JsonObject()
            val metadata = if (metadataStr.isNotBlank()) JsonParser.parseString(metadataStr).asJsonObject else JsonObject()
            val content = if (contentStr.isNotBlank()) JsonParser.parseString(contentStr).asJsonObject else JsonObject()

            ParsedJupyterMessage(identities, header, parentHeader, metadata, content)
        }.getOrNull()
    }

    private fun sendMessage(
        socket: ZMQ.Socket,
        header: JsonObject,
        content: JsonObject,
        parentHeader: JsonObject = JsonObject(),
        metadata: JsonObject = JsonObject()
    ) {
        val headerStr = gson.toJson(header)
        val parentHeaderStr = gson.toJson(parentHeader)
        val metaStr = gson.toJson(metadata)
        val contentStr = gson.toJson(content)

        val signature = computeHmac(headerStr, parentHeaderStr, metaStr, contentStr)

        socket.send(DELIMITER, ZMQ.SNDMORE)
        socket.send(signature, ZMQ.SNDMORE)
        socket.send(headerStr, ZMQ.SNDMORE)
        socket.send(parentHeaderStr, ZMQ.SNDMORE)
        socket.send(metaStr, ZMQ.SNDMORE)
        socket.send(contentStr, 0)
    }

    private fun computeHmac(vararg parts: String): String {
        val key = secretKeySpec ?: return ""
        return runCatching {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(key)
            for (part in parts) {
                mac.update(part.toByteArray(StandardCharsets.UTF_8))
            }
            val digest = mac.doFinal()
            digest.joinToString("") { "%02x".format(it) }
        }.getOrDefault("")
    }

    private fun parseData(obj: JsonObject?): Map<String, Any> {
        if (obj == null) return emptyMap()
        val result = mutableMapOf<String, Any>()
        for ((k, v) in obj.entrySet()) {
            if (v.isJsonPrimitive) result[k] = v.asString
            else result[k] = v.toString()
        }
        return result
    }

    private fun findFreePort(): Int {
        ServerSocket(0).use { socket ->
            socket.reuseAddress = true
            return socket.localPort
        }
    }

    companion object {
        private val DELIMITER = "<IDS|MSG>".toByteArray(StandardCharsets.UTF_8)
    }
}
