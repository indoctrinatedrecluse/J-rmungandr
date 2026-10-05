package org.jormungandr.database.engine

import org.jormungandr.database.model.DatabaseDialect
import java.sql.Connection
import java.sql.SQLException

data class ExplainPlanNode(
    val id: Int,
    val title: String,
    val detail: String,
    val isScanWarning: Boolean = false,
    val children: MutableList<ExplainPlanNode> = mutableListOf()
)

data class ExplainPlanResult(
    val dialect: String,
    val sql: String,
    val rawText: String,
    val rootNodes: List<ExplainPlanNode>,
    val durationMs: Long
)

/**
 * Executes and structures EXPLAIN query execution plans across database dialects.
 */
object ExplainPlanEngine {

    fun explain(
        connection: Connection,
        dialect: DatabaseDialect,
        sql: String
    ): ExplainPlanResult {
        val trimmed = sql.trim().trimEnd(';')
        val explainQuery = when (dialect) {
            DatabaseDialect.SQLITE -> "EXPLAIN QUERY PLAN $trimmed"
            DatabaseDialect.DUCKDB -> "EXPLAIN $trimmed"
            DatabaseDialect.POSTGRESQL -> "EXPLAIN $trimmed"
            DatabaseDialect.MYSQL -> "EXPLAIN $trimmed"
            DatabaseDialect.ORACLE_PLSQL -> "EXPLAIN PLAN FOR $trimmed"
            else -> "EXPLAIN $trimmed"
        }

        val startTime = System.currentTimeMillis()
        val rawLines = mutableListOf<String>()
        val rootNodes = mutableListOf<ExplainPlanNode>()

        try {
            connection.createStatement().use { stmt ->
                stmt.executeQuery(explainQuery).use { rs ->
                    val meta = rs.metaData
                    val colCount = meta.columnCount

                    when (dialect) {
                        DatabaseDialect.SQLITE -> {
                            val nodeMap = mutableMapOf<Int, ExplainPlanNode>()
                            val parentMap = mutableMapOf<Int, Int>()

                            while (rs.next()) {
                                val id = rs.getInt(1)
                                val parent = rs.getInt(2)
                                val detail = rs.getString(4) ?: ""
                                rawLines.add("[$id -> $parent] $detail")

                                val isWarning = detail.contains("SCAN", ignoreCase = true) &&
                                        !detail.contains("INDEX", ignoreCase = true)
                                val title = extractOperationTitle(detail)
                                val node = ExplainPlanNode(id, title, detail, isWarning)
                                nodeMap[id] = node
                                parentMap[id] = parent
                            }

                            for ((id, node) in nodeMap) {
                                val parentId = parentMap[id] ?: 0
                                if (parentId == 0 || !nodeMap.containsKey(parentId)) {
                                    rootNodes.add(node)
                                } else {
                                    nodeMap[parentId]?.children?.add(node)
                                }
                            }
                        }
                        else -> {
                            var counter = 1
                            while (rs.next()) {
                                val line = (1..colCount).joinToString(" | ") { i -> rs.getString(i) ?: "" }
                                rawLines.add(line)

                                val isWarning = line.contains("SCAN", ignoreCase = true) ||
                                        line.contains("Seq Scan", ignoreCase = true)
                                val title = extractOperationTitle(line)
                                rootNodes.add(ExplainPlanNode(counter++, title, line, isWarning))
                            }
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            val errMsg = "EXPLAIN execution failed: ${e.message}"
            rawLines.add(errMsg)
            rootNodes.add(ExplainPlanNode(0, "Execution Error", errMsg, true))
        }

        val duration = System.currentTimeMillis() - startTime
        return ExplainPlanResult(
            dialect = dialect.displayName,
            sql = sql,
            rawText = rawLines.joinToString("\n"),
            rootNodes = rootNodes,
            durationMs = duration
        )
    }

    private fun extractOperationTitle(detail: String): String {
        val trimmed = detail.trim()
        val lower = trimmed.lowercase()
        return when {
            lower.contains("scan") && lower.contains("index") -> "🔍 Index Scan"
            lower.contains("scan") -> "⚠️ Full Table Scan"
            lower.contains("search") -> "🎯 Index Search"
            lower.contains("join") -> "🔗 Join Operation"
            lower.contains("aggregate") -> "∑ Aggregation"
            lower.contains("filter") -> "⚡ Filter"
            lower.contains("sort") || lower.contains("order") -> "🔃 Sort"
            lower.contains("group") -> "📊 Group By"
            lower.contains("projection") -> "📋 Projection"
            else -> trimmed.take(30)
        }
    }
}
