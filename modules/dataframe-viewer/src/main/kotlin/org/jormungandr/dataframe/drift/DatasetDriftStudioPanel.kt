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

package org.jormungandr.dataframe.drift

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import org.jormungandr.dataframe.model.DataFrame
import java.awt.*
import java.awt.datatransfer.StringSelection
import javax.swing.*
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel
import kotlin.math.max

/**
 * Interactive Dataset Comparison & Distribution Drift Studio Panel.
 * Visualizes Population Stability Index (PSI), Kolmogorov-Smirnov distribution shifts,
 * and dual histogram/CDF overlays between reference (train) and target (test/prod) datasets.
 */
class DatasetDriftStudioPanel(
    private val project: Project? = null
) : JPanel(BorderLayout()) {

    private var currentReport: DatasetDriftReport? = null
    private var selectedFeature: FeatureDriftResult? = null

    private val statusBanner = JBLabel("No datasets compared yet").apply {
        font = font.deriveFont(Font.BOLD, 12f)
    }

    private val tableModel = DefaultTableModel(
        arrayOf("Feature", "Status", "PSI", "K-S Stat", "P-Value", "Shift %", "Ref Missing", "Tgt Missing"), 0
    )

    private val driftTable = JTable(tableModel).apply {
        rowHeight = 24
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        selectionModel.addListSelectionListener {
            val sel = selectedRow
            if (sel in 0 until (currentReport?.featureDrifts?.size ?: 0)) {
                selectedFeature = currentReport?.featureDrifts?.getOrNull(sel)
                canvas.repaint()
            }
        }
        columnModel.getColumn(1).cellRenderer = object : DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(
                table: JTable?, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
            ): Component {
                val c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column) as JLabel
                when (value?.toString()) {
                    "🔴 SEVERE" -> c.foreground = Color(220, 38, 38)
                    "🟡 MODERATE" -> c.foreground = Color(217, 119, 6)
                    else -> c.foreground = Color(16, 185, 129)
                }
                c.font = c.font.deriveFont(Font.BOLD, 11f)
                return c
            }
        }
    }

    private val canvas = DriftHistogramCanvas()

    init {
        setupUI()
    }

    private fun setupUI() {
        val topBar = JPanel(BorderLayout()).apply {
            border = CompoundBorder(
                LineBorder(Color(226, 232, 240), 1),
                EmptyBorder(6, 8, 6, 8)
            )
            background = Color(248, 250, 252)
        }

        val left = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply { isOpaque = false }
        val titleLbl = JBLabel("📊 Dataset Comparison & Distribution Drift Studio").apply {
            font = font.deriveFont(Font.BOLD, 12f)
            foreground = Color(30, 41, 59)
        }
        left.add(titleLbl)
        left.add(statusBanner)

        val right = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply { isOpaque = false }
        val copyScriptBtn = JButton("📋 Copy Python Drift Script").apply {
            font = font.deriveFont(Font.PLAIN, 11f)
            isFocusable = false
            toolTipText = "Generate Python script using scipy.stats.ks_2samp and evidently"
            addActionListener { copyPythonScript() }
        }
        right.add(copyScriptBtn)

        topBar.add(left, BorderLayout.WEST)
        topBar.add(right, BorderLayout.EAST)
        add(topBar, BorderLayout.NORTH)

        val split = JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT,
            JBScrollPane(driftTable),
            canvas
        ).apply {
            dividerLocation = 480
            resizeWeight = 0.5
            isContinuousLayout = true
        }

        add(split, BorderLayout.CENTER)
    }

    fun setReport(report: DatasetDriftReport) {
        this.currentReport = report
        updateBanner(report)
        updateTable(report)
        selectedFeature = report.featureDrifts.firstOrNull()
        canvas.repaint()
    }

    fun compare(reference: DataFrame, target: DataFrame, refName: String = "Train", tgtName: String = "Test") {
        val report = DatasetDriftDetectorService.compareDatasets(reference, target, refName, tgtName)
        setReport(report)
    }

    private fun updateBanner(report: DatasetDriftReport) {
        val severityStr = when (report.overallDriftSeverity) {
            DriftSeverity.SEVERE -> "🔴 SEVERE DRIFT DETECTED"
            DriftSeverity.MODERATE -> "🟡 MODERATE DRIFT DETECTED"
            DriftSeverity.STABLE -> "🟢 DATASETS STABLE"
        }
        statusBanner.text = "$severityStr (${report.driftDetectedCount} of ${report.featureDrifts.size} features drifted) | Ref: ${report.referenceName} vs Tgt: ${report.targetName}"
        statusBanner.foreground = when (report.overallDriftSeverity) {
            DriftSeverity.SEVERE -> Color(220, 38, 38)
            DriftSeverity.MODERATE -> Color(217, 119, 6)
            DriftSeverity.STABLE -> Color(16, 185, 129)
        }
    }

    private fun updateTable(report: DatasetDriftReport) {
        tableModel.rowCount = 0
        for (f in report.featureDrifts) {
            val status = when (f.severity) {
                DriftSeverity.SEVERE -> "🔴 SEVERE"
                DriftSeverity.MODERATE -> "🟡 MODERATE"
                DriftSeverity.STABLE -> "🟢 STABLE"
            }
            tableModel.addRow(
                arrayOf(
                    f.featureName,
                    status,
                    String.format("%.4f", f.psi),
                    String.format("%.4f", f.ksStatistic),
                    String.format("%.4f", f.ksPValue),
                    "${String.format("%+.1f", f.meanShiftPct ?: 0.0)}%",
                    "${String.format("%.1f", f.referenceMissingPct)}%",
                    "${String.format("%.1f", f.targetMissingPct)}%"
                )
            )
        }
    }

    private fun copyPythonScript() {
        val rep = currentReport ?: return
        val code = """
            # Automated Dataset Drift Analysis (Scipy & Evidently)
            from scipy.stats import ks_2samp
            import numpy as np
            import pandas as pd
            import matplotlib.pyplot as plt
            
            # Reference and Target DataFrames
            # df_ref = pd.read_csv(...)
            # df_tgt = pd.read_csv(...)
            
            features = ${rep.featureDrifts.map { "'${it.featureName}'" }}
            
            for col in features:
                d_stat, p_val = ks_2samp(df_ref[col].dropna(), df_tgt[col].dropna())
                print(f"Feature: {col} | K-S Stat: {d_stat:.4f} | P-Val: {p_val:.4f}")
                
            # Dual distribution histogram plot
            fig, ax = plt.subplots(figsize=(10, 5))
            col = features[0]
            ax.hist(df_ref[col], bins=20, alpha=0.5, label='Reference', density=True)
            ax.hist(df_tgt[col], bins=20, alpha=0.5, label='Target', density=True)
            ax.set_title(f"Distribution Comparison: {col}")
            ax.legend()
            plt.show()
        """.trimIndent()

        val sel = StringSelection(code)
        Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, sel)
        Messages.showInfoMessage(project, "Python drift script copied to clipboard!", "Script Copied")
    }

    private inner class DriftHistogramCanvas : JPanel() {
        init {
            background = Color(255, 255, 255)
            preferredSize = Dimension(500, 400)
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2 = g as? Graphics2D ?: return
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

            val feature = selectedFeature
            if (feature == null) {
                g2.color = Color.GRAY
                g2.font = Font("SansSerif", Font.ITALIC, 13)
                g2.drawString("Select a feature from the table to inspect distribution overlays.", 30, 50)
                return
            }

            val w = width
            val h = height

            // Header
            g2.font = Font("SansSerif", Font.BOLD, 13)
            g2.color = Color(15, 23, 42)
            g2.drawString("Distribution Comparison: ${feature.featureName}", 30, 30)

            g2.font = Font("SansSerif", Font.PLAIN, 11)
            g2.color = Color(71, 85, 105)
            val psiStr = "PSI: ${String.format("%.4f", feature.psi)}"
            val ksStr = "K-S: ${String.format("%.4f", feature.ksStatistic)} (p=${String.format("%.4f", feature.ksPValue)})"
            val shiftStr = "Mean Shift: ${String.format("%+.2f", feature.meanShiftPct ?: 0.0)}%"
            g2.drawString("$psiStr   |   $ksStr   |   $shiftStr", 30, 50)

            // Legend
            g2.color = Color(59, 130, 246)
            g2.fillRect(30, 65, 12, 12)
            g2.color = Color(30, 41, 59)
            g2.font = Font("SansSerif", Font.PLAIN, 10)
            g2.drawString("Reference (Train)", 48, 75)

            g2.color = Color(245, 158, 11)
            g2.fillRect(160, 65, 12, 12)
            g2.color = Color(30, 41, 59)
            g2.drawString("Target (Test / Prod)", 178, 75)

            // Plot area
            val plotX = 40
            val plotY = 95
            val plotW = w - 80
            val plotH = h - 140

            g2.color = Color(248, 250, 252)
            g2.fillRect(plotX, plotY, plotW, plotH)
            g2.color = Color(226, 232, 240)
            g2.drawRect(plotX, plotY, plotW, plotH)

            val nBins = feature.referenceBins.size
            if (nBins == 0) return

            val maxFraction = max(
                feature.referenceBins.maxOrNull() ?: 0.1,
                feature.targetBins.maxOrNull() ?: 0.1
            ).coerceAtLeast(1e-4)

            val binWidth = (plotW / nBins).coerceAtLeast(4)

            // Draw side-by-side or overlapping bars
            for (i in 0 until nBins) {
                val bx = plotX + i * binWidth

                val refHeight = ((feature.referenceBins[i] / maxFraction) * plotH).toInt()
                val tgtHeight = ((feature.targetBins[i] / maxFraction) * plotH).toInt()

                // Reference Bar (Translucent Blue)
                g2.color = Color(59, 130, 246, 160)
                g2.fillRect(bx + 2, plotY + plotH - refHeight, (binWidth / 2) - 2, refHeight)

                // Target Bar (Translucent Amber)
                g2.color = Color(245, 158, 11, 160)
                g2.fillRect(bx + (binWidth / 2), plotY + plotH - tgtHeight, (binWidth / 2) - 2, tgtHeight)
            }

            // Min and Max x-axis bounds
            g2.font = Font("Monospaced", Font.PLAIN, 10)
            g2.color = Color(100, 116, 139)
            g2.drawString("${String.format("%.2f", feature.binEdges.first())}", plotX, plotY + plotH + 16)
            g2.drawString("${String.format("%.2f", feature.binEdges.last())}", plotX + plotW - 40, plotY + plotH + 16)
        }
    }
}
