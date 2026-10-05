/*
 * Copyright 2025–2026 indoctrinatedrecluse
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

package org.jormungandr.database.ui

import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import org.jormungandr.database.history.QueryHistoryEntry
import org.jormungandr.database.history.QueryHistoryManager
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.text.SimpleDateFormat
import java.util.Date
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.table.DefaultTableModel

/**
 * UI panel for reviewing, filtering, and recalling previous SQL query executions.
 */
class QueryHistoryPanel(
    private val onSelectQuery: (String) -> Unit
) : JPanel(BorderLayout()) {

    private val searchField = JBTextField(20)
    private val timeFormat = SimpleDateFormat("HH:mm:ss")
    private val tableModel = object : DefaultTableModel(
        arrayOf("Time", "Connection", "Duration", "Rows", "Status", "Query"), 0
    ) {
        override fun isCellEditable(row: Int, column: Int): Boolean = false
    }
    private val table = JBTable(tableModel)
    private var currentEntries: List<QueryHistoryEntry> = emptyList()

    init {
        border = EmptyBorder(6, 6, 6, 6)

        // Toolbar
        val toolbar = JPanel(BorderLayout()).apply {
            border = EmptyBorder(0, 0, 6, 0)
        }
        val left = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
            add(JBLabel("🔍 Search:"))
            add(searchField)
        }
        val right = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0))

        val copyBtn = JButton("Copy SQL").apply {
            isFocusable = false
            addActionListener { copySelectedQuery() }
        }
        val loadBtn = JButton("Load to Editor").apply {
            isFocusable = false
            addActionListener { loadSelectedQuery() }
        }
        val clearBtn = JButton("Clear").apply {
            isFocusable = false
            addActionListener {
                QueryHistoryManager.clear()
                refresh()
            }
        }

        right.add(copyBtn)
        right.add(loadBtn)
        right.add(clearBtn)

        toolbar.add(left, BorderLayout.WEST)
        toolbar.add(right, BorderLayout.EAST)
        add(toolbar, BorderLayout.NORTH)

        // Table
        table.autoResizeMode = JTable.AUTO_RESIZE_LAST_COLUMN
        table.columnModel.getColumn(0).preferredWidth = 70   // Time
        table.columnModel.getColumn(1).preferredWidth = 110  // Connection
        table.columnModel.getColumn(2).preferredWidth = 70   // Duration
        table.columnModel.getColumn(3).preferredWidth = 60   // Rows
        table.columnModel.getColumn(4).preferredWidth = 60   // Status
        table.columnModel.getColumn(5).preferredWidth = 350  // Query

        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    loadSelectedQuery()
                }
            }
        })

        add(JBScrollPane(table), BorderLayout.CENTER)

        // Search live filter
        searchField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = refresh()
            override fun removeUpdate(e: DocumentEvent?) = refresh()
            override fun changedUpdate(e: DocumentEvent?) = refresh()
        })

        refresh()
    }

    fun refresh() {
        val query = searchField.text.trim()
        currentEntries = QueryHistoryManager.getHistory(searchQuery = query)

        tableModel.rowCount = 0
        for (entry in currentEntries) {
            val status = if (entry.isSuccess) "✓ OK" else "✗ ERR"
            val timeStr = timeFormat.format(Date(entry.timestamp))
            tableModel.addRow(arrayOf(
                timeStr,
                entry.connectionName,
                "${entry.durationMs}ms",
                entry.rowCount.toString(),
                status,
                entry.query.replace("\n", " ").trim()
            ))
        }
    }

    private fun loadSelectedQuery() {
        val row = table.selectedRow
        if (row in currentEntries.indices) {
            val entry = currentEntries[row]
            onSelectQuery(entry.query)
        }
    }

    private fun copySelectedQuery() {
        val row = table.selectedRow
        if (row in currentEntries.indices) {
            val entry = currentEntries[row]
            val selection = StringSelection(entry.query)
            Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
        }
    }
}
