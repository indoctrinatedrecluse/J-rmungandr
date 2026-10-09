package org.jormungandr.jupyter.kernel

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.wm.ToolWindowManager
import org.jormungandr.jupyter.model.CellOutput
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Programmatic IDE Bridge connecting running Python code & Jupyter cells
 * directly to Jörmungandr's flagship UI studios, tool windows, visualizers, and inspectors.
 */
object JupyterIdeActionBridge {
    private val LOG = logger<JupyterIdeActionBridge>()

    const val TOKEN_START = "__JG_IDE_ACTION__"
    const val TOKEN_END = "__JG_IDE_ACTION_END__"

    fun processLine(line: String, onOutput: ((CellOutput) -> Unit)? = null): Boolean {
        if (!line.contains(TOKEN_START) || !line.contains(TOKEN_END)) return false
        val payload = line.substringAfter(TOKEN_START).substringBefore(TOKEN_END)
        handleAction(payload, onOutput)
        return true
    }

    fun handleAction(payload: String, onOutput: ((CellOutput) -> Unit)? = null) {
        try {
            val json = JsonParser.parseString(payload).asJsonObject
            val action = json.get("action")?.asString ?: return
            val path = if (json.has("path") && !json.get("path").isJsonNull) json.get("path").asString else null
            val title = if (json.has("title") && !json.get("title").isJsonNull) json.get("title").asString else "Jörmungandr Studio"
            val dataObj = if (json.has("data") && json.get("data").isJsonObject) json.getAsJsonObject("data") else null

            ApplicationManager.getApplication().invokeLater {
                val project = ProjectManager.getInstance().openProjects.firstOrNull { !it.isDisposed }
                    ?: ProjectManager.getInstance().defaultProject

                when (action) {
                    "show_dataframe" -> openDataFrame(project, path, dataObj, title)
                    "show_lakehouse" -> openLakehouse(project, path)
                    "show_pipeline_lineage" -> openPipelineLineage(project)
                    "show_database_studio" -> openDatabaseStudio(project)
                    "show_model_inspector" -> openModelInspector(project, path)
                    "show_gpu_monitor" -> openGpuMonitor(project)
                    "show_prompt_studio" -> openPromptStudio(project)
                    "show_r_console" -> openRConsole(project)
                    "show_plots" -> openPlots(project)
                    "show_dag" -> openDag(project)
                }
            }

            if (onOutput != null) {
                emitNotificationCard(action, title, path, onOutput)
            }
        } catch (e: Throwable) {
            LOG.warn("Failed to process Jörmungandr IDE Action: $payload", e)
        }
    }

    private fun openDataFrame(project: Project, path: String?, dataObj: JsonObject?, title: String) {
        if (!path.isNullOrBlank()) {
            val vFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(path.replace('\\', '/'))
            if (vFile != null) {
                FileEditorManager.getInstance(project).openFile(vFile, true)
                return
            }
        }
        if (dataObj != null) {
            runCatching {
                val tempFile = File.createTempFile("jg_df_viewer_", ".csv")
                tempFile.deleteOnExit()
                val cols = dataObj.getAsJsonArray("columns").map { it.asString }
                val records = dataObj.getAsJsonArray("records")
                val sb = StringBuilder()
                sb.append(cols.joinToString(",")).append("\n")
                for (elem in records) {
                    val rec = elem.asJsonObject
                    val row = cols.map { col ->
                        val v = if (rec.has(col) && !rec.get(col).isJsonNull) rec.get(col).asString else ""
                        if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
                            "\"" + v.replace("\"", "\"\"") + "\""
                        } else v
                    }
                    sb.append(row.joinToString(",")).append("\n")
                }
                tempFile.writeText(sb.toString(), StandardCharsets.UTF_8)
                val vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempFile)
                if (vFile != null) {
                    FileEditorManager.getInstance(project).openFile(vFile, true)
                    return
                }
            }
        }
        ToolWindowManager.getInstance(project).getToolWindow("DataFrame Viewer")?.show()
    }

    private fun openLakehouse(project: Project, path: String?) {
        ToolWindowManager.getInstance(project).getToolWindow("DuckDB Lakehouse")?.show()
        if (!path.isNullOrBlank()) {
            val file = File(path)
            if (file.exists()) {
                runCatching {
                    val clazz = Class.forName("org.jormungandr.dataframe.lakehouse.LakehouseInspectorDialog")
                    val ctor = clazz.getConstructor(Project::class.java, File::class.java)
                    val dialog = ctor.newInstance(project, file)
                    val showMethod = clazz.getMethod("show")
                    showMethod.invoke(dialog)
                }
            }
        }
    }

    private fun openPipelineLineage(project: Project) {
        ToolWindowManager.getInstance(project).getToolWindow("Data Pipelines")?.show()
    }

    private fun openDatabaseStudio(project: Project) {
        ToolWindowManager.getInstance(project).getToolWindow("Database Studio")?.show()
    }

    private fun openModelInspector(project: Project, path: String?) {
        if (!path.isNullOrBlank()) {
            val vFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(path.replace('\\', '/'))
            if (vFile != null) {
                FileEditorManager.getInstance(project).openFile(vFile, true)
            }
        }
    }

    private fun openGpuMonitor(project: Project) {
        runCatching {
            val action = ActionManager.getInstance().getAction("Jormungandr.Tools.GpuMonitor")
            if (action != null) {
                val event = AnActionEvent.createFromAnAction(action, null, ActionPlaces.UNKNOWN, DataContext.EMPTY_CONTEXT)
                action.actionPerformed(event)
            }
        }
    }

    private fun openPromptStudio(project: Project) {
        ToolWindowManager.getInstance(project).getToolWindow("Prompt Studio")?.show()
    }

    private fun openRConsole(project: Project) {
        ToolWindowManager.getInstance(project).getToolWindow("R Console")?.show()
    }

    private fun openPlots(project: Project) {
        ToolWindowManager.getInstance(project).getToolWindow("Scientific Plots")?.show()
    }

    private fun openDag(project: Project) {
        runCatching {
            val editorManager = FileEditorManager.getInstance(project)
            val activeEditor = editorManager.selectedEditors.firstOrNull {
                it.javaClass.simpleName == "JupyterNotebookFileEditor"
            }
            if (activeEditor != null) {
                val modelProp = activeEditor.javaClass.getMethod("getModel").invoke(activeEditor)
                val dialogClass = Class.forName("org.jormungandr.jupyter.dag.NotebookDependencyGraphDialog")
                val ctor = dialogClass.constructors.first()
                val dialog = ctor.newInstance(project, modelProp)
                dialogClass.getMethod("show").invoke(dialog)
            }
        }
    }

    private fun emitNotificationCard(action: String, title: String, path: String?, onOutput: (CellOutput) -> Unit) {
        val (icon, label) = when (action) {
            "show_dataframe" -> "📊" to "DataFrame Viewer & 2D Vector Charting Studio"
            "show_lakehouse" -> "🏛️" to "Modern Lakehouse & Deep Parquet Inspector"
            "show_pipeline_lineage" -> "🔄" to "Data Pipelines & Lineage DAG Visualizer (dbt / Airflow)"
            "show_database_studio" -> "🗄️" to "Database Analytics Studio & Query Console"
            "show_model_inspector" -> "🧠" to "Neural Checkpoint & Architecture Inspector"
            "show_gpu_monitor" -> "🎮" to "Hardware Telemetry & GPU Monitor"
            "show_prompt_studio" -> "🤖" to "Local AI & Prompt Engineering Studio"
            "show_r_console" -> "🔮" to "R Interactive Statistical REPL Console"
            "show_plots" -> "📈" to "Scientific Plots & Graphics Studio"
            "show_dag" -> "🪐" to "Reactive Execution DAG & AST Dependency Graph"
            else -> "⚡" to action
        }
        val targetDesc = if (!path.isNullOrBlank()) "Target: <code>${File(path).name}</code>" else "Active Studio Window"
        val cardHtml = """
            <div style="background: linear-gradient(135deg, #0f172a, #1e293b); border: 1px solid #38bdf8; border-radius: 8px; padding: 10px 14px; margin: 6px 0; color: #f8fafc; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;">
                <div style="display: flex; align-items: center; justify-content: space-between;">
                    <div style="display: flex; align-items: center; gap: 8px;">
                        <span style="font-size: 16px;">$icon</span>
                        <strong style="color: #38bdf8; font-size: 12px;">Jörmungandr Studio Triggered: $label</strong>
                    </div>
                    <span style="font-size: 11px; background: #0369a1; color: #e0f2fe; padding: 2px 8px; border-radius: 4px; font-weight: 500;">✓ Active</span>
                </div>
                <div style="margin-top: 4px; font-size: 11px; color: #94a3b8;">$targetDesc</div>
            </div>
        """.trimIndent()
        onOutput(CellOutput.DisplayDataOutput(mapOf("text/html" to cardHtml)))
    }
}
