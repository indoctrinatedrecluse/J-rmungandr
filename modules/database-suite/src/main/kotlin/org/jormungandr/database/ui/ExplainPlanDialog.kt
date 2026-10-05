package org.jormungandr.database.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.treeStructure.Tree
import org.jormungandr.database.engine.ExplainPlanNode
import org.jormungandr.database.engine.ExplainPlanResult
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import javax.swing.*
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel

class ExplainPlanDialog(
    project: Project? = null,
    private val result: ExplainPlanResult
) : DialogWrapper(project, true) {

    init {
        title = "Execution Plan Analysis (${result.dialect})"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val root = JPanel(BorderLayout(8, 8)).apply {
            preferredSize = Dimension(680, 480)
            border = EmptyBorder(8, 8, 8, 8)
        }

        // Header info bar
        val header = JPanel(BorderLayout()).apply {
            background = Color(248, 250, 252)
            border = CompoundBorder(
                LineBorder(Color(226, 232, 240), 1),
                EmptyBorder(8, 12, 8, 12)
            )
        }
        val scanCount = countWarnings(result.rootNodes)
        val warningBadge = if (scanCount > 0) " | ⚠️ $scanCount Full Table Scans detected" else " | ✓ Optimized"
        val headerLabel = JBLabel("⏱ Analysis Duration: ${result.durationMs} ms$warningBadge").apply {
            font = font.deriveFont(Font.BOLD, 12f)
            foreground = if (scanCount > 0) Color(194, 65, 12) else Color(22, 101, 52)
        }
        header.add(headerLabel, BorderLayout.WEST)
        root.add(header, BorderLayout.NORTH)

        val tabbedPane = JBTabbedPane()

        // 1. Visual Execution Tree
        val treeRoot = DefaultMutableTreeNode("Query Plan")
        for (node in result.rootNodes) {
            treeRoot.add(buildTreeNode(node))
        }
        val treeModel = DefaultTreeModel(treeRoot)
        val tree = Tree(treeModel).apply {
            isRootVisible = false
            showsRootHandles = true
            cellRenderer = object : DefaultTreeCellRenderer() {
                override fun getTreeCellRendererComponent(
                    tree: JTree?,
                    value: Any?,
                    sel: Boolean,
                    expanded: Boolean,
                    leaf: Boolean,
                    row: Int,
                    hasFocus: Boolean
                ): java.awt.Component {
                    super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus)
                    val uo = (value as? DefaultMutableTreeNode)?.userObject as? ExplainPlanNode
                    if (uo != null) {
                        text = "${uo.title}: ${uo.detail}"
                        if (uo.isScanWarning) {
                            foreground = Color(194, 65, 12)
                            font = font.deriveFont(Font.BOLD)
                        } else {
                            foreground = if (sel) textSelectionColor else textNonSelectionColor
                        }
                    }
                    return this
                }
            }
        }
        for (i in 0 until tree.rowCount) {
            tree.expandRow(i)
        }
        tabbedPane.addTab("🌳 Visual Plan Tree", JBScrollPane(tree))

        // 2. Raw Text Output
        val rawArea = JBTextArea(result.rawText).apply {
            isEditable = false
            font = Font("Monospaced", Font.PLAIN, 12)
            background = Color(248, 250, 252)
        }
        tabbedPane.addTab("📄 Raw Engine Output", JBScrollPane(rawArea))

        // 3. SQL Query
        val sqlArea = JBTextArea(result.sql).apply {
            isEditable = false
            font = Font("Monospaced", Font.PLAIN, 12)
            background = Color(248, 250, 252)
        }
        tabbedPane.addTab("🔍 Query Text", JBScrollPane(sqlArea))

        root.add(tabbedPane, BorderLayout.CENTER)
        return root
    }

    private fun buildTreeNode(node: ExplainPlanNode): DefaultMutableTreeNode {
        val tn = DefaultMutableTreeNode(node)
        for (child in node.children) {
            tn.add(buildTreeNode(child))
        }
        return tn
    }

    private fun countWarnings(nodes: List<ExplainPlanNode>): Int {
        var count = 0
        for (n in nodes) {
            if (n.isScanWarning) count++
            count += countWarnings(n.children)
        }
        return count
    }
}
