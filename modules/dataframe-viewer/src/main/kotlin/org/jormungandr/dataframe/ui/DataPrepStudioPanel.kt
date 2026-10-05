/*
 * Copyright © 2025–2026 indoctrinatedrecluse
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

import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import org.jormungandr.core.theme.DataGridThemeTokens
import org.jormungandr.dataframe.io.DataFrameExporter
import org.jormungandr.dataframe.model.ColumnMetadata
import org.jormungandr.dataframe.model.DataFrame
import org.jormungandr.dataframe.model.DataTypeCategory
import org.jormungandr.dataframe.transform.*
import java.awt.*
import java.awt.datatransfer.StringSelection
import javax.swing.*
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder

/**
 * Interactive Data Prep Studio for visual data cleaning, type coercion,
 * imputation, normalization, outlier clipping, and automated Python/SQL pipeline generation.
 */
class DataPrepStudioPanel(
    private var sourceDataFrame: DataFrame = DataFrame.empty(),
    private val gridTheme: DataGridThemeTokens,
    private val onApplyTransformation: (DataFrame) -> Unit = {}
) : JPanel(BorderLayout()) {

    private val pipeline = DataPrepPipeline()
    private var previewDataFrame: DataFrame = sourceDataFrame

    // Left Panel - Wizard Controls
    private val categoryCombo = JComboBox(arrayOf("Missing Values", "Text Cleaning", "Types & Schema", "Scaling & Outliers", "Deduplicate & Encode"))
    private val targetColumnCombo = JComboBox<String>()
    private val opCombo = JComboBox<String>()
    private val paramField1 = JBTextField(12)
    private val paramField2 = JBTextField(12)
    private val paramLabel1 = JBLabel("Param 1:")
    private val paramLabel2 = JBLabel("Param 2:")

    private val addStepBtn = JButton("➕ Add Step").apply {
        font = font.deriveFont(Font.BOLD, 11f)
        foreground = Color(30, 64, 175)
        isFocusable = false
    }

    private val stepListModel = DefaultListModel<String>()
    private val stepList = JList(stepListModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        border = EmptyBorder(4, 4, 4, 4)
    }

    private val moveUpBtn = JButton("▲").apply { toolTipText = "Move Step Up"; isFocusable = false }
    private val moveDownBtn = JButton("▼").apply { toolTipText = "Move Step Down"; isFocusable = false }
    private val removeStepBtn = JButton("✕ Remove").apply { toolTipText = "Remove Step"; isFocusable = false }
    private val clearPipelineBtn = JButton("🧹 Clear All").apply { toolTipText = "Clear All Steps"; isFocusable = false }

    // Right Panel - Preview & Code Tabs
    private val previewTableModel = DataFrameTableModel(sourceDataFrame)
    private val previewTable = JBTable(previewTableModel).apply {
        autoResizeMode = JTable.AUTO_RESIZE_OFF
        rowHeight = 24
        setShowGrid(true)
    }

    private val pandasTextArea = JTextArea().apply {
        isEditable = false
        font = Font("Consolas", Font.PLAIN, 12)
        background = Color(248, 250, 252)
        border = EmptyBorder(8, 8, 8, 8)
    }
    private val polarsTextArea = JTextArea().apply {
        isEditable = false
        font = Font("Consolas", Font.PLAIN, 12)
        background = Color(248, 250, 252)
        border = EmptyBorder(8, 8, 8, 8)
    }
    private val sqlTextArea = JTextArea().apply {
        isEditable = false
        font = Font("Consolas", Font.PLAIN, 12)
        background = Color(248, 250, 252)
        border = EmptyBorder(8, 8, 8, 8)
    }

    private val shapeStatusLabel = JBLabel("0 rows × 0 cols").apply {
        font = font.deriveFont(Font.BOLD, 11f)
        foreground = Color(71, 85, 105)
    }

    // Bottom Action Bar
    private val applyBtn = JButton("🚀 Apply Pipeline to Grid").apply {
        font = font.deriveFont(Font.BOLD, 11f)
        foreground = Color(255, 255, 255)
        background = Color(37, 99, 235)
        isOpaque = true
        border = EmptyBorder(6, 12, 6, 12)
        isFocusable = false
    }
    private val copyCsvBtn = JButton("📋 Copy Transformed CSV").apply { isFocusable = false }

    init {
        val leftPanel = createLeftWizardPanel()
        val rightPanel = createRightPreviewPanel()
        val bottomBar = createBottomBar()

        val split = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel).apply {
            resizeWeight = 0.32
            isContinuousLayout = true
            border = null
        }

        add(split, BorderLayout.CENTER)
        add(bottomBar, BorderLayout.SOUTH)

        setupListeners()
        refreshColumns()
        updateOpOptions()
        runPipelinePreview()
    }

    private fun createLeftWizardPanel(): JComponent {
        val panel = JPanel(BorderLayout()).apply {
            border = CompoundBorder(LineBorder(Color(226, 232, 240), 1), EmptyBorder(8, 8, 8, 8))
            background = Color(248, 250, 252)
        }

        val topForm = JPanel(GridBagLayout()).apply { isOpaque = false }
        val gbc = GridBagConstraints().apply {
            insets = Insets(3, 4, 3, 4)
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
        }

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2
        val header = JLabel("🛠️ Add Pipeline Operation").apply {
            font = font.deriveFont(Font.BOLD, 12f)
            foreground = Color(15, 23, 42)
        }
        topForm.add(header, gbc)

        gbc.gridy = 1; gbc.gridwidth = 1; gbc.weightx = 0.3
        topForm.add(JBLabel("Category:"), gbc)
        gbc.gridx = 1; gbc.weightx = 0.7
        topForm.add(categoryCombo, gbc)

        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.3
        topForm.add(JBLabel("Column:"), gbc)
        gbc.gridx = 1; gbc.weightx = 0.7
        topForm.add(targetColumnCombo, gbc)

        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0.3
        topForm.add(JBLabel("Action:"), gbc)
        gbc.gridx = 1; gbc.weightx = 0.7
        topForm.add(opCombo, gbc)

        gbc.gridx = 0; gbc.gridy = 4; gbc.weightx = 0.3
        topForm.add(paramLabel1, gbc)
        gbc.gridx = 1; gbc.weightx = 0.7
        topForm.add(paramField1, gbc)

        gbc.gridx = 0; gbc.gridy = 5; gbc.weightx = 0.3
        topForm.add(paramLabel2, gbc)
        gbc.gridx = 1; gbc.weightx = 0.7
        topForm.add(paramField2, gbc)

        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 2
        val btnRow = JPanel(FlowLayout(FlowLayout.RIGHT, 0, 4)).apply { isOpaque = false }
        btnRow.add(addStepBtn)
        topForm.add(btnRow, gbc)

        val centerStack = JPanel(BorderLayout(4, 4)).apply {
            isOpaque = false
            border = EmptyBorder(6, 0, 0, 0)
        }
        val stackHeader = JLabel("📋 Transformation Steps:").apply {
            font = font.deriveFont(Font.BOLD, 11f)
            foreground = Color(51, 65, 85)
        }
        val stepScroll = JBScrollPane(stepList).apply {
            border = LineBorder(Color(203, 213, 225), 1)
            preferredSize = Dimension(200, 180)
        }

        val stepTools = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply { isOpaque = false }
        stepTools.add(moveUpBtn)
        stepTools.add(moveDownBtn)
        stepTools.add(removeStepBtn)
        stepTools.add(clearPipelineBtn)

        centerStack.add(stackHeader, BorderLayout.NORTH)
        centerStack.add(stepScroll, BorderLayout.CENTER)
        centerStack.add(stepTools, BorderLayout.SOUTH)

        panel.add(topForm, BorderLayout.NORTH)
        panel.add(centerStack, BorderLayout.CENTER)
        return panel
    }

    private fun createRightPreviewPanel(): JComponent {
        val tabbedPane = JBTabbedPane()

        // Tab 1: Live Preview Table
        val previewTab = JPanel(BorderLayout())
        val previewToolbar = JPanel(BorderLayout()).apply {
            border = EmptyBorder(4, 8, 4, 8)
            background = Color(248, 250, 252)
        }
        previewToolbar.add(shapeStatusLabel, BorderLayout.WEST)
        previewTab.add(previewToolbar, BorderLayout.NORTH)
        previewTab.add(JBScrollPane(previewTable), BorderLayout.CENTER)

        // Tab 2: Pandas Code
        val pandasTab = JPanel(BorderLayout())
        val pandasHeader = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 4)).apply {
            isOpaque = false
            val copyBtn = JButton("📋 Copy Pandas Code").apply {
                addActionListener { copyToClipboard(pandasTextArea.text, "Pandas code") }
            }
            add(copyBtn)
        }
        pandasTab.add(pandasHeader, BorderLayout.NORTH)
        pandasTab.add(JBScrollPane(pandasTextArea), BorderLayout.CENTER)

        // Tab 3: Polars Code
        val polarsTab = JPanel(BorderLayout())
        val polarsHeader = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 4)).apply {
            isOpaque = false
            val copyBtn = JButton("📋 Copy Polars Code").apply {
                addActionListener { copyToClipboard(polarsTextArea.text, "Polars code") }
            }
            add(copyBtn)
        }
        polarsTab.add(polarsHeader, BorderLayout.NORTH)
        polarsTab.add(JBScrollPane(polarsTextArea), BorderLayout.CENTER)

        // Tab 4: SQL Code
        val sqlTab = JPanel(BorderLayout())
        val sqlHeader = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 4)).apply {
            isOpaque = false
            val copyBtn = JButton("📋 Copy SQL Code").apply {
                addActionListener { copyToClipboard(sqlTextArea.text, "SQL code") }
            }
            add(copyBtn)
        }
        sqlTab.add(sqlHeader, BorderLayout.NORTH)
        sqlTab.add(JBScrollPane(sqlTextArea), BorderLayout.CENTER)

        tabbedPane.addTab("👀 Live Transformed Preview", previewTab)
        tabbedPane.addTab("🐍 Pandas Pipeline", pandasTab)
        tabbedPane.addTab("⚡ Polars Pipeline", polarsTab)
        tabbedPane.addTab("💾 SQL Pipeline", sqlTab)

        return tabbedPane
    }

    private fun createBottomBar(): JComponent {
        val bar = JPanel(BorderLayout()).apply {
            border = EmptyBorder(6, 12, 6, 12)
            background = Color(241, 245, 249)
        }
        val left = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply { isOpaque = false }
        left.add(applyBtn)
        left.add(copyCsvBtn)

        bar.add(left, BorderLayout.WEST)
        return bar
    }

    private fun setupListeners() {
        categoryCombo.addActionListener { updateOpOptions() }
        opCombo.addActionListener { updateParamVisibility() }

        addStepBtn.addActionListener {
            val step = buildStepFromInputs()
            if (step != null) {
                pipeline.addStep(step)
                updateStepsListUI()
                runPipelinePreview()
            }
        }

        moveUpBtn.addActionListener {
            val idx = stepList.selectedIndex
            if (idx > 0) {
                pipeline.moveStepUp(idx)
                updateStepsListUI()
                stepList.selectedIndex = idx - 1
                runPipelinePreview()
            }
        }

        moveDownBtn.addActionListener {
            val idx = stepList.selectedIndex
            if (idx >= 0 && idx < pipeline.steps.size - 1) {
                pipeline.moveStepDown(idx)
                updateStepsListUI()
                stepList.selectedIndex = idx + 1
                runPipelinePreview()
            }
        }

        removeStepBtn.addActionListener {
            val idx = stepList.selectedIndex
            if (idx >= 0) {
                pipeline.removeStep(idx)
                updateStepsListUI()
                runPipelinePreview()
            }
        }

        clearPipelineBtn.addActionListener {
            pipeline.clear()
            updateStepsListUI()
            runPipelinePreview()
        }

        applyBtn.addActionListener {
            if (pipeline.steps.isEmpty()) {
                Messages.showInfoMessage(this, "Add at least one transformation step before applying.", "Empty Pipeline")
                return@addActionListener
            }
            onApplyTransformation(previewDataFrame)
            Messages.showInfoMessage(this, "Successfully applied ${pipeline.steps.size} transformation steps to active grid!", "Pipeline Applied")
        }

        copyCsvBtn.addActionListener {
            val csv = DataFrameExporter.toCsv(previewDataFrame)
            copyToClipboard(csv, "Transformed CSV")
        }
    }

    fun setDataFrame(newDf: DataFrame) {
        this.sourceDataFrame = newDf
        refreshColumns()
        runPipelinePreview()
    }

    private fun refreshColumns() {
        targetColumnCombo.removeAllItems()
        targetColumnCombo.addItem("(All Columns)")
        for (col in sourceDataFrame.columns) {
            targetColumnCombo.addItem(col.name)
        }
    }

    private fun updateOpOptions() {
        opCombo.removeAllItems()
        when (categoryCombo.selectedIndex) {
            0 -> { // Missing Values
                opCombo.addItem("Drop NA Rows")
                opCombo.addItem("Fill NA with Mean")
                opCombo.addItem("Fill NA with Median")
                opCombo.addItem("Fill NA with Mode")
                opCombo.addItem("Fill NA with Constant")
                opCombo.addItem("Forward Fill (ffill)")
                opCombo.addItem("Backward Fill (bfill)")
            }
            1 -> { // Text Cleaning
                opCombo.addItem("Trim Whitespace")
                opCombo.addItem("Lowercase")
                opCombo.addItem("Uppercase")
                opCombo.addItem("Title Case")
                opCombo.addItem("Remove Punctuation")
                opCombo.addItem("Regex Replace")
            }
            2 -> { // Types & Schema
                opCombo.addItem("Cast to Integer")
                opCombo.addItem("Cast to Float")
                opCombo.addItem("Cast to String")
                opCombo.addItem("Cast to Boolean")
                opCombo.addItem("Cast to DateTime")
                opCombo.addItem("Rename Column")
                opCombo.addItem("Drop Column")
            }
            3 -> { // Scaling & Outliers
                opCombo.addItem("Min-Max Scaler [0, 1]")
                opCombo.addItem("Standard Z-Score (Mean=0, Std=1)")
                opCombo.addItem("Log Transform log(1+x)")
                opCombo.addItem("Clip Outliers (1.5× IQR)")
                opCombo.addItem("Clip Outliers (3-Sigma)")
                opCombo.addItem("Clip Bounds (Custom Min/Max)")
            }
            4 -> { // Deduplicate & Encode
                opCombo.addItem("Remove Duplicate Rows")
                opCombo.addItem("One-Hot Encode (Dummies)")
            }
        }
        updateParamVisibility()
    }

    private fun updateParamVisibility() {
        val categoryIdx = categoryCombo.selectedIndex
        val op = opCombo.selectedItem as? String ?: ""

        when {
            op == "Fill NA with Constant" -> {
                paramLabel1.text = "Value:"
                paramLabel1.isVisible = true
                paramField1.isVisible = true
                paramLabel2.isVisible = false
                paramField2.isVisible = false
            }
            op == "Regex Replace" -> {
                paramLabel1.text = "Pattern:"
                paramLabel2.text = "Replace:"
                paramLabel1.isVisible = true
                paramField1.isVisible = true
                paramLabel2.isVisible = true
                paramField2.isVisible = true
            }
            op == "Rename Column" -> {
                paramLabel1.text = "New Name:"
                paramLabel1.isVisible = true
                paramField1.isVisible = true
                paramLabel2.isVisible = false
                paramField2.isVisible = false
            }
            op == "Clip Bounds (Custom Min/Max)" -> {
                paramLabel1.text = "Min Val:"
                paramLabel2.text = "Max Val:"
                paramLabel1.isVisible = true
                paramField1.isVisible = true
                paramLabel2.isVisible = true
                paramField2.isVisible = true
            }
            else -> {
                paramLabel1.isVisible = false
                paramField1.isVisible = false
                paramLabel2.isVisible = false
                paramField2.isVisible = false
            }
        }
        revalidate()
        repaint()
    }

    private fun buildStepFromInputs(): DataPrepStep? {
        val colRaw = targetColumnCombo.selectedItem as? String ?: return null
        val col = if (colRaw == "(All Columns)") "" else colRaw
        val op = opCombo.selectedItem as? String ?: return null

        return when (op) {
            "Drop NA Rows" -> DataPrepStep.DropNa(if (col.isNotBlank()) listOf(col) else emptyList(), DropNaHow.ANY)
            "Fill NA with Mean" -> if (col.isNotBlank()) DataPrepStep.FillNa(col, FillStrategy.MEAN) else null
            "Fill NA with Median" -> if (col.isNotBlank()) DataPrepStep.FillNa(col, FillStrategy.MEDIAN) else null
            "Fill NA with Mode" -> if (col.isNotBlank()) DataPrepStep.FillNa(col, FillStrategy.MODE) else null
            "Fill NA with Constant" -> if (col.isNotBlank()) DataPrepStep.FillNa(col, FillStrategy.CONSTANT, paramField1.text) else null
            "Forward Fill (ffill)" -> if (col.isNotBlank()) DataPrepStep.FillNa(col, FillStrategy.FORWARD_FILL) else null
            "Backward Fill (bfill)" -> if (col.isNotBlank()) DataPrepStep.FillNa(col, FillStrategy.BACKWARD_FILL) else null

            "Trim Whitespace" -> if (col.isNotBlank()) DataPrepStep.StringClean(col, StringCleanOp.TRIM) else null
            "Lowercase" -> if (col.isNotBlank()) DataPrepStep.StringClean(col, StringCleanOp.LOWERCASE) else null
            "Uppercase" -> if (col.isNotBlank()) DataPrepStep.StringClean(col, StringCleanOp.UPPERCASE) else null
            "Title Case" -> if (col.isNotBlank()) DataPrepStep.StringClean(col, StringCleanOp.TITLECASE) else null
            "Remove Punctuation" -> if (col.isNotBlank()) DataPrepStep.StringClean(col, StringCleanOp.REMOVE_PUNCTUATION) else null
            "Regex Replace" -> if (col.isNotBlank()) DataPrepStep.StringClean(col, StringCleanOp.REGEX_REPLACE, paramField1.text, paramField2.text) else null

            "Cast to Integer" -> if (col.isNotBlank()) DataPrepStep.TypeCast(col, DataTypeCategory.INTEGER) else null
            "Cast to Float" -> if (col.isNotBlank()) DataPrepStep.TypeCast(col, DataTypeCategory.FLOAT) else null
            "Cast to String" -> if (col.isNotBlank()) DataPrepStep.TypeCast(col, DataTypeCategory.STRING) else null
            "Cast to Boolean" -> if (col.isNotBlank()) DataPrepStep.TypeCast(col, DataTypeCategory.BOOLEAN) else null
            "Cast to DateTime" -> if (col.isNotBlank()) DataPrepStep.TypeCast(col, DataTypeCategory.DATETIME) else null
            "Rename Column" -> if (col.isNotBlank() && paramField1.text.isNotBlank()) DataPrepStep.RenameColumn(col, paramField1.text.trim()) else null
            "Drop Column" -> if (col.isNotBlank()) DataPrepStep.DropColumn(listOf(col)) else null

            "Min-Max Scaler [0, 1]" -> if (col.isNotBlank()) DataPrepStep.NumericalScale(col, ScaleMethod.MIN_MAX) else null
            "Standard Z-Score (Mean=0, Std=1)" -> if (col.isNotBlank()) DataPrepStep.NumericalScale(col, ScaleMethod.Z_SCORE) else null
            "Log Transform log(1+x)" -> if (col.isNotBlank()) DataPrepStep.NumericalScale(col, ScaleMethod.LOG1P) else null
            "Clip Outliers (1.5× IQR)" -> if (col.isNotBlank()) DataPrepStep.OutlierClip(col, ClipMethod.IQR_1_5) else null
            "Clip Outliers (3-Sigma)" -> if (col.isNotBlank()) DataPrepStep.OutlierClip(col, ClipMethod.Z_SCORE_3) else null
            "Clip Bounds (Custom Min/Max)" -> if (col.isNotBlank()) DataPrepStep.OutlierClip(col, ClipMethod.CUSTOM_BOUNDS, paramField1.text.toDoubleOrNull(), paramField2.text.toDoubleOrNull()) else null

            "Remove Duplicate Rows" -> DataPrepStep.Deduplicate(if (col.isNotBlank()) listOf(col) else emptyList())
            "One-Hot Encode (Dummies)" -> if (col.isNotBlank()) DataPrepStep.OneHotEncode(col) else null

            else -> null
        }
    }

    private fun updateStepsListUI() {
        stepListModel.clear()
        for ((idx, s) in pipeline.steps.withIndex()) {
            stepListModel.addElement("#${idx + 1} ${s.description}")
        }
    }

    private fun runPipelinePreview() {
        val transformed = pipeline.apply(sourceDataFrame)
        previewDataFrame = transformed
        previewTableModel.dataFrame = transformed

        shapeStatusLabel.text = "${transformed.rowCount} rows × ${transformed.columnCount} cols (Original: ${sourceDataFrame.rowCount}×${sourceDataFrame.columnCount})"
        pandasTextArea.text = pipeline.generatePandasCode()
        polarsTextArea.text = pipeline.generatePolarsCode()
        sqlTextArea.text = pipeline.generateSqlCode(sourceDataFrame.name.ifBlank { "source_df" })
    }

    private fun copyToClipboard(text: String, label: String) {
        val sel = StringSelection(text)
        Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, sel)
        shapeStatusLabel.text = "✓ Copied $label to clipboard"
    }
}
