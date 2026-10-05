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

    fun getActiveSession(): KernelSession? = activeSession
}
