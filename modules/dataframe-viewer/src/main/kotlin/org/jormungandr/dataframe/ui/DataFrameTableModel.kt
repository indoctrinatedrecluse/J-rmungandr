package org.jormungandr.dataframe.ui

import org.jormungandr.dataframe.model.DataFrame
import javax.swing.table.AbstractTableModel

/**
 * High-performance virtualized TableModel for Swing JBTable.
 * Prepends a dedicated 1-based Row Index column `[#]`.
 */
class DataFrameTableModel(
    initialDataFrame: DataFrame = DataFrame.empty()
) : AbstractTableModel() {

    private var currentDataFrame: DataFrame = initialDataFrame
    private var displayDataFrame: DataFrame = initialDataFrame

    var dataFrame: DataFrame
        get() = displayDataFrame
        set(value) {
            currentDataFrame = value
            displayDataFrame = value
            fireTableStructureChanged()
        }

    fun updateDataFrame(newDataFrame: DataFrame) {
        this.dataFrame = newDataFrame
    }

    fun applyFilter(query: String) {
        displayDataFrame = if (query.isBlank()) currentDataFrame else currentDataFrame.filterText(query)
        fireTableDataChanged()
    }

    fun applySort(dataColumnIndex: Int, ascending: Boolean) {
        displayDataFrame = displayDataFrame.sort(dataColumnIndex, ascending)
        fireTableDataChanged()
    }

    fun resetFilterAndSort() {
        displayDataFrame = currentDataFrame
        fireTableDataChanged()
    }

    override fun getRowCount(): Int = displayDataFrame.rowCount

    override fun getColumnCount(): Int = displayDataFrame.columnCount + 1 // +1 for row number gutter

    override fun getColumnName(column: Int): String {
        return if (column == 0) "#" else displayDataFrame.columns[column - 1].name
    }

    fun getColumnMetadata(column: Int) = if (column > 0) displayDataFrame.columns[column - 1] else null

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? {
        if (columnIndex == 0) {
            return rowIndex + 1
        }
        val dataColIndex = columnIndex - 1
        if (rowIndex in 0 until displayDataFrame.rowCount && dataColIndex in 0 until displayDataFrame.columnCount) {
            return displayDataFrame.rows[rowIndex].getOrNull(dataColIndex)
        }
        return null
    }

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false
}
