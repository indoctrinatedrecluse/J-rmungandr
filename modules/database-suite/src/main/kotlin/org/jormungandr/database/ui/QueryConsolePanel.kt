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
import org.jormungandr.dataframe.codegen.DataFrameCodeGenerator
import org.jormungandr.dataframe.io.DataFrameExporter
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
            addActionListener { executeCurrentQuery() }
        }

        val historyBtn = JButton("📜 History").apply {
            isFocusable = false
            toolTipText = "View execution history"
            addActionListener {
                val dlg = QueryHistoryDialog(project) { sql ->
                    setQueryText(sql)
                }
                dlg.show()
            }
        }

        val exportBtn = JButton("⤓ Export").apply {
            isFocusable = false
            toolTipText = "Export query results"
            addActionListener {
                showExportMenu(this)
            }
        }

        leftTools.add(JBLabel("Connection:"))
        leftTools.add(connectionCombo)
        leftTools.add(runBtn)
        leftTools.add(historyBtn)
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
}
