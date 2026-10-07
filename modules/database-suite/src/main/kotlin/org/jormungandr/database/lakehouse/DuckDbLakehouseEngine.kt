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

package org.jormungandr.database.lakehouse

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import org.jormungandr.core.plot.PlotItem
import org.jormungandr.core.plot.PlotManagerService
import org.jormungandr.dataframe.model.ColumnMetadata
import org.jormungandr.dataframe.model.DataFrame
import org.jormungandr.dataframe.model.DataTypeCategory
import org.jormungandr.dataframe.service.DataFrameService
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Types
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Result of a query executed on the In-Memory DuckDB Lakehouse.
 */
data class DuckDbLakehouseResult(
    val dataFrame: DataFrame,
    val executionTimeMs: Long,
    val sql: String,
    val rowCount: Long,
    val columnNames: List<String>
)

/**
 * Descriptor for a discovered local Parquet, CSV, or Arrow data file in the workspace.
 */
data class LakehouseFileItem(
    val file: File,
    val relativePath: String,
    val format: String,
    val sizeBytes: Long
)

/**
 * High-performance In-Memory DuckDB Lakehouse Engine.
 * Allows zero-copy SQL queries directly over active Jörmungandr DataFrames,
 * local Parquet / CSV data lakes, and pipes query results to Scientific Plots and DataFrame Studio.
 */
object DuckDbLakehouseEngine {

    private val LOG = Logger.getInstance(DuckDbLakehouseEngine::class.java)

    @Volatile
    private var connection: Connection? = null

    private val registeredTables = ConcurrentHashMap<String, Long>()

    init {
        runCatching {
            Class.forName("org.duckdb.DuckDBDriver")
        }
    }

    @Synchronized
    fun getConnection(): Connection {
        val existing = connection
        if (existing != null && !existing.isClosed) {
            return existing
        }
        val conn = DriverManager.getConnection("jdbc:duckdb:")
        connection = conn
        LOG.info("Initialized In-Memory DuckDB Lakehouse instance.")
        return conn
    }

    /**
     * Synchronizes all active DataFrames from DataFrameService into DuckDB in-memory tables.
     */
    fun syncAllActiveDataFrames() {
        val dfService = ApplicationManager.getApplication()?.getService(DataFrameService::class.java) ?: return
        val allDfs = dfService.getAllDataFrames()
        val activeDf = dfService.activeDataFrame.value

        allDfs.forEach { df ->
            val tableName = sanitizeTableName(df.name)
            registerDataFrame(tableName, df)
        }

        if (activeDf != null) {
            registerDataFrame("active_df", activeDf)
        }
    }

    /**
     * Registers a DataFrame as an in-memory SQL table in DuckDB.
     */
    fun registerDataFrame(tableName: String, df: DataFrame) {
        val sanitized = sanitizeTableName(tableName)
        val conn = getConnection()

        try {
            val stmt = conn.createStatement()
            stmt.execute("DROP TABLE IF EXISTS \"$sanitized\"")

            // 1. Build CREATE TABLE statement
            val colDefs = df.columns.map { col ->
                val type = when (col.category) {
                    DataTypeCategory.INTEGER -> "BIGINT"
                    DataTypeCategory.FLOAT -> "DOUBLE"
                    DataTypeCategory.BOOLEAN -> "BOOLEAN"
                    DataTypeCategory.DATETIME -> "TIMESTAMP"
                    else -> "VARCHAR"
                }
                "\"${col.name.replace("\"", "\"\"")}\" $type"
            }.joinToString(", ")

            stmt.execute("CREATE TABLE \"$sanitized\" ($colDefs)")

            // 2. Insert rows in batches
            if (df.rowCount > 0) {
                val placeholders = df.columns.indices.joinToString(", ") { "?" }
                val insertSql = "INSERT INTO \"$sanitized\" VALUES ($placeholders)"
                val prepStmt = conn.prepareStatement(insertSql)

                for (rowIdx in 0 until df.rowCount) {
                    val row = df.rows[rowIdx]
                    for (colIdx in df.columns.indices) {
                        val value = row.getOrNull(colIdx)
                        if (value == null) {
                            prepStmt.setNull(colIdx + 1, Types.NULL)
                        } else {
                            when (df.columns[colIdx].category) {
                                DataTypeCategory.INTEGER -> {
                                    val num = (value as? Number)?.toLong() ?: value.toString().toLongOrNull() ?: 0L
                                    prepStmt.setLong(colIdx + 1, num)
                                }
                                DataTypeCategory.FLOAT -> {
                                    val num = (value as? Number)?.toDouble() ?: value.toString().toDoubleOrNull() ?: 0.0
                                    prepStmt.setDouble(colIdx + 1, num)
                                }
                                DataTypeCategory.BOOLEAN -> {
                                    val bool = (value as? Boolean) ?: value.toString().toBoolean()
                                    prepStmt.setBoolean(colIdx + 1, bool)
                                }
                                else -> prepStmt.setString(colIdx + 1, value.toString())
                            }
                        }
                    }
                    prepStmt.addBatch()
                    if (rowIdx > 0 && rowIdx % 1000 == 0) {
                        prepStmt.executeBatch()
                    }
                }
                prepStmt.executeBatch()
                prepStmt.close()
            }
            stmt.close()

            registeredTables[sanitized] = df.rowCount.toLong()
            LOG.info("Registered DataFrame '${df.name}' as DuckDB table '$sanitized' (${df.rowCount} rows).")
        } catch (e: Exception) {
            LOG.warn("Failed to register DataFrame '$tableName' in DuckDB", e)
        }
    }

    /**
     * Executes analytical DuckDB SQL and converts result to a DataFrame.
     */
    fun executeQuery(sql: String, maxRows: Int = 10000): DuckDbLakehouseResult {
        val startTime = System.currentTimeMillis()
        val conn = getConnection()

        val stmt = conn.createStatement()
        stmt.maxRows = maxRows
        val rs = stmt.executeQuery(sql)

        val meta = rs.metaData
        val colCount = meta.columnCount
        val colNames = (1..colCount).map { meta.getColumnLabel(it) }
        val colTypes = (1..colCount).map { meta.getColumnType(it) }

        val rowsData = mutableListOf<List<Any?>>()
        var rowCount = 0L

        while (rs.next()) {
            rowCount++
            val row = mutableListOf<Any?>()
            for (i in 1..colCount) {
                val value: Any? = when (colTypes[i - 1]) {
                    Types.INTEGER, Types.BIGINT, Types.SMALLINT, Types.TINYINT -> rs.getObject(i)?.let { (it as Number).toLong() }
                    Types.FLOAT, Types.DOUBLE, Types.DECIMAL, Types.NUMERIC -> rs.getObject(i)?.let { (it as Number).toDouble() }
                    Types.BOOLEAN, Types.BIT -> rs.getObject(i)?.let { it as? Boolean ?: (it.toString() == "1") }
                    else -> rs.getString(i)
                }
                row.add(value)
            }
            rowsData.add(row)
        }

        rs.close()
        stmt.close()

        val rawCols = colNames.mapIndexed { idx, name ->
            val category = when (colTypes[idx]) {
                Types.INTEGER, Types.BIGINT, Types.SMALLINT, Types.TINYINT -> DataTypeCategory.INTEGER
                Types.FLOAT, Types.DOUBLE, Types.DECIMAL, Types.NUMERIC -> DataTypeCategory.FLOAT
                Types.BOOLEAN, Types.BIT -> DataTypeCategory.BOOLEAN
                else -> DataTypeCategory.STRING
            }
            Pair(name, category)
        }

        val resultDf = DataFrame.buildWithStatistics("DuckDB Query Result", rawCols, rowsData)
        val elapsed = System.currentTimeMillis() - startTime

        return DuckDbLakehouseResult(
            dataFrame = resultDf,
            executionTimeMs = elapsed,
            sql = sql,
            rowCount = rowCount,
            columnNames = colNames
        )
    }

    /**
     * Scans the workspace directory for queryable Parquet, CSV, and JSON data files.
     */
    fun scanLocalDataLakes(projectDir: File): List<LakehouseFileItem> {
        val items = mutableListOf<LakehouseFileItem>()
        if (!projectDir.exists() || !projectDir.isDirectory) return items

        projectDir.walkTopDown()
            .maxDepth(5)
            .filter { it.isFile && !it.name.startsWith(".") && it.length() > 0 }
            .forEach { file ->
                val ext = file.extension.lowercase()
                if (ext in listOf("parquet", "csv", "jsonl", "json", "tsv")) {
                    val relPath = file.relativeTo(projectDir).path.replace("\\", "/")
                    items.add(LakehouseFileItem(file, relPath, ext.uppercase(), file.length()))
                }
            }

        return items.sortedBy { it.relativePath }
    }

    /**
     * Pipes a DuckDB query result directly into the Scientific Plots panel.
     */
    fun pipeToScientificPlots(
        result: DuckDbLakehouseResult,
        title: String = "DuckDB Lakehouse Query",
        chartType: String = "BAR"
    ) {
        val df = result.dataFrame
        if (df.rowCount == 0 || df.columns.isEmpty()) return

        val width = 1200
        val height = 750
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        // Background
        g.color = Color(253, 246, 227) // Solarized Base3
        g.fillRect(0, 0, width, height)

        val px = 80
        val py = 80
        val pw = width - 160
        val ph = height - 160

        g.color = Color(238, 232, 213)
        g.fillRect(px, py, pw, ph)
        g.color = Color(147, 161, 161)
        g.drawRect(px, py, pw, ph)

        // Grid lines
        g.color = Color(245, 240, 225)
        for (i in 1..8) {
            val gy = py + (ph * i / 9)
            g.drawLine(px, gy, px + pw, gy)
        }

        // Title
        g.color = Color(7, 54, 66)
        g.font = Font("Segoe UI", Font.BOLD, 22)
        g.drawString("🦆 DuckDB Lakehouse: $title (${result.rowCount} rows in ${result.executionTimeMs}ms)", px, 50)

        val yColIdx = df.columns.indexOfFirst { it.isNumeric }.let { if (it >= 0) it else (df.columns.size - 1).coerceAtLeast(0) }
        val count = minOf(df.rowCount, 30)
        val yValues = (0 until count).map {
            val v = df.rows[it].getOrNull(yColIdx)
            (v as? Number)?.toDouble() ?: 0.0
        }
        val maxY = yValues.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0

        if (chartType == "BAR") {
            val barW = (pw / count.coerceAtLeast(1)).coerceAtLeast(8)
            g.color = Color(38, 139, 210) // Blue
            for (i in 0 until count) {
                val bx = px + (i * pw / count) + 4
                val bh = ((yValues[i] / maxY) * (ph - 40)).toInt()
                val by = py + ph - bh
                g.fillRect(bx, by, (barW - 8).coerceAtLeast(4), bh)
            }
        } else {
            g.color = Color(42, 161, 152) // Cyan line
            g.stroke = java.awt.BasicStroke(3f)
            for (i in 0 until count - 1) {
                val x1 = px + (i * pw / count)
                val y1 = py + ph - ((yValues[i] / maxY) * (ph - 40)).toInt()
                val x2 = px + ((i + 1) * pw / count)
                val y2 = py + ph - ((yValues[i + 1] / maxY) * (ph - 40)).toInt()
                g.drawLine(x1, y1, x2, y2)
                g.fillOval(x1 - 4, y1 - 4, 8, 8)
            }
        }

        g.dispose()

        val plotItem = PlotItem(
            id = UUID.randomUUID().toString(),
            title = "🦆 $title",
            source = "DuckDB Lakehouse",
            timestamp = System.currentTimeMillis(),
            image = img
        )
        PlotManagerService.getInstance().addPlot(plotItem)
        LOG.info("Piped DuckDB query result to Scientific Plots Panel.")
    }

    /**
     * Pipes a DuckDB query result into DataFrame Studio.
     */
    fun pipeToDataFrameStudio(result: DuckDbLakehouseResult, datasetName: String) {
        val dfService = ApplicationManager.getApplication()?.getService(DataFrameService::class.java)
        if (dfService != null) {
            val finalDf = DataFrame(name = datasetName, columns = result.dataFrame.columns, rows = result.dataFrame.rows)
            dfService.registerDataFrame(datasetName, finalDf)
            LOG.info("Piped DuckDB query result to DataFrame Studio as '$datasetName'.")
        }
    }

    fun getRegisteredTableNames(): List<String> = registeredTables.keys().toList().sorted()

    fun getTableCount(): Int = registeredTables.size

    private fun sanitizeTableName(name: String): String {
        val clean = name.replace(Regex("""[^a-zA-Z0-9_]"""), "_").trim('_')
        return if (clean.isEmpty() || clean.first().isDigit()) "tbl_$clean" else clean
    }
}
