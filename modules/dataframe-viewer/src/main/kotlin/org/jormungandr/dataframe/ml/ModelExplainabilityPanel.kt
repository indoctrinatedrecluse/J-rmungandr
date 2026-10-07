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

package org.jormungandr.dataframe.ml

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import java.awt.*
import java.awt.datatransfer.StringSelection
import javax.swing.*
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder
import kotlin.math.abs
import kotlin.math.max

enum class ExplainabilityViewMode {
    WATERFALL,
    GLOBAL_IMPORTANCE,
    PARTIAL_DEPENDENCE
}

/**
 * Interactive Model Explainability & Attribution Studio Panel.
 * Visualizes SHAP local waterfall force plots, global mean |SHAP| feature importance,
 * and partial dependence (PDP & ICE) curves.
 */
class ModelExplainabilityPanel(
    private val project: Project? = null
) : JPanel(BorderLayout()) {

    private var currentReport: ExplainabilityReport? = null
    private var currentMode = ExplainabilityViewMode.WATERFALL
    private var selectedInstanceIndex = 0
    private var selectedFeatureName: String = ""

    private val titleLabel = JBLabel("🧠 Model Explainability & Attribution (SHAP & PDP)").apply {
        font = font.deriveFont(Font.BOLD, 12f)
        foreground = Color(30, 41, 59)
    }

    private val modelSummaryLabel = JBLabel("No model evaluated yet").apply {
        font = font.deriveFont(Font.PLAIN, 11f)
        foreground = Color(100, 116, 139)
    }

    private val instanceSpinner = JSpinner(SpinnerNumberModel(0, 0, 0, 1)).apply {
        toolTipText = "Select dataset instance row index"
        addChangeListener {
            selectedInstanceIndex = (value as? Number)?.toInt() ?: 0
            canvas.repaint()
        }
    }

    private val featureSelectorCombo = JComboBox<String>().apply {
        isFocusable = false
        addActionListener {
            selectedFeatureName = (selectedItem as? String) ?: ""
            canvas.repaint()
        }
    }

    private val canvas = ExplainabilityCanvas()

    init {
        setupUI()
    }

    private fun setupUI() {
        background = Color(248, 250, 252)

        val topBar = JPanel(BorderLayout()).apply {
            border = CompoundBorder(
                LineBorder(Color(226, 232, 240), 1),
                EmptyBorder(6, 8, 6, 8)
            )
            background = Color(248, 250, 252)
        }

        val left = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply { isOpaque = false }
        left.add(titleLabel)
        left.add(modelSummaryLabel)

        val right = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply { isOpaque = false }

        val waterfallBtn = JToggleButton("🌊 Waterfall Force", true).apply {
            font = font.deriveFont(Font.PLAIN, 11f)
            isFocusable = false
        }
        val globalBtn = JToggleButton("📊 Global Importance", false).apply {
            font = font.deriveFont(Font.PLAIN, 11f)
            isFocusable = false
        }
        val pdpBtn = JToggleButton("📈 Partial Dep (PDP)", false).apply {
            font = font.deriveFont(Font.PLAIN, 11f)
            isFocusable = false
        }

        val modeGroup = ButtonGroup().apply {
            add(waterfallBtn)
            add(globalBtn)
            add(pdpBtn)
        }

        waterfallBtn.addActionListener {
            currentMode = ExplainabilityViewMode.WATERFALL
            updateControlVisibility()
            canvas.repaint()
        }
        globalBtn.addActionListener {
            currentMode = ExplainabilityViewMode.GLOBAL_IMPORTANCE
            updateControlVisibility()
            canvas.repaint()
        }
        pdpBtn.addActionListener {
            currentMode = ExplainabilityViewMode.PARTIAL_DEPENDENCE
            updateControlVisibility()
            canvas.repaint()
        }

        right.add(waterfallBtn)
        right.add(globalBtn)
        right.add(pdpBtn)
        right.add(Box.createHorizontalStrut(6))

        val instanceLabel = JBLabel("Row: ")
        right.add(instanceLabel)
        right.add(instanceSpinner)

        val featLabel = JBLabel("Feature: ")
        right.add(featLabel)
        right.add(featureSelectorCombo)

        val copyCodeBtn = JButton("📋 Copy Python SHAP").apply {
            font = font.deriveFont(Font.PLAIN, 11f)
            isFocusable = false
            toolTipText = "Generate Python snippet with shap and PartialDependenceDisplay"
            addActionListener { copyPythonCode() }
        }
        right.add(copyCodeBtn)

        topBar.add(left, BorderLayout.WEST)
        topBar.add(right, BorderLayout.EAST)
        add(topBar, BorderLayout.NORTH)

        add(JBScrollPane(canvas), BorderLayout.CENTER)
        updateControlVisibility()
    }

    private fun updateControlVisibility() {
        val isWaterfall = currentMode == ExplainabilityViewMode.WATERFALL
        val isPdp = currentMode == ExplainabilityViewMode.PARTIAL_DEPENDENCE
        instanceSpinner.isVisible = isWaterfall
        featureSelectorCombo.isVisible = isPdp
    }

    fun setReport(report: ExplainabilityReport) {
        this.currentReport = report
        modelSummaryLabel.text = "Model: ${report.modelName} | Base E[f(X)]: ${String.format("%.4f", report.baseValue)}"

        val maxRow = (report.instanceExplanations.size - 1).coerceAtLeast(0)
        instanceSpinner.model = SpinnerNumberModel(0, 0, maxRow, 1)
        selectedInstanceIndex = 0

        featureSelectorCombo.removeAllItems()
        for (f in report.partialDependenceCurves.keys) {
            featureSelectorCombo.addItem(f)
        }
        selectedFeatureName = report.partialDependenceCurves.keys.firstOrNull() ?: ""

        updateControlVisibility()
        canvas.repaint()
    }

    private fun copyPythonCode() {
        val report = currentReport ?: return
        val code = ModelExplainabilityEngine.generatePythonShapCode(
            modelName = report.modelName,
            featureNames = report.globalImportance.map { it.featureName }
        )
        val selection = StringSelection(code)
        Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
        Messages.showInfoMessage(project, "Python SHAP script copied to clipboard!", "SHAP Code Copied")
    }

    private inner class ExplainabilityCanvas : JPanel() {
        init {
            background = Color(255, 255, 255)
            preferredSize = Dimension(700, 450)
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2 = g as? Graphics2D ?: return
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

            val report = currentReport
            if (report == null || report.instanceExplanations.isEmpty()) {
                g2.color = Color.GRAY
                g2.font = Font("SansSerif", Font.ITALIC, 13)
                g2.drawString("Train a regression or classification model to view SHAP & PDP attribution.", 40, 60)
                return
            }

            when (currentMode) {
                ExplainabilityViewMode.WATERFALL -> paintWaterfall(g2, report)
                ExplainabilityViewMode.GLOBAL_IMPORTANCE -> paintGlobalImportance(g2, report)
                ExplainabilityViewMode.PARTIAL_DEPENDENCE -> paintPartialDependence(g2, report)
            }
        }

        private fun paintWaterfall(g2: Graphics2D, report: ExplainabilityReport) {
            val instance = report.instanceExplanations.getOrNull(selectedInstanceIndex) ?: return
            val w = width.coerceAtLeast(600)
            val h = height.coerceAtLeast(400)

            // Header summary
            g2.font = Font("SansSerif", Font.BOLD, 12)
            g2.color = Color(15, 23, 42)
            g2.drawString("SHAP Waterfall Force Plot - Row #${instance.rowIndex}", 30, 30)

            g2.font = Font("SansSerif", Font.PLAIN, 11)
            g2.color = Color(71, 85, 105)
            g2.drawString(
                "Base Value E[f(X)] = ${String.format("%.4f", instance.baseValue)}   ➜   Model Output f(x) = ${String.format("%.4f", instance.prediction)}",
                30,
                50
            )

            val startY = 80
            val barHeight = 24
            val gapY = 10
            val maxBars = minOf(instance.attributions.size, 10)

            val centerX = w / 2
            val maxBarWidth = (w / 2) - 140

            // Baseline vertical rule
            g2.color = Color(203, 213, 225)
            g2.stroke = BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0f, floatArrayOf(4f, 4f), 0f)
            g2.drawLine(centerX, startY - 10, centerX, startY + maxBars * (barHeight + gapY))
            g2.stroke = BasicStroke(1.0f)

            for (i in 0 until maxBars) {
                val attr = instance.attributions[i]
                val y = startY + i * (barHeight + gapY)
                val isPositive = attr.shapValue >= 0

                val barLen = (attr.relativeImpact * maxBarWidth).toInt().coerceAtLeast(4)

                if (isPositive) {
                    g2.color = Color(16, 185, 129) // Green (pushes higher)
                    g2.fillRoundRect(centerX, y, barLen, barHeight, 6, 6)
                } else {
                    g2.color = Color(239, 68, 68) // Red (pushes lower)
                    g2.fillRoundRect(centerX - barLen, y, barLen, barHeight, 6, 6)
                }

                // Label feature name and actual value
                g2.font = Font("Monospaced", Font.BOLD, 11)
                g2.color = Color(30, 41, 59)
                val valStr = String.format("%.2f", attr.featureValue)
                val label = "${attr.featureName} = $valStr"
                val strWidth = g2.fontMetrics.stringWidth(label)

                if (isPositive) {
                    g2.drawString(label, centerX - strWidth - 12, y + 16)
                    val impactStr = "+${String.format("%.4f", attr.shapValue)}"
                    g2.drawString(impactStr, centerX + barLen + 8, y + 16)
                } else {
                    g2.drawString(label, centerX + 12, y + 16)
                    val impactStr = String.format("%.4f", attr.shapValue)
                    g2.drawString(impactStr, centerX - barLen - g2.fontMetrics.stringWidth(impactStr) - 8, y + 16)
                }
            }
        }

        private fun paintGlobalImportance(g2: Graphics2D, report: ExplainabilityReport) {
            val w = width.coerceAtLeast(600)
            val h = height.coerceAtLeast(400)

            g2.font = Font("SansSerif", Font.BOLD, 12)
            g2.color = Color(15, 23, 42)
            g2.drawString("Global Feature Importance (Mean |SHAP| Values)", 30, 30)

            val startY = 60
            val barHeight = 22
            val gapY = 8
            val maxBars = minOf(report.globalImportance.size, 12)
            val maxBarWidth = w - 320

            for (i in 0 until maxBars) {
                val item = report.globalImportance[i]
                val y = startY + i * (barHeight + gapY)

                // Feature Name
                g2.font = Font("Monospaced", Font.BOLD, 11)
                g2.color = Color(51, 65, 85)
                g2.drawString(item.featureName, 30, y + 16)

                // Bar
                val barLen = (item.normalizedImportance * maxBarWidth).toInt().coerceAtLeast(4)
                g2.color = Color(59, 130, 246) // Blue
                g2.fillRoundRect(180, y, barLen, barHeight, 4, 4)

                // Mean abs shap text
                g2.font = Font("SansSerif", Font.PLAIN, 10)
                g2.color = Color(100, 116, 139)
                val valStr = String.format("%.4f", item.meanAbsShap)
                g2.drawString(valStr, 180 + barLen + 8, y + 15)
            }
        }

        private fun paintPartialDependence(g2: Graphics2D, report: ExplainabilityReport) {
            val curve = report.partialDependenceCurves[selectedFeatureName] ?: return
            val w = width.coerceAtLeast(600)
            val h = height.coerceAtLeast(400)

            g2.font = Font("SansSerif", Font.BOLD, 12)
            g2.color = Color(15, 23, 42)
            g2.drawString("Partial Dependence (PDP) & Individual Expectation (ICE) - ${curve.featureName}", 30, 30)

            val plotX = 60
            val plotY = 60
            val plotW = w - 120
            val plotH = h - 120

            // Background
            g2.color = Color(248, 250, 252)
            g2.fillRect(plotX, plotY, plotW, plotH)
            g2.color = Color(226, 232, 240)
            g2.drawRect(plotX, plotY, plotW, plotH)

            val allValues = curve.iceCurves.flatMap { it.asIterable() } + curve.pdpValues.asIterable()
            val minY = allValues.minOrNull() ?: 0.0
            val maxY = allValues.maxOrNull() ?: 1.0
            val spanY = (maxY - minY).coerceAtLeast(1e-6)

            val nPts = curve.gridPoints.size
            if (nPts < 2) return

            fun toScreenX(idx: Int): Int = plotX + (idx * plotW) / (nPts - 1)
            fun toScreenY(v: Double): Int = plotY + plotH - ((v - minY) / spanY * plotH).toInt()

            // Draw ICE Curves (Semi-transparent individual rows)
            g2.color = Color(148, 163, 184, 90)
            g2.stroke = BasicStroke(1.0f)
            for (ice in curve.iceCurves) {
                for (i in 0 until nPts - 1) {
                    val x1 = toScreenX(i)
                    val y1 = toScreenY(ice[i])
                    val x2 = toScreenX(i + 1)
                    val y2 = toScreenY(ice[i + 1])
                    g2.drawLine(x1, y1, x2, y2)
                }
            }

            // Draw Average PDP Curve (Thick Royal Blue)
            g2.color = Color(37, 99, 235)
            g2.stroke = BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            for (i in 0 until nPts - 1) {
                val x1 = toScreenX(i)
                val y1 = toScreenY(curve.pdpValues[i])
                val x2 = toScreenX(i + 1)
                val y2 = toScreenY(curve.pdpValues[i + 1])
                g2.drawLine(x1, y1, x2, y2)
            }

            // Draw min/max axis labels
            g2.font = Font("Monospaced", Font.PLAIN, 10)
            g2.color = Color(71, 85, 105)
            g2.drawString("${String.format("%.2f", curve.gridPoints.first())}", plotX, plotY + plotH + 16)
            g2.drawString("${String.format("%.2f", curve.gridPoints.last())}", plotX + plotW - 40, plotY + plotH + 16)
            g2.drawString("Max: ${String.format("%.3f", maxY)}", plotX + 6, plotY + 16)
            g2.drawString("Min: ${String.format("%.3f", minY)}", plotX + 6, plotY + plotH - 6)
        }
    }
}
