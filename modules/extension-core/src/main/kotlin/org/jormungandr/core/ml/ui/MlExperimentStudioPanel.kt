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

package org.jormungandr.core.ml.ui

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextArea
import org.jormungandr.core.ml.*
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.awt.geom.Path2D
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder
import javax.swing.table.DefaultTableModel

/**
 * Visual ML Experiment Tracker & Metric Studio Panel (MLflow / Weights & Biases style).
 * Features run leaderboards, interactive convergence loss/accuracy curve charts,
 * multi-run overlays, hyperparameter comparisons, and Python code generation.
 */
class MlExperimentStudioPanel(
    private val project: Project? = null,
    private val onInsertNotebook: ((String) -> Unit)? = null
) : JPanel(BorderLayout()) {

    private val experimentCombo = JComboBox<String>()
    private var currentRuns: List<MlRun> = emptyList()

    // Leaderboard Table
    private val tableModel = object : DefaultTableModel(
        arrayOf("Compare", "Status", "Run Name", "Model", "Val Loss", "Val Acc / Score", "Duration", "Started"), 0
    ) {
        override fun getColumnClass(columnIndex: Int): Class<*> {
            return if (columnIndex == 0) java.lang.Boolean::class.java else java.lang.String::class.java
        }

        override fun isCellEditable(row: Int, column: Int): Boolean = column == 0
    }
    private val leaderboardTable = JTable(tableModel).apply {
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        rowHeight = 24
    }

    // Metric Curve Chart Canvas
    private val chartCanvas = MetricCurveCanvas()

    // Params & Metrics Detail Table
    private val detailsModel = DefaultTableModel(arrayOf("Property / Metric", "Value"), 0)
    private val detailsTable = JTable(detailsModel)

    // Python Snippet Area
    private val pythonSnippetArea = JBTextArea().apply {
        font = Font("Monospaced", Font.PLAIN, 13)
        isEditable = false
        margin = Insets(6, 6, 6, 6)
    }

    private val metricSelectorCombo = JComboBox<String>()
    private val statusLabel = JBLabel("Ready").apply {
        foreground = Color(110, 110, 110)
    }

    init {
        val mainSplit = JSplitPane(JSplitPane.VERTICAL_SPLIT).apply {
            resizeWeight = 0.45
            isContinuousLayout = true
            border = null
        }

        // Top Leaderboard Panel
        val topPanel = JPanel(BorderLayout())
        topPanel.add(createHeaderToolbar(), BorderLayout.NORTH)
        topPanel.add(JBScrollPane(leaderboardTable), BorderLayout.CENTER)

        // Bottom Studio Tabs
        val bottomTabs = JBTabbedPane()

        // Tab 1: Curves Canvas
        val curvesPanel = JPanel(BorderLayout())
        val curvesToolbar = JPanel(FlowLayout(FlowLayout.LEFT, 6, 2)).apply {
            border = EmptyBorder(4, 6, 4, 6)
        }
        curvesToolbar.add(JBLabel("Metric:"))
        curvesToolbar.add(metricSelectorCombo)
        metricSelectorCombo.addActionListener {
            updateChart()
        }
        curvesPanel.add(curvesToolbar, BorderLayout.NORTH)
        curvesPanel.add(chartCanvas, BorderLayout.CENTER)

        bottomTabs.addTab("📈 Metric Convergence Curves", curvesPanel)
        bottomTabs.addTab("⚙️ Run Parameters & Metrics", JBScrollPane(detailsTable))
        bottomTabs.addTab("📦 Checkpoint Vault", createCheckpointVaultTab())
        bottomTabs.addTab("🚀 Production Packager", createDeploymentPackagerTab())
        bottomTabs.addTab("🐍 Python Logger Snippet", JBScrollPane(pythonSnippetArea))

        mainSplit.topComponent = topPanel
        mainSplit.bottomComponent = bottomTabs

        add(mainSplit, BorderLayout.CENTER)
        add(createStatusBar(), BorderLayout.SOUTH)

        setupTableInteractions()
        refreshExperiments()

        MlExperimentTrackerService.addChangeListener {
            SwingUtilities.invokeLater {
                refreshRuns()
            }
        }
    }

    private fun createHeaderToolbar(): JPanel {
        val panel = JPanel(BorderLayout()).apply {
            border = EmptyBorder(6, 8, 6, 8)
        }
        val left = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0))
        val right = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0))

        val newExpBtn = JButton("+ Experiment").apply {
            isFocusable = false
            font = font.deriveFont(Font.PLAIN, 11f)
            addActionListener { createNewExperimentDialog() }
        }

        val startRunBtn = JButton("▶ Start Run").apply {
            isFocusable = false
            font = font.deriveFont(Font.BOLD, 11f)
            foreground = Color(20, 120, 20)
            addActionListener { startNewRunDialog() }
        }

        val copyCodeBtn = JButton("📋 Copy Logger").apply {
            isFocusable = false
            font = font.deriveFont(Font.PLAIN, 11f)
            addActionListener {
                val code = pythonSnippetArea.text
                Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(code), null)
                statusLabel.text = "Copied ML tracker snippet to clipboard"
            }
        }

        val insertNbBtn = JButton("➕ Insert in Notebook").apply {
            isFocusable = false
            font = font.deriveFont(Font.PLAIN, 11f)
            addActionListener {
                onInsertNotebook?.invoke(pythonSnippetArea.text)
            }
        }

        val refreshBtn = JButton("🔄").apply {
            isFocusable = false
            addActionListener { refreshRuns() }
        }

        experimentCombo.addActionListener {
            refreshRuns()
        }

        left.add(JBLabel("Experiment:"))
        left.add(experimentCombo)
        left.add(newExpBtn)

        right.add(startRunBtn)
        right.add(copyCodeBtn)
        if (onInsertNotebook != null) {
            right.add(insertNbBtn)
        }
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
        leaderboardTable.selectionModel.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                val row = leaderboardTable.selectedRow
                if (row >= 0 && row < currentRuns.size) {
                    showRunDetails(currentRuns[row])
                    updateChart()
                }
            }
        }

        tableModel.addTableModelListener { e ->
            if (e.column == 0) {
                updateChart()
            }
        }
    }

    private fun refreshExperiments() {
        val prevSelected = experimentCombo.selectedItem as? String
        experimentCombo.removeAllItems()
        val allExp = MlExperimentTrackerService.getAllExperiments()
        for (exp in allExp) {
            experimentCombo.addItem(exp.name)
        }
        if (prevSelected != null) {
            experimentCombo.selectedItem = prevSelected
        } else if (allExp.isNotEmpty()) {
            experimentCombo.selectedIndex = 0
        }
        refreshRuns()
    }

    private fun refreshRuns() {
        val expName = experimentCombo.selectedItem as? String ?: return
        val exp = MlExperimentTrackerService.getAllExperiments().find { it.name == expName } ?: return

        currentRuns = MlExperimentTrackerService.getRunsForExperiment(exp.experimentId)

        tableModel.rowCount = 0
        val sdf = SimpleDateFormat("HH:mm:ss")

        for (run in currentRuns) {
            val lossVal = run.getBestMetric("val_loss") ?: run.getBestMetric("loss") ?: run.getBestMetric("eval_loss")
            val lossStr = lossVal?.let { "%.4f".format(it) } ?: "-"

            val accVal = run.getBestMetric("val_accuracy", minimize = false)
                ?: run.getBestMetric("auc_roc", minimize = false)
                ?: run.getBestMetric("f1_score", minimize = false)
            val accStr = accVal?.let { "%.4f".format(it) } ?: "-"

            val modelName = run.parameters["model"] ?: run.parameters["base_model"] ?: "Baseline"

            tableModel.addRow(
                arrayOf(
                    false,
                    run.status.badge,
                    run.runName,
                    modelName,
                    lossStr,
                    accStr,
                    run.durationFormatted,
                    sdf.format(Date(run.startTime))
                )
            )
        }

        if (tableModel.rowCount > 0 && leaderboardTable.selectedRow < 0) {
            leaderboardTable.setRowSelectionInterval(0, 0)
        }

        // Update metric selector combo with available metric names
        val allMetricKeys = currentRuns.flatMap { it.metrics.keys }.distinct().sorted()
        val prevMetric = metricSelectorCombo.selectedItem as? String
        metricSelectorCombo.removeAllItems()
        for (k in allMetricKeys) {
            metricSelectorCombo.addItem(k)
        }
        if (prevMetric != null && allMetricKeys.contains(prevMetric)) {
            metricSelectorCombo.selectedItem = prevMetric
        } else if (allMetricKeys.isNotEmpty()) {
            metricSelectorCombo.selectedIndex = 0
        }

        pythonSnippetArea.text = MlExperimentTrackerService.getPythonLoggerSnippet(exp.name)
        statusLabel.text = "Loaded ${currentRuns.size} run(s) for '${exp.name}'"
        updateChart()
    }

    private fun showRunDetails(run: MlRun) {
        detailsModel.rowCount = 0
        detailsModel.addRow(arrayOf("Run ID", run.runId))
        detailsModel.addRow(arrayOf("Run Name", run.runName))
        detailsModel.addRow(arrayOf("Status", run.status.name))
        detailsModel.addRow(arrayOf("Duration", run.durationFormatted))

        detailsModel.addRow(arrayOf("--- Parameters ---", "-----------------"))
        for ((k, v) in run.parameters) {
            detailsModel.addRow(arrayOf("param: $k", v))
        }

        detailsModel.addRow(arrayOf("--- Metrics ---", "-----------------"))
        for ((k, steps) in run.metrics) {
            val last = steps.lastOrNull()?.value
            val min = steps.map { it.value }.minOrNull()
            val max = steps.map { it.value }.maxOrNull()
            detailsModel.addRow(arrayOf("metric: $k (final)", last?.let { "%.4f".format(it) } ?: "-"))
            detailsModel.addRow(arrayOf("metric: $k (best)", min?.let { "%.4f".format(it) } ?: "-"))
        }
    }

    private fun updateChart() {
        val selectedMetric = metricSelectorCombo.selectedItem as? String ?: return

        // Check if any runs have "Compare" checked
        val comparedRuns = mutableListOf<MlRun>()
        for (row in 0 until tableModel.rowCount) {
            val checked = tableModel.getValueAt(row, 0) as? Boolean ?: false
            if (checked && row < currentRuns.size) {
                comparedRuns.add(currentRuns[row])
            }
        }

        // If none checked for comparison, show currently selected row
        if (comparedRuns.isEmpty()) {
            val sel = leaderboardTable.selectedRow
            if (sel >= 0 && sel < currentRuns.size) {
                comparedRuns.add(currentRuns[sel])
            }
        }

        chartCanvas.setData(selectedMetric, comparedRuns)
    }

    private fun createNewExperimentDialog() {
        val name = JOptionPane.showInputDialog(this, "Enter Experiment Name:", "New ML Experiment", JOptionPane.PLAIN_MESSAGE)
        if (!name.isNullOrBlank()) {
            val exp = MlExperimentTrackerService.createExperiment(name.trim())
            refreshExperiments()
            experimentCombo.selectedItem = exp.name
        }
    }

    private fun startNewRunDialog() {
        val expName = experimentCombo.selectedItem as? String ?: return
        val exp = MlExperimentTrackerService.getAllExperiments().find { it.name == expName } ?: return

        val name = JOptionPane.showInputDialog(this, "Enter Run Name:", "Start ML Run", JOptionPane.PLAIN_MESSAGE)
        if (!name.isNullOrBlank()) {
            val run = MlExperimentTrackerService.startRun(exp.experimentId, name.trim(), mapOf("model" to "CustomScript"))
            refreshRuns()
            statusLabel.text = "Started run '${run.runName}' (${run.runId})"
        }
    }

    private fun createCheckpointVaultTab(): JPanel {
        val panel = JPanel(BorderLayout(0, 6)).apply {
            border = EmptyBorder(6, 6, 6, 6)
        }
        val top = JPanel(BorderLayout())
        val title = JBLabel("📦 Linked Model Checkpoints (.safetensors, .onnx, .pt, .h5)").apply {
            font = font.deriveFont(Font.BOLD, 12f)
        }
        val actionBox = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0))
        val registerBtn = JButton("➕ Register Checkpoint").apply {
            isFocusable = false
            addActionListener {
                val fChooser = JFileChooser()
                if (fChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                    val sel = fChooser.selectedFile
                    val ext = sel.extension.uppercase()
                    statusLabel.text = "Linked checkpoint: ${sel.name} ($ext, ${sel.length() / 1024} KB)"
                }
            }
        }
        actionBox.add(registerBtn)
        top.add(title, BorderLayout.WEST)
        top.add(actionBox, BorderLayout.EAST)
        panel.add(top, BorderLayout.NORTH)

        val vaultModel = DefaultTableModel(arrayOf("Model Name", "Format", "Size", "Linked Run ID", "Input Signature", "Output Signature"), 0)
        vaultModel.addRow(arrayOf("qwen2.5_lora_rank16.safetensors", "SAFETENSORS", "1.2 GB", "r-lora16", "input_ids [1, 2048]", "logits [1, 2048, 151936]"))
        vaultModel.addRow(arrayOf("xgboost_churn_v2.onnx", "ONNX", "4.8 MB", "r-xgb150", "features [1, 14] (float32)", "probabilities [1, 2]"))
        vaultModel.addRow(arrayOf("random_forest_baseline.joblib", "JOBLIB", "12.3 MB", "r-rf100", "X [n, 14]", "prediction [n, 1]"))

        val vaultTable = JTable(vaultModel).apply { rowHeight = 24 }
        panel.add(JBScrollPane(vaultTable), BorderLayout.CENTER)
        return panel
    }

    private fun createDeploymentPackagerTab(): JPanel {
        val panel = JPanel(BorderLayout(0, 6)).apply {
            border = EmptyBorder(6, 6, 6, 6)
        }

        val topForm = JPanel(FlowLayout(FlowLayout.LEFT, 8, 2))
        val modelNameField = JTextField("churn_predictor", 12)
        val frameworkCombo = JComboBox(arrayOf("ONNX", "PyTorch", "Safetensors", "Scikit-Learn", "XGBoost"))
        val featuresField = JTextField("age, balance, tenure, num_products, credit_score", 20)
        val generateBtn = JButton("⚡ Generate Microservice").apply {
            isFocusable = false
            font = font.deriveFont(Font.BOLD, 11f)
            background = Color(16, 185, 129)
            foreground = Color.WHITE
        }
        val exportBtn = JButton("💾 Export Bundle to Folder...").apply {
            isFocusable = false
        }

        topForm.add(JBLabel("Model Name:"))
        topForm.add(modelNameField)
        topForm.add(JBLabel("Framework:"))
        topForm.add(frameworkCombo)
        topForm.add(JBLabel("Features:"))
        topForm.add(featuresField)
        topForm.add(generateBtn)
        topForm.add(exportBtn)

        val codeTabs = JBTabbedPane()
        val appPyArea = JBTextArea().apply { font = Font("Monospaced", Font.PLAIN, 12); isEditable = false }
        val dockerArea = JBTextArea().apply { font = Font("Monospaced", Font.PLAIN, 12); isEditable = false }
        val reqsArea = JBTextArea().apply { font = Font("Monospaced", Font.PLAIN, 12); isEditable = false }
        val clientArea = JBTextArea().apply { font = Font("Monospaced", Font.PLAIN, 12); isEditable = false }
        val scriptArea = JBTextArea().apply { font = Font("Monospaced", Font.PLAIN, 12); isEditable = false }

        codeTabs.addTab("app.py (FastAPI)", JBScrollPane(appPyArea))
        codeTabs.addTab("Dockerfile", JBScrollPane(dockerArea))
        codeTabs.addTab("requirements.txt", JBScrollPane(reqsArea))
        codeTabs.addTab("client_test.py", JBScrollPane(clientArea))
        codeTabs.addTab("run_service.sh", JBScrollPane(scriptArea))

        var currentBundle = ModelDeploymentPackager.generateBundle(
            modelName = "churn_predictor",
            framework = "ONNX",
            inputFeatures = listOf("age", "balance", "tenure", "num_products", "credit_score")
        )

        fun updateCodeAreas(bundle: DeploymentBundle) {
            currentBundle = bundle
            appPyArea.text = bundle.appPy
            dockerArea.text = bundle.dockerfile
            reqsArea.text = bundle.requirementsTxt
            clientArea.text = bundle.clientTestPy
            scriptArea.text = bundle.launchScriptSh
        }

        updateCodeAreas(currentBundle)

        generateBtn.addActionListener {
            val feats = featuresField.text.split(",").map { it.trim() }.filter { it.isNotBlank() }
            val b = ModelDeploymentPackager.generateBundle(
                modelName = modelNameField.text.trim(),
                framework = frameworkCombo.selectedItem?.toString() ?: "ONNX",
                inputFeatures = if (feats.isNotEmpty()) feats else listOf("f1", "f2", "f3")
            )
            updateCodeAreas(b)
            statusLabel.text = "Generated production deployment service for ${b.modelName} (${b.framework})."
        }

        exportBtn.addActionListener {
            val chooser = JFileChooser().apply {
                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                dialogTitle = "Select Target Directory for Deployment Bundle"
            }
            if (chooser.showOpenDialog(panel) == JFileChooser.APPROVE_OPTION) {
                val targetDir = chooser.selectedFile
                val files = currentBundle.exportToDirectory(targetDir)
                JOptionPane.showMessageDialog(panel, "Exported ${files.size} microservice files to ${targetDir.absolutePath}", "Deployment Packager", JOptionPane.INFORMATION_MESSAGE)
                statusLabel.text = "Exported microservice bundle to ${targetDir.name}"
            }
        }

        panel.add(topForm, BorderLayout.NORTH)
        panel.add(codeTabs, BorderLayout.CENTER)
        return panel
    }

    /**
     * Interactive 2D canvas plotting multi-run convergence curves.
     */
    private class MetricCurveCanvas : JPanel() {
        private var metricName: String = "loss"
        private var runs: List<MlRun> = emptyList()

        private val runColors = arrayOf(
            Color(66, 133, 244),  // Google Blue
            Color(234, 67, 53),   // Google Red
            Color(251, 188, 4),   // Google Yellow
            Color(52, 168, 83),   // Google Green
            Color(156, 39, 176),  // Purple
            Color(255, 111, 0)    // Deep Orange
        )

        init {
            background = Color.WHITE
            border = LineBorder(Color(230, 230, 230), 1)
        }

        fun setData(metricName: String, runs: List<MlRun>) {
            this.metricName = metricName
            this.runs = runs
            repaint()
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2 = g as? Graphics2D ?: return
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

            val w = width
            val h = height
            val padding = 50

            if (runs.isEmpty()) {
                g2.color = Color(150, 150, 150)
                g2.font = font.deriveFont(Font.PLAIN, 13f)
                val msg = "Select or check runs above to plot convergence curves for '$metricName'"
                val bounds = g2.fontMetrics.getStringBounds(msg, g2)
                g2.drawString(msg, ((w - bounds.width) / 2).toInt(), h / 2)
                return
            }

            // Find global min and max across all plotted runs
            var minVal = Double.MAX_VALUE
            var maxVal = Double.MIN_VALUE
            var maxStep = 1

            for (run in runs) {
                val steps = run.metrics[metricName] ?: continue
                for (s in steps) {
                    if (s.value < minVal) minVal = s.value
                    if (s.value > maxVal) maxVal = s.value
                    if (s.step > maxStep) maxStep = s.step
                }
            }

            if (minVal == Double.MAX_VALUE || maxVal == Double.MIN_VALUE) {
                g2.color = Color(150, 150, 150)
                g2.drawString("No step metrics recorded for '$metricName' in selected run(s)", padding, h / 2)
                return
            }

            // Adjust bounds slightly
            if (minVal == maxVal) {
                minVal -= 0.1
                maxVal += 0.1
            }
            val valRange = maxVal - minVal

            val plotW = (w - padding * 2).coerceAtLeast(10)
            val plotH = (h - padding * 2).coerceAtLeast(10)

            // Draw Gridlines and Axes
            g2.color = Color(240, 240, 240)
            for (i in 0..4) {
                val y = padding + (plotH * i / 4)
                g2.drawLine(padding, y, w - padding, y)

                val v = maxVal - (valRange * i / 4)
                g2.color = Color(140, 140, 140)
                g2.font = font.deriveFont(Font.PLAIN, 10f)
                g2.drawString("%.3f".format(v), 8, y + 4)
                g2.color = Color(240, 240, 240)
            }

            g2.color = Color(180, 180, 180)
            g2.drawLine(padding, padding, padding, h - padding)
            g2.drawLine(padding, h - padding, w - padding, h - padding)

            // Step labels along X axis
            g2.color = Color(140, 140, 140)
            g2.drawString("Step 1", padding, h - padding + 16)
            g2.drawString("Step $maxStep", w - padding - 30, h - padding + 16)

            // Draw curves for each run
            runs.forEachIndexed { idx, run ->
                val steps = run.metrics[metricName] ?: return@forEachIndexed
                if (steps.isEmpty()) return@forEachIndexed

                val col = runColors[idx % runColors.size]
                g2.color = col
                g2.stroke = BasicStroke(2.2f)

                val path = Path2D.Double()
                steps.forEachIndexed { i, s ->
                    val x = padding + (s.step.toDouble() / maxStep * plotW)
                    val y = h - padding - ((s.value - minVal) / valRange * plotH)

                    if (i == 0) {
                        path.moveTo(x, y)
                    } else {
                        path.lineTo(x, y)
                    }
                    g2.fillOval((x - 3).toInt(), (y - 3).toInt(), 6, 6)
                }
                g2.draw(path)
            }

            // Draw Legend
            var legendX = padding + 10
            runs.forEachIndexed { idx, run ->
                val col = runColors[idx % runColors.size]
                g2.color = col
                g2.fillRect(legendX, 12, 12, 12)
                g2.color = Color(50, 50, 50)
                g2.font = font.deriveFont(Font.BOLD, 11f)
                val label = "${run.runName} (${run.getLatestMetric(metricName)?.let { "%.3f".format(it) } ?: "-"})"
                g2.drawString(label, legendX + 16, 22)
                legendX += g2.fontMetrics.stringWidth(label) + 28
            }
        }
    }
}
