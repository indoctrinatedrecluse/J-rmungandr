package org.jormungandr.dataframe.ui

import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import org.jormungandr.dataframe.model.DataFrame
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.table.AbstractTableModel

/**
 * Analytical Column Profiler panel presenting dataset-wide statistics across all columns.
 */
class ColumnProfilerPanel(
    private var dataFrame: DataFrame = DataFrame.empty()
) : JPanel(BorderLayout()) {

    private val tableModel = ProfilerTableModel(dataFrame)
    private val table = JBTable(tableModel)

    init {
        table.autoResizeMode = JBTable.AUTO_RESIZE_ALL_COLUMNS
        table.rowHeight = 28
        table.tableHeader.reorderingAllowed = false
        add(JBScrollPane(table), BorderLayout.CENTER)
    }

    fun setDataFrame(df: DataFrame) {
        this.dataFrame = df
        tableModel.updateDataFrame(df)
    }

    private class ProfilerTableModel(
        private var df: DataFrame
    ) : AbstractTableModel() {

        private val columnHeaders = listOf(
            "Column", "Type", "Nulls (%)", "Distinct",
            "Min", "Max", "Mean", "Median", "StdDev", "IQR", "Skewness"
        )

        fun updateDataFrame(newDf: DataFrame) {
            this.df = newDf
            fireTableDataChanged()
        }

        override fun getRowCount(): Int = df.columns.size

        override fun getColumnCount(): Int = columnHeaders.size

        override fun getColumnName(column: Int): String = columnHeaders[column]

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? {
            val col = df.columns.getOrNull(rowIndex) ?: return null
            return when (columnIndex) {
                0 -> col.name
                1 -> "${col.typeName} (${col.category.badgeShort})"
                2 -> "${col.nullCount} (%.1f%%)".format(col.nullPercentage)
                3 -> col.distinctCount
                4 -> col.minVal ?: "-"
                5 -> col.maxVal ?: "-"
                6 -> col.meanVal?.let { "%.2f".format(it) } ?: "-"
                7 -> col.medianVal?.let { "%.2f".format(it) } ?: "-"
                8 -> col.stdDev?.let { "%.2f".format(it) } ?: "-"
                9 -> col.iqr?.let { "%.2f".format(it) } ?: "-"
                10 -> col.skewness?.let { "%.2f".format(it) } ?: "-"
                else -> null
            }
        }
    }
}
