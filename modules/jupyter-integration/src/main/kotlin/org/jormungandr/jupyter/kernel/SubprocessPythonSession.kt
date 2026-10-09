package org.jormungandr.jupyter.kernel

import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jormungandr.jupyter.model.CellOutput
import org.jormungandr.jupyter.model.JupyterKernelSpec
import java.io.*
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

private val LOG = logger<SubprocessPythonSession>()

/**
 * Robust interactive Python process kernel session.
 * Executes Python cells directly via an internal loop with live streaming output, rich MIME inspection,
 * automatic IPython shim injection, magic command preprocessing, and Windows UTF-8 compatibility.
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
    private var runnerTempFile: File? = null

    private val executionCounter = AtomicInteger(1)
    private val sessionScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override suspend fun start(): Boolean = withContext(Dispatchers.IO) {
        if (_status.value.isRunning && process?.isAlive == true) return@withContext true
        _status.value = KernelStatus.STARTING
        try {
            val runnerScript = buildRunnerScript()

            val tempFile = File.createTempFile("jg_kernel_runner_", ".py")
            tempFile.deleteOnExit()
            tempFile.writeText(runnerScript, StandardCharsets.UTF_8)
            runnerTempFile = tempFile

            val pb = ProcessBuilder(pythonExecutable, "-u", tempFile.absolutePath)
            pb.environment().putAll(spec.env)
            pb.environment()["PYTHONUNBUFFERED"] = "1"
            pb.environment()["PYTHONIOENCODING"] = "utf-8"
            pb.environment()["PYTHONUTF8"] = "1"
            pb.redirectErrorStream(true)

            val p = pb.start()
            process = p
            writer = BufferedWriter(OutputStreamWriter(p.outputStream, StandardCharsets.UTF_8))
            reader = BufferedReader(InputStreamReader(p.inputStream, StandardCharsets.UTF_8))

            val ready = withTimeoutOrNull(8000) {
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
            var inErrorBlock = false
            val errorLines = mutableListOf<String>()

            // 5 minute timeout per cell execution
            val executed = withTimeoutOrNull(300_000L) {
                while (true) {
                    val line = r.readLine() ?: break
                    if (line.contains("__JG_EXEC_DONE__")) {
                        break
                    }

                    if (line.contains("__JG_ERROR_START__")) {
                        isError = true
                        inErrorBlock = true
                        continue
                    }
                    if (line.contains("__JG_ERROR_END__")) {
                        inErrorBlock = false
                        continue
                    }

                    if (inErrorBlock) {
                        errorLines.add(line)
                        continue
                    }

                    // Rich MIME Tags
                    if (line.startsWith("__JG_MIME_HTML__") && line.contains("__JG_MIME_HTML_END__")) {
                        val b64 = line.substringAfter("__JG_MIME_HTML__").substringBefore("__JG_MIME_HTML_END__")
                        val decodedHtml = String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8)
                        val disp = CellOutput.DisplayDataOutput(mapOf("text/html" to decodedHtml))
                        outputs.add(disp)
                        onOutput(disp)
                    } else if (line.startsWith("__JG_MIME_PNG__") && line.contains("__JG_MIME_PNG_END__")) {
                        val b64 = line.substringAfter("__JG_MIME_PNG__").substringBefore("__JG_MIME_PNG_END__")
                        val disp = CellOutput.DisplayDataOutput(mapOf("image/png" to b64))
                        outputs.add(disp)
                        onOutput(disp)
                    } else if (line.startsWith("__JG_MIME_JSON__") && line.contains("__JG_MIME_JSON_END__")) {
                        val b64 = line.substringAfter("__JG_MIME_JSON__").substringBefore("__JG_MIME_JSON_END__")
                        val decodedJson = String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8)
                        val disp = CellOutput.DisplayDataOutput(mapOf("application/json" to decodedJson))
                        outputs.add(disp)
                        onOutput(disp)
                    } else if (line.startsWith("__JG_MIME_SVG__") && line.contains("__JG_MIME_SVG_END__")) {
                        val b64 = line.substringAfter("__JG_MIME_SVG__").substringBefore("__JG_MIME_SVG_END__")
                        val decodedSvg = String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8)
                        val disp = CellOutput.DisplayDataOutput(mapOf("image/svg+xml" to decodedSvg))
                        outputs.add(disp)
                        onOutput(disp)
                    } else if (line.startsWith("__JG_MIME_AUDIO__") && line.contains("__JG_MIME_AUDIO_END__")) {
                        val b64 = line.substringAfter("__JG_MIME_AUDIO__").substringBefore("__JG_MIME_AUDIO_END__")
                        val disp = CellOutput.DisplayDataOutput(mapOf("audio/wav" to b64))
                        outputs.add(disp)
                        onOutput(disp)
                    } else if (line.startsWith("__JG_MIME_LATEX__") && line.contains("__JG_MIME_LATEX_END__")) {
                        val b64 = line.substringAfter("__JG_MIME_LATEX__").substringBefore("__JG_MIME_LATEX_END__")
                        val decodedLatex = String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8)
                        val disp = CellOutput.DisplayDataOutput(mapOf("text/latex" to decodedLatex))
                        outputs.add(disp)
                        onOutput(disp)
                    } else {
                        val streamOut = CellOutput.StreamOutput("stdout", line + "\n")
                        outputs.add(streamOut)
                        onOutput(streamOut)
                    }
                }
                true
            } ?: false

            if (!executed) {
                val err = CellOutput.ErrorOutput("TimeoutError", "Execution timed out after 300 seconds", emptyList())
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
        runnerTempFile?.delete()
        runnerTempFile = null
        sessionScope.cancel()
    }

    private fun buildRunnerScript(): String {
        return """
import sys, base64, traceback, ast, io, os, json, types, builtins

# 1. UTF-8 standard streams configuration
try:
    if hasattr(sys.stdout, 'reconfigure'):
        sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    if hasattr(sys.stderr, 'reconfigure'):
        sys.stderr.reconfigure(encoding='utf-8', errors='replace')
    if hasattr(sys.stdin, 'reconfigure'):
        sys.stdin.reconfigure(encoding='utf-8', errors='replace')
except Exception:
    pass

cell_globals = {'__name__': '__main__', '__doc__': None}

# 2. Rich Display MIME Wrapper Classes
class _JgHtml:
    def __init__(self, data): self.data = str(data)
    def _repr_html_(self): return self.data

class _JgJson:
    def __init__(self, data):
        self.data = data if isinstance(data, str) else json.dumps(data)
    def _repr_json_(self): return self.data

class _JgSvg:
    def __init__(self, data): self.data = str(data)
    def _repr_svg_(self): return self.data

class _JgAudio:
    def __init__(self, data=None, filename=None, url=None, rate=None):
        if data is not None:
            if isinstance(data, bytes):
                self.b64 = base64.b64encode(data).decode('ascii')
            else:
                self.b64 = str(data)
        elif filename is not None and os.path.exists(filename):
            with open(filename, 'rb') as f:
                self.b64 = base64.b64encode(f.read()).decode('ascii')
        else:
            self.b64 = ""
        self.rate = rate
    def _repr_mimebundle_(self, **kwargs):
        return {'audio/wav': self.b64}

class _JgLatex:
    def __init__(self, data): self.data = str(data)
    def _repr_latex_(self): return self.data

class _JgMarkdown:
    def __init__(self, data): self.data = str(data)
    def _repr_markdown_(self): return self.data

class _JgImage:
    def __init__(self, data=None, filename=None, url=None):
        if data is not None:
            self.b64 = data if isinstance(data, str) else base64.b64encode(data).decode('ascii')
        elif filename is not None and os.path.exists(filename):
            with open(filename, 'rb') as f:
                self.b64 = base64.b64encode(f.read()).decode('ascii')
        else:
            self.b64 = ""
    def _repr_png_(self): return self.b64

def _jg_display_object(obj):
    if obj is None:
        return
    # Check for Jupyter _repr_mimebundle_
    if hasattr(obj, '_repr_mimebundle_'):
        try:
            mb = obj._repr_mimebundle_()
            if isinstance(mb, tuple): mb = mb[0]
            if isinstance(mb, dict):
                if 'text/html' in mb:
                    h = mb['text/html']
                    print('__JG_MIME_HTML__' + base64.b64encode(h.encode('utf-8')).decode('ascii') + '__JG_MIME_HTML_END__', flush=True)
                    return
                elif 'image/png' in mb:
                    p = mb['image/png']
                    b64 = p if isinstance(p, str) else base64.b64encode(p).decode('ascii')
                    print('__JG_MIME_PNG__' + b64 + '__JG_MIME_PNG_END__', flush=True)
                    return
                elif 'application/json' in mb:
                    j = mb['application/json']
                    jstr = j if isinstance(j, str) else json.dumps(j)
                    print('__JG_MIME_JSON__' + base64.b64encode(jstr.encode('utf-8')).decode('ascii') + '__JG_MIME_JSON_END__', flush=True)
                    return
                elif 'image/svg+xml' in mb:
                    s = mb['image/svg+xml']
                    print('__JG_MIME_SVG__' + base64.b64encode(s.encode('utf-8')).decode('ascii') + '__JG_MIME_SVG_END__', flush=True)
                    return
                elif 'audio/wav' in mb:
                    a = mb['audio/wav']
                    print('__JG_MIME_AUDIO__' + a + '__JG_MIME_AUDIO_END__', flush=True)
                    return
                elif 'text/latex' in mb:
                    l = mb['text/latex']
                    print('__JG_MIME_LATEX__' + base64.b64encode(l.encode('utf-8')).decode('ascii') + '__JG_MIME_LATEX_END__', flush=True)
                    return
        except Exception:
            pass
    if hasattr(obj, '_repr_html_'):
        try:
            h = obj._repr_html_()
            if h:
                print('__JG_MIME_HTML__' + base64.b64encode(h.encode('utf-8')).decode('ascii') + '__JG_MIME_HTML_END__', flush=True)
                return
        except Exception:
            pass
    if hasattr(obj, 'to_html'):
        try:
            h = obj.to_html()
            if h and isinstance(h, str):
                print('__JG_MIME_HTML__' + base64.b64encode(h.encode('utf-8')).decode('ascii') + '__JG_MIME_HTML_END__', flush=True)
                return
        except Exception:
            pass
    if hasattr(obj, '_repr_json_'):
        try:
            j = obj._repr_json_()
            if j:
                if isinstance(j, tuple): j = j[0]
                jstr = j if isinstance(j, str) else json.dumps(j)
                print('__JG_MIME_JSON__' + base64.b64encode(jstr.encode('utf-8')).decode('ascii') + '__JG_MIME_JSON_END__', flush=True)
                return
        except Exception:
            pass
    if hasattr(obj, '_repr_svg_'):
        try:
            s = obj._repr_svg_()
            if s:
                print('__JG_MIME_SVG__' + base64.b64encode(s.encode('utf-8')).decode('ascii') + '__JG_MIME_SVG_END__', flush=True)
                return
        except Exception:
            pass
    if hasattr(obj, '_repr_png_'):
        try:
            p = obj._repr_png_()
            if p:
                b64 = p if isinstance(p, str) else base64.b64encode(p).decode('ascii')
                print('__JG_MIME_PNG__' + b64 + '__JG_MIME_PNG_END__', flush=True)
                return
        except Exception:
            pass
    if hasattr(obj, '_repr_latex_'):
        try:
            l = obj._repr_latex_()
            if l:
                print('__JG_MIME_LATEX__' + base64.b64encode(l.encode('utf-8')).decode('ascii') + '__JG_MIME_LATEX_END__', flush=True)
                return
        except Exception:
            pass
    if isinstance(obj, _JgHtml):
        print('__JG_MIME_HTML__' + base64.b64encode(obj.data.encode('utf-8')).decode('ascii') + '__JG_MIME_HTML_END__', flush=True)
        return
    if isinstance(obj, _JgJson):
        print('__JG_MIME_JSON__' + base64.b64encode(obj.data.encode('utf-8')).decode('ascii') + '__JG_MIME_JSON_END__', flush=True)
        return
    if isinstance(obj, _JgSvg):
        print('__JG_MIME_SVG__' + base64.b64encode(obj.data.encode('utf-8')).decode('ascii') + '__JG_MIME_SVG_END__', flush=True)
        return
    if isinstance(obj, _JgAudio):
        print('__JG_MIME_AUDIO__' + obj.b64 + '__JG_MIME_AUDIO_END__', flush=True)
        return
    if isinstance(obj, _JgLatex):
        print('__JG_MIME_LATEX__' + base64.b64encode(obj.data.encode('utf-8')).decode('ascii') + '__JG_MIME_LATEX_END__', flush=True)
        return
    # Default repr print
    print(repr(obj), flush=True)

def _jg_display(*objs, **kwargs):
    for o in objs:
        _jg_display_object(o)

# 3. Inject Shim or Hook into IPython
try:
    import IPython.display
    import IPython.core.display_functions
    IPython.display.display = _jg_display
    IPython.core.display_functions.display = _jg_display
except Exception:
    pass

if 'IPython' not in sys.modules:
    ipy_mod = types.ModuleType('IPython')
    ipy_core = types.ModuleType('IPython.core')
    ipy_disp = types.ModuleType('IPython.display')
    ipy_core_disp = types.ModuleType('IPython.core.display')
    for m in (ipy_disp, ipy_core_disp):
        m.display = _jg_display
        m.HTML = _JgHtml
        m.JSON = _JgJson
        m.SVG = _JgSvg
        m.Audio = _JgAudio
        m.Image = _JgImage
        m.Latex = _JgLatex
        m.Markdown = _JgMarkdown
    ipy_mod.display = ipy_disp
    ipy_mod.core = ipy_core
    sys.modules['IPython'] = ipy_mod
    sys.modules['IPython.core'] = ipy_core
    sys.modules['IPython.display'] = ipy_disp
    sys.modules['IPython.core.display'] = ipy_core_disp

builtins.display = _jg_display
cell_globals['display'] = _jg_display

# Matplotlib & Plotly integration hooks
try:
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    def __jg_show(*args, **kwargs):
        for __num in plt.get_fignums():
            __f = plt.figure(__num)
            __buf = io.BytesIO()
            __f.savefig(__buf, format='png', bbox_inches='tight', dpi=100)
            print('__JG_MIME_PNG__' + base64.b64encode(__buf.getvalue()).decode('ascii') + '__JG_MIME_PNG_END__', flush=True)
        plt.close('all')
    plt.show = __jg_show
except Exception:
    pass

try:
    import plotly.io as pio
    pio.renderers.default = 'notebook_connected'
    def __jg_plotly_show(fig, *args, **kwargs):
        html = fig.to_html(include_plotlyjs='cdn', full_html=True)
        print('__JG_MIME_HTML__' + base64.b64encode(html.encode('utf-8')).decode('ascii') + '__JG_MIME_HTML_END__', flush=True)
    pio.show = __jg_plotly_show
except Exception:
    pass

def _jg_preprocess_code(raw):
    lines = raw.splitlines()
    out = []
    for line in lines:
        s = line.strip()
        if s.startswith('%matplotlib'):
            out.append("# " + line)
        elif s.startswith('%time '):
            out.append(line.replace('%time ', '', 1))
        elif s.startswith('%'):
            out.append("# " + line)
        elif s.startswith('!'):
            cmd = s[1:].strip()
            out.append(f"import subprocess; subprocess.run({repr(cmd)}, shell=True)")
        else:
            out.append(line)
    return '\n'.join(out)

print('__JG_KERNEL_READY__', flush=True)

while True:
    line = sys.stdin.readline()
    if not line: break
    line = line.strip()
    if not line: continue
    if line == '__JG_EXIT__': break
    parts = line.split(' ', 1)
    tag = parts[0]
    payload = parts[1] if len(parts) > 1 else ''
    if tag == 'EXEC':
        code = base64.b64decode(payload).decode('utf-8')
        clean_code = _jg_preprocess_code(code)
        try:
            tree = ast.parse(clean_code)
            if tree.body and isinstance(tree.body[-1], ast.Expr):
                exec_body = tree.body[:-1]
                if exec_body:
                    exec_mod = ast.Module(body=exec_body, type_ignores=[])
                    exec(compile(exec_mod, '<cell>', 'exec'), cell_globals)
                expr_ast = ast.Expression(body=tree.body[-1].value)
                res = eval(compile(expr_ast, '<cell>', 'eval'), cell_globals)
                if res is not None:
                    _jg_display_object(res)
            else:
                exec(compile(tree, '<cell>', 'exec'), cell_globals)
        except Exception:
            print('__JG_ERROR_START__', flush=True)
            traceback.print_exc()
            print('__JG_ERROR_END__', flush=True)
        finally:
            try:
                if 'matplotlib.pyplot' in sys.modules:
                    import matplotlib.pyplot as plt
                    if plt.get_fignums():
                        for __num in plt.get_fignums():
                            __f = plt.figure(__num)
                            __buf = io.BytesIO()
                            __f.savefig(__buf, format='png', bbox_inches='tight', dpi=100)
                            print('__JG_MIME_PNG__' + base64.b64encode(__buf.getvalue()).decode('ascii') + '__JG_MIME_PNG_END__', flush=True)
                        plt.close('all')
            except Exception:
                pass
            sys.stdout.flush()
            sys.stderr.flush()
            print('__JG_EXEC_DONE__', flush=True)
""".trimIndent()
    }
}
