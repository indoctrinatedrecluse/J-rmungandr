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

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.components.*
import com.intellij.ui.table.JBTable
import kotlinx.coroutines.*
import org.jormungandr.dataframe.model.DataFrame
import org.jormungandr.dataframe.model.DataTypeCategory
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.io.File
import javax.swing.*
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.table.DefaultTableModel

/**
 * Interactive Studio Panel for the In-Memory DuckDB SQL Lakehouse.
 * Allows zero-copy SQL queries across active DataFrames and local Parquet/CSV files.
 */
class DuckDbLakehousePanel(
    val project: Project? = null
) : JPanel(BorderLayout()) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val sqlEditor = JBTextArea(8, 50)
    private val statusLabel = JBLabel("🦆 DuckDB Lakehouse Ready")

    private val resultTableModel = DefaultTableModel()
    private val resultTable = JBTable(resultTableModel)

    private val catalogListModel = DefaultListModel<String>()
    private val catalogList = JBList(catalogListModel)

    private var lastResult: DuckDbLakehouseResult? = null

    init {
        buildUi()
        refreshCatalog()
        loadDefaultQuery()
    }

    private fun buildUi() {
        // --- 1. Top Toolbar ---
        val toolbar = JPanel(BorderLayout(8, 0)).apply {
            border = CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Color(220, 220, 220)),
                EmptyBorder(6, 10, 6, 10)
            )
            background = Color(248, 249, 250)
        }

        val leftTools = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply { isOpaque = false }
        val runBtn = JButton("▶ Run SQL (Ctrl+Enter)").apply {
            isFocusable = false
            font = font.deriveFont(Font.BOLD, 12f)
            background = Color(224, 102, 0) // DuckDB Orange
            foreground = Color.WHITE
            addActionListener { executeSql() }
        }
        leftTools.add(runBtn)

        val templateCombo = JComboBox(
            arrayOf(
                "Templates & Lakehouse Patterns...",
                "Query Active DataFrame (active_df)",
                "Group By Aggregations & Summary",
                "Analytical Window Functions",
                "Scan Local Parquet Lake ('data/*.parquet')",
                "Direct CSV Lake Scan ('*.csv')",
                "Create View from Parquet File"
            )
        ).apply {
            isFocusable = false
            addActionListener {
                val selected = selectedItem as? String ?: return@addActionListener
                applyTemplate(selected)
            }
        }
        leftTools.add(templateCombo)

        val syncBtn = JButton("🔄 Sync DataFrames").apply {
            isFocusable = false
            toolTipText = "Sync all active DataFrames from DataFrame Studio into DuckDB memory"
            addActionListener {
                DuckDbLakehouseEngine.syncAllActiveDataFrames()
                refreshCatalog()
                statusLabel.text = "🦆 Synced ${DuckDbLakehouseEngine.getTableCount()} DataFrames into DuckDB."
            }
        }
        leftTools.add(syncBtn)

        val rightTools = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply { isOpaque = false }
        val lic = org.jormungandr.core.license.LicenseService.getInstance().currentLicense.value
        val licBadge = JBLabel(" [${lic.licenseType.name}] ").apply {
            font = font.deriveFont(Font.BOLD, 10.5f)
            foreground = when (lic.licenseType) {
                org.jormungandr.core.license.LicenseType.ADMIN -> Color(245, 158, 11)
                org.jormungandr.core.license.LicenseType.DEVELOPER -> Color(168, 85, 247)
                org.jormungandr.core.license.LicenseType.USER -> Color(16, 185, 129)
                org.jormungandr.core.license.LicenseType.TRIAL -> Color(100, 116, 139)
            }
        }
        rightTools.add(licBadge)
        val pipeDfBtn = JButton("📊 Open in Studio").apply {
            isFocusable = false
            toolTipText = "Open query result directly in DataFrame Viewer Studio"
            addActionListener {
                lastResult?.let { res ->
                    DuckDbLakehouseEngine.pipeToDataFrameStudio(res, "DuckDB Query: ${System.currentTimeMillis() % 1000}")
                    project?.let { p ->
                        ToolWindowManager.getInstance(p).getToolWindow("DataFrame Viewer")?.activate(null)
                    }
                }
            }
        }
        rightTools.add(pipeDfBtn)

        val pipePlotBtn = JButton("📈 Pipe to Plots").apply {
            isFocusable = false
            toolTipText = "Render query results into Scientific Plots Panel"
            addActionListener {
                lastResult?.let { res ->
                    DuckDbLakehouseEngine.pipeToScientificPlots(res, "DuckDB Query Result", "BAR")
                    project?.let { p ->
                        ToolWindowManager.getInstance(p).getToolWindow("Scientific Plots")?.activate(null)
                    }
                }
            }
        }
        rightTools.add(pipePlotBtn)

        val copyPythonBtn = JButton("🐍 Copy Python").apply {
            isFocusable = false
            toolTipText = "Copy equivalent duckdb.sql(...) Python snippet to clipboard"
            addActionListener {
                val sql = sqlEditor.text.trim().replace("\"", "\\\"")
                val py = "import duckdb\n\nres = duckdb.sql(\"\"\"\n$sql\n\"\"\")\nprint(res.show())\ndf = res.to_df()"
                Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(py), null)
                statusLabel.text = "Copied Python DuckDB snippet to clipboard."
            }
        }
        rightTools.add(copyPythonBtn)

        toolbar.add(leftTools, BorderLayout.WEST)
        toolbar.add(rightTools, BorderLayout.EAST)
        add(toolbar, BorderLayout.NORTH)

        // --- 2. Main Center Split (Catalog vs Editor & Results) ---
        catalogList.apply {
            font = Font("Segoe UI", Font.PLAIN, 12)
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            addListSelectionListener {
                val sel = selectedValue ?: return@addListSelectionListener
                if (sel.startsWith("📊 ")) {
                    val tbl = sel.substringAfter("📊 ").substringBefore(" (")
                    sqlEditor.text = "SELECT * FROM \"$tbl\" LIMIT 100;"
                } else if (sel.startsWith("📁 ")) {
                    val path = sel.substringAfter("📁 ").substringBefore(" (")
                    sqlEditor.text = "SELECT * FROM '$path' LIMIT 100;"
                }
            }
        }

        val catalogPanel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createTitledBorder("Lakehouse Catalog")
            preferredSize = Dimension(220, 400)
            add(JBScrollPane(catalogList), BorderLayout.CENTER)
        }

        // Editor & Table Split
        sqlEditor.apply {
            font = Font("Consolas", Font.PLAIN, 13)
            background = Color(253, 246, 227) // Solarized Base3
            foreground = Color(7, 54, 66)     // Solarized Base02
            border = EmptyBorder(6, 6, 6, 6)
            lineWrap = true
            wrapStyleWord = true
            addKeyListener(object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent) {
                    if (e.isControlDown && e.keyCode == KeyEvent.VK_ENTER) {
                        e.consume()
                        executeSql()
                    }
                }
            })
        }

        val editorPanel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createTitledBorder("DuckDB SQL Editor (Ctrl+Enter to Run)")
            add(JBScrollPane(sqlEditor), BorderLayout.CENTER)
        }

        resultTable.apply {
            autoResizeMode = JTable.AUTO_RESIZE_OFF
            fillsViewportHeight = true
        }

        val resultPanel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createTitledBorder("Query Results")
            add(JBScrollPane(resultTable), BorderLayout.CENTER)
        }

        val rightSplit = JSplitPane(JSplitPane.VERTICAL_SPLIT, editorPanel, resultPanel).apply {
            resizeWeight = 0.35
            dividerSize = 4
        }

        val mainSplit = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, catalogPanel, rightSplit).apply {
            resizeWeight = 0.22
            dividerSize = 4
        }
        add(mainSplit, BorderLayout.CENTER)

        // --- 3. Bottom Status Bar ---
        val statusBar = JPanel(BorderLayout()).apply {
            border = CompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Color(220, 220, 220)),
                EmptyBorder(4, 10, 4, 10)
            )
            background = Color(248, 249, 250)
        }
        statusBar.add(statusLabel, BorderLayout.WEST)
        add(statusBar, BorderLayout.SOUTH)
    }

    private fun loadDefaultQuery() {
        sqlEditor.text = """
            -- In-Memory DuckDB SQL Lakehouse
            SELECT 
                'DuckDB Embedded Lakehouse' AS engine,
                version() AS version,
                current_date AS today,
                42 AS answer;
        """.trimIndent()
    }

    private fun refreshCatalog() {
        catalogListModel.clear()

        // 1. In-Memory Tables
        DuckDbLakehouseEngine.getRegisteredTableNames().forEach { name ->
            catalogListModel.addElement("📊 $name")
        }

        // 2. Scan Local Files
        val baseDir = project?.basePath?.let { File(it) } ?: File(".")
        val files = DuckDbLakehouseEngine.scanLocalDataLakes(baseDir)
        files.forEach { fileItem ->
            val sizeKb = (fileItem.sizeBytes / 1024).coerceAtLeast(1)
            catalogListModel.addElement("📁 ${fileItem.relativePath} (${sizeKb}KB)")
        }

        if (catalogListModel.isEmpty) {
            catalogListModel.addElement("📊 active_df (Sample Table)")
        }
    }

    private fun executeSql() {
        val sql = sqlEditor.text.trim()
        if (sql.isEmpty()) return

        if (!org.jormungandr.core.license.LicenseService.getInstance().isLicensed()) {
            JOptionPane.showMessageDialog(
                this,
                "DuckDB Lakehouse Query Execution is locked in Trial Mode.\nA valid User, Developer, or Admin commercial license is required to run queries.",
                "Commercial License Required",
                JOptionPane.WARNING_MESSAGE
            )
            return
        }

        statusLabel.text = "Executing DuckDB analytical query..."

        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    DuckDbLakehouseEngine.executeQuery(sql)
                }
                lastResult = result

                // Populate Table
                resultTableModel.setRowCount(0)
                resultTableModel.setColumnIdentifiers(result.columnNames.toTypedArray())

                val df = result.dataFrame
                for (rowIdx in 0 until df.rowCount) {
                    val rowData = Array<Any?>(df.columnCount) { colIdx ->
                        df.rows[rowIdx].getOrNull(colIdx)
                    }
                    resultTableModel.addRow(rowData)
                }

                statusLabel.text = "🦆 Query finished in ${result.executionTimeMs}ms | ${result.rowCount} rows (${result.columnNames.size} columns)."
            } catch (e: Exception) {
                statusLabel.text = "❌ Error: ${e.message}"
            }
        }
    }

    private fun applyTemplate(name: String) {
        val sql = when (name) {
            "Query Active DataFrame (active_df)" -> """
                SELECT * 
                FROM active_df 
                LIMIT 50;
            """.trimIndent()

            "Group By Aggregations & Summary" -> """
                SELECT 
                    COUNT(*) AS total_records,
                    AVG(1.0) AS avg_value
                FROM active_df;
            """.trimIndent()

            "Analytical Window Functions" -> """
                SELECT *,
                    ROW_NUMBER() OVER () AS row_num,
                    COUNT(*) OVER () AS total_window_count
                FROM active_df
                LIMIT 100;
            """.trimIndent()

            "Scan Local Parquet Lake ('data/*.parquet')" -> """
                SELECT * 
                FROM 'data/**/*.parquet' 
                LIMIT 50;
            """.trimIndent()

            "Direct CSV Lake Scan ('*.csv')" -> """
                SELECT * 
                FROM read_csv_auto('*.csv') 
                LIMIT 50;
            """.trimIndent()

            "Create View from Parquet File" -> """
                CREATE OR REPLACE VIEW lakehouse_view AS 
                SELECT * FROM 'data/dataset.parquet';
                
                SELECT * FROM lakehouse_view LIMIT 25;
            """.trimIndent()

            else -> return
        }
        sqlEditor.text = sql
        sqlEditor.requestFocus()
    }
}
