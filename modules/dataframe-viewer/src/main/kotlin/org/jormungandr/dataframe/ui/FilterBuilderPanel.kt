package org.jormungandr.dataframe.ui

import com.intellij.ui.components.JBTextField
import org.jormungandr.dataframe.filter.CompoundFilter
import org.jormungandr.dataframe.filter.Conjunction
import org.jormungandr.dataframe.filter.FilterCondition
import org.jormungandr.dataframe.filter.FilterOperator
import org.jormungandr.dataframe.model.DataFrame
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.*
import javax.swing.border.EmptyBorder

class FilterBuilderPanel(
    private var dataFrame: DataFrame = DataFrame.empty(),
    private val onFilterApplied: (CompoundFilter) -> Unit
) : JPanel(BorderLayout()) {

    private val conditionRowsPanel = JPanel()
    private val conjunctionCombo = JComboBox(Conjunction.values())
    private val conditionWidgets = mutableListOf<ConditionRowWidget>()

    init {
        border = EmptyBorder(4, 6, 4, 6)
        conditionRowsPanel.layout = BoxLayout(conditionRowsPanel, BoxLayout.Y_AXIS)

        val topBar = JPanel(FlowLayout(FlowLayout.LEFT, 6, 2))
        topBar.add(JLabel("Match:"))
        topBar.add(conjunctionCombo)

        val addBtn = JButton("+ Add Condition").apply {
            isFocusable = false
            addActionListener { addConditionRow() }
        }
        val applyBtn = JButton("✓ Apply Filter").apply {
            isFocusable = false
            addActionListener { applyFilter() }
        }
        val clearBtn = JButton("Reset").apply {
            isFocusable = false
            addActionListener {
                conditionWidgets.clear()
                conditionRowsPanel.removeAll()
                conditionRowsPanel.revalidate()
                conditionRowsPanel.repaint()
                onFilterApplied(CompoundFilter(emptyList()))
            }
        }

        topBar.add(addBtn)
        topBar.add(applyBtn)
        topBar.add(clearBtn)

        add(topBar, BorderLayout.NORTH)
        add(conditionRowsPanel, BorderLayout.CENTER)

        if (dataFrame.columns.isNotEmpty()) {
            addConditionRow()
        }
    }

    fun setDataFrame(df: DataFrame) {
        this.dataFrame = df
        conditionWidgets.forEach { it.updateColumns(df) }
    }

    private fun addConditionRow() {
        val rowWidget = ConditionRowWidget(dataFrame) { widget ->
            conditionWidgets.remove(widget)
            conditionRowsPanel.remove(widget)
            conditionRowsPanel.revalidate()
            conditionRowsPanel.repaint()
        }
        conditionWidgets.add(rowWidget)
        conditionRowsPanel.add(rowWidget)
        conditionRowsPanel.revalidate()
        conditionRowsPanel.repaint()
    }

    private fun applyFilter() {
        val conditions = conditionWidgets.mapNotNull { it.toCondition() }
        val conj = conjunctionCombo.selectedItem as? Conjunction ?: Conjunction.AND
        onFilterApplied(CompoundFilter(conditions, conj))
    }

    private class ConditionRowWidget(
        private var df: DataFrame,
        private val onRemove: (ConditionRowWidget) -> Unit
    ) : JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)) {

        private val colCombo = JComboBox<String>()
        private val opCombo = JComboBox(FilterOperator.values())
        private val valField = JBTextField(12)
        private val removeBtn = JButton("✕").apply {
            isFocusable = false
            margin = java.awt.Insets(1, 4, 1, 4)
            addActionListener { onRemove(this@ConditionRowWidget) }
        }

        init {
            updateColumns(df)
            opCombo.addActionListener {
                val op = opCombo.selectedItem as FilterOperator
                valField.isVisible = op.requiresValue
            }
            add(colCombo)
            add(opCombo)
            add(valField)
            add(removeBtn)
        }

        fun updateColumns(newDf: DataFrame) {
            this.df = newDf
            val prev = colCombo.selectedItem as? String
            colCombo.removeAllItems()
            for (c in newDf.columns) colCombo.addItem(c.name)
            if (prev != null && newDf.getColumnIndex(prev) >= 0) colCombo.selectedItem = prev
        }

        fun toCondition(): FilterCondition? {
            val col = colCombo.selectedItem as? String ?: return null
            val op = opCombo.selectedItem as? FilterOperator ?: return null
            return FilterCondition(col, op, valField.text.trim())
        }
    }
}
