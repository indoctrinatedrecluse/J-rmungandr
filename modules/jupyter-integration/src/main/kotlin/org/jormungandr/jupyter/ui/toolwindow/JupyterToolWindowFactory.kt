package org.jormungandr.jupyter.ui.toolwindow

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.table.JBTable
import kotlinx.coroutines.*
import org.jormungandr.jupyter.kernel.JupyterKernelService
import org.jormungandr.jupyter.kernel.KernelDiscovery
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.table.DefaultTableModel

/**
 * Tool window displaying active Jupyter kernels, discovered specs, and runtime controls.
 */
class JupyterToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = JPanel(BorderLayout())
        val kernelService = ApplicationManager.getApplication().getService(JupyterKernelService::class.java)

        // Header Controls
        val header = JPanel(BorderLayout()).apply {
            border = EmptyBorder(6, 12, 6, 12)
        }
        val title = JLabel("🪐 Jupyter Active Kernels & ZeroMQ Runtimes").apply {
            font = font.deriveFont(Font.BOLD, 13f)
        }
        header.add(title, BorderLayout.WEST)

        val btnPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0))
        val refreshBtn = JButton("🔄 Refresh").apply {
            font = font.deriveFont(Font.PLAIN, 11f)
            isFocusPainted = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        }
        val restartAllBtn = JButton("⏹ Stop All").apply {
            font = font.deriveFont(Font.PLAIN, 11f)
            isFocusPainted = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        }
        btnPanel.add(refreshBtn)
        btnPanel.add(restartAllBtn)
        header.add(btnPanel, BorderLayout.EAST)
        panel.add(header, BorderLayout.NORTH)

        // Kernels Table
        val columnNames = arrayOf("Kernel Name", "Language", "Status", "Transport", "Process ID / Session")
        val tableModel = DefaultTableModel(columnNames, 0)
        val table = JBTable(tableModel).apply {
            rowHeight = 24
        }
        val scrollPane = JBScrollPane(table)
        panel.add(scrollPane, BorderLayout.CENTER)

        fun refreshTable() {
            tableModel.rowCount = 0
            val activeSessions = kernelService?.getActiveSessions() ?: emptyList()
            if (activeSessions.isEmpty()) {
                val specs = KernelDiscovery.discoverKernels()
                for (spec in specs) {
                    tableModel.addRow(arrayOf(spec.displayName, spec.language, "Standby (Available)", "ZeroMQ / Process", "Unbound"))
                }
            } else {
                for (session in activeSessions) {
                    tableModel.addRow(arrayOf(
                        session.spec.displayName,
                        session.spec.language,
                        session.status.value.displayName,
                        if (session.javaClass.simpleName.contains("Zmq")) "ZeroMQ (TCP)" else "Subprocess",
                        session.id.take(8)
                    ))
                }
            }
        }

        refreshBtn.addActionListener { refreshTable() }
        restartAllBtn.addActionListener {
            CoroutineScope(Dispatchers.IO).launch {
                val active = kernelService?.getActiveSessions() ?: emptyList()
                active.forEach { it.shutdown() }
                withContext(Dispatchers.Main) {
                    refreshTable()
                }
            }
        }

        refreshTable()

        val kernelsContent = ContentFactory.getInstance().createContent(panel, "Active Kernels", false)
        toolWindow.contentManager.addContent(kernelsContent)

        val variablePanel = org.jormungandr.jupyter.ui.VariableInspectorPanel(project)
        val varsContent = ContentFactory.getInstance().createContent(variablePanel, "Variable Inspector", false)
        toolWindow.contentManager.addContent(varsContent)

        val remoteGatewayPanel = org.jormungandr.jupyter.remote.RemoteJupyterGatewayPanel(project)
        val remoteContent = ContentFactory.getInstance().createContent(remoteGatewayPanel, "🌐 Remote Gateway", false)
        toolWindow.contentManager.addContent(remoteContent)
    }
}
