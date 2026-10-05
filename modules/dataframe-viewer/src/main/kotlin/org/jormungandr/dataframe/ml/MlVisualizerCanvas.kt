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

import java.awt.*
import java.awt.geom.*
import javax.swing.JPanel
import javax.swing.UIManager
import kotlin.math.*

enum class MlVisualizerViewMode(val displayName: String) {
    REGRESSION_ACTUAL_VS_PRED("Actual vs. Predicted"),
    REGRESSION_RESIDUALS("Residuals vs. Predicted"),
    CLASSIFICATION_CONFUSION_MATRIX("Confusion Matrix Heatmap"),
    CLASSIFICATION_ROC_CURVE("ROC Curve (AUC)"),
    CLUSTERING_2D_SCATTER("Cluster Scatter & Centroids"),
    CLUSTERING_ELBOW_CURVE("Elbow Method (Inertia vs k)"),
    PCA_SCREE_PLOT("PCA Scree Plot (Explained Variance)"),
    FEATURE_WEIGHTS("Feature Weights / Importance")
}

/**
 * Interactive 2D Graphics visualizer canvas rendering rich evaluations
 * for Regression, Classification, Clustering, and PCA models.
 */
class MlVisualizerCanvas : JPanel() {

    var viewMode: MlVisualizerViewMode = MlVisualizerViewMode.REGRESSION_ACTUAL_VS_PRED
        set(value) {
            field = value
            repaint()
        }

    var regressionResult: RegressionResult? = null
        set(value) {
            field = value
            repaint()
        }

    var classificationResult: ClassificationResult? = null
        set(value) {
            field = value
            repaint()
        }

    var clusteringResult: ClusteringResult? = null
        set(value) {
            field = value
            repaint()
        }

    var pcaResult: PcaResult? = null
        set(value) {
            field = value
            repaint()
        }

    var featureImportanceItems: List<Pair<String, Double>> = emptyList()
        set(value) {
            field = value
            repaint()
        }

    init {
        background = Color(248, 250, 252)
        preferredSize = Dimension(650, 380)
        minimumSize = Dimension(300, 250)
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as? Graphics2D ?: return
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        // Adjust background for dark/light theme
        val isDark = isDarkTheme()
        val bg = if (isDark) Color(24, 26, 32) else Color(250, 252, 255)
        g2.color = bg
        g2.fillRect(0, 0, width, height)

        when (viewMode) {
            MlVisualizerViewMode.REGRESSION_ACTUAL_VS_PRED -> renderActualVsPredicted(g2, isDark)
            MlVisualizerViewMode.REGRESSION_RESIDUALS -> renderResiduals(g2, isDark)
            MlVisualizerViewMode.CLASSIFICATION_CONFUSION_MATRIX -> renderConfusionMatrix(g2, isDark)
            MlVisualizerViewMode.CLASSIFICATION_ROC_CURVE -> renderRocCurve(g2, isDark)
            MlVisualizerViewMode.CLUSTERING_2D_SCATTER -> renderClusterScatter(g2, isDark)
            MlVisualizerViewMode.CLUSTERING_ELBOW_CURVE -> renderElbowCurve(g2, isDark)
            MlVisualizerViewMode.PCA_SCREE_PLOT -> renderPcaScree(g2, isDark)
            MlVisualizerViewMode.FEATURE_WEIGHTS -> renderFeatureWeights(g2, isDark)
        }
    }

    // --- 1. Regression: Actual vs Predicted ---
    private fun renderActualVsPredicted(g2: Graphics2D, isDark: Boolean) {
        val res = regressionResult
        if (res == null || res.yTrue.isEmpty() || res.yPred.isEmpty()) {
            renderEmptyPlaceholder(g2, "No Regression Model Trained", "Click '▶ Train Model' to evaluate regression predictions.", isDark)
            return
        }

        val padding = 65
        val plotW = width - 2 * padding
        val plotH = height - 2 * padding
        if (plotW <= 0 || plotH <= 0) return

        val minVal = min(res.yTrue.minOrNull() ?: 0.0, res.yPred.minOrNull() ?: 0.0)
        val maxVal = max(res.yTrue.maxOrNull() ?: 1.0, res.yPred.maxOrNull() ?: 1.0)
        val span = if (maxVal > minVal) maxVal - minVal else 1.0

        drawAxes(g2, padding, plotW, plotH, "Actual Target (y)", "Predicted Target (ŷ)", isDark)

        // Ideal y = x dashed line
        g2.color = if (isDark) Color(239, 68, 68, 200) else Color(220, 38, 38, 200)
        g2.stroke = BasicStroke(2.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0f, floatArrayOf(6f, 6f), 0f)
        val x0 = padding
        val y0 = padding + plotH
        val x1 = padding + plotW
        val y1 = padding
        g2.drawLine(x0, y0, x1, y1)

        // Scatter points
        g2.stroke = BasicStroke(1.0f)
        val pointColor = if (isDark) Color(56, 189, 248, 200) else Color(2, 132, 199, 190)
        val pointBorder = if (isDark) Color(14, 116, 144) else Color(3, 105, 161)

        for (i in res.yTrue.indices) {
            val px = padding + ((res.yTrue[i] - minVal) / span * plotW).toInt()
            val py = padding + plotH - ((res.yPred[i] - minVal) / span * plotH).toInt()

            g2.color = pointColor
            g2.fillOval(px - 4, py - 4, 8, 8)
            g2.color = pointBorder
            g2.drawOval(px - 4, py - 4, 8, 8)
        }

        // Metrics badge card
        drawMetricBadge(
            g2, padding + 15, padding + 15,
            listOf("R²: %.4f".format(res.r2), "RMSE: %.4f".format(res.rmse), "MAE: %.4f".format(res.mae)),
            isDark
        )
    }

    // --- 2. Regression: Residuals vs Predicted ---
    private fun renderResiduals(g2: Graphics2D, isDark: Boolean) {
        val res = regressionResult
        if (res == null || res.residuals.isEmpty()) {
            renderEmptyPlaceholder(g2, "No Residual Data", "Fit a model to inspect residual distributions.", isDark)
            return
        }

        val padding = 65
        val plotW = width - 2 * padding
        val plotH = height - 2 * padding
        if (plotW <= 0 || plotH <= 0) return

        val minPred = res.yPred.minOrNull() ?: 0.0
        val maxPred = res.yPred.maxOrNull() ?: 1.0
        val spanPred = if (maxPred > minPred) maxPred - minPred else 1.0

        val maxResidual = max(abs(res.residuals.minOrNull() ?: 0.0), abs(res.residuals.maxOrNull() ?: 0.0)).coerceAtLeast(0.01)

        drawAxes(g2, padding, plotW, plotH, "Predicted Target (ŷ)", "Residuals (y - ŷ)", isDark)

        // Zero residual horizontal line
        val zeroY = padding + (plotH / 2)
        g2.color = if (isDark) Color(148, 163, 184) else Color(100, 116, 139)
        g2.stroke = BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0f, floatArrayOf(4f, 4f), 0f)
        g2.drawLine(padding, zeroY, padding + plotW, zeroY)

        // Points
        for (i in res.yPred.indices) {
            val px = padding + ((res.yPred[i] - minPred) / spanPred * plotW).toInt()
            val normErr = res.residuals[i] / maxResidual
            val py = zeroY - (normErr * (plotH / 2) * 0.9).toInt()

            val isPos = res.residuals[i] >= 0
            g2.color = if (isPos) Color(16, 185, 129, 180) else Color(244, 63, 94, 180)
            g2.fillOval(px - 4, py - 4, 8, 8)
        }

        drawMetricBadge(
            g2, padding + 15, padding + 15,
            listOf("Residual Mean: %.4f".format(res.residuals.average()), "Residuals Count: ${res.residuals.size}"),
            isDark
        )
    }

    // --- 3. Classification: Confusion Matrix Heatmap ---
    private fun renderConfusionMatrix(g2: Graphics2D, isDark: Boolean) {
        val res = classificationResult
        if (res == null || res.confusionMatrix.isEmpty()) {
            renderEmptyPlaceholder(g2, "No Classification Model Trained", "Fit a classifier to inspect the Confusion Matrix.", isDark)
            return
        }

        val matrix = res.confusionMatrix
        val numClasses = matrix.size
        val labels = if (res.classLabels.size >= numClasses) res.classLabels else (0 until numClasses).map { "Class $it" }

        val totalSamples = matrix.sumOf { row -> row.sum() }.coerceAtLeast(1)
        val maxCell = matrix.maxOfOrNull { row -> row.maxOrNull() ?: 0 }?.coerceAtLeast(1) ?: 1

        val padding = 70
        val cellSize = min((width - 2 * padding) / numClasses, (height - 2 * padding) / numClasses).coerceIn(40, 120)
        val startX = (width - (numClasses * cellSize)) / 2
        val startY = (height - (numClasses * cellSize)) / 2

        g2.font = Font("SansSerif", Font.BOLD, 12)
        val textColor = if (isDark) Color(226, 232, 240) else Color(30, 41, 59)

        // Draw Headers
        g2.color = textColor
        g2.drawString("Predicted Class ➔", startX, startY - 15)

        for (c in 0 until numClasses) {
            val lbl = labels[c].take(10)
            g2.drawString(lbl, startX + c * cellSize + (cellSize / 4), startY - 2)
        }

        for (r in 0 until numClasses) {
            val lbl = labels[r].take(10)
            g2.drawString(lbl, startX - padding + 10, startY + r * cellSize + (cellSize / 2) + 4)

            for (c in 0 until numClasses) {
                val count = matrix[r][c]
                val intensity = count.toDouble() / maxCell.toDouble()
                val isDiagonal = r == c

                val cellBg = if (isDiagonal) {
                    if (isDark) Color(16, 185, 129, (80 + 175 * intensity).toInt()) else Color(34, 197, 94, (60 + 190 * intensity).toInt())
                } else {
                    if (isDark) Color(239, 68, 68, (30 + 180 * intensity).toInt()) else Color(244, 63, 94, (20 + 170 * intensity).toInt())
                }

                val x = startX + c * cellSize
                val y = startY + r * cellSize

                g2.color = cellBg
                g2.fillRoundRect(x + 2, y + 2, cellSize - 4, cellSize - 4, 8, 8)

                g2.color = if (isDark) Color(71, 85, 105) else Color(203, 213, 225)
                g2.drawRoundRect(x + 2, y + 2, cellSize - 4, cellSize - 4, 8, 8)

                // Text count & percentage
                val pct = (count.toDouble() / totalSamples * 100.0)
                g2.color = textColor
                g2.font = Font("SansSerif", Font.BOLD, 14)
                g2.drawString("$count", x + (cellSize / 3), y + (cellSize / 2) - 2)
                g2.font = Font("SansSerif", Font.PLAIN, 10)
                g2.drawString("%.1f%%".format(pct), x + (cellSize / 3), y + (cellSize / 2) + 14)
            }
        }

        // Accuracy card
        drawMetricBadge(
            g2, 20, height - 50,
            listOf("Accuracy: %.2f%%".format(res.accuracy * 100), "F1 Score: %.4f".format(res.f1)),
            isDark
        )
    }

    // --- 4. Classification: ROC Curve ---
    private fun renderRocCurve(g2: Graphics2D, isDark: Boolean) {
        val res = classificationResult
        if (res == null || res.rocCurve.isEmpty()) {
            renderEmptyPlaceholder(g2, "No ROC Data Available", "Binary or Multiclass probability predictions required.", isDark)
            return
        }

        val padding = 65
        val plotW = width - 2 * padding
        val plotH = height - 2 * padding
        if (plotW <= 0 || plotH <= 0) return

        drawAxes(g2, padding, plotW, plotH, "False Positive Rate (FPR)", "True Positive Rate (TPR)", isDark)

        // Diagonal chance line (y = x)
        g2.color = if (isDark) Color(148, 163, 184) else Color(148, 163, 184)
        g2.stroke = BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0f, floatArrayOf(4f, 4f), 0f)
        g2.drawLine(padding, padding + plotH, padding + plotW, padding)

        // Shaded AUC Path
        val path = Path2D.Double()
        path.moveTo(padding.toDouble(), (padding + plotH).toDouble())

        for ((fpr, tpr) in res.rocCurve) {
            val px = padding + (fpr * plotW)
            val py = padding + plotH - (tpr * plotH)
            path.lineTo(px, py)
        }
        path.lineTo((padding + plotW).toDouble(), (padding + plotH).toDouble())
        path.closePath()

        g2.color = if (isDark) Color(124, 58, 237, 70) else Color(147, 51, 234, 50)
        g2.fill(path)

        // ROC Curve stroke
        g2.color = if (isDark) Color(168, 85, 247) else Color(124, 58, 237)
        g2.stroke = BasicStroke(2.5f)
        var prevX = padding
        var prevY = padding + plotH

        for ((fpr, tpr) in res.rocCurve) {
            val px = padding + (fpr * plotW).toInt()
            val py = padding + plotH - (tpr * plotH).toInt()
            g2.drawLine(prevX, prevY, px, py)
            prevX = px
            prevY = py
        }

        drawMetricBadge(
            g2, padding + 15, padding + 15,
            listOf("ROC AUC: %.4f".format(res.rocCurveAuc), "Precision: %.4f".format(res.precision), "Recall: %.4f".format(res.recall)),
            isDark
        )
    }

    // --- 5. Clustering: 2D Scatter with Centroids ---
    private fun renderClusterScatter(g2: Graphics2D, isDark: Boolean) {
        val res = clusteringResult
        if (res == null || res.data2D.isEmpty()) {
            renderEmptyPlaceholder(g2, "No Clustering Model Trained", "Fit K-Means to inspect clustered point clouds.", isDark)
            return
        }

        val padding = 65
        val plotW = width - 2 * padding
        val plotH = height - 2 * padding
        if (plotW <= 0 || plotH <= 0) return

        val minX = res.data2D.minOfOrNull { it[0] } ?: 0.0
        val maxX = res.data2D.maxOfOrNull { it[0] } ?: 1.0
        val minY = res.data2D.minOfOrNull { it[1] } ?: 0.0
        val maxY = res.data2D.maxOfOrNull { it[1] } ?: 1.0

        val spanX = if (maxX > minX) maxX - minX else 1.0
        val spanY = if (maxY > minY) maxY - minY else 1.0

        val featX = res.featureNames.getOrNull(0) ?: "Dim 1"
        val featY = res.featureNames.getOrNull(1) ?: "Dim 2"
        drawAxes(g2, padding, plotW, plotH, featX, featY, isDark)

        val clusterPalette = listOf(
            Color(14, 165, 233), // Sky Blue
            Color(239, 68, 68),  // Red
            Color(16, 185, 129), // Emerald
            Color(168, 85, 247), // Purple
            Color(245, 158, 11), // Amber
            Color(236, 72, 153)  // Pink
        )

        // Points
        for (i in res.data2D.indices) {
            val c = res.clusterAssignments[i]
            val color = clusterPalette[c % clusterPalette.size]

            val px = padding + ((res.data2D[i][0] - minX) / spanX * plotW).toInt()
            val py = padding + plotH - ((res.data2D[i][1] - minY) / spanY * plotH).toInt()

            g2.color = Color(color.red, color.green, color.blue, 180)
            g2.fillOval(px - 4, py - 4, 8, 8)
        }

        // Draw Centroids with star badges
        for (c in 0 until res.k) {
            val cx = padding + ((res.centroids[c][0] - minX) / spanX * plotW).toInt()
            val cy = padding + plotH - ((res.centroids[c][1] - minY) / spanY * plotH).toInt()

            val color = clusterPalette[c % clusterPalette.size]
            g2.color = Color.BLACK
            g2.fillOval(cx - 9, cy - 9, 18, 18)
            g2.color = color
            g2.fillOval(cx - 7, cy - 7, 14, 14)
            g2.color = Color.WHITE
            g2.font = Font("SansSerif", Font.BOLD, 10)
            g2.drawString("${c + 1}", cx - 3, cy + 4)
        }

        drawMetricBadge(
            g2, padding + 15, padding + 15,
            listOf("Clusters (k): ${res.k}", "Inertia (WCSS): %.2f".format(res.inertia)),
            isDark
        )
    }

    // --- 6. Clustering: Elbow Method Curve ---
    private fun renderElbowCurve(g2: Graphics2D, isDark: Boolean) {
        val res = clusteringResult
        if (res == null || res.elbowCurve.isEmpty()) {
            renderEmptyPlaceholder(g2, "No Elbow Curve Available", "Fit K-Means to compute multi-k inertia bend.", isDark)
            return
        }

        val padding = 65
        val plotW = width - 2 * padding
        val plotH = height - 2 * padding
        if (plotW <= 0 || plotH <= 0) return

        val minK = res.elbowCurve.minOf { it.first }
        val maxK = res.elbowCurve.maxOf { it.first }
        val maxInertia = res.elbowCurve.maxOf { it.second }.coerceAtLeast(1.0)

        drawAxes(g2, padding, plotW, plotH, "Number of Clusters (k)", "Inertia (Within-Cluster Sum of Squares)", isDark)

        g2.color = if (isDark) Color(245, 158, 11) else Color(217, 119, 6)
        g2.stroke = BasicStroke(2.5f)

        var prevX = 0
        var prevY = 0

        for (i in res.elbowCurve.indices) {
            val (kVal, inertia) = res.elbowCurve[i]
            val px = padding + ((kVal - minK).toDouble() / (maxK - minK).coerceAtLeast(1) * plotW).toInt()
            val py = padding + plotH - ((inertia / maxInertia) * plotH).toInt()

            if (i > 0) {
                g2.drawLine(prevX, prevY, px, py)
            }

            g2.fillOval(px - 5, py - 5, 10, 10)
            prevX = px
            prevY = py
        }

        drawMetricBadge(
            g2, padding + 15, padding + 15,
            listOf("Optimal Elbow Search", "Current Model k = ${res.k}"),
            isDark
        )
    }

    // --- 7. PCA Scree Plot ---
    private fun renderPcaScree(g2: Graphics2D, isDark: Boolean) {
        val res = pcaResult
        if (res == null || res.explainedVarianceRatio.isEmpty()) {
            renderEmptyPlaceholder(g2, "No PCA Model Trained", "Fit PCA to view explained variance ratios.", isDark)
            return
        }

        val padding = 65
        val plotW = width - 2 * padding
        val plotH = height - 2 * padding
        if (plotW <= 0 || plotH <= 0) return

        val k = res.explainedVarianceRatio.size
        val barW = (plotW / (k * 2)).coerceIn(20, 60)

        drawAxes(g2, padding, plotW, plotH, "Principal Component", "Explained Variance Ratio", isDark)

        val barColor = if (isDark) Color(14, 165, 233, 200) else Color(2, 132, 199, 200)

        for (i in 0 until k) {
            val ratio = res.explainedVarianceRatio[i]
            val bx = padding + (i * 2 + 1) * barW
            val bh = (ratio * plotH).toInt()
            val by = padding + plotH - bh

            g2.color = barColor
            g2.fillRoundRect(bx, by, barW, bh, 6, 6)

            g2.color = if (isDark) Color(226, 232, 240) else Color(30, 41, 59)
            g2.font = Font("SansSerif", Font.PLAIN, 10)
            g2.drawString("PC${i + 1}", bx + 4, padding + plotH + 14)
            g2.drawString("%.1f%%".format(ratio * 100), bx + 2, by - 4)
        }

        drawMetricBadge(
            g2, padding + 15, padding + 15,
            listOf("Top 2 PCs Variance: %.1f%%".format((res.cumulativeVariance.getOrNull(1) ?: res.cumulativeVariance[0]) * 100)),
            isDark
        )
    }

    // --- 8. Feature Weights / Importance ---
    private fun renderFeatureWeights(g2: Graphics2D, isDark: Boolean) {
        val items = featureImportanceItems
        if (items.isEmpty()) {
            renderEmptyPlaceholder(g2, "No Feature Importance Data", "Fit model or run preliminary analysis to view weights.", isDark)
            return
        }

        val padding = 70
        val plotW = width - 2 * padding
        val plotH = height - 2 * padding
        if (plotW <= 0 || plotH <= 0) return

        val count = min(items.size, 10)
        val maxVal = items.take(count).maxOfOrNull { abs(it.second) }?.coerceAtLeast(0.001) ?: 1.0

        val rowH = (plotH / count).coerceIn(20, 40)
        val midX = padding + (plotW / 2)

        g2.color = if (isDark) Color(71, 85, 105) else Color(203, 213, 225)
        g2.drawLine(midX, padding, midX, padding + count * rowH)

        for (i in 0 until count) {
            val (name, weight) = items[i]
            val y = padding + i * rowH + 4
            val h = rowH - 8

            val normW = ((abs(weight) / maxVal) * (plotW / 2) * 0.9).toInt()

            val isPos = weight >= 0
            g2.color = if (isPos) {
                if (isDark) Color(16, 185, 129, 210) else Color(5, 150, 105, 210)
            } else {
                if (isDark) Color(244, 63, 94, 210) else Color(225, 29, 72, 210)
            }

            if (isPos) {
                g2.fillRoundRect(midX, y, normW, h, 6, 6)
            } else {
                g2.fillRoundRect(midX - normW, y, normW, h, 6, 6)
            }

            g2.color = if (isDark) Color(226, 232, 240) else Color(30, 41, 59)
            g2.font = Font("SansSerif", Font.PLAIN, 11)
            g2.drawString(name.take(16), padding - 55, y + (h / 2) + 4)
            val valStr = "%+.3f".format(weight)
            if (isPos) {
                g2.drawString(valStr, midX + normW + 6, y + (h / 2) + 4)
            } else {
                g2.drawString(valStr, midX - normW - 40, y + (h / 2) + 4)
            }
        }
    }

    // --- Helper Graphics Utilities ---

    private fun drawAxes(g2: Graphics2D, padding: Int, plotW: Int, plotH: Int, xLabel: String, yLabel: String, isDark: Boolean) {
        val axisColor = if (isDark) Color(100, 116, 139) else Color(148, 163, 184)
        val gridColor = if (isDark) Color(39, 44, 56) else Color(241, 245, 249)

        // Grid lines
        g2.color = gridColor
        g2.stroke = BasicStroke(1.0f)
        for (i in 1..4) {
            val y = padding + (i * plotH / 5)
            g2.drawLine(padding, y, padding + plotW, y)
            val x = padding + (i * plotW / 5)
            g2.drawLine(x, padding, x, padding + plotH)
        }

        // Axes lines
        g2.color = axisColor
        g2.stroke = BasicStroke(1.5f)
        g2.drawLine(padding, padding + plotH, padding + plotW, padding + plotH) // X axis
        g2.drawLine(padding, padding, padding, padding + plotH)                 // Y axis

        // Labels
        g2.color = if (isDark) Color(203, 213, 225) else Color(71, 85, 105)
        g2.font = Font("SansSerif", Font.PLAIN, 11)
        g2.drawString(xLabel, padding + (plotW / 2) - 30, padding + plotH + 30)

        val oldTrans = g2.transform
        g2.rotate(-Math.PI / 2.0, (padding - 35).toDouble(), (padding + (plotH / 2)).toDouble())
        g2.drawString(yLabel, padding - 35, padding + (plotH / 2))
        g2.transform = oldTrans
    }

    private fun drawMetricBadge(g2: Graphics2D, x: Int, y: Int, lines: List<String>, isDark: Boolean) {
        val cardBg = if (isDark) Color(30, 41, 59, 220) else Color(255, 255, 255, 230)
        val cardBorder = if (isDark) Color(71, 85, 105) else Color(226, 232, 240)
        val textColor = if (isDark) Color(241, 245, 249) else Color(15, 23, 42)

        val cardW = 160
        val cardH = lines.size * 18 + 12

        g2.color = cardBg
        g2.fillRoundRect(x, y, cardW, cardH, 8, 8)
        g2.color = cardBorder
        g2.drawRoundRect(x, y, cardW, cardH, 8, 8)

        g2.color = textColor
        g2.font = Font("SansSerif", Font.BOLD, 11)
        for (i in lines.indices) {
            g2.drawString(lines[i], x + 10, y + 16 + (i * 18))
        }
    }

    private fun renderEmptyPlaceholder(g2: Graphics2D, title: String, subtitle: String, isDark: Boolean) {
        val textColor = if (isDark) Color(148, 163, 184) else Color(100, 116, 139)
        g2.color = textColor
        g2.font = Font("SansSerif", Font.BOLD, 15)
        val fm1 = g2.fontMetrics
        g2.drawString(title, (width - fm1.stringWidth(title)) / 2, height / 2 - 10)

        g2.font = Font("SansSerif", Font.PLAIN, 12)
        val fm2 = g2.fontMetrics
        g2.drawString(subtitle, (width - fm2.stringWidth(subtitle)) / 2, height / 2 + 14)
    }

    private fun isDarkTheme(): Boolean {
        val bg = UIManager.getColor("Panel.background") ?: return false
        val lum = 0.299 * bg.red + 0.587 * bg.green + 0.114 * bg.blue
        return lum < 128.0
    }
}
