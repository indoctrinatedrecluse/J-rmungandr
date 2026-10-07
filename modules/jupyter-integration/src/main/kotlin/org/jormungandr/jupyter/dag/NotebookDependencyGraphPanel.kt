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

package org.jormungandr.jupyter.dag

import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import org.jormungandr.jupyter.model.NotebookModel
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder

/**
 * Interactive Visual DAG & Reactive Execution Dashboard for Jupyter Notebooks.
 * Displays cell-to-cell variable dependency flows, flags out-of-order stale outputs,
 * and enables 1-click downstream cascade re-execution.
 */
class NotebookDependencyGraphPanel(
    private var notebookModel: NotebookModel,
    private val onSelectCell: ((String) -> Unit)? = null,
    private val onRunCascade: ((List<String>) -> Unit)? = null,
    private val onRunDagOrder: ((List<String>) -> Unit)? = null
) : JPanel(BorderLayout()) {

    private var currentGraph: NotebookDependencyGraph = NotebookDependencyGraphService.buildDependencyGraph(notebookModel)
    private var selectedCellId: String? = currentGraph.cellAnalyses.keys.firstOrNull()

    private val statusBanner = JBLabel("Ready").apply {
        font = font.deriveFont(Font.BOLD, 11f)
    }
    private val canvas = DagCanvas()

    init {
        setupUI()
        refreshGraph()
    }

    private fun setupUI() {
        val topBar = JPanel(BorderLayout()).apply {
            border = CompoundBorder(
                LineBorder(Color(226, 232, 240), 1),
                EmptyBorder(6, 8, 6, 8)
            )
            background = Color(248, 250, 252)
        }

        val left = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply { isOpaque = false }
        val titleLbl = JBLabel("⚡ Reactive DAG & Dependency Graph").apply {
            font = font.deriveFont(Font.BOLD, 12f)
            foreground = Color(30, 41, 59)
        }
        left.add(titleLbl)
        left.add(statusBanner)

        val right = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply { isOpaque = false }
        val cascadeBtn = JButton("⚡ Run Downstream Cascade").apply {
            font = font.deriveFont(Font.BOLD, 11f)
            foreground = Color(16, 185, 129)
            isFocusable = false
            toolTipText = "Re-execute all downstream cells that depend on the selected cell"
            addActionListener {
                val sel = selectedCellId
                if (sel != null) {
                    val cascade = currentGraph.getDownstreamCascade(sel)
                    if (cascade.isNotEmpty()) {
                        onRunCascade?.invoke(listOf(sel) + cascade)
                    } else {
                        onRunCascade?.invoke(listOf(sel))
                    }
                }
            }
        }

        val runAllDagBtn = JButton("🔄 Run All (DAG Order)").apply {
            font = font.deriveFont(Font.PLAIN, 11f)
            isFocusable = false
            toolTipText = "Execute all cells in topological dependency order"
            addActionListener {
                onRunDagOrder?.invoke(currentGraph.topologicalOrder)
            }
        }

        val refreshBtn = JButton("🔍 Refresh").apply {
            font = font.deriveFont(Font.PLAIN, 11f)
            isFocusable = false
            addActionListener { refreshGraph() }
        }

        right.add(cascadeBtn)
        right.add(runAllDagBtn)
        right.add(refreshBtn)

        topBar.add(left, BorderLayout.WEST)
        topBar.add(right, BorderLayout.EAST)
        add(topBar, BorderLayout.NORTH)

        add(JBScrollPane(canvas), BorderLayout.CENTER)
    }

    fun updateModel(model: NotebookModel) {
        this.notebookModel = model
        refreshGraph()
    }

    fun refreshGraph() {
        currentGraph = NotebookDependencyGraphService.buildDependencyGraph(notebookModel)
        val staleCount = currentGraph.staleCellIds.size
        if (staleCount > 0) {
            statusBanner.text = "⚠️ $staleCount stale output${if (staleCount > 1) "s" else ""} detected!"
            statusBanner.foreground = Color(234, 88, 12) // Amber
        } else {
            statusBanner.text = "🟢 All outputs in sync (${currentGraph.cellAnalyses.size} code cells)"
            statusBanner.foreground = Color(16, 185, 129) // Green
        }
        canvas.repaint()
    }

    private inner class DagCanvas : JPanel() {
        private val nodeBounds = mutableMapOf<String, Rectangle>()

        init {
            background = Color(255, 255, 255)
            preferredSize = Dimension(600, 400)

            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    for ((cellId, rect) in nodeBounds) {
                        if (rect.contains(e.point)) {
                            selectedCellId = cellId
                            onSelectCell?.invoke(cellId)
                            repaint()
                            break
                        }
                    }
                }
            })
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2 = g as? Graphics2D ?: return
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            val analyses = currentGraph.cellAnalyses.values.toList()
            if (analyses.isEmpty()) {
                g2.color = Color.GRAY
                g2.font = Font("SansSerif", Font.ITALIC, 13)
                g2.drawString("No code cells available to analyze.", 40, 60)
                return
            }

            nodeBounds.clear()

            // Calculate grid layout for DAG nodes
            val nodeWidth = 160
            val nodeHeight = 64
            val startX = 30
            val startY = 30
            val gapX = 60
            val gapY = 80

            val positions = mutableMapOf<String, Point>()
            analyses.forEachIndexed { i, a ->
                val x = startX + (i % 3) * (nodeWidth + gapX)
                val y = startY + (i / 3) * (nodeHeight + gapY)
                val rect = Rectangle(x, y, nodeWidth, nodeHeight)
                nodeBounds[a.cellId] = rect
                positions[a.cellId] = Point(x + nodeWidth / 2, y + nodeHeight / 2)
            }

            // Draw directed edges
            for (edge in currentGraph.edges) {
                val p1 = positions[edge.upstreamCellId]
                val p2 = positions[edge.downstreamCellId]
                if (p1 != null && p2 != null) {
                    g2.color = Color(148, 163, 184)
                    g2.stroke = BasicStroke(1.5f)
                    g2.drawLine(p1.x, p1.y, p2.x, p2.y)

                    // Draw variable badge on midpoint
                    val midX = (p1.x + p2.x) / 2
                    val midY = (p1.y + p2.y) / 2
                    g2.font = Font("Monospaced", Font.BOLD, 10)
                    g2.color = Color(59, 130, 246)
                    g2.drawString(edge.variable, midX + 4, midY - 2)
                }
            }

            // Draw cell nodes
            for (a in analyses) {
                val rect = nodeBounds[a.cellId] ?: continue
                val isSelected = a.cellId == selectedCellId
                val isStale = a.isStale

                // Background
                g2.color = when {
                    isStale -> Color(254, 243, 199) // Light amber
                    isSelected -> Color(239, 246, 255) // Light blue
                    else -> Color(248, 250, 252)
                }
                g2.fillRoundRect(rect.x, rect.y, rect.width, rect.height, 8, 8)

                // Border
                g2.color = when {
                    isStale -> Color(245, 158, 11) // Amber
                    isSelected -> Color(37, 99, 235) // Blue
                    else -> Color(203, 213, 225)
                }
                g2.stroke = BasicStroke(if (isSelected) 2.0f else 1.0f)
                g2.drawRoundRect(rect.x, rect.y, rect.width, rect.height, 8, 8)

                // Header
                g2.font = Font("SansSerif", Font.BOLD, 11)
                g2.color = Color(15, 23, 42)
                val execText = if (a.executionCount != null) "In [${a.executionCount}]" else "In [ ]"
                g2.drawString("Cell #${a.cellIndex + 1} ($execText)", rect.x + 8, rect.y + 18)

                // Stale badge
                if (isStale) {
                    g2.color = Color(220, 38, 38)
                    g2.font = Font("SansSerif", Font.BOLD, 10)
                    g2.drawString("⚠️ STALE", rect.x + rect.width - 55, rect.y + 18)
                }

                // Variable defs
                g2.font = Font("Monospaced", Font.PLAIN, 10)
                g2.color = Color(16, 185, 129)
                val defsStr = if (a.definedVariables.isNotEmpty()) {
                    "+ " + a.definedVariables.take(3).joinToString(", ")
                } else "None"
                g2.drawString("Defs: $defsStr", rect.x + 8, rect.y + 36)

                // Variable uses
                g2.color = Color(100, 116, 139)
                val usesStr = if (a.usedVariables.isNotEmpty()) {
                    a.usedVariables.take(3).joinToString(", ")
                } else "None"
                g2.drawString("Uses: $usesStr", rect.x + 8, rect.y + 52)
            }
        }
    }
}
