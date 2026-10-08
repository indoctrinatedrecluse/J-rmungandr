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

package org.jormungandr.database.orchestration

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.io.File
import java.util.regex.Pattern

enum class PipelineNodeType(val displayName: String, val badgeColorHex: String) {
    DBT_SOURCE("dbt Source", "#f59e0b"),
    DBT_MODEL("dbt Model", "#10b981"),
    DBT_SEED("dbt Seed", "#6366f1"),
    DBT_SNAPSHOT("dbt Snapshot", "#ec4899"),
    AIRFLOW_TASK("Airflow Task", "#06b6d4"),
    AIRFLOW_DAG("Airflow DAG", "#3b82f6"),
    DAGSTER_ASSET("Dagster Asset", "#8b5cf6")
}

enum class PipelineExecutionStatus(val displayName: String, val colorHex: String) {
    IDLE("Idle", "#64748b"),
    QUEUED("Queued", "#f59e0b"),
    RUNNING("Running", "#3b82f6"),
    SUCCESS("Success", "#10b981"),
    FAILED("Failed", "#ef4444"),
    SKIPPED("Skipped", "#94a3b8")
}

data class PipelineNode(
    val id: String,
    val name: String,
    val type: PipelineNodeType,
    val filePath: String? = null,
    val lineNumber: Int = 1,
    val materialization: String = "view",
    val description: String = "",
    val upstreamIds: MutableSet<String> = mutableSetOf(),
    val downstreamIds: MutableSet<String> = mutableSetOf(),
    var status: PipelineExecutionStatus = PipelineExecutionStatus.IDLE,
    var layer: Int = 0,
    var x: Int = 0,
    var y: Int = 0
)

data class PipelineEdge(
    val sourceId: String,
    val targetId: String
)

data class PipelineGraph(
    val nodes: MutableMap<String, PipelineNode> = mutableMapOf(),
    val edges: MutableList<PipelineEdge> = mutableListOf(),
    val projectName: String = "Workspace Lineage"
) {
    fun addNode(node: PipelineNode) {
        nodes[node.id] = node
    }

    fun addEdge(fromId: String, toId: String) {
        if (fromId == toId) return
        if (edges.none { it.sourceId == fromId && it.targetId == toId }) {
            edges.add(PipelineEdge(fromId, toId))
            nodes[fromId]?.downstreamIds?.add(toId)
            nodes[toId]?.upstreamIds?.add(fromId)
        }
    }

    fun calculateLayers() {
        val visited = mutableSetOf<String>()
        val inDegree = mutableMapOf<String, Int>()

        nodes.keys.forEach { inDegree[it] = 0 }
        edges.forEach { edge ->
            inDegree[edge.targetId] = (inDegree[edge.targetId] ?: 0) + 1
        }

        val queue = ArrayDeque<String>()
        inDegree.filter { it.value == 0 }.keys.forEach {
            queue.add(it)
            nodes[it]?.layer = 0
        }

        while (queue.isNotEmpty()) {
            val currId = queue.removeFirst()
            visited.add(currId)
            val currLayer = nodes[currId]?.layer ?: 0

            nodes[currId]?.downstreamIds?.forEach { nextId ->
                val nextNode = nodes[nextId]
                if (nextNode != null) {
                    if (nextNode.layer < currLayer + 1) {
                        nextNode.layer = currLayer + 1
                    }
                    val deg = (inDegree[nextId] ?: 1) - 1
                    inDegree[nextId] = deg
                    if (deg == 0) {
                        queue.add(nextId)
                    }
                }
            }
        }

        // Handle possible cycles or remaining isolated nodes
        nodes.values.forEach { node ->
            if (node.id !in visited) {
                node.layer = 1
            }
        }
    }
}

/**
 * Intelligent project scanner that discovers dbt models and Airflow DAGs/tasks.
 */
object PipelineScanner {

    private val DBT_REF_PATTERN = Pattern.compile("""ref\s*\(\s*['"]([a-zA-Z0-9_\-]+)['"]\s*\)""")
    private val DBT_SOURCE_PATTERN = Pattern.compile("""source\s*\(\s*['"]([a-zA-Z0-9_\-]+)['"]\s*,\s*['"]([a-zA-Z0-9_\-]+)['"]\s*\)""")
    private val DBT_CONFIG_PATTERN = Pattern.compile("""config\s*\(\s*materialized\s*=\s*['"]([a-zA-Z0-9_\-]+)['"]""", Pattern.CASE_INSENSITIVE)

    private val AIRFLOW_DAG_PATTERN = Pattern.compile("""(?:DAG\s*\(\s*['"]([a-zA-Z0-9_\-]+)['"]|with\s+DAG\s*\(\s*['"]([a-zA-Z0-9_\-]+)['"])""")
    private val AIRFLOW_TASK_PATTERN = Pattern.compile("""([a-zA-Z0-9_]+)\s*=\s*(?:[a-zA-Z0-9_]+Operator|Task)\s*\(\s*(?:.*task_id\s*=\s*['"]([a-zA-Z0-9_\-]+)['"])?""", Pattern.DOTALL)
    private val AIRFLOW_BITSHIFT_R_PATTERN = Pattern.compile("""([a-zA-Z0-9_]+)\s*>>\s*([a-zA-Z0-9_]+)""")
    private val AIRFLOW_BITSHIFT_L_PATTERN = Pattern.compile("""([a-zA-Z0-9_]+)\s*<<\s*([a-zA-Z0-9_]+)""")

    fun scanProject(project: Project): PipelineGraph {
        val graph = PipelineGraph(projectName = project.name)
        val baseDir = project.basePath?.let { File(it) }

        if (baseDir != null && baseDir.exists()) {
            scanDirectory(baseDir, graph)
        }

        // If no files found, populate rich demo lineage so user immediately sees interactive DAG capabilities
        if (graph.nodes.isEmpty()) {
            populateDemoLineage(graph)
        }

        graph.calculateLayers()
        return graph
    }

    private fun scanDirectory(dir: File, graph: PipelineGraph, depth: Int = 0) {
        if (depth > 8) return
        val files = dir.listFiles() ?: return

        for (file in files) {
            if (file.isDirectory) {
                val name = file.name
                if (!name.startsWith(".") && name != "venv" && name != "node_modules" && name != "build" && name != "target" && name != ".git") {
                    scanDirectory(file, graph, depth + 1)
                }
            } else {
                when {
                    file.name.endsWith(".sql") -> scanDbtSql(file, graph)
                    file.name.endsWith(".py") -> scanAirflowPython(file, graph)
                }
            }
        }
    }

    private fun scanDbtSql(file: File, graph: PipelineGraph) {
        val text = try { file.readText() } catch (_: Exception) { return }
        if (!text.contains("{{") && !text.contains("ref(") && !text.contains("source(")) {
            return
        }

        val modelName = file.nameWithoutExtension
        val configMatcher = DBT_CONFIG_PATTERN.matcher(text)
        val mat = if (configMatcher.find()) configMatcher.group(1) else "view"

        val modelNode = PipelineNode(
            id = "dbt.$modelName",
            name = modelName,
            type = PipelineNodeType.DBT_MODEL,
            filePath = file.absolutePath,
            materialization = mat,
            description = "dbt Model ($mat)"
        )
        graph.addNode(modelNode)

        // Parse sources
        val srcMatcher = DBT_SOURCE_PATTERN.matcher(text)
        while (srcMatcher.find()) {
            val srcNamespace = srcMatcher.group(1)
            val srcTable = srcMatcher.group(2)
            val sourceId = "source.$srcNamespace.$srcTable"
            if (!graph.nodes.containsKey(sourceId)) {
                graph.addNode(
                    PipelineNode(
                        id = sourceId,
                        name = "$srcNamespace.$srcTable",
                        type = PipelineNodeType.DBT_SOURCE,
                        filePath = file.absolutePath,
                        materialization = "source",
                        description = "External raw source: $srcNamespace"
                    )
                )
            }
            graph.addEdge(sourceId, modelNode.id)
        }

        // Parse refs
        val refMatcher = DBT_REF_PATTERN.matcher(text)
        while (refMatcher.find()) {
            val upstreamModel = refMatcher.group(1)
            val upstreamId = "dbt.$upstreamModel"
            if (!graph.nodes.containsKey(upstreamId)) {
                graph.addNode(
                    PipelineNode(
                        id = upstreamId,
                        name = upstreamModel,
                        type = PipelineNodeType.DBT_MODEL,
                        filePath = null,
                        materialization = "view",
                        description = "Upstream dbt model reference"
                    )
                )
            }
            graph.addEdge(upstreamId, modelNode.id)
        }
    }

    private fun scanAirflowPython(file: File, graph: PipelineGraph) {
        val text = try { file.readText() } catch (_: Exception) { return }
        if (!text.contains("DAG") && !text.contains(">>") && !text.contains("task_id")) {
            return
        }

        val dagMatcher = AIRFLOW_DAG_PATTERN.matcher(text)
        val dagName = if (dagMatcher.find()) {
            dagMatcher.group(1) ?: dagMatcher.group(2) ?: file.nameWithoutExtension
        } else {
            file.nameWithoutExtension
        }

        val declaredTasks = mutableMapOf<String, String>() // varName to taskId

        // Find tasks
        val lines = text.lines()
        lines.forEachIndexed { idx, line ->
            if (line.contains("Operator(") || line.contains("task_id=")) {
                val varNameMatch = Pattern.compile("""^([a-zA-Z0-9_]+)\s*=""").matcher(line.trim())
                if (varNameMatch.find()) {
                    val varName = varNameMatch.group(1)
                    val taskIdMatch = Pattern.compile("""task_id\s*=\s*['"]([a-zA-Z0-9_\-]+)['"]""").matcher(line)
                    val taskId = if (taskIdMatch.find()) taskIdMatch.group(1) else varName
                    declaredTasks[varName] = taskId

                    val nodeId = "airflow.$dagName.$taskId"
                    graph.addNode(
                        PipelineNode(
                            id = nodeId,
                            name = taskId,
                            type = PipelineNodeType.AIRFLOW_TASK,
                            filePath = file.absolutePath,
                            lineNumber = idx + 1,
                            materialization = "operator",
                            description = "DAG: $dagName | Operator"
                        )
                    )
                }
            }
        }

        // Bitshifts >>
        val rShift = AIRFLOW_BITSHIFT_R_PATTERN.matcher(text)
        while (rShift.find()) {
            val fromVar = rShift.group(1)
            val toVar = rShift.group(2)
            val fromId = "airflow.$dagName.${declaredTasks[fromVar] ?: fromVar}"
            val toId = "airflow.$dagName.${declaredTasks[toVar] ?: toVar}"
            if (graph.nodes.containsKey(fromId) && graph.nodes.containsKey(toId)) {
                graph.addEdge(fromId, toId)
            }
        }

        // Bitshifts <<
        val lShift = AIRFLOW_BITSHIFT_L_PATTERN.matcher(text)
        while (lShift.find()) {
            val toVar = lShift.group(1)
            val fromVar = lShift.group(2)
            val fromId = "airflow.$dagName.${declaredTasks[fromVar] ?: fromVar}"
            val toId = "airflow.$dagName.${declaredTasks[toVar] ?: toVar}"
            if (graph.nodes.containsKey(fromId) && graph.nodes.containsKey(toId)) {
                graph.addEdge(fromId, toId)
            }
        }
    }

    private fun populateDemoLineage(graph: PipelineGraph) {
        // Sources
        val s1 = PipelineNode("source.raw.events", "raw.clickstream_events", PipelineNodeType.DBT_SOURCE, materialization = "source", description = "Raw web event stream")
        val s2 = PipelineNode("source.raw.users", "raw.stripe_customers", PipelineNodeType.DBT_SOURCE, materialization = "source", description = "Stripe transactional billing API")
        val s3 = PipelineNode("source.raw.inventory", "raw.warehouse_stock", PipelineNodeType.DBT_SOURCE, materialization = "source", description = "ERP PostgreSQL replica")

        // Staging models
        val stg1 = PipelineNode("dbt.stg_events", "stg_clickstream", PipelineNodeType.DBT_MODEL, materialization = "view", description = "De-duplicated JSON events parsed with DuckDB")
        val stg2 = PipelineNode("dbt.stg_users", "stg_customers", PipelineNodeType.DBT_MODEL, materialization = "view", description = "PII masked customer profiles")
        val stg3 = PipelineNode("dbt.stg_inventory", "stg_stock_levels", PipelineNodeType.DBT_MODEL, materialization = "table", description = "Current SKU inventory levels")

        // Intermediate / Marts
        val int1 = PipelineNode("dbt.int_user_engagement", "int_user_session_metrics", PipelineNodeType.DBT_MODEL, materialization = "incremental", description = "Windowed session durations & funnel drops")
        val fct1 = PipelineNode("dbt.fct_daily_revenue", "fct_orders_and_revenue", PipelineNodeType.DBT_MODEL, materialization = "table", description = "Executive Daily Revenue Fact Table")
        val dim1 = PipelineNode("dbt.dim_customers", "dim_customer_360", PipelineNodeType.DBT_MODEL, materialization = "table", description = "Unified customer dimension with LTV tiering")

        // Airflow orchestration
        val af1 = PipelineNode("airflow.etl.extract_stripe", "extract_stripe_api", PipelineNodeType.AIRFLOW_TASK, materialization = "PythonOperator", description = "Airflow Operator: Hourly Stripe API Sync")
        val af2 = PipelineNode("airflow.etl.dbt_run_marts", "dbt_run_marts", PipelineNodeType.AIRFLOW_TASK, materialization = "BashOperator", description = "Airflow Task: dbt run -s marts.*")
        val af3 = PipelineNode("airflow.etl.slack_alert", "notify_slack_exec_kpi", PipelineNodeType.AIRFLOW_TASK, materialization = "SlackWebhookOperator", description = "Publishes morning KPI report to #exec-metrics")

        listOf(s1, s2, s3, stg1, stg2, stg3, int1, fct1, dim1, af1, af2, af3).forEach { graph.addNode(it) }

        graph.addEdge(af1.id, s2.id)
        graph.addEdge(s1.id, stg1.id)
        graph.addEdge(s2.id, stg2.id)
        graph.addEdge(s3.id, stg3.id)

        graph.addEdge(stg1.id, int1.id)
        graph.addEdge(stg2.id, int1.id)
        graph.addEdge(stg2.id, dim1.id)
        graph.addEdge(stg3.id, fct1.id)
        graph.addEdge(int1.id, fct1.id)

        graph.addEdge(fct1.id, af2.id)
        graph.addEdge(dim1.id, af2.id)
        graph.addEdge(af2.id, af3.id)

        // Set realistic statuses
        s1.status = PipelineExecutionStatus.SUCCESS
        s2.status = PipelineExecutionStatus.SUCCESS
        s3.status = PipelineExecutionStatus.SUCCESS
        stg1.status = PipelineExecutionStatus.SUCCESS
        stg2.status = PipelineExecutionStatus.SUCCESS
        stg3.status = PipelineExecutionStatus.SUCCESS
        int1.status = PipelineExecutionStatus.SUCCESS
        fct1.status = PipelineExecutionStatus.RUNNING
        dim1.status = PipelineExecutionStatus.QUEUED
        af1.status = PipelineExecutionStatus.SUCCESS
        af2.status = PipelineExecutionStatus.RUNNING
        af3.status = PipelineExecutionStatus.IDLE
    }
}
