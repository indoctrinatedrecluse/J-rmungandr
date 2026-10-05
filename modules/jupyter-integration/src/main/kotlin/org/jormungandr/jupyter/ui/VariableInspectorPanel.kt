package org.jormungandr.jupyter.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.jormungandr.jupyter.variable.VariableInfo
import org.jormungandr.jupyter.variable.VariableInspectorService
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.table.DefaultTableModel

/**
 * Dedicated Variable Inspector UI Panel.
 * Displays active Python workspace variables with real-time shapes, types, sizes, and DataFrame integration.
 */
class VariableInspectorPanel(
    private val project: Project
) : JPanel(BorderLayout()) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val service = VariableInspectorService.getInstance(project)

    private val searchField = JBTextField(16)
    private val statusLabel = JBLabel("No variables active in current kernel.")

    private val columnNames = arrayOf("Name", "Type", "Shape", "Size", "Value Preview")
    private val tableModel = object : DefaultTableModel(columnNames, 0) {
        override fun isCellEditable(row: Int, column: Int): Boolean = false
    }
    private val table = JBTable(tableModel)

    private var allVariables: List<VariableInfo> = emptyList()

    init {
        setupTable()

        val topPanel = JPanel(BorderLayout()).apply {
            border = EmptyBorder(6, 8, 6, 8)
            background = Color(248, 250, 252)
        }

        val left = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply { isOpaque = false }
        searchField.emptyText.text = "Filter variables..."
        searchField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = applyFilter()
            override fun removeUpdate(e: DocumentEvent?) = applyFilter()
            override fun changedUpdate(e: DocumentEvent?) = applyFilter()
        })
        left.add(JBLabel("🔍 Search:"))
        left.add(searchField)

        val right = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply { isOpaque = false }
        val refreshBtn = JButton("🔄 Refresh").apply {
            font = font.deriveFont(Font.PLAIN, 11f)
            addActionListener { triggerRefresh() }
        }
        val openDfBtn = JButton("📊 Open in DataFrame Studio").apply {
            font = font.deriveFont(Font.BOLD, 11f)
            foreground = Color(30, 64, 175)
            toolTipText = "Open selected variable in Jörmungandr DataFrame Studio"
            addActionListener { openSelectedInDataFrameStudio() }
        }
        val copyBtn = JButton("📋 Copy").apply {
            font = font.deriveFont(Font.PLAIN, 11f)
            addActionListener { copySelectedValue() }
        }

        val plotVarBtn = JButton("📈 Plot Variable").apply {
            font = font.deriveFont(Font.BOLD, 11f)
            foreground = Color(5, 150, 105)
            toolTipText = "Generate quick Matplotlib plot for selected NumPy array, Series, or variable"
            addActionListener { plotSelectedVariable() }
        }

        right.add(refreshBtn)
        right.add(openDfBtn)
        right.add(plotVarBtn)
        right.add(copyBtn)

        topPanel.add(left, BorderLayout.WEST)
        topPanel.add(right, BorderLayout.EAST)

        val bottomPanel = JPanel(BorderLayout()).apply {
            border = EmptyBorder(4, 8, 4, 8)
            background = Color(248, 250, 252)
            add(statusLabel, BorderLayout.WEST)
        }

        add(topPanel, BorderLayout.NORTH)
        add(JBScrollPane(table), BorderLayout.CENTER)
        add(bottomPanel, BorderLayout.SOUTH)

        // Observe variables StateFlow
        scope.launch {
            service.variables.collect { vars ->
                allVariables = vars
                applyFilter()
            }
        }
    }

    private fun setupTable() {
        table.rowHeight = 24
        table.setShowGrid(true)
        table.gridColor = Color(229, 231, 235)
        table.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION

        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    openSelectedInDataFrameStudio()
                }
            }
        })
    }

    private fun applyFilter() {
        val query = searchField.text.trim().lowercase()
        val filtered = if (query.isEmpty()) {
            allVariables
        } else {
            allVariables.filter {
                it.name.lowercase().contains(query) || it.typeName.lowercase().contains(query)
            }
        }

        tableModel.rowCount = 0
        for (v in filtered) {
            tableModel.addRow(arrayOf(
                v.name,
                v.typeName,
                v.shape,
                v.sizeFormatted,
                v.preview
            ))
        }

        statusLabel.text = "Total Active Variables: ${allVariables.size} | Filtered: ${filtered.size}"
    }

    private fun triggerRefresh() {
        val session = service.getActiveSession()
        if (session != null) {
            statusLabel.text = "Refreshing workspace variables..."
            scope.launch(Dispatchers.IO) {
                service.refresh(session)
            }
        } else {
            statusLabel.text = "⚠️ No active kernel session to inspect."
        }
    }

    private fun openSelectedInDataFrameStudio() {
        val row = table.selectedRow
        if (row < 0 || row >= tableModel.rowCount) return
        val varName = tableModel.getValueAt(row, 0)?.toString() ?: return

        val session = service.getActiveSession()
        if (session == null) {
            JOptionPane.showMessageDialog(this, "No active kernel session connected.", "DataFrame Export", JOptionPane.WARNING_MESSAGE)
            return
        }

        scope.launch(Dispatchers.IO) {
            val csv = service.exportVariableToCsv(session, varName)
            if (csv != null) {
                val tempFile = File.createTempFile("jupyter_${varName}_", ".csv")
                tempFile.writeText(csv, Charsets.UTF_8)
                tempFile.deleteOnExit()

                ApplicationManager.getApplication().invokeLater {
                    val vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempFile)
                    if (vFile != null) {
                        FileEditorManager.getInstance(project).openFile(vFile, true)
                    }
                }
            } else {
                ApplicationManager.getApplication().invokeLater {
                    JOptionPane.showMessageDialog(this@VariableInspectorPanel, "Could not export variable '$varName' as tabular dataset.", "Export Warning", JOptionPane.WARNING_MESSAGE)
                }
            }
        }
    }

    private fun copySelectedValue() {
        val row = table.selectedRow
        if (row < 0 || row >= tableModel.rowCount) return
        val preview = tableModel.getValueAt(row, 4)?.toString() ?: return
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(preview), null)
    }

    private fun plotSelectedVariable() {
        val row = table.selectedRow
        if (row < 0 || row >= tableModel.rowCount) return
        val varName = tableModel.getValueAt(row, 0)?.toString() ?: return

        val session = service.getActiveSession()
        if (session == null) {
            JOptionPane.showMessageDialog(this, "No active kernel session connected.", "Plot Variable", JOptionPane.WARNING_MESSAGE)
            return
        }

        statusLabel.text = "Generating plot for '$varName'..."
        scope.launch(Dispatchers.IO) {
            val img = service.plotVariable(session, varName)
            if (img != null) {
                val plotItem = org.jormungandr.core.plot.PlotItem(
                    title = "Plot: $varName",
                    source = "Variable Inspector",
                    image = img
                )
                org.jormungandr.core.plot.PlotManagerService.getInstance().addPlot(plotItem)
                ApplicationManager.getApplication().invokeLater {
                    statusLabel.text = "✓ Plot generated for '$varName' and added to Scientific Plots"
                    runCatching {
                        val tw = com.intellij.openapi.wm.ToolWindowManager.getInstance(project).getToolWindow("Scientific Plots")
                        tw?.show()
                    }
                }
            } else {
                ApplicationManager.getApplication().invokeLater {
                    statusLabel.text = "⚠️ Could not plot variable '$varName'"
                }
            }
        }
    }
}
