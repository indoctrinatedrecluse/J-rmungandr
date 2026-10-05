package org.jormungandr.database.ui

import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import org.jormungandr.database.engine.DatabaseConnectionManager
import org.jormungandr.database.engine.SqlQueryExecutor
import org.jormungandr.database.model.ConnectionConfig
import org.jormungandr.dataframe.model.DataFrame
import org.jormungandr.dataframe.ui.DataFrameGridPanel
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.*
import javax.swing.border.EmptyBorder

import org.jormungandr.database.history.QueryHistoryManager
import org.jormungandr.database.engine.ExplainPlanEngine
import org.jormungandr.dataframe.codegen.DataFrameCodeGenerator
import org.jormungandr.dataframe.io.DataFrameExporter
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

class QueryConsolePanel(private val project: com.intellij.openapi.project.Project? = null) : JPanel(BorderLayout()) {

    private val connectionCombo = JComboBox<String>()
    private val queryEditor = JBTextArea(6, 40)
    private val limitCombo = JComboBox(arrayOf(100, 500, 1000, 5000))
    private val timerLabel = JBLabel("⏱ Ready")
    private val statusLabel = JBLabel("")

    private val gridPanel = DataFrameGridPanel(DataFrame.empty("Query Results"))

    init {
        val topPanel = JPanel(BorderLayout())

        // Toolbar
        val toolbar = JPanel(BorderLayout()).apply {
            border = EmptyBorder(4, 8, 4, 8)
        }
        val leftTools = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0))
        val rightTools = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0))

        val runBtn = JButton("▶ Run (Ctrl+Enter)").apply {
            isFocusable = false
            font = font.deriveFont(Font.BOLD, 11f)
            addActionListener { executeCurrentQuery() }
        }

        val explainBtn = JButton("🔍 Explain Plan").apply {
            isFocusable = false
            font = font.deriveFont(Font.PLAIN, 11f)
            toolTipText = "Inspect query execution plan and detect table scans"
            addActionListener { explainCurrentQuery() }
        }

        val openDfStudioBtn = JButton("📊 Open in DataFrame Studio").apply {
            isFocusable = false
            font = font.deriveFont(Font.BOLD, 11f)
            foreground = Color(30, 64, 175)
            toolTipText = "Open current query results in Jörmungandr DataFrame Studio"
            addActionListener { openInDataFrameStudio() }
        }

        val historyBtn = JButton("📜 History").apply {
            isFocusable = false
            font = font.deriveFont(Font.PLAIN, 11f)
            toolTipText = "View execution history"
            addActionListener {
                val dlg = QueryHistoryDialog(project) { sql ->
                    setQueryText(sql)
                }
                dlg.show()
            }
        }

        val templatesBtn = JButton("📝 Templates ▾").apply {
            isFocusable = false
            font = font.deriveFont(Font.PLAIN, 11f)
            toolTipText = "Common SQL query templates (Analytics, DuckDB, Parquet)"
            addActionListener { showTemplatesMenu(this) }
        }

        val exportBtn = JButton("⤓ Export").apply {
            isFocusable = false
            font = font.deriveFont(Font.PLAIN, 11f)
            toolTipText = "Export query results"
            addActionListener {
                showExportMenu(this)
            }
        }

        leftTools.add(JBLabel("Connection:"))
        leftTools.add(connectionCombo)
        leftTools.add(runBtn)
        leftTools.add(explainBtn)
        leftTools.add(openDfStudioBtn)
        leftTools.add(historyBtn)
        leftTools.add(templatesBtn)
        leftTools.add(exportBtn)
        leftTools.add(JBLabel("Limit:"))
        leftTools.add(limitCombo)

        rightTools.add(timerLabel)
        rightTools.add(statusLabel)

        toolbar.add(leftTools, BorderLayout.WEST)
        toolbar.add(rightTools, BorderLayout.EAST)

        // Editor
        queryEditor.font = Font("Monospaced", Font.PLAIN, 13)
        queryEditor.text = "SELECT 1 AS id, 'Hello Jörmungandr' AS greeting;"
        queryEditor.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.isControlDown && e.keyCode == KeyEvent.VK_ENTER) {
                    executeCurrentQuery()
                    e.consume()
                }
            }
        })
        val editorScroll = JBScrollPane(queryEditor).apply {
            preferredSize = Dimension(600, 120)
        }

        topPanel.add(toolbar, BorderLayout.NORTH)
        topPanel.add(editorScroll, BorderLayout.CENTER)

        val split = JSplitPane(JSplitPane.VERTICAL_SPLIT, topPanel, gridPanel).apply {
            resizeWeight = 0.25
            isContinuousLayout = true
            border = null
        }

        add(split, BorderLayout.CENTER)
        refreshConnections()
    }

    fun setQueryText(sql: String) {
        queryEditor.text = sql
    }

    fun refreshConnections() {
        val selected = connectionCombo.selectedItem as? String
        connectionCombo.removeAllItems()
        val configs = DatabaseConnectionManager.getAllConfigs()
        for (cfg in configs) {
            connectionCombo.addItem(cfg.name)
        }
        if (selected != null) {
            connectionCombo.selectedItem = selected
        }
    }

    fun executeCurrentQuery() {
        val connName = connectionCombo.selectedItem as? String
        val configs = DatabaseConnectionManager.getAllConfigs()
        val config = configs.find { it.name == connName } ?: configs.firstOrNull()

        if (config == null) {
            statusLabel.text = "No active connection"
            statusLabel.foreground = Color(200, 50, 50)
            return
        }

        val conn = DatabaseConnectionManager.getConnection(config.id)
            ?: runCatching { DatabaseConnectionManager.connect(config) }.getOrNull()

        if (conn == null) {
            statusLabel.text = "Failed to connect to ${config.name}"
            statusLabel.foreground = Color(200, 50, 50)
            return
        }

        val sql = queryEditor.text.trim()
        val limit = limitCombo.selectedItem as? Int ?: 1000

        timerLabel.text = "⏱ Running..."
        val result = SqlQueryExecutor.execute(conn, sql, limit, "query_results")
        timerLabel.text = "⏱ ${result.executionTimeMs} ms"

        if (result.isSuccess) {
            val df = result.dataFrame ?: DataFrame.empty("query_results")
            gridPanel.dataFrame = df
            statusLabel.text = "✓ ${df.rowCount} rows returned"
            statusLabel.foreground = Color(40, 160, 60)
        } else {
            statusLabel.text = "✗ ${result.errorMessage}"
            statusLabel.foreground = Color(200, 50, 50)
        }

        // Record execution in history
        QueryHistoryManager.recordExecution(
            connectionId = config.id,
            connectionName = config.name,
            query = sql,
            durationMs = result.executionTimeMs,
            rowCount = result.dataFrame?.rowCount?.toInt() ?: result.rowsAffected,
            isSuccess = result.isSuccess,
            errorMessage = result.errorMessage
        )
    }

    private fun showExportMenu(anchor: JComponent) {
        val df = gridPanel.dataFrame
        if (df.rowCount == 0) {
            statusLabel.text = "⚠️ No query results to export"
            statusLabel.foreground = Color(200, 140, 40)
            return
        }

        val menu = JPopupMenu()
        menu.add(JMenuItem("Copy as CSV").apply {
            addActionListener {
                val csv = DataFrameExporter.toCsv(df)
                copyToClipboard(csv)
                statusLabel.text = "✓ Copied CSV (${df.rowCount} rows) to clipboard"
                statusLabel.foreground = Color(40, 160, 60)
            }
        })
        menu.add(JMenuItem("Copy as JSON").apply {
            addActionListener {
                val json = DataFrameExporter.toJson(df)
                copyToClipboard(json)
                statusLabel.text = "✓ Copied JSON (${df.rowCount} rows) to clipboard"
                statusLabel.foreground = Color(40, 160, 60)
            }
        })
        menu.add(JMenuItem("Copy as Markdown").apply {
            addActionListener {
                val md = DataFrameExporter.toMarkdown(df)
                copyToClipboard(md)
                statusLabel.text = "✓ Copied Markdown to clipboard"
                statusLabel.foreground = Color(40, 160, 60)
            }
        })
        menu.add(JMenuItem("Copy as SQL DDL + INSERTs").apply {
            addActionListener {
                val sql = DataFrameCodeGenerator.toSqlDdlAndInserts(df)
                copyToClipboard(sql)
                statusLabel.text = "✓ Copied SQL statements to clipboard"
                statusLabel.foreground = Color(40, 160, 60)
            }
        })
        menu.show(anchor, 0, anchor.height)
    }

    private fun copyToClipboard(text: String) {
        val sel = StringSelection(text)
        Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, sel)
    }

    fun explainCurrentQuery() {
        val connName = connectionCombo.selectedItem as? String
        val configs = DatabaseConnectionManager.getAllConfigs()
        val config = configs.find { it.name == connName } ?: configs.firstOrNull()

        if (config == null) {
            statusLabel.text = "No active connection"
            statusLabel.foreground = Color(200, 50, 50)
            return
        }

        val conn = DatabaseConnectionManager.getConnection(config.id)
            ?: runCatching { DatabaseConnectionManager.connect(config) }.getOrNull()

        if (conn == null) {
            statusLabel.text = "Failed to connect to ${config.name}"
            statusLabel.foreground = Color(200, 50, 50)
            return
        }

        val sql = queryEditor.text.trim()
        val plan = ExplainPlanEngine.explain(conn, config.dialect, sql)
        ExplainPlanDialog(project, plan).show()
    }

    fun openInDataFrameStudio() {
        val df = gridPanel.dataFrame
        if (df.rowCount == 0) {
            statusLabel.text = "⚠️ No query results to open in DataFrame Studio"
            statusLabel.foreground = Color(200, 140, 40)
            return
        }

        val p = project
        if (p == null) {
            statusLabel.text = "⚠️ Project context not available"
            return
        }

        runCatching {
            val csv = DataFrameExporter.toCsv(df)
            val tempFile = File.createTempFile("db_result_", ".csv")
            tempFile.writeText(csv, Charsets.UTF_8)
            tempFile.deleteOnExit()

            val vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempFile)
            if (vFile != null) {
                FileEditorManager.getInstance(p).openFile(vFile, true)
                statusLabel.text = "✓ Opened ${df.rowCount} rows in DataFrame Studio"
                statusLabel.foreground = Color(40, 160, 60)
            }
        }.onFailure { e ->
            statusLabel.text = "✗ Could not open in DataFrame Studio: ${e.message}"
            statusLabel.foreground = Color(200, 50, 50)
        }
    }

    private fun showTemplatesMenu(anchor: JComponent) {
        val menu = JPopupMenu()
        menu.add(JMenuItem("🦆 DuckDB: Query Parquet File Directly").apply {
            addActionListener {
                setQueryText("SELECT * FROM 'data.parquet' LIMIT 50;")
            }
        })
        menu.add(JMenuItem("🦆 DuckDB: Query CSV File Directly").apply {
            addActionListener {
                setQueryText("SELECT * FROM read_csv_auto('dataset.csv') LIMIT 50;")
            }
        })
        menu.add(JMenuItem("🦆 DuckDB: Analytical Window & Aggregation").apply {
            addActionListener {
                setQueryText("SELECT category, COUNT(*) AS total_items, AVG(price) AS avg_price,\n       RANK() OVER (ORDER BY COUNT(*) DESC) AS category_rank\nFROM sales\nGROUP BY category;")
            }
        })
        menu.addSeparator()
        menu.add(JMenuItem("🗄️ Filter & Sort Query").apply {
            addActionListener {
                setQueryText("SELECT * FROM users WHERE active = 1 ORDER BY id DESC LIMIT 100;")
            }
        })
        menu.add(JMenuItem("🗄️ Join Two Tables").apply {
            addActionListener {
                setQueryText("SELECT o.*, u.name AS user_name, u.email\nFROM orders o\nJOIN users u ON o.user_id = u.id\nLIMIT 50;")
            }
        })
        menu.show(anchor, 0, anchor.height)
    }
}
