package org.jormungandr.jupyter.ui.outline

import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import org.jormungandr.jupyter.model.CellType
import org.jormungandr.jupyter.model.NotebookModel
import java.awt.*
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * Heading item discovered within a notebook markdown cell.
 */
data class NotebookHeading(
    val cellIndex: Int,
    val level: Int,
    val title: String
)

/**
 * Interactive Table of Contents (Outline) Panel for navigating Jupyter Notebooks.
 * Displays extracted Markdown headings with hierarchical indentation, live search filtering,
 * and direct jump-to-cell navigation.
 */
class NotebookOutlinePanel(
    private val onHeadingSelected: (cellIndex: Int) -> Unit
) : JPanel(BorderLayout()) {

    private val allHeadings = mutableListOf<NotebookHeading>()
    private val listModel = DefaultListModel<NotebookHeading>()
    private val headingList = JBList(listModel)
    private val searchField = JBTextField()
    private val countLabel = JLabel("0 sections")

    init {
        preferredSize = Dimension(240, 400)
        border = BorderFactory.createCompoundBorder(
            LineBorder(Color(220, 220, 220), 1),
            EmptyBorder(8, 8, 8, 8)
        )
        background = Color(250, 250, 250)

        buildUi()
    }

    private fun buildUi() {
        // Header
        val header = JPanel(BorderLayout(6, 4)).apply {
            isOpaque = false
            border = EmptyBorder(0, 0, 6, 0)
        }
        val titleLabel = JBLabel("📑 Outline").apply {
            font = font.deriveFont(Font.BOLD, 12.5f)
        }
        countLabel.apply {
            font = font.deriveFont(Font.PLAIN, 10f)
            foreground = Color.GRAY
        }
        header.add(titleLabel, BorderLayout.WEST)
        header.add(countLabel, BorderLayout.EAST)

        searchField.emptyText.text = "Filter sections..."
        searchField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = applyFilter()
            override fun removeUpdate(e: DocumentEvent?) = applyFilter()
            override fun changedUpdate(e: DocumentEvent?) = applyFilter()
        })
        header.add(searchField, BorderLayout.SOUTH)

        add(header, BorderLayout.NORTH)

        // List
        headingList.apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            cellRenderer = HeadingCellRenderer()
            fixedCellHeight = 30
            addListSelectionListener {
                val selected = selectedValue
                if (selected != null && !valueIsAdjusting) {
                    onHeadingSelected(selected.cellIndex)
                }
            }
        }

        val scrollPane = JBScrollPane(headingList).apply {
            border = LineBorder(Color(230, 230, 230), 1)
        }
        add(scrollPane, BorderLayout.CENTER)
    }

    /**
     * Extracts and populates all Markdown headings from the provided [NotebookModel].
     */
    fun updateOutline(model: NotebookModel) {
        allHeadings.clear()

        model.cells.forEachIndexed { index, cell ->
            if (cell.cellType == CellType.MARKDOWN) {
                for (line in cell.source.lines()) {
                    val trimmed = line.trim()
                    val match = Regex("^(#{1,6})\\s+(.*)").find(trimmed)
                    if (match != null) {
                        val level = match.groupValues[1].length
                        val text = match.groupValues[2].trim()
                        allHeadings.add(NotebookHeading(index, level, text))
                    }
                }
            }
        }

        applyFilter()
    }

    private fun applyFilter() {
        val query = searchField.text.trim().lowercase()
        listModel.clear()

        for (h in allHeadings) {
            if (query.isEmpty() || h.title.lowercase().contains(query)) {
                listModel.addElement(h)
            }
        }

        countLabel.text = "${allHeadings.size} section${if (allHeadings.size == 1) "" else "s"}"
    }

    /** Custom renderer for outline headings with hierarchical indentation and level badges */
    private inner class HeadingCellRenderer : ListCellRenderer<NotebookHeading> {
        private val panel = JPanel(BorderLayout(6, 0)).apply {
            border = EmptyBorder(2, 4, 2, 4)
        }
        private val badgeLabel = JLabel().apply {
            font = font.deriveFont(Font.BOLD, 9f)
            foreground = Color(38, 139, 210)
        }
        private val textLabel = JLabel().apply {
            font = font.deriveFont(Font.PLAIN, 11f)
        }

        init {
            panel.add(badgeLabel, BorderLayout.WEST)
            panel.add(textLabel, BorderLayout.CENTER)
        }

        override fun getListCellRendererComponent(
            list: JList<out NotebookHeading>,
            value: NotebookHeading?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            if (value != null) {
                val indent = (value.level - 1) * 12 + 4
                panel.border = EmptyBorder(2, indent, 2, 4)
                badgeLabel.text = "H${value.level}"
                textLabel.text = value.title
                textLabel.font = if (value.level == 1) textLabel.font.deriveFont(Font.BOLD, 11.5f)
                else textLabel.font.deriveFont(Font.PLAIN, 11f)
            }

            panel.background = if (isSelected) Color(238, 232, 213) else Color.WHITE
            return panel
        }
    }
}
