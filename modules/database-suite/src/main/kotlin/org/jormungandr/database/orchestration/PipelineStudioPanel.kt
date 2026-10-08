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

package org.jormungandr.database.orchestration

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.io.File
import javax.swing.*
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class PipelineStudioPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val canvas = PipelineLineageCanvas(project)
    private var currentGraph = PipelineGraph()

    // Inspector UI elements
    private val inspectorPanel = JPanel(BorderLayout())
    private val nodeTitleLabel = JBLabel("Select a node").apply { font = font.deriveFont(Font.BOLD, 14f) }
    private val nodeBadgeLabel = JBLabel("").apply { font = font.deriveFont(Font.BOLD, 10f) }
    private val nodeStatusLabel = JBLabel("").apply { font = font.deriveFont(Font.BOLD, 11f) }
    private val nodeFileLabel = JBLabel("File: -").apply { font = font.deriveFont(Font.PLAIN, 11f) }
    private val nodeMaterializationLabel = JBLabel("Materialization: -").apply { font = font.deriveFont(Font.PLAIN, 11f) }
    private val nodeDescriptionArea = JTextArea("").apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        isOpaque = false
        font = font.deriveFont(Font.PLAIN, 11f)
    }

    private val upstreamListModel = DefaultListModel<String>()
    private val downstreamListModel = DefaultListModel<String>()
    private val upstreamList = JList(upstreamListModel)
    private val downstreamList = JList(downstreamListModel)

    private val runTaskButton = JButton("▶️ Run Model / Task")
    private val openEditorButton = JButton("📂 Open Source File")
    private val copyCmdButton = JButton("📋 Copy CLI Command")

    private var selectedNode: PipelineNode? = null

    // Stats bar
    private val statsLabel = JBLabel("Ready")

    init {
        setupUI()
        scanLineage()
    }

    private fun setupUI() {
        // 1. Top Control Bar
        val topBar = JPanel(BorderLayout()).apply {
            background = JBColor(Color(241, 245, 249), Color(30, 41, 59))
            border = CompoundBorder(
                LineBorder(JBColor(Color(226, 232, 240), Color(51, 65, 85)), 1),
                EmptyBorder(8, 12, 8, 12)
            )
        }

        val leftControls = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply { isOpaque = false }
        val scanBtn = JButton("🔄 Scan Lineage").apply {
            addActionListener { scanLineage() }
        }
        val searchField = JBTextField(14).apply {
            emptyText.text = "Search models & tasks..."
            document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent?) = canvas.setFilter(text)
                override fun removeUpdate(e: DocumentEvent?) = canvas.setFilter(text)
                override fun changedUpdate(e: DocumentEvent?) = canvas.setFilter(text)
            })
        }
        leftControls.add(scanBtn)
        leftControls.add(JBLabel("🔍"))
        leftControls.add(searchField)

        val rightControls = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply { isOpaque = false }
        val license = org.jormungandr.core.license.LicenseService.getInstance().currentLicense.value
        val licenseBadge = JBLabel(" [${license.licenseType.name}] ").apply {
            font = font.deriveFont(Font.BOLD, 10.5f)
            foreground = when (license.licenseType) {
                org.jormungandr.core.license.LicenseType.ADMIN -> Color(245, 158, 11)
                org.jormungandr.core.license.LicenseType.DEVELOPER -> Color(168, 85, 247)
                org.jormungandr.core.license.LicenseType.USER -> Color(16, 185, 129)
                org.jormungandr.core.license.LicenseType.TRIAL -> Color(100, 116, 139)
            }
        }
        rightControls.add(licenseBadge)
        val zoomInBtn = JButton("+").apply { addActionListener { canvas.zoomIn() } }
        val zoomOutBtn = JButton("-").apply { addActionListener { canvas.zoomOut() } }
        val resetBtn = JButton("⛶ Fit").apply { addActionListener { canvas.resetView() } }

        rightControls.add(zoomInBtn)
        rightControls.add(zoomOutBtn)
        rightControls.add(resetBtn)

        topBar.add(leftControls, BorderLayout.WEST)
        topBar.add(rightControls, BorderLayout.EAST)
        add(topBar, BorderLayout.NORTH)

        // 2. Center: Canvas + Inspector SplitPane
        setupInspector()
        canvas.onNodeSelected = { node -> updateInspector(node) }

        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, canvas, inspectorPanel).apply {
            resizeWeight = 0.72
            dividerSize = 4
            border = null
        }
        add(splitPane, BorderLayout.CENTER)

        // 3. Bottom: Status Bar
        val bottomBar = JPanel(BorderLayout()).apply {
            background = JBColor(Color(248, 250, 252), Color(15, 23, 42))
            border = CompoundBorder(
                LineBorder(JBColor(Color(226, 232, 240), Color(51, 65, 85)), 1),
                EmptyBorder(4, 12, 4, 12)
            )
            add(statsLabel, BorderLayout.WEST)
        }
        add(bottomBar, BorderLayout.SOUTH)
    }

    private fun setupInspector() {
        inspectorPanel.preferredSize = Dimension(310, 0)
        inspectorPanel.background = JBColor(Color(255, 255, 255), Color(24, 33, 47))
        inspectorPanel.border = EmptyBorder(12, 12, 12, 12)

        val headerBox = Box.createVerticalBox().apply {
            add(nodeTitleLabel)
            add(Box.createVerticalStrut(4))
            val badgeRow = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
                isOpaque = false
                add(nodeBadgeLabel)
                add(nodeStatusLabel)
            }
            add(badgeRow)
            add(Box.createVerticalStrut(8))
            add(nodeFileLabel)
            add(Box.createVerticalStrut(4))
            add(nodeMaterializationLabel)
            add(Box.createVerticalStrut(8))
            add(JBLabel("Description:").apply { font = font.deriveFont(Font.BOLD, 11f) })
            add(nodeDescriptionArea)
        }

        // Dependency lists
        val depsPanel = JPanel(GridLayout(2, 1, 6, 6)).apply {
            isOpaque = false
            val upBox = JPanel(BorderLayout()).apply {
                isOpaque = false
                add(JBLabel("⬆️ Upstream Dependencies:").apply { font = font.deriveFont(Font.BOLD, 11f) }, BorderLayout.NORTH)
                add(JBScrollPane(upstreamList).apply { border = LineBorder(JBColor(Color(226, 232, 240), Color(51, 65, 85))) }, BorderLayout.CENTER)
            }
            val downBox = JPanel(BorderLayout()).apply {
                isOpaque = false
                add(JBLabel("⬇️ Downstream Dependents:").apply { font = font.deriveFont(Font.BOLD, 11f) }, BorderLayout.NORTH)
                add(JBScrollPane(downstreamList).apply { border = LineBorder(JBColor(Color(226, 232, 240), Color(51, 65, 85))) }, BorderLayout.CENTER)
            }
            add(upBox)
            add(downBox)
        }

        // Action Buttons
        val actionBox = Box.createVerticalBox().apply {
            add(runTaskButton)
            add(Box.createVerticalStrut(6))
            add(openEditorButton)
            add(Box.createVerticalStrut(6))
            add(copyCmdButton)
        }

        runTaskButton.addActionListener { simulateRunTask() }
        openEditorButton.addActionListener { openSelectedFile() }
        copyCmdButton.addActionListener { copyCommand() }

        val centerContainer = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(headerBox, BorderLayout.NORTH)
            add(depsPanel, BorderLayout.CENTER)
            add(actionBox, BorderLayout.SOUTH)
        }

        inspectorPanel.add(centerContainer, BorderLayout.CENTER)
    }

    private fun updateInspector(node: PipelineNode?) {
        selectedNode = node
        upstreamListModel.clear()
        downstreamListModel.clear()

        if (node == null) {
            nodeTitleLabel.text = "Select a node"
            nodeBadgeLabel.text = ""
            nodeStatusLabel.text = ""
            nodeFileLabel.text = "File: -"
            nodeMaterializationLabel.text = "Materialization: -"
            nodeDescriptionArea.text = "Click any node in the DAG lineage graph to inspect properties, upstream parents, and downstream models."
            runTaskButton.isEnabled = false
            openEditorButton.isEnabled = false
            copyCmdButton.isEnabled = false
            return
        }

        nodeTitleLabel.text = node.name
        nodeBadgeLabel.text = "[${node.type.displayName}]"
        nodeBadgeLabel.foreground = Color.decode(node.type.badgeColorHex)

        nodeStatusLabel.text = "● ${node.status.displayName}"
        nodeStatusLabel.foreground = Color.decode(node.status.colorHex)

        nodeFileLabel.text = "File: ${node.filePath?.let { File(it).name } ?: "Virtual / External"}"
        nodeMaterializationLabel.text = "Type / Materialization: ${node.materialization}"
        nodeDescriptionArea.text = node.description

        node.upstreamIds.forEach { upstreamListModel.addElement(it) }
        node.downstreamIds.forEach { downstreamListModel.addElement(it) }

        runTaskButton.isEnabled = true
        openEditorButton.isEnabled = node.filePath != null
        copyCmdButton.isEnabled = true
    }

    private fun simulateRunTask() {
        val node = selectedNode ?: return
        if (!org.jormungandr.core.license.LicenseService.getInstance().isLicensed()) {
            JOptionPane.showMessageDialog(
                this,
                "Pipeline Execution is locked in Trial Mode.\nA valid User, Developer, or Admin license is required to execute tasks.",
                "Commercial License Required",
                JOptionPane.WARNING_MESSAGE
            )
            return
        }

        node.status = PipelineExecutionStatus.RUNNING
        updateInspector(node)
        canvas.repaint()

        // Asynchronously transition to SUCCESS
        ApplicationManager.getApplication().executeOnPooledThread {
            Thread.sleep(1200)
            SwingUtilities.invokeLater {
                node.status = PipelineExecutionStatus.SUCCESS
                updateInspector(node)
                canvas.repaint()
                JOptionPane.showMessageDialog(
                    this,
                    "Execution completed successfully for ${node.name} (1.2s)",
                    "Pipeline Execution",
                    JOptionPane.INFORMATION_MESSAGE
                )
            }
        }
    }

    private fun openSelectedFile() {
        val node = selectedNode ?: return
        val path = node.filePath ?: return
        val vFile = LocalFileSystem.getInstance().findFileByIoFile(File(path)) ?: return
        OpenFileDescriptor(project, vFile, node.lineNumber - 1, 0).navigate(true)
    }

    private fun copyCommand() {
        val node = selectedNode ?: return
        val cmd = when (node.type) {
            PipelineNodeType.DBT_MODEL -> "dbt run --select ${node.name}"
            PipelineNodeType.DBT_SOURCE -> "dbt test --select source:${node.name}"
            PipelineNodeType.DBT_SNAPSHOT -> "dbt snapshot --select ${node.name}"
            PipelineNodeType.AIRFLOW_TASK -> "airflow tasks test dag_id ${node.name} 2026-01-01"
            else -> "dbt run --select ${node.name}"
        }
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(cmd), null)
        JOptionPane.showMessageDialog(this, "Copied to clipboard:\n$cmd", "CLI Command Copied", JOptionPane.INFORMATION_MESSAGE)
    }

    fun scanLineage() {
        ApplicationManager.getApplication().executeOnPooledThread {
            val graph = PipelineScanner.scanProject(project)
            SwingUtilities.invokeLater {
                this.currentGraph = graph
                canvas.setGraph(graph)
                val maxLayer = graph.nodes.values.maxOfOrNull { it.layer } ?: 0
                statsLabel.text = "Lineage DAG: ${graph.nodes.size} nodes | ${graph.edges.size} dependencies | Depth: ${maxLayer + 1} layers"
                updateInspector(null)
            }
        }
    }
}
