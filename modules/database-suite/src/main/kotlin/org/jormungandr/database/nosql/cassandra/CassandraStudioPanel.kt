package org.jormungandr.database.nosql.cassandra

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.treeStructure.Tree
import org.jormungandr.dataframe.io.DataFrameExporter
import org.jormungandr.dataframe.model.DataFrame
import org.jormungandr.dataframe.ui.DataFrameGridPanel
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class CassandraStudioPanel(private val project: Project? = null) : JPanel(BorderLayout()) {

    private val rootNode = DefaultMutableTreeNode("Cassandra Keyspaces")
    private val treeModel = DefaultTreeModel(rootNode)
    private val tree = Tree(treeModel)

    private val cqlEditor = JBTextArea(6, 40).apply {
        font = Font("Monospaced", Font.PLAIN, 13)
        text = CassandraEngine.TEMPLATE_SELECT_PK
    }
    private val timerLabel = JBLabel("⏱ Ready")
    private val statusLabel = JBLabel("")
    private val gridPanel = DataFrameGridPanel(DataFrame.empty("cql_results"))

    init {
        // Left Schema Panel
        val leftPanel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createTitledBorder("🪐 Keyspaces & Tables")
            add(JBScrollPane(tree), BorderLayout.CENTER)
        }

        // Right Query & Results Panel
        val rightPanel = JPanel(BorderLayout())

        val queryTopPanel = JPanel(BorderLayout())
        val toolbar = JPanel(BorderLayout()).apply {
            border = EmptyBorder(4, 6, 4, 6)
        }
        val leftTools = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0))
        val rightTools = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0))

        val runBtn = JButton("▶ Run CQL (Ctrl+Enter)").apply {
            isFocusable = false
            font = font.deriveFont(Font.BOLD, 11f)
            addActionListener { executeCql() }
        }
        val tmplBtn = JButton("📝 CQL Templates ▾").apply {
            isFocusable = false
            font = font.deriveFont(Font.PLAIN, 11f)
            addActionListener { showTemplatesMenu(this) }
        }
        val openDfBtn = JButton("📊 Open in DataFrame Studio").apply {
            isFocusable = false
            font = font.deriveFont(Font.BOLD, 11f)
            foreground = Color(30, 64, 175)
            toolTipText = "Open CQL tabular result set in DataFrame Studio"
            addActionListener { openInDataFrameStudio() }
        }
        val copyBtn = JButton("📋 Copy CSV").apply {
            isFocusable = false
            font = font.deriveFont(Font.PLAIN, 11f)
            addActionListener { copyCsvToClipboard() }
        }

        leftTools.add(runBtn)
        leftTools.add(tmplBtn)
        leftTools.add(openDfBtn)
        leftTools.add(copyBtn)

        rightTools.add(timerLabel)
        rightTools.add(statusLabel)

        toolbar.add(leftTools, BorderLayout.WEST)
        toolbar.add(rightTools, BorderLayout.EAST)

        cqlEditor.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.isControlDown && e.keyCode == KeyEvent.VK_ENTER) {
                    executeCql()
                    e.consume()
                }
            }
        })

        queryTopPanel.add(toolbar, BorderLayout.NORTH)
        queryTopPanel.add(JBScrollPane(cqlEditor), BorderLayout.CENTER)

        val verticalSplit = JSplitPane(JSplitPane.VERTICAL_SPLIT, queryTopPanel, gridPanel).apply {
            resizeWeight = 0.28
            isContinuousLayout = true
            border = null
        }

        rightPanel.add(verticalSplit, BorderLayout.CENTER)

        val mainSplit = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel).apply {
            resizeWeight = 0.28
            isContinuousLayout = true
            border = null
        }

        add(mainSplit, BorderLayout.CENTER)

        setupTree()
        refreshKeyspaces()
    }

    private fun setupTree() {
        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val path = tree.getPathForLocation(e.x, e.y) ?: return
                val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
                val table = node.userObject as? CassandraTable ?: return
                if (e.clickCount == 2 || SwingUtilities.isRightMouseButton(e)) {
                    cqlEditor.text = "SELECT * FROM ${table.keyspace}.${table.name} LIMIT 50;"
                    executeCql()
                }
            }
        })
    }

    fun refreshKeyspaces() {
        rootNode.removeAllChildren()
        val ksList = CassandraEngine.listKeyspaces()
        for (ks in ksList) {
            val ksNode = DefaultMutableTreeNode("📁 ${ks.name} [${ks.replicationStrategy.substringBefore(" ")}]")
            for (table in ks.tables) {
                val tableNode = DefaultMutableTreeNode(table)
                for (col in table.columns) {
                    val pkBadge = when {
                        col.isPartitionKey -> " [PK]"
                        col.isClusteringKey -> " [CK]"
                        else -> ""
                    }
                    val colNode = DefaultMutableTreeNode("• ${col.name} (${col.typeName})$pkBadge")
                    tableNode.add(colNode)
                }
                ksNode.add(tableNode)
            }
            rootNode.add(ksNode)
        }
        treeModel.reload()
        tree.expandRow(0)
    }

    fun executeCql() {
        val cql = cqlEditor.text.trim()
        if (cql.isBlank()) return

        timerLabel.text = "⏱ Running..."
        val result = CassandraEngine.executeCql(cql = cql)
        timerLabel.text = "⏱ ${result.executionTimeMs} ms"

        if (result.isSuccess) {
            val df = result.dataFrame ?: DataFrame.empty("cql_results")
            gridPanel.dataFrame = df
            statusLabel.text = "✓ ${df.rowCount} rows returned"
            statusLabel.foreground = Color(40, 160, 60)
        } else {
            statusLabel.text = "✗ ${result.errorMessage}"
            statusLabel.foreground = Color(200, 50, 50)
        }
    }

    private fun showTemplatesMenu(anchor: JComponent) {
        val menu = JPopupMenu()
        menu.add(JMenuItem("SELECT with Partition Key & LIMIT").apply {
            addActionListener { cqlEditor.text = CassandraEngine.TEMPLATE_SELECT_PK }
        })
        menu.add(JMenuItem("CREATE KEYSPACE (NetworkTopology)").apply {
            addActionListener { cqlEditor.text = CassandraEngine.TEMPLATE_CREATE_KEYSPACE }
        })
        menu.add(JMenuItem("CREATE TABLE with Compound Key").apply {
            addActionListener { cqlEditor.text = CassandraEngine.TEMPLATE_CREATE_TABLE }
        })
        menu.add(JMenuItem("BATCH INSERT Statements").apply {
            addActionListener { cqlEditor.text = CassandraEngine.TEMPLATE_BATCH_INSERT }
        })
        menu.show(anchor, 0, anchor.height)
    }

    private fun copyCsvToClipboard() {
        val df = gridPanel.dataFrame
        if (df.rowCount == 0) return
        val csv = DataFrameExporter.toCsv(df)
        val sel = StringSelection(csv)
        Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, sel)
        statusLabel.text = "✓ Copied CSV (${df.rowCount} rows) to clipboard"
        statusLabel.foreground = Color(40, 160, 60)
    }

    private fun openInDataFrameStudio() {
        val df = gridPanel.dataFrame
        if (df.rowCount == 0) return
        val p = project ?: return

        runCatching {
            val csv = DataFrameExporter.toCsv(df)
            val tempFile = File.createTempFile("cassandra_cql_", ".csv")
            tempFile.writeText(csv, Charsets.UTF_8)
            tempFile.deleteOnExit()

            val vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempFile)
            if (vFile != null) {
                FileEditorManager.getInstance(p).openFile(vFile, true)
                statusLabel.text = "✓ Opened ${df.rowCount} rows in DataFrame Studio"
                statusLabel.foreground = Color(40, 160, 60)
            }
        }
    }
}
