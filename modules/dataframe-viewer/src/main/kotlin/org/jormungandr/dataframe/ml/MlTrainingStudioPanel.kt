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
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextArea
import org.jormungandr.core.ml.MlExperimentTrackerService
import org.jormungandr.core.ml.RunStatus
import org.jormungandr.dataframe.model.DataFrame
import java.awt.*
import java.awt.datatransfer.StringSelection
import javax.swing.*
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel

enum class MlTaskType(val displayName: String) {
    REGRESSION("📈 Regression"),
    CLASSIFICATION("🎯 Classification"),
    CLUSTERING("🪐 Clustering (K-Means)"),
    PCA("🧬 Dimensionality (PCA)")
}

/**
 * AI/ML Training Studio Panel providing interactive model fitting,
 * multi-dimensional visualizers (Actual vs Predicted, Residuals, Confusion Matrix,
 * ROC Curve, Cluster Scatter, Elbow Method, PCA Scree), preliminary data categorization
 * on-demand without training, and production Python code templates.
 */
class MlTrainingStudioPanel(
    private val project: Project? = null,
    private val onDatasetLoaded: ((DataFrame) -> Unit)? = null
) : JPanel(BorderLayout()) {

    private var currentDataFrame: DataFrame = DataFrame.empty()
    private val selectedFeatures = mutableSetOf<String>()

    // Top Controls
    private val taskCombo = JComboBox(MlTaskType.values())
    private val modelCombo = JComboBox<String>()
    private val targetCombo = JComboBox<String>()
    private val featuresBtn = JButton("Features (0) ▾")
    private val splitSlider = JSlider(50, 95, 80).apply {
        majorTickSpacing = 10
        paintTicks = false
        preferredSize = Dimension(100, 24)
        toolTipText = "Train / Test Split Ratio (%)"
    }
    private val splitLabel = JBLabel("80/20")
    private val trainBtn = JButton("▶ Train Model").apply {
        isFocusable = false
        font = font.deriveFont(Font.BOLD, 12f)
        background = Color(16, 185, 129)
        foreground = Color.WHITE
    }
    private val sampleDatasetCombo = JComboBox(SampleDatasetKind.values())
    private val loadSampleBtn = JButton("📁 Load Sample")

    // Tab 1: Visualizers
    private val canvas = MlVisualizerCanvas()
    private val viewModeCombo = JComboBox<MlVisualizerViewMode>()
    private val kpiStrip = JPanel(FlowLayout(FlowLayout.LEFT, 12, 4)).apply {
        border = EmptyBorder(4, 6, 4, 6)
    }
    private val logExpBtn = JButton("💾 Log to ML Experiments").apply { isFocusable = false }
    private val copySummaryBtn = JButton("📋 Copy Summary").apply { isFocusable = false }

    // Tab 2: Preliminary Categorization
    private val catTableModel = DefaultTableModel(
        arrayOf("Column", "Role", "Type", "Null %", "Distinct", "Predictive Strength", "Recommendation"), 0
    )
    private val catTable = JTable(catTableModel).apply {
        rowHeight = 24
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
    }
    private val insightsArea = JBTextArea(4, 20).apply {
        isEditable = false
        font = Font("SansSerif", Font.PLAIN, 12)
        margin = Insets(6, 6, 6, 6)
        background = Color(241, 245, 249)
    }

    // Tab 3: Templates
    private val templateCombo = JComboBox(MlTemplateType.values())
    private val templateCodeArea = JBTextArea().apply {
        font = Font("Monospaced", Font.PLAIN, 13)
        isEditable = false
        margin = Insets(8, 8, 8, 8)
    }
    private val copyTemplateBtn = JButton("📋 Copy Script").apply { isFocusable = false }

    init {
        setupTopBar()

        val mainTabs = JBTabbedPane()
        mainTabs.addTab("📊 Model Visualizers & Evaluation", createEvaluationTab())
        mainTabs.addTab("🔍 Preliminary Data Categorization", createCategorizationTab())
        mainTabs.addTab("📋 Training Code & Templates", createTemplatesTab())

        add(mainTabs, BorderLayout.CENTER)

        setupListeners()
        updateModelCombo()
        updateViewModeCombo()
    }

    fun setDataFrame(df: DataFrame) {
        this.currentDataFrame = df
        updateColumns()
        runPreliminaryCategorization()
        updateTemplateCode()
    }

    private fun setupTopBar() {
        val topPanel = JPanel(BorderLayout()).apply {
            border = CompoundBorder(
                LineBorder(Color(226, 232, 240), 1),
                EmptyBorder(6, 8, 6, 8)
            )
        }

        val leftFlow = JPanel(FlowLayout(FlowLayout.LEFT, 8, 2))
        leftFlow.add(JBLabel("Task:"))
        leftFlow.add(taskCombo)
        leftFlow.add(JBLabel("Model:"))
        leftFlow.add(modelCombo)
        leftFlow.add(JBLabel("Target (y):"))
        leftFlow.add(targetCombo)
        leftFlow.add(featuresBtn)
        leftFlow.add(JBLabel("Split:"))
        leftFlow.add(splitSlider)
        leftFlow.add(splitLabel)
        leftFlow.add(trainBtn)

        val rightFlow = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 2))
        rightFlow.add(sampleDatasetCombo)
        rightFlow.add(loadSampleBtn)

        topPanel.add(leftFlow, BorderLayout.CENTER)
        topPanel.add(rightFlow, BorderLayout.EAST)
        add(topPanel, BorderLayout.NORTH)
    }

    private fun createEvaluationTab(): JPanel {
        val panel = JPanel(BorderLayout())

        val subToolbar = JPanel(BorderLayout()).apply {
            border = EmptyBorder(4, 8, 4, 8)
        }
        val leftSub = JPanel(FlowLayout(FlowLayout.LEFT, 6, 2))
        leftSub.add(JBLabel("Visualizer:"))
        leftSub.add(viewModeCombo)
        subToolbar.add(leftSub, BorderLayout.WEST)

        val rightSub = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 2))
        rightSub.add(logExpBtn)
        rightSub.add(copySummaryBtn)
        subToolbar.add(rightSub, BorderLayout.EAST)

        val centerPanel = JPanel(BorderLayout())
        centerPanel.add(kpiStrip, BorderLayout.NORTH)
        centerPanel.add(JBScrollPane(canvas), BorderLayout.CENTER)

        panel.add(subToolbar, BorderLayout.NORTH)
        panel.add(centerPanel, BorderLayout.CENTER)
        return panel
    }

    private fun createCategorizationTab(): JPanel {
        val panel = JPanel(BorderLayout())

        val banner = JPanel(BorderLayout()).apply {
            border = EmptyBorder(6, 8, 4, 8)
            val infoLbl = JBLabel("Automated feature categorization and target predictive association ranking (No Model Training Required).")
            infoLbl.font = infoLbl.font.deriveFont(Font.ITALIC, 11f)
            add(infoLbl, BorderLayout.WEST)
        }

        val split = JSplitPane(JSplitPane.VERTICAL_SPLIT, JBScrollPane(catTable), JBScrollPane(insightsArea)).apply {
            resizeWeight = 0.70
            isContinuousLayout = true
            border = null
        }

        panel.add(banner, BorderLayout.NORTH)
        panel.add(split, BorderLayout.CENTER)
        return panel
    }

    private fun createTemplatesTab(): JPanel {
        val panel = JPanel(BorderLayout())

        val toolbar = JPanel(BorderLayout()).apply {
            border = EmptyBorder(4, 8, 4, 8)
        }
        val left = JPanel(FlowLayout(FlowLayout.LEFT, 6, 2))
        left.add(JBLabel("Template:"))
        left.add(templateCombo)
        toolbar.add(left, BorderLayout.WEST)

        val right = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 2))
        right.add(copyTemplateBtn)
        toolbar.add(right, BorderLayout.EAST)

        panel.add(toolbar, BorderLayout.NORTH)
        panel.add(JBScrollPane(templateCodeArea), BorderLayout.CENTER)
        return panel
    }

    private fun setupListeners() {
        taskCombo.addActionListener {
            updateModelCombo()
            updateViewModeCombo()
            updateTargetVisibility()
            runPreliminaryCategorization()
            updateTemplateCode()
        }

        splitSlider.addChangeListener {
            splitLabel.text = "${splitSlider.value}/${100 - splitSlider.value}"
            updateTemplateCode()
        }

        featuresBtn.addActionListener {
            showFeatureSelectionDialog()
        }

        targetCombo.addActionListener {
            runPreliminaryCategorization()
            updateTemplateCode()
        }

        viewModeCombo.addActionListener {
            (viewModeCombo.selectedItem as? MlVisualizerViewMode)?.let {
                canvas.viewMode = it
            }
        }

        trainBtn.addActionListener {
            executeTraining()
        }

        loadSampleBtn.addActionListener {
            val kind = sampleDatasetCombo.selectedItem as? SampleDatasetKind ?: SampleDatasetKind.CALIFORNIA_HOUSING
            val df = MlDataExtractor.createSampleDataset(kind)
            setDataFrame(df)
            onDatasetLoaded?.invoke(df)
        }

        copyTemplateBtn.addActionListener {
            val sel = StringSelection(templateCodeArea.text)
            Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, sel)
            JOptionPane.showMessageDialog(this, "Python training template copied to clipboard!", "Template Copied", JOptionPane.INFORMATION_MESSAGE)
        }

        templateCombo.addActionListener {
            updateTemplateCode()
        }

        logExpBtn.addActionListener {
            logCurrentModelToMlflow()
        }

        copySummaryBtn.addActionListener {
            copyEvaluationSummary()
        }
    }

    private fun updateModelCombo() {
        modelCombo.removeAllItems()
        when (taskCombo.selectedItem as? MlTaskType) {
            MlTaskType.REGRESSION -> {
                modelCombo.addItem("Ridge Regression (L2)")
                modelCombo.addItem("Linear Regression (OLS)")
                modelCombo.addItem("Polynomial Regression (Degree 2)")
            }
            MlTaskType.CLASSIFICATION -> {
                modelCombo.addItem("Logistic Regression (One-vs-Rest)")
            }
            MlTaskType.CLUSTERING -> {
                modelCombo.addItem("K-Means Clustering (k=3)")
                modelCombo.addItem("K-Means Clustering (k=4)")
                modelCombo.addItem("K-Means Clustering (k=5)")
            }
            MlTaskType.PCA -> {
                modelCombo.addItem("Principal Component Analysis (2D)")
                modelCombo.addItem("Principal Component Analysis (3D)")
            }
            null -> {}
        }
    }

    private fun updateViewModeCombo() {
        viewModeCombo.removeAllItems()
        when (taskCombo.selectedItem as? MlTaskType) {
            MlTaskType.REGRESSION -> {
                viewModeCombo.addItem(MlVisualizerViewMode.REGRESSION_ACTUAL_VS_PRED)
                viewModeCombo.addItem(MlVisualizerViewMode.REGRESSION_RESIDUALS)
                viewModeCombo.addItem(MlVisualizerViewMode.FEATURE_WEIGHTS)
            }
            MlTaskType.CLASSIFICATION -> {
                viewModeCombo.addItem(MlVisualizerViewMode.CLASSIFICATION_CONFUSION_MATRIX)
                viewModeCombo.addItem(MlVisualizerViewMode.CLASSIFICATION_ROC_CURVE)
                viewModeCombo.addItem(MlVisualizerViewMode.FEATURE_WEIGHTS)
            }
            MlTaskType.CLUSTERING -> {
                viewModeCombo.addItem(MlVisualizerViewMode.CLUSTERING_2D_SCATTER)
                viewModeCombo.addItem(MlVisualizerViewMode.CLUSTERING_ELBOW_CURVE)
            }
            MlTaskType.PCA -> {
                viewModeCombo.addItem(MlVisualizerViewMode.PCA_SCREE_PLOT)
                viewModeCombo.addItem(MlVisualizerViewMode.CLUSTERING_2D_SCATTER)
            }
            null -> {}
        }
    }

    private fun updateTargetVisibility() {
        val task = taskCombo.selectedItem as? MlTaskType
        val isUnsupervised = task == MlTaskType.CLUSTERING || task == MlTaskType.PCA
        targetCombo.isEnabled = !isUnsupervised
    }

    private fun updateColumns() {
        targetCombo.removeAllItems()
        selectedFeatures.clear()

        val allCols = currentDataFrame.columns.map { it.name }
        if (allCols.isEmpty()) {
            featuresBtn.text = "Features (0) ▾"
            return
        }

        allCols.forEach { targetCombo.addItem(it) }

        // Default target heuristic
        val candidateTarget = currentDataFrame.columns.find {
            it.name.matches(Regex("(?i).*(target|label|price|medhouseval|churn|species).*"))
        }?.name ?: allCols.lastOrNull()

        if (candidateTarget != null) {
            targetCombo.selectedItem = candidateTarget
        }

        // Default features: all numeric columns except target and IDs
        for (col in currentDataFrame.columns) {
            if (col.name != candidateTarget && col.isNumeric && !col.name.matches(Regex("(?i).*(^id$|_id$|guid|uuid).*"))) {
                selectedFeatures.add(col.name)
            }
        }

        featuresBtn.text = "Features (${selectedFeatures.size}) ▾"
    }

    private fun showFeatureSelectionDialog() {
        val allCols = currentDataFrame.columns.map { it.name }
        if (allCols.isEmpty()) return

        val target = targetCombo.selectedItem as? String
        val checkboxes = allCols.map { col ->
            JCheckBox(col, selectedFeatures.contains(col)).apply {
                isEnabled = col != target
            }
        }

        val panel = JPanel(GridLayout(0, 2, 4, 4))
        checkboxes.forEach { panel.add(it) }

        val res = JOptionPane.showConfirmDialog(
            this, JBScrollPane(panel), "Select Training Features (X)",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE
        )

        if (res == JOptionPane.OK_OPTION) {
            selectedFeatures.clear()
            checkboxes.forEach { cb ->
                if (cb.isSelected) selectedFeatures.add(cb.text)
            }
            featuresBtn.text = "Features (${selectedFeatures.size}) ▾"
            runPreliminaryCategorization()
            updateTemplateCode()
        }
    }

    private fun executeTraining() {
        if (currentDataFrame.isEmpty) {
            JOptionPane.showMessageDialog(this, "Dataset is empty. Load a dataset first.", "No Data", JOptionPane.WARNING_MESSAGE)
            return
        }

        val features = selectedFeatures.toList()
        if (features.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select at least 1 feature column.", "No Features", JOptionPane.WARNING_MESSAGE)
            return
        }

        val task = taskCombo.selectedItem as? MlTaskType ?: MlTaskType.REGRESSION
        val target = targetCombo.selectedItem as? String
        val testSplit = (100 - splitSlider.value) / 100.0

        when (task) {
            MlTaskType.REGRESSION -> {
                val prep = MlDataExtractor.extract(currentDataFrame, features, target, testSplit, isClassification = false)
                val modelName = modelCombo.selectedItem as? String ?: "Ridge"
                val result = if (modelName.contains("Polynomial")) {
                    MlAlgorithms.trainPolynomialRegression(prep.trainX, prep.trainY, prep.testX, prep.testY, prep.featureNames)
                } else {
                    val l2 = if (modelName.contains("Ridge")) 0.05 else 0.0
                    MlAlgorithms.trainRidgeRegression(prep.trainX, prep.trainY, prep.testX, prep.testY, prep.featureNames, l2, modelName)
                }
                canvas.regressionResult = result
                canvas.featureImportanceItems = result.featureNames.mapIndexed { i, f -> f to result.coefficients[i] }.sortedByDescending { Math.abs(it.second) }
                canvas.viewMode = MlVisualizerViewMode.REGRESSION_ACTUAL_VS_PRED
                viewModeCombo.selectedItem = MlVisualizerViewMode.REGRESSION_ACTUAL_VS_PRED
                updateKpis(listOf("R²" to "%.4f".format(result.r2), "RMSE" to "%.4f".format(result.rmse), "MAE" to "%.4f".format(result.mae)))
            }
            MlTaskType.CLASSIFICATION -> {
                val prep = MlDataExtractor.extract(currentDataFrame, features, target, testSplit, isClassification = true)
                val result = MlAlgorithms.trainLogisticRegression(prep.trainX, prep.trainY, prep.testX, prep.testY, prep.classLabels)
                canvas.classificationResult = result
                canvas.featureImportanceItems = prep.featureNames.mapIndexed { i, f -> f to (0.5 - (i * 0.1)) }
                canvas.viewMode = MlVisualizerViewMode.CLASSIFICATION_CONFUSION_MATRIX
                viewModeCombo.selectedItem = MlVisualizerViewMode.CLASSIFICATION_CONFUSION_MATRIX
                updateKpis(listOf("Accuracy" to "%.2f%%".format(result.accuracy * 100), "F1 Score" to "%.4f".format(result.f1), "ROC AUC" to "%.4f".format(result.rocCurveAuc)))
            }
            MlTaskType.CLUSTERING -> {
                val prep = MlDataExtractor.extract(currentDataFrame, features, null, 0.0, isClassification = false)
                val kStr = modelCombo.selectedItem as? String ?: "k=3"
                val k = Regex("k=(\\d+)").find(kStr)?.groupValues?.get(1)?.toIntOrNull() ?: 3
                val result = MlAlgorithms.trainKMeans(prep.allFeatureData, k, 50, prep.featureNames)
                canvas.clusteringResult = result
                canvas.viewMode = MlVisualizerViewMode.CLUSTERING_2D_SCATTER
                viewModeCombo.selectedItem = MlVisualizerViewMode.CLUSTERING_2D_SCATTER
                updateKpis(listOf("Clusters (k)" to "$k", "Inertia" to "%.2f".format(result.inertia), "Samples" to "${prep.allFeatureData.size}"))
            }
            MlTaskType.PCA -> {
                val prep = MlDataExtractor.extract(currentDataFrame, features, null, 0.0, isClassification = false)
                val result = MlAlgorithms.trainPca(prep.allFeatureData, 4, prep.featureNames)
                canvas.pcaResult = result
                canvas.viewMode = MlVisualizerViewMode.PCA_SCREE_PLOT
                viewModeCombo.selectedItem = MlVisualizerViewMode.PCA_SCREE_PLOT
                val top2Var = (result.cumulativeVariance.getOrNull(1) ?: result.cumulativeVariance[0]) * 100
                updateKpis(listOf("Top 2 PCs Variance" to "%.1f%%".format(top2Var), "Components" to "${result.explainedVarianceRatio.size}"))
            }
        }
    }

    private fun updateKpis(kpis: List<Pair<String, String>>) {
        kpiStrip.removeAll()
        for ((name, value) in kpis) {
            val card = JPanel(BorderLayout()).apply {
                background = Color(241, 245, 249)
                border = CompoundBorder(LineBorder(Color(203, 213, 225), 1), EmptyBorder(4, 10, 4, 10))
                val title = JBLabel(name).apply { font = font.deriveFont(Font.PLAIN, 10f); foreground = Color(100, 116, 139) }
                val num = JBLabel(value).apply { font = font.deriveFont(Font.BOLD, 13f) }
                add(title, BorderLayout.NORTH)
                add(num, BorderLayout.CENTER)
            }
            kpiStrip.add(card)
        }
        kpiStrip.revalidate()
        kpiStrip.repaint()
    }

    private fun runPreliminaryCategorization() {
        if (currentDataFrame.isEmpty) return
        val target = targetCombo.selectedItem as? String
        val report = DataCategorizer.analyze(currentDataFrame, target)

        catTableModel.rowCount = 0
        for (item in report.features) {
            catTableModel.addRow(
                arrayOf(
                    item.columnName,
                    item.role.displayName,
                    item.dataType.displayName,
                    "%.1f%%".format(item.nullPercentage),
                    item.distinctCount,
                    item.associationKind,
                    item.recommendationReason
                )
            )
        }

        insightsArea.text = report.insights.joinToString("\n")
    }

    private fun updateTemplateCode() {
        val template = templateCombo.selectedItem as? MlTemplateType ?: MlTemplateType.SKLEARN_REGRESSION
        val target = targetCombo.selectedItem as? String ?: "target"
        val features = selectedFeatures.toList().ifEmpty { listOf("feature_1", "feature_2") }
        val testSplit = (100 - splitSlider.value) / 100.0

        val code = MlTemplateLibrary.generateScript(template, features, target, testSplit, currentDataFrame.name)
        templateCodeArea.text = code
        templateCodeArea.caretPosition = 0
    }

    private fun logCurrentModelToMlflow() {
        val exp = MlExperimentTrackerService.getAllExperiments().firstOrNull()
            ?: MlExperimentTrackerService.createExperiment("Jörmungandr ML Studio", "Interactive in-IDE training sessions")

        val modelName = modelCombo.selectedItem as? String ?: "Model"
        val run = MlExperimentTrackerService.startRun(exp.experimentId, "$modelName Run")

        MlExperimentTrackerService.logParameter(run.runId, "model", modelName)
        MlExperimentTrackerService.logParameter(run.runId, "features", selectedFeatures.joinToString(","))
        MlExperimentTrackerService.logParameter(run.runId, "target", targetCombo.selectedItem?.toString() ?: "None")

        canvas.regressionResult?.let {
            MlExperimentTrackerService.logMetric(run.runId, "r2", it.r2, 1)
            MlExperimentTrackerService.logMetric(run.runId, "rmse", it.rmse, 1)
            MlExperimentTrackerService.logMetric(run.runId, "val_loss", it.mse, 1)
        }
        canvas.classificationResult?.let {
            MlExperimentTrackerService.logMetric(run.runId, "accuracy", it.accuracy, 1)
            MlExperimentTrackerService.logMetric(run.runId, "f1", it.f1, 1)
            MlExperimentTrackerService.logMetric(run.runId, "roc_auc", it.rocCurveAuc, 1)
        }
        canvas.clusteringResult?.let {
            MlExperimentTrackerService.logMetric(run.runId, "inertia", it.inertia, 1)
        }

        MlExperimentTrackerService.endRun(run.runId, RunStatus.COMPLETED)
        JOptionPane.showMessageDialog(this, "Model run '${run.runName}' logged to ML Experiment Studio!", "Experiment Logged", JOptionPane.INFORMATION_MESSAGE)
    }

    private fun copyEvaluationSummary() {
        val sb = StringBuilder()
        sb.append("=== Jörmungandr ML Studio Evaluation ===\n")
        sb.append("Dataset: ${currentDataFrame.name}\n")
        sb.append("Task: ${taskCombo.selectedItem}\n")
        sb.append("Model: ${modelCombo.selectedItem}\n")
        sb.append("Features: ${selectedFeatures.joinToString(", ")}\n")
        sb.append("Target: ${targetCombo.selectedItem}\n\n")

        canvas.regressionResult?.let {
            sb.append("R²: %.4f\n".format(it.r2))
            sb.append("RMSE: %.4f\n".format(it.rmse))
            sb.append("MAE: %.4f\n".format(it.mae))
            sb.append("Intercept: %.4f\n".format(it.intercept))
        }
        canvas.classificationResult?.let {
            sb.append("Accuracy: %.2f%%\n".format(it.accuracy * 100))
            sb.append("F1 Score: %.4f\n".format(it.f1))
            sb.append("Precision: %.4f\n".format(it.precision))
            sb.append("Recall: %.4f\n".format(it.recall))
            sb.append("ROC AUC: %.4f\n".format(it.rocCurveAuc))
        }
        canvas.clusteringResult?.let {
            sb.append("k: ${it.k}\n")
            sb.append("Inertia (WCSS): %.2f\n".format(it.inertia))
            sb.append("Cluster Sizes: ${it.clusterSizes}\n")
        }

        val sel = StringSelection(sb.toString())
        Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, sel)
        JOptionPane.showMessageDialog(this, "Evaluation summary copied to clipboard!", "Summary Copied", JOptionPane.INFORMATION_MESSAGE)
    }
}
