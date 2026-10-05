package org.jormungandr.dataframe.ui

import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import org.jormungandr.core.theme.DataGridThemeTokens
import org.jormungandr.core.theme.JormungandrTheme
import org.jormungandr.dataframe.io.DataFrameExporter
import org.jormungandr.dataframe.model.DataFrame
import org.jormungandr.dataframe.transform.PivotAggType
import org.jormungandr.dataframe.transform.PivotConfig
import org.jormungandr.dataframe.transform.PivotTableEngine
import java.awt.*
import java.awt.datatransfer.StringSelection
import javax.swing.*
import javax.swing.border.EmptyBorder

/**
 * Interactive Pivot Table Studio tab for DataFrame Viewer.
 * Generates dynamic 2D cross-tabulation matrices with configurable row/column dimensions,
 * metric aggregation functions, and grand totals.
 */
class PivotTablePanel(
    private var sourceDataFrame: DataFrame,
    private var gridTheme: DataGridThemeTokens = JormungandrTheme.SOLARIZED_LIGHT.dataGrid
) : JPanel(BorderLayout()) {

    private val rowCombo = JComboBox<String>()
    private val colCombo = JComboBox<String>()
    private val valCombo = JComboBox<String>()
    private val aggCombo = JComboBox(PivotAggType.values())
    private val totalsCheck = JBCheckBox("Grand Totals", true)

    private val pivotTableModel = DataFrameTableModel(DataFrame.empty("Pivot Matrix"))
    private val pivotTable = JBTable(pivotTableModel)
    private val statusLabel = JBLabel("Select dimensions and metric to generate pivot table.")

    private var currentPivotDf: DataFrame? = null

    init {
        setupTable()
        refreshDropdowns()

        val topPanel = JPanel(BorderLayout())
        topPanel.add(createControlsPanel(), BorderLayout.NORTH)

        val centerPanel = JPanel(BorderLayout())
        centerPanel.add(JBScrollPane(pivotTable), BorderLayout.CENTER)

        val bottomPanel = JPanel(BorderLayout()).apply {
            border = EmptyBorder(6, 12, 6, 12)
            background = Color(248, 250, 252)
            add(statusLabel, BorderLayout.WEST)

            val exportBtn = JButton("Export Pivot...").apply {
                toolTipText = "Export generated pivot table"
                addActionListener { exportPivot() }
            }
            add(exportBtn, BorderLayout.EAST)
        }

        add(topPanel, BorderLayout.NORTH)
        add(centerPanel, BorderLayout.CENTER)
        add(bottomPanel, BorderLayout.SOUTH)

        // Generate initial pivot
        generatePivot()
    }

    fun setDataFrame(df: DataFrame) {
        this.sourceDataFrame = df
        refreshDropdowns()
        generatePivot()
    }

    fun applyTheme(themeTokens: DataGridThemeTokens) {
        this.gridTheme = themeTokens
        pivotTable.tableHeader.defaultRenderer = DataFrameHeaderRenderer(gridTheme)
        pivotTable.setDefaultRenderer(Any::class.java, DataFrameCellRenderer(gridTheme))
        pivotTable.tableHeader.repaint()
        pivotTable.repaint()
    }

    private fun setupTable() {
        pivotTable.autoResizeMode = JTable.AUTO_RESIZE_OFF
        pivotTable.rowHeight = 24
        pivotTable.tableHeader.reorderingAllowed = false
        pivotTable.tableHeader.defaultRenderer = DataFrameHeaderRenderer(gridTheme)
        pivotTable.setDefaultRenderer(Any::class.java, DataFrameCellRenderer(gridTheme))
    }

    private fun refreshDropdowns() {
        val colNames = sourceDataFrame.columns.map { it.name }.toTypedArray()
        val numNames = sourceDataFrame.columns.filter { it.isNumeric }.map { it.name }.toTypedArray()

        rowCombo.removeAllItems()
        colCombo.removeAllItems()
        valCombo.removeAllItems()

        colNames.forEach {
            rowCombo.addItem(it)
            colCombo.addItem(it)
        }

        if (numNames.isNotEmpty()) {
            numNames.forEach { valCombo.addItem(it) }
        } else {
            colNames.forEach { valCombo.addItem(it) }
        }

        if (rowCombo.itemCount > 0) rowCombo.selectedIndex = 0
        if (colCombo.itemCount > 1) colCombo.selectedIndex = 1
        if (valCombo.itemCount > 0) valCombo.selectedIndex = valCombo.itemCount - 1
    }

    private fun createControlsPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.border = EmptyBorder(8, 12, 8, 12)
        panel.background = Color(241, 245, 249)

        val left = JPanel(FlowLayout(FlowLayout.LEFT, 10, 0)).apply { isOpaque = false }

        left.add(JBLabel("Row:"))
        left.add(rowCombo)

        left.add(JBLabel("Column:"))
        left.add(colCombo)

        left.add(JBLabel("Value:"))
        left.add(valCombo)

        left.add(JBLabel("Function:"))
        left.add(aggCombo)

        left.add(totalsCheck)

        val runBtn = JButton("Generate").apply {
            font = font.deriveFont(Font.BOLD)
            foreground = Color(30, 64, 175)
            addActionListener { generatePivot() }
        }
        left.add(runBtn)

        panel.add(left, BorderLayout.WEST)

        // Auto-refresh when combos change
        val listener = java.awt.event.ActionListener { generatePivot() }
        rowCombo.addActionListener(listener)
        colCombo.addActionListener(listener)
        valCombo.addActionListener(listener)
        aggCombo.addActionListener(listener)
        totalsCheck.addActionListener(listener)

        return panel
    }

    private fun generatePivot() {
        val row = rowCombo.selectedItem as? String ?: return
        val col = colCombo.selectedItem as? String ?: return
        val value = valCombo.selectedItem as? String ?: return
        val agg = aggCombo.selectedItem as? PivotAggType ?: PivotAggType.SUM
        val includeTotals = totalsCheck.isSelected

        if (row == col) {
            statusLabel.text = "⚠️ Row and Column dimensions must be different."
            statusLabel.foreground = Color(180, 83, 9)
            return
        }

        val config = PivotConfig(
            rowField = row,
            columnField = col,
            valueField = value,
            aggregation = agg,
            includeTotals = includeTotals
        )

        val pivotDf = PivotTableEngine.generatePivot(sourceDataFrame, config)
        currentPivotDf = pivotDf
        pivotTableModel.updateDataFrame(pivotDf)

        val numCols = pivotDf.columnCount - 1
        statusLabel.text = "✓ Pivot generated: ${pivotDf.rowCount} rows × $numCols column buckets (${agg.displayName} of '$value')"
        statusLabel.foreground = Color(22, 101, 52)
    }

    private fun exportPivot() {
        val df = currentPivotDf ?: return
        val options = arrayOf("Copy CSV to Clipboard", "Copy Markdown Table", "Export Excel (.xml)")
        val choice = JOptionPane.showOptionDialog(
            this,
            "Choose export format for current Pivot Matrix (${df.rowCount} rows):",
            "Export Pivot Table",
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.PLAIN_MESSAGE,
            null,
            options,
            options[0]
        )
        if (choice >= 0) {
            val content = when (choice) {
                0 -> DataFrameExporter.toCsv(df)
                1 -> DataFrameExporter.toMarkdown(df, 500)
                2 -> DataFrameExporter.toExcelXml(df)
                else -> ""
            }
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(content), null)
            JOptionPane.showMessageDialog(this, "Pivot matrix exported and copied to clipboard!", "Export Complete", JOptionPane.INFORMATION_MESSAGE)
        }
    }
}
