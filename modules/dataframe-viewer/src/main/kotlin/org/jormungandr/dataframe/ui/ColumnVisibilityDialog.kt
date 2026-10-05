package org.jormungandr.dataframe.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBScrollPane
import org.jormungandr.dataframe.model.DataFrame
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.border.EmptyBorder

class ColumnVisibilityDialog(
    project: Project? = null,
    private val df: DataFrame,
    initiallySelected: List<String> = df.columns.map { it.name }
) : DialogWrapper(project, true) {

    private val checkBoxes = mutableMapOf<String, JBCheckBox>()

    init {
        title = "Configure Column Visibility"
        for (col in df.columns) {
            val cb = JBCheckBox(col.name, initiallySelected.contains(col.name))
            checkBoxes[col.name] = cb
        }
        init()
    }

    fun getSelectedColumns(): List<String> {
        return checkBoxes.filter { it.value.isSelected }.map { it.key }
    }

    override fun createCenterPanel(): JComponent {
        val root = JPanel(BorderLayout(8, 8)).apply {
            preferredSize = Dimension(320, 360)
            border = EmptyBorder(8, 12, 8, 12)
        }

        val topToolbar = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0))
        val selectAllBtn = JButton("Select All").apply {
            isFocusable = false
            addActionListener { checkBoxes.values.forEach { it.isSelected = true } }
        }
        val deselectAllBtn = JButton("Deselect All").apply {
            isFocusable = false
            addActionListener { checkBoxes.values.forEach { it.isSelected = false } }
        }
        topToolbar.add(selectAllBtn)
        topToolbar.add(deselectAllBtn)

        val listPanel = JPanel(GridLayout(0, 1, 2, 2))
        for (cb in checkBoxes.values) {
            listPanel.add(cb)
        }

        root.add(topToolbar, BorderLayout.NORTH)
        root.add(JBScrollPane(listPanel), BorderLayout.CENTER)
        return root
    }
}
