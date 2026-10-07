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

package org.jormungandr.dataframe.ui

import com.intellij.ui.components.*
import com.intellij.ui.table.JBTable
import org.jormungandr.dataframe.model.DataFrame
import org.jormungandr.dataframe.quality.*
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.table.DefaultTableModel

/**
 * Interactive Automated Data Quality, Profiler & Correlation Studio Panel.
 * Provides deep statistical insights, correlation heatmaps, missing value patterns,
 * and Pandera/Great Expectations validation rules.
 */
class DataQualityStudioPanel(
    private var dataFrame: DataFrame = DataFrame.empty()
) : JPanel(BorderLayout()) {

    private val healthBadge = JBLabel("Score: --")
    private val statsSummaryLabel = JBLabel("Dataset: 0 rows, 0 columns")

    private val correlationCanvas = CorrelationHeatmapCanvas()
    private val correlationMethodCombo = JComboBox(CorrelationMethod.values())

    private val sparsityTableModel = DefaultTableModel(arrayOf("Column", "Type", "Missing Count", "Missing (%)", "Status"), 0)
    private val sparsityTable = JBTable(sparsityTableModel)
    private val completenessLabel = JBLabel("Completeness: --")

    private val rulesTableModel = DefaultTableModel(arrayOf("Type", "Column", "Description", "Status", "Violations", "Pass Rate"), 0)
    private val rulesTable = JBTable(rulesTableModel)

    private val outliersTableModel = DefaultTableModel(arrayOf("Column", "Z-Score Outliers", "IQR Outliers", "Sample Outlier Values"), 0)
    private val outliersTable = JBTable(outliersTableModel)

    private var currentScorecard: DataQualityScorecard? = null

    init {
        buildUi()
        refreshQualityAudit()
    }

    fun setDataFrame(df: DataFrame) {
        this.dataFrame = df
        refreshQualityAudit()
    }

    private fun buildUi() {
        // --- 1. Top Header & Toolbar ---
        val header = JPanel(BorderLayout(12, 0)).apply {
            border = CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Color(220, 220, 220)),
                EmptyBorder(8, 12, 8, 12)
            )
            background = Color(248, 249, 250)
        }

        val leftHeader = JPanel(FlowLayout(FlowLayout.LEFT, 10, 0)).apply { isOpaque = false }
        healthBadge.font = healthBadge.font.deriveFont(Font.BOLD, 13f)
        healthBadge.isOpaque = true
        healthBadge.border = EmptyBorder(4, 10, 4, 10)
        healthBadge.background = Color(40, 167, 69)
        healthBadge.foreground = Color.WHITE

        statsSummaryLabel.font = Font("Segoe UI", Font.PLAIN, 12)
        statsSummaryLabel.foreground = Color(100, 100, 100)

        leftHeader.add(healthBadge)
        leftHeader.add(statsSummaryLabel)

        val rightHeader = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply { isOpaque = false }

        val refreshBtn = JButton("🔄 Run Audit").apply {
            isFocusable = false
            addActionListener { refreshQualityAudit() }
        }

        val exportPanderaBtn = JButton("🐍 Export Pandera Schema").apply {
            isFocusable = false
            toolTipText = "Copy generated Pandera Python schema validation code"
            addActionListener {
                currentScorecard?.panderaCode?.let { code ->
                    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(code), null)
                    JOptionPane.showMessageDialog(this, "Copied Pandera schema to clipboard!", "Data Quality Studio", JOptionPane.INFORMATION_MESSAGE)
                }
            }
        }

        rightHeader.add(refreshBtn)
        rightHeader.add(exportPanderaBtn)

        header.add(leftHeader, BorderLayout.WEST)
        header.add(rightHeader, BorderLayout.EAST)
        add(header, BorderLayout.NORTH)

        // --- 2. Tabbed Studio View ---
        val tabs = JBTabbedPane()

        // Tab 1: Correlation Matrix Heatmap
        val corrPanel = JPanel(BorderLayout()).apply {
            border = EmptyBorder(8, 8, 8, 8)
        }
        val corrToolbar = JPanel(FlowLayout(FlowLayout.LEFT, 8, 4)).apply {
            add(JBLabel("Method:"))
            add(correlationMethodCombo)
            val info = JBLabel("💡 Hover over cells for exact coefficients").apply {
                foreground = Color(120, 120, 120)
            }
            add(info)
        }
        correlationMethodCombo.addActionListener {
            val selected = correlationMethodCombo.selectedItem as? CorrelationMethod ?: CorrelationMethod.PEARSON
            updateCorrelationView(selected)
        }
        corrPanel.add(corrToolbar, BorderLayout.NORTH)
        corrPanel.add(JBScrollPane(correlationCanvas), BorderLayout.CENTER)
        tabs.addTab("🌡️ Correlation Heatmap", corrPanel)

        // Tab 2: Missing Values & Sparsity
        val sparsityPanel = JPanel(BorderLayout(0, 8)).apply {
            border = EmptyBorder(8, 8, 8, 8)
        }
        val sparsityTop = JPanel(FlowLayout(FlowLayout.LEFT, 8, 4)).apply {
            completenessLabel.font = completenessLabel.font.deriveFont(Font.BOLD, 12f)
            add(completenessLabel)
        }
        sparsityTable.autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS
        sparsityTable.rowHeight = 24
        sparsityPanel.add(sparsityTop, BorderLayout.NORTH)
        sparsityPanel.add(JBScrollPane(sparsityTable), BorderLayout.CENTER)
        tabs.addTab("🔍 Sparsity & Missing Values", sparsityPanel)

        // Tab 3: Assertions & Rules
        val rulesPanel = JPanel(BorderLayout(0, 8)).apply {
            border = EmptyBorder(8, 8, 8, 8)
        }
        rulesTable.autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS
        rulesTable.rowHeight = 24
        rulesPanel.add(JBScrollPane(rulesTable), BorderLayout.CENTER)
        tabs.addTab("🛡️ Pandera Assertions", rulesPanel)

        // Tab 4: Outlier Detection
        val outlierPanel = JPanel(BorderLayout(0, 8)).apply {
            border = EmptyBorder(8, 8, 8, 8)
        }
        outliersTable.autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS
        outliersTable.rowHeight = 24
        outlierPanel.add(JBScrollPane(outliersTable), BorderLayout.CENTER)
        tabs.addTab("🎯 Outlier Studio (Z-Score & IQR)", outlierPanel)

        add(tabs, BorderLayout.CENTER)
    }

    private fun refreshQualityAudit() {
        if (dataFrame.isEmpty) {
            healthBadge.text = "Score: --"
            statsSummaryLabel.text = "Dataset is empty"
            return
        }

        val scorecard = DataQualityProfilerService.analyzeQuality(dataFrame)
        currentScorecard = scorecard

        // 1. Header
        val score = scorecard.overallHealthScore
        healthBadge.text = "Quality: %.1f%% (%s)".format(score, scorecard.healthGrade)
        healthBadge.background = when {
            score >= 85.0 -> Color(40, 167, 69)  // Green
            score >= 70.0 -> Color(255, 193, 7)  // Yellow
            else -> Color(220, 53, 69)           // Red
        }
        healthBadge.foreground = if (score in 70.0..85.0) Color.BLACK else Color.WHITE

        statsSummaryLabel.text = "${dataFrame.name} | ${dataFrame.rowCount} rows, ${dataFrame.columnCount} columns"

        // 2. Correlation
        val method = correlationMethodCombo.selectedItem as? CorrelationMethod ?: CorrelationMethod.PEARSON
        updateCorrelationView(method)

        // 3. Sparsity
        completenessLabel.text = "Overall Completeness: %.2f%% | Missing Cells: %d / %d".format(
            scorecard.sparsity.completenessPercentage,
            scorecard.sparsity.missingCells,
            scorecard.sparsity.totalCells
        )

        sparsityTableModel.setRowCount(0)
        dataFrame.columns.forEach { col ->
            val missing = scorecard.sparsity.columnMissingCounts[col.name] ?: 0L
            val pct = scorecard.sparsity.columnMissingPercentages[col.name] ?: 0.0
            val status = if (missing == 0L) "✅ 100% Clean" else if (pct < 10.0) "⚠️ Low Missing" else "❌ High Sparsity"
            sparsityTableModel.addRow(arrayOf<Any>(col.name, col.typeName, missing, "%.2f%%".format(pct), status))
        }

        // 4. Rules
        rulesTableModel.setRowCount(0)
        scorecard.validationResults.forEach { res ->
            val status = if (res.passed) "✅ PASSED" else "❌ FAILED"
            rulesTableModel.addRow(
                arrayOf<Any>(
                    res.ruleType,
                    res.column,
                    res.description,
                    status,
                    "${res.violationsCount} / ${res.totalEvaluated}",
                    "%.1f%%".format(res.passPercentage)
                )
            )
        }

        // 5. Outliers
        outliersTableModel.setRowCount(0)
        scorecard.outliers.forEach { out ->
            val sampleStr = if (out.sampleOutlierValues.isEmpty()) "None" else out.sampleOutlierValues.joinToString(", ") { "%.2f".format(it) }
            outliersTableModel.addRow(
                arrayOf<Any>(
                    out.columnName,
                    out.zScoreOutliersCount,
                    out.iqrOutliersCount,
                    sampleStr
                )
            )
        }
    }

    private fun updateCorrelationView(method: CorrelationMethod) {
        val corr = CorrelationMatrixCalculator.compute(dataFrame, method)
        correlationCanvas.setMatrix(corr)
    }

    /**
     * Custom Swing component painting an interactive 2D Correlation Heatmap.
     */
    private class CorrelationHeatmapCanvas : JComponent() {

        private var matrix: CorrelationMatrix? = null
        private var hoveredCell: Pair<Int, Int>? = null

        init {
            preferredSize = Dimension(650, 480)
            addMouseMotionListener(object : MouseAdapter() {
                override fun mouseMoved(e: MouseEvent) {
                    val m = matrix ?: return
                    val n = m.columnNames.size
                    if (n == 0) return

                    val margin = 90
                    val availW = width - margin - 20
                    val availH = height - margin - 20
                    val cellSize = minOf(availW / n, availH / n).coerceIn(24, 70)

                    val col = (e.x - margin) / cellSize
                    val row = (e.y - margin) / cellSize

                    if (col in 0 until n && row in 0 until n) {
                        hoveredCell = Pair(row, col)
                        val valStr = "%.3f".format(m.matrix[row][col])
                        toolTipText = "${m.columnNames[row]} vs ${m.columnNames[col]}: $valStr"
                    } else {
                        hoveredCell = null
                        toolTipText = null
                    }
                    repaint()
                }
            })
        }

        fun setMatrix(m: CorrelationMatrix) {
            this.matrix = m
            repaint()
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

            val m = matrix
            if (m == null || m.columnNames.isEmpty()) {
                g2.color = Color(150, 150, 150)
                g2.font = Font("Segoe UI", Font.PLAIN, 14)
                g2.drawString("No numeric columns available for correlation analysis.", 40, 50)
                return
            }

            val n = m.columnNames.size
            val margin = 100
            val availW = width - margin - 30
            val availH = height - margin - 30
            val cellSize = minOf(availW / n, availH / n).coerceIn(28, 75)

            // Draw axis headers
            g2.font = Font("Segoe UI", Font.BOLD, 11)
            for (i in 0 until n) {
                val label = m.columnNames[i].take(10)
                // Top header
                g2.color = Color(60, 60, 60)
                val tx = margin + i * cellSize + 4
                val ty = margin - 12
                g2.drawString(label, tx, ty)

                // Left header
                val lx = 10
                val ly = margin + i * cellSize + cellSize / 2 + 4
                g2.drawString(label, lx, ly)
            }

            // Draw cells
            g2.font = Font("Consolas", Font.PLAIN, if (cellSize > 40) 11 else 9)
            for (r in 0 until n) {
                for (c in 0 until n) {
                    val value = m.matrix[r][c]
                    val cellColor = getHeatmapColor(value)

                    val x = margin + c * cellSize
                    val y = margin + r * cellSize

                    g2.color = cellColor
                    g2.fillRect(x, y, cellSize - 1, cellSize - 1)

                    // Highlight hovered
                    if (hoveredCell == Pair(r, c)) {
                        g2.color = Color(255, 215, 0)
                        g2.stroke = BasicStroke(2f)
                        g2.drawRect(x, y, cellSize - 1, cellSize - 1)
                    }

                    // Numeric label
                    if (cellSize >= 32) {
                        g2.color = if (Math.abs(value) > 0.45) Color.WHITE else Color(30, 30, 30)
                        val valStr = "%.2f".format(value)
                        val fm = g2.fontMetrics
                        val strW = fm.stringWidth(valStr)
                        val strH = fm.ascent
                        g2.drawString(valStr, x + (cellSize - strW) / 2, y + (cellSize + strH) / 2 - 2)
                    }
                }
            }
        }

        private fun getHeatmapColor(value: Double): Color {
            val v = value.coerceIn(-1.0, 1.0)
            return if (v >= 0.0) {
                // White (0.0) -> Crimson Red (+1.0)
                val ratio = v.toFloat()
                val r = (255 - ratio * (255 - 200)).toInt().coerceIn(0, 255)
                val g = (255 - ratio * (255 - 40)).toInt().coerceIn(0, 255)
                val b = (255 - ratio * (255 - 40)).toInt().coerceIn(0, 255)
                Color(r, g, b)
            } else {
                // Deep Blue (-1.0) -> White (0.0)
                val ratio = (-v).toFloat()
                val r = (255 - ratio * (255 - 30)).toInt().coerceIn(0, 255)
                val g = (255 - ratio * (255 - 100)).toInt().coerceIn(0, 255)
                val b = (255 - ratio * (255 - 200)).toInt().coerceIn(0, 255)
                Color(r, g, b)
            }
        }
    }
}
