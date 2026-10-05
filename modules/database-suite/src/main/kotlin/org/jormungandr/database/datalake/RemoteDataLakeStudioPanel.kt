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

package org.jormungandr.database.datalake

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextArea
import org.jormungandr.database.engine.DatabaseConnectionManager
import org.jormungandr.database.model.ConnectionConfig
import org.jormungandr.database.model.DatabaseDialect
import org.jormungandr.dataframe.model.DataFrame
import org.jormungandr.dataframe.ui.DataFrameGridPanel
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.table.DefaultTableModel

/**
 * Visual Data Lake & Object Storage Explorer Studio Panel.
 * Allows interactive browsing of S3/MinIO, GCS, and HTTP endpoints,
 * in-memory streaming preview, zero-copy DuckDB analytics, and Polars/PyArrow code export.
 */
class RemoteDataLakeStudioPanel(
    private val project: Project? = null,
    private val onSendToConsole: ((String) -> Unit)? = null
) : JPanel(BorderLayout()) {

    private val connectionCombo = JComboBox<String>()
    private var currentPrefix: String = ""
    private var objectList: List<RemoteObjectItem> = emptyList()

    private val pathField = JTextField(25).apply {
        isEditable = false
        text = "/"
    }

    private val tableModel = object : DefaultTableModel(
        arrayOf("Type", "Name", "Format", "Size", "Last Modified", "URI"), 0
    ) {
        override fun isCellEditable(row: Int, column: Int): Boolean = false
    }
    private val objectTable = JTable(tableModel).apply {
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        rowHeight = 22
    }

    private val gridPanel = DataFrameGridPanel(DataFrame.empty("Remote Data Preview"))
    private val duckDbArea = JBTextArea().apply {
        font = Font("Monospaced", Font.PLAIN, 13)
        isEditable = false
        margin = Insets(6, 6, 6, 6)
    }
    private val pythonArea = JBTextArea().apply {
        font = Font("Monospaced", Font.PLAIN, 13)
        isEditable = false
        margin = Insets(6, 6, 6, 6)
    }

    private val statusLabel = JBLabel("Ready").apply {
        foreground = Color(110, 110, 110)
    }

    init {
        val mainSplit = JSplitPane(JSplitPane.VERTICAL_SPLIT).apply {
            resizeWeight = 0.4
            isContinuousLayout = true
            border = null
        }

        // Top browser panel
        val topPanel = JPanel(BorderLayout())
        topPanel.add(createToolbar(), BorderLayout.NORTH)
        topPanel.add(JBScrollPane(objectTable), BorderLayout.CENTER)

        // Bottom preview & code panel
        val bottomTabs = JBTabbedPane()
        bottomTabs.addTab("📊 Live Stream Preview", gridPanel)
        bottomTabs.addTab("🦆 DuckDB Zero-Copy SQL", JBScrollPane(duckDbArea))
        bottomTabs.addTab("🐍 Polars / Python Script", JBScrollPane(pythonArea))

        mainSplit.topComponent = topPanel
        mainSplit.bottomComponent = bottomTabs

        add(mainSplit, BorderLayout.CENTER)
        add(createStatusBar(), BorderLayout.SOUTH)

        setupTableInteractions()
        refreshConnections()
    }

    private fun createToolbar(): JPanel {
        val panel = JPanel(BorderLayout()).apply {
            border = EmptyBorder(6, 8, 6, 8)
        }
        val left = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0))
        val right = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0))

        val upBtn = JButton("⬆ Up").apply {
            isFocusable = false
            font = font.deriveFont(Font.PLAIN, 11f)
            toolTipText = "Navigate to parent partition directory"
            addActionListener { navigateUp() }
        }

        val previewBtn = JButton("⚡ Stream Preview").apply {
            isFocusable = false
            font = font.deriveFont(Font.BOLD, 11f)
            foreground = Color(20, 120, 20)
            toolTipText = "Preview dataset stream directly into DataFrame Studio"
            addActionListener { previewSelectedObject() }
        }

        val duckDbBtn = JButton("🦆 Query in DuckDB").apply {
            isFocusable = false
            font = font.deriveFont(Font.PLAIN, 11f)
            toolTipText = "Send DuckDB remote query to SQL console"
            addActionListener {
                val sql = duckDbArea.text
                if (sql.isNotBlank()) {
                    onSendToConsole?.invoke(sql)
                }
            }
        }

        val copyPyBtn = JButton("📋 Copy Python").apply {
            isFocusable = false
            font = font.deriveFont(Font.PLAIN, 11f)
            addActionListener {
                val code = pythonArea.text
                Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(code), null)
                statusLabel.text = "Copied Python reader pipeline to clipboard"
            }
        }

        val refreshBtn = JButton("🔄").apply {
            isFocusable = false
            toolTipText = "Refresh bucket contents"
            addActionListener { refreshObjects() }
        }

        connectionCombo.addActionListener {
            currentPrefix = ""
            refreshObjects()
        }

        left.add(JBLabel("Bucket:"))
        left.add(connectionCombo)
        left.add(JBLabel("Path:"))
        left.add(pathField)
        left.add(upBtn)

        right.add(previewBtn)
        right.add(duckDbBtn)
        right.add(copyPyBtn)
        right.add(refreshBtn)

        panel.add(left, BorderLayout.WEST)
        panel.add(right, BorderLayout.EAST)
        return panel
    }

    private fun createStatusBar(): JPanel {
        val bar = JPanel(BorderLayout()).apply {
            border = EmptyBorder(3, 8, 3, 8)
        }
        bar.add(statusLabel, BorderLayout.WEST)
        return bar
    }

    private fun setupTableInteractions() {
        objectTable.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val row = objectTable.selectedRow
                if (row < 0 || row >= objectList.size) return
                val item = objectList[row]

                updateCodePanels(item)

                if (e.clickCount == 2) {
                    if (item.isPrefix) {
                        currentPrefix = item.key
                        refreshObjects()
                    } else {
                        previewSelectedObject()
                    }
                }
            }
        })
    }

    private fun navigateUp() {
        if (currentPrefix.isBlank() || currentPrefix == "/") return
        val trimmed = currentPrefix.trimEnd('/')
        currentPrefix = if (trimmed.contains('/')) {
            trimmed.substringBeforeLast('/') + "/"
        } else {
            ""
        }
        refreshObjects()
    }

    fun refreshConnections() {
        val prevSelected = connectionCombo.selectedItem as? String
        connectionCombo.removeAllItems()
        val configs = DatabaseConnectionManager.getAllConfigs()
        val lakeConfigs = configs.filter {
            it.dialect == DatabaseDialect.S3_DATA_LAKE ||
            it.dialect == DatabaseDialect.GCS_DATA_LAKE ||
            it.dialect == DatabaseDialect.HTTP_DATA_LAKE
        }

        for (cfg in lakeConfigs) {
            connectionCombo.addItem(cfg.name)
        }

        if (prevSelected != null) {
            connectionCombo.selectedItem = prevSelected
        } else if (lakeConfigs.isNotEmpty()) {
            connectionCombo.selectedIndex = 0
        }
        refreshObjects()
    }

    fun refreshObjects() {
        val config = getActiveConfig()
        if (config == null) {
            pathField.text = "/"
            tableModel.rowCount = 0
            statusLabel.text = "No active Data Lake storage connection"
            return
        }

        pathField.text = if (currentPrefix.isBlank()) "/" else "/$currentPrefix"
        objectList = DataLakeEngine.listObjects(config, currentPrefix)

        tableModel.rowCount = 0
        for (item in objectList) {
            val typeStr = if (item.isPrefix) "📁 Folder" else "📄 Object"
            tableModel.addRow(
                arrayOf(
                    typeStr,
                    item.name,
                    item.format.badge,
                    item.sizeFormatted,
                    item.lastModified,
                    item.uri
                )
            )
        }

        statusLabel.text = "Listed ${objectList.size} item(s) in '${pathField.text}'"

        val firstFile = objectList.firstOrNull { !it.isPrefix }
        if (firstFile != null) {
            updateCodePanels(firstFile)
        }
    }

    private fun updateCodePanels(item: RemoteObjectItem) {
        if (item.isPrefix) return
        duckDbArea.text = DataLakeEngine.generateDuckDbQuery(item)
        pythonArea.text = DataLakeEngine.generatePythonReaderCode(item)
    }

    private fun previewSelectedObject() {
        val config = getActiveConfig() ?: return
        val row = objectTable.selectedRow
        val item = if (row >= 0 && row < objectList.size) objectList[row] else objectList.firstOrNull { !it.isPrefix }

        if (item == null || item.isPrefix) {
            statusLabel.text = "Please select a remote dataset file to preview"
            return
        }

        statusLabel.text = "Streaming preview from ${item.uri}..."
        val startTime = System.currentTimeMillis()

        SwingUtilities.invokeLater {
            val df = DataLakeEngine.previewRemoteDataset(config, item)
            val elapsed = System.currentTimeMillis() - startTime
            gridPanel.dataFrame = df
            statusLabel.text = "Loaded stream preview (${df.rowCount} rows, ${df.columnCount} columns) in ${elapsed}ms"
            statusLabel.foreground = Color(20, 120, 20)
        }
    }

    private fun getActiveConfig(): ConnectionConfig? {
        val selected = connectionCombo.selectedItem as? String ?: return null
        return DatabaseConnectionManager.getAllConfigs().find { it.name == selected }
    }
}
