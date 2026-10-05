package org.jormungandr.jupyter.variable

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jormungandr.jupyter.kernel.KernelSession
import org.jormungandr.jupyter.model.CellOutput

@Service(Service.Level.PROJECT)
class VariableInspectorService(private val project: Project) {

    private val gson = Gson()
    private val _variables = MutableStateFlow<List<VariableInfo>>(emptyList())
    val variables: StateFlow<List<VariableInfo>> = _variables.asStateFlow()

    private var activeSession: KernelSession? = null

    companion object {
        fun getInstance(project: Project): VariableInspectorService {
            return project.getService(VariableInspectorService::class.java)
        }
    }

    suspend fun refresh(session: KernelSession) {
        this.activeSession = session
        val introspectCode = """
            import sys, json
            __jg_vars = []
            for __k, __v in list(globals().items()):
                if __k.startswith('_') or __k in ('In', 'Out', 'get_ipython', 'exit', 'quit', 'sys', 'json', '__jg_vars'):
                    continue
                try:
                    __t = type(__v).__name__
                    if __t in ('module', 'function', 'type', 'builtin_function_or_method'):
                        continue
                    __shape = str(getattr(__v, 'shape', '-'))
                    __size = int(getattr(__v, 'nbytes', getattr(__v, '__sizeof__', lambda: 0)()))
                    __is_df = __t in ('DataFrame', 'Series') or hasattr(__v, 'to_csv')
                    __rep = repr(__v)
                    if len(__rep) > 100:
                        __rep = __rep[:97] + '...'
                    __jg_vars.append({
                        'name': __k,
                        'type': __t,
                        'shape': __shape,
                        'size': __size,
                        'preview': __rep,
                        'is_df': __is_df
                    })
                except Exception:
                    pass
            print('__JG_VARS_JSON__' + json.dumps(__jg_vars))
        """.trimIndent()

        var jsonOutput: String? = null
        session.execute(introspectCode) { output ->
            if (output is CellOutput.StreamOutput) {
                for (line in output.text.lines()) {
                    if (line.startsWith("__JG_VARS_JSON__")) {
                        jsonOutput = line.removePrefix("__JG_VARS_JSON__").trim()
                    }
                }
            }
        }

        if (jsonOutput != null) {
            runCatching {
                val listType = object : TypeToken<List<Map<String, Any>>>() {}.type
                val rawList: List<Map<String, Any>> = gson.fromJson(jsonOutput, listType)
                val parsed = rawList.map { item ->
                    val name = item["name"]?.toString() ?: ""
                    val typeName = item["type"]?.toString() ?: ""
                    val shape = item["shape"]?.toString() ?: "-"
                    val sizeBytes = (item["size"] as? Number)?.toLong() ?: 0L
                    val preview = item["preview"]?.toString() ?: ""
                    val isDf = item["is_df"] as? Boolean ?: false
                    VariableInfo(
                        name = name,
                        typeName = typeName,
                        shape = shape,
                        sizeBytes = sizeBytes,
                        sizeFormatted = VariableInfo.formatBytes(sizeBytes),
                        preview = preview,
                        isDataFrame = isDf
                    )
                }
                _variables.value = parsed
            }
        }
    }

    suspend fun exportVariableToCsv(session: KernelSession, varName: String): String? {
        val code = """
            try:
                if hasattr($varName, 'to_csv'):
                    print('__JG_CSV_START__\n' + $varName.to_csv(index=False) + '\n__JG_CSV_END__')
                else:
                    import pandas as pd
                    print('__JG_CSV_START__\n' + pd.DataFrame($varName).to_csv(index=False) + '\n__JG_CSV_END__')
            except Exception as e:
                print('__JG_CSV_ERR__' + str(e))
        """.trimIndent()

        val captured = StringBuilder()
        var insideCsv = false
        session.execute(code) { output ->
            if (output is CellOutput.StreamOutput) {
                for (line in output.text.lines()) {
                    if (line.contains("__JG_CSV_START__")) {
                        insideCsv = true
                    } else if (line.contains("__JG_CSV_END__")) {
                        insideCsv = false
                    } else if (insideCsv) {
                        captured.append(line).append("\n")
                    }
                }
            }
        }

        val res = captured.toString().trim()
        return if (res.isNotEmpty()) res else null
    }

    suspend fun plotVariable(session: KernelSession, varName: String): java.awt.image.BufferedImage? {
        val code = """
            import io, base64
            try:
                import matplotlib
                matplotlib.use('Agg')
                import matplotlib.pyplot as plt
                plt.figure(figsize=(6, 4))
                __v = globals().get('$varName')
                if hasattr(__v, 'plot'):
                    __v.plot()
                elif hasattr(__v, '__len__') and not isinstance(__v, (str, dict)):
                    plt.plot(__v)
                else:
                    plt.plot([__v])
                plt.title('Variable: $varName')
                plt.grid(True, linestyle='--', alpha=0.5)
                __buf = io.BytesIO()
                plt.savefig(__buf, format='png', bbox_inches='tight', dpi=100)
                plt.close('all')
                print('__JG_PLOT_START__\n' + base64.b64encode(__buf.getvalue()).decode('ascii') + '\n__JG_PLOT_END__')
            except Exception as e:
                print('__JG_PLOT_ERR__' + str(e))
        """.trimIndent()

        val captured = StringBuilder()
        var insidePlot = false
        session.execute(code) { output ->
            if (output is CellOutput.StreamOutput) {
                for (line in output.text.lines()) {
                    if (line.contains("__JG_PLOT_START__")) {
                        insidePlot = true
                    } else if (line.contains("__JG_PLOT_END__")) {
                        insidePlot = false
                    } else if (insidePlot) {
                        captured.append(line.trim())
                    }
                }
            }
        }

        val b64 = captured.toString().trim()
        if (b64.isNotEmpty()) {
            return runCatching {
                val bytes = java.util.Base64.getDecoder().decode(b64)
                javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(bytes))
            }.getOrNull()
        }
        return null
    }

    fun getActiveSession(): KernelSession? = activeSession
}
