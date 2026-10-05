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

class QueryConsolePanel : JPanel(BorderLayout()) {

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

        leftTools.add(JBLabel("Connection:"))
        leftTools.add(connectionCombo)
        leftTools.add(runBtn)
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
    }
}
