package org.jormungandr.jupyter.kernel

import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jormungandr.jupyter.model.CellOutput
import org.jormungandr.jupyter.model.JupyterKernelSpec
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

private val LOG = logger<SubprocessPythonSession>()

/**
 * Robust interactive Python process kernel session.
 * Executes Python cells directly via an internal loop with live streaming output and exception reporting,
 * providing zero-dependency notebook execution even when ipykernel is not installed.
 */
class SubprocessPythonSession(
    override val spec: JupyterKernelSpec,
    private val pythonExecutable: String = KernelDiscovery.findPythonExecutable()
) : KernelSession {

    override val id: String = UUID.randomUUID().toString()

    private val _status = MutableStateFlow(KernelStatus.DISCONNECTED)
    override val status: StateFlow<KernelStatus> = _status.asStateFlow()

    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null

    private val executionCounter = AtomicInteger(1)
    private val sessionScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override suspend fun start(): Boolean = withContext(Dispatchers.IO) {
        if (_status.value.isRunning && process?.isAlive == true) return@withContext true
        _status.value = KernelStatus.STARTING
        try {
            val runnerScript = "import sys, base64, traceback\n" +
                "cell_globals = {'__name__': '__main__', '__doc__': None}\n" +
                "print('__JG_KERNEL_READY__', flush=True)\n" +
                "while True:\n" +
                "    line = sys.stdin.readline()\n" +
                "    if not line: break\n" +
                "    line = line.strip()\n" +
                "    if not line: continue\n" +
                "    if line == '__JG_EXIT__': break\n" +
                "    parts = line.split(' ', 1)\n" +
                "    tag = parts[0]\n" +
                "    payload = parts[1] if len(parts) > 1 else ''\n" +
                "    if tag == 'EXEC':\n" +
                "        code = base64.b64decode(payload).decode('utf-8')\n" +
                "        try:\n" +
                "            compiled = compile(code, '<cell>', 'exec')\n" +
                "            exec(compiled, cell_globals)\n" +
                "        except Exception:\n" +
                "            traceback.print_exc()\n" +
                "        finally:\n" +
                "            sys.stdout.flush()\n" +
                "            sys.stderr.flush()\n" +
                "            print('__JG_EXEC_DONE__', flush=True)\n"

            val pb = ProcessBuilder(pythonExecutable, "-u", "-c", runnerScript)
            pb.environment().putAll(spec.env)
            pb.environment()["PYTHONUNBUFFERED"] = "1"
            pb.environment()["PYTHONIOENCODING"] = "utf-8"
            pb.redirectErrorStream(true)

            val p = pb.start()
            process = p
            writer = BufferedWriter(OutputStreamWriter(p.outputStream, StandardCharsets.UTF_8))
            reader = BufferedReader(InputStreamReader(p.inputStream, StandardCharsets.UTF_8))

            val ready = withTimeoutOrNull(5000) {
                var isReady = false
                while (p.isAlive) {
                    val line = reader?.readLine() ?: break
                    if (line.contains("__JG_KERNEL_READY__")) {
                        isReady = true
                        break
                    }
                }
                isReady
            } ?: false

            if (ready) {
                _status.value = KernelStatus.IDLE
                LOG.info("SubprocessPythonSession started successfully using: $pythonExecutable")
                true
            } else {
                _status.value = KernelStatus.DEAD
                p.destroyForcibly()
                false
            }
        } catch (e: Exception) {
            _status.value = KernelStatus.DEAD
            LOG.error("Failed to start SubprocessPythonSession", e)
            false
        }
    }

    override suspend fun execute(code: String, onOutput: (CellOutput) -> Unit): ExecutionResult = withContext(Dispatchers.IO) {
        if (process == null || process?.isAlive != true) {
            val started = start()
            if (!started) {
                val err = CellOutput.ErrorOutput("KernelError", "Could not connect to Python runtime", emptyList())
                onOutput(err)
                return@withContext ExecutionResult(0, false, listOf(err))
            }
        }

        _status.value = KernelStatus.BUSY
        val currentCount = executionCounter.getAndIncrement()
        val startTime = System.currentTimeMillis()
        val outputs = mutableListOf<CellOutput>()

        try {
            val w = writer ?: throw IllegalStateException("Process output stream is not available")
            val r = reader ?: throw IllegalStateException("Process input stream is not available")

            val b64Code = Base64.getEncoder().encodeToString(code.toByteArray(StandardCharsets.UTF_8))
            w.write("EXEC $b64Code")
            w.newLine()
            w.flush()

            var isError = false
            val errorLines = mutableListOf<String>()

            val executed = withTimeoutOrNull(10000) {
                while (true) {
                    val line = r.readLine() ?: break
                    if (line.contains("__JG_EXEC_DONE__")) {
                        break
                    }
                    if (line.startsWith("Traceback (most recent call last):") || errorLines.isNotEmpty()) {
                        isError = true
                        errorLines.add(line)
                    } else {
                        val streamOut = CellOutput.StreamOutput("stdout", line + "\n")
                        outputs.add(streamOut)
                        onOutput(streamOut)
                    }
                }
                true
            } ?: false

            if (!executed) {
                val err = CellOutput.ErrorOutput("TimeoutError", "Execution timed out after 10 seconds", emptyList())
                outputs.add(err)
                onOutput(err)
                _status.value = KernelStatus.IDLE
                return@withContext ExecutionResult(currentCount, false, outputs)
            }

            if (isError && errorLines.isNotEmpty()) {
                val ename = errorLines.lastOrNull()?.substringBefore(":")?.trim() ?: "ExecutionError"
                val evalue = errorLines.lastOrNull()?.substringAfter(":", "")?.trim() ?: ""
                val errOut = CellOutput.ErrorOutput(ename, evalue, errorLines)
                outputs.add(errOut)
                onOutput(errOut)
            }

            val duration = System.currentTimeMillis() - startTime
            _status.value = KernelStatus.IDLE
            ExecutionResult(currentCount, !isError, outputs, duration)
        } catch (e: Exception) {
            _status.value = KernelStatus.DEAD
            val errOut = CellOutput.ErrorOutput(e.javaClass.simpleName, e.message ?: "Execution error", emptyList())
            outputs.add(errOut)
            onOutput(errOut)
            ExecutionResult(currentCount, false, outputs)
        }
    }

    override suspend fun interrupt() = withContext(Dispatchers.IO) {
        LOG.warn("Interrupting SubprocessPythonSession...")
        restart()
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
            writer?.write("__JG_EXIT__")
            writer?.newLine()
            writer?.flush()
        }
        runCatching { writer?.close() }
        runCatching { reader?.close() }
        process?.destroyForcibly()
        process = null
        writer = null
        reader = null
        sessionScope.cancel()
    }
}
