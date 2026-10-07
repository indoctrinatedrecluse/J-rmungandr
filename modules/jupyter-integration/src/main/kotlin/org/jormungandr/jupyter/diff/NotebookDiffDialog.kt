package org.jormungandr.jupyter.diff

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import org.jormungandr.jupyter.model.NotebookModel
import java.awt.*
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder

/**
 * Visual Notebook Diff Dialog presenting a cell-by-cell semantic comparison of two notebooks.
 */
class NotebookDiffDialog(
    project: Project? = null,
    private val oldModel: NotebookModel,
    private val newModel: NotebookModel,
    private val oldTitle: String = "Base Version",
    private val newTitle: String = "Modified Version"
) : DialogWrapper(project, true) {

    private val diffResult: NotebookDiffResult = NotebookDiffService.diff(oldModel, newModel)
    private val cardsContainer = JPanel()
    private val filterCheckbox = JCheckBox("Show Changed Cells Only", true)

    init {
        title = "Notebook Visual Diff"
        isResizable = true
        init()
        updateCards()
    }

    override fun createCenterPanel(): JComponent {
        val root = JPanel(BorderLayout(0, 10)).apply {
            preferredSize = Dimension(840, 560)
            border = EmptyBorder(10, 14, 10, 14)
        }

        // Header summary
        val header = JPanel(BorderLayout(8, 6)).apply {
            isOpaque = false
            border = BorderFactory.createCompoundBorder(
                LineBorder(Color(220, 220, 220), 1, true),
                EmptyBorder(10, 12, 10, 12)
            )
            background = Color(248, 249, 250)
        }

        val titlePanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            val hLabel = JBLabel("Comparing: $oldTitle ➔ $newTitle").apply {
                font = font.deriveFont(Font.BOLD, 13f)
            }
            add(hLabel)
        }
        header.add(titlePanel, BorderLayout.WEST)

        val badgesPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply { isOpaque = false }
        badgesPanel.add(createBadge("+${diffResult.addedCount} Added", Color(230, 245, 230), Color(46, 125, 50)))
        badgesPanel.add(createBadge("-${diffResult.deletedCount} Deleted", Color(255, 235, 235), Color(198, 40, 40)))
        badgesPanel.add(createBadge("~${diffResult.modifiedCount} Modified", Color(255, 248, 225), Color(245, 124, 0)))
        badgesPanel.add(createBadge("=${diffResult.unchangedCount} Unchanged", Color(240, 240, 240), Color(117, 117, 117)))

        filterCheckbox.isOpaque = false
        filterCheckbox.addActionListener { updateCards() }
        badgesPanel.add(filterCheckbox)

        header.add(badgesPanel, BorderLayout.EAST)
        root.add(header, BorderLayout.NORTH)

        // Cards Container
        cardsContainer.layout = BoxLayout(cardsContainer, BoxLayout.Y_AXIS)
        cardsContainer.isOpaque = true
        cardsContainer.background = Color(255, 255, 255)
        cardsContainer.border = EmptyBorder(6, 6, 6, 6)

        val scroll = JBScrollPane(cardsContainer).apply {
            verticalScrollBar.unitIncrement = 24
            border = LineBorder(Color(225, 225, 225), 1)
        }
        root.add(scroll, BorderLayout.CENTER)

        return root
    }

    private fun updateCards() {
        cardsContainer.removeAll()

        val changesOnly = filterCheckbox.isSelected
        val filtered = diffResult.items.filter { !changesOnly || it.status != DiffStatus.UNCHANGED }

        if (filtered.isEmpty()) {
            val emptyLabel = JLabel("No differences detected matching current filter.").apply {
                alignmentX = Component.CENTER_ALIGNMENT
                border = EmptyBorder(40, 0, 40, 0)
                foreground = Color.GRAY
            }
            cardsContainer.add(emptyLabel)
        } else {
            for (item in filtered) {
                cardsContainer.add(createCellDiffCard(item))
                cardsContainer.add(Box.createVerticalStrut(10))
            }
        }

        cardsContainer.revalidate()
        cardsContainer.repaint()
    }

    private fun createCellDiffCard(item: CellDiffItem): JPanel {
        val card = JPanel(BorderLayout(0, 6)).apply {
            border = BorderFactory.createCompoundBorder(
                LineBorder(getBorderColorForStatus(item.status), 1, true),
                EmptyBorder(8, 10, 8, 10)
            )
            background = getCardBgForStatus(item.status)
            maximumSize = Dimension(Int.MAX_VALUE, 400)
        }

        // Header
        val cardHeader = JPanel(BorderLayout()).apply { isOpaque = false }
        val titleText = "Cell #${item.index + 1} (${item.newCell?.cellType?.value ?: item.oldCell?.cellType?.value ?: "cell"})"
        val leftTitle = JBLabel(titleText).apply { font = font.deriveFont(Font.BOLD, 12f) }

        val rightSummary = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply { isOpaque = false }
        rightSummary.add(createBadge(item.status.name, getCardBgForStatus(item.status), getBorderColorForStatus(item.status)))

        cardHeader.add(leftTitle, BorderLayout.WEST)
        cardHeader.add(rightSummary, BorderLayout.EAST)
        card.add(cardHeader, BorderLayout.NORTH)

        // Diff lines
        val linesPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = true
            background = Color(250, 250, 250)
            border = EmptyBorder(6, 8, 6, 8)
        }

        for (line in item.sourceDiff.take(30)) {
            val lineLabel = JLabel(line).apply {
                font = Font(Font.MONOSPACED, Font.PLAIN, 11)
                isOpaque = true
                when {
                    line.startsWith("+") -> {
                        background = Color(230, 245, 230)
                        foreground = Color(46, 125, 50)
                    }
                    line.startsWith("-") -> {
                        background = Color(255, 235, 235)
                        foreground = Color(198, 40, 40)
                    }
                    else -> {
                        background = Color(250, 250, 250)
                        foreground = Color(60, 60, 60)
                    }
                }
            }
            linesPanel.add(lineLabel)
        }
        if (item.sourceDiff.size > 30) {
            linesPanel.add(JLabel("... (${item.sourceDiff.size - 30} more lines)").apply {
                font = font.deriveFont(Font.ITALIC, 10f)
                foreground = Color.GRAY
            })
        }

        card.add(linesPanel, BorderLayout.CENTER)
        return card
    }

    private fun getBorderColorForStatus(status: DiffStatus): Color = when (status) {
        DiffStatus.ADDED -> Color(46, 125, 50)
        DiffStatus.DELETED -> Color(198, 40, 40)
        DiffStatus.MODIFIED -> Color(245, 124, 0)
        DiffStatus.UNCHANGED -> Color(180, 180, 180)
    }

    private fun getCardBgForStatus(status: DiffStatus): Color = when (status) {
        DiffStatus.ADDED -> Color(244, 253, 244)
        DiffStatus.DELETED -> Color(255, 244, 244)
        DiffStatus.MODIFIED -> Color(255, 252, 242)
        DiffStatus.UNCHANGED -> Color(255, 255, 255)
    }

    private fun createBadge(text: String, bg: Color, fg: Color): JLabel {
        return JLabel(text).apply {
            font = font.deriveFont(Font.BOLD, 10f)
            isOpaque = true
            background = bg
            foreground = fg
            border = BorderFactory.createCompoundBorder(
                LineBorder(fg, 1, true),
                EmptyBorder(2, 6, 2, 6)
            )
        }
    }
}
