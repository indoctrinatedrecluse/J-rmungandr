package org.jormungandr.database.ui

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import org.jormungandr.database.engine.DdlGenerator
import org.jormungandr.database.engine.DatabaseConnectionManager
import org.jormungandr.database.engine.SchemaIntrospector
import org.jormungandr.database.model.ConnectionConfig
import org.jormungandr.database.model.DatabaseDialect
import org.jormungandr.database.model.TableMetadata
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.UUID
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class DatabaseStudioPanel(private val project: Project? = null) : JPanel(BorderLayout()) {

    private val rootNode = DefaultMutableTreeNode("Data Sources")
    private val treeModel = DefaultTreeModel(rootNode)
    private val tree = Tree(treeModel)
    private val consolePanel = QueryConsolePanel(project)

    init {
        // Ensure default SQLite in-memory sample connection is registered
        if (DatabaseConnectionManager.getAllConfigs().isEmpty()) {
            val sampleConfig = ConnectionConfig(
                id = UUID.randomUUID().toString(),
                name = "SQLite In-Memory (Sample)",
                dialect = DatabaseDialect.SQLITE,
                databaseName = ":memory:"
            )
            val conn = DatabaseConnectionManager.connect(sampleConfig)
            // Populate sample tables
            runCatching {
                conn.createStatement().use { st ->
                    st.execute("CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT, email TEXT, active BOOLEAN);")
                    st.execute("INSERT INTO users VALUES (1, 'Alice Smith', 'alice@jormungandr.org', 1);")
                    st.execute("INSERT INTO users VALUES (2, 'Bob Jones', 'bob@jormungandr.org', 1);")
                    st.execute("INSERT INTO users VALUES (3, 'Charlie Brown', 'charlie@jormungandr.org', 0);")
                    st.execute("CREATE TABLE metrics (metric_id INTEGER PRIMARY KEY, name TEXT, value REAL);")
                    st.execute("INSERT INTO metrics VALUES (101, 'accuracy', 0.942);")
                    st.execute("INSERT INTO metrics VALUES (102, 'loss', 0.058);")
                }
            }
        }

        refreshSchemaTree()

        // Explorer Left Panel
        val explorerPanel = JPanel(BorderLayout()).apply {
            border = EmptyBorder(4, 4, 4, 4)
        }
        val explorerToolbar = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0))
        val addConnBtn = JButton("+ Add Connection").apply {
            isFocusable = false
            addActionListener {
                val dlg = NewConnectionDialog(project)
                if (dlg.showAndGet()) {
                    dlg.resultConfig?.let { cfg ->
                        runCatching {
                            DatabaseConnectionManager.connect(cfg)
                            refreshSchemaTree()
                            consolePanel.refreshConnections()
                        }
                    }
                }
            }
        }
        val refreshBtn = JButton("🔄").apply {
            isFocusable = false
            toolTipText = "Refresh Schema"
            addActionListener {
                refreshSchemaTree()
                consolePanel.refreshConnections()
            }
        }
        explorerToolbar.add(addConnBtn)
        explorerToolbar.add(refreshBtn)

        explorerPanel.add(explorerToolbar, BorderLayout.NORTH)
        explorerPanel.add(JBScrollPane(tree), BorderLayout.CENTER)

        // Split pane
        val split = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, explorerPanel, consolePanel).apply {
            resizeWeight = 0.28
            isContinuousLayout = true
            border = null
        }

        add(split, BorderLayout.CENTER)

        setupTreeInteractions()
    }

    private fun setupTreeInteractions() {
        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    val path = tree.getPathForLocation(e.x, e.y) ?: return
                    val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
                    val userObj = node.userObject
                    if (userObj is TableMetadata) {
                        val menu = JPopupMenu()
                        menu.add(JMenuItem("SELECT * FROM ${userObj.name} LIMIT 100").apply {
                            addActionListener {
                                consolePanel.setQueryText("SELECT * FROM ${userObj.name} LIMIT 100;")
                                consolePanel.executeCurrentQuery()
                            }
                        })
                        menu.add(JMenuItem("SELECT COUNT(*) FROM ${userObj.name}").apply {
                            addActionListener {
                                consolePanel.setQueryText("SELECT COUNT(*) AS total_count FROM ${userObj.name};")
                                consolePanel.executeCurrentQuery()
                            }
                        })
                        menu.addSeparator()
                        menu.add(JMenuItem("Generate CREATE TABLE DDL").apply {
                            addActionListener {
                                val ddl = DdlGenerator.generateCreateTable(userObj)
                                consolePanel.setQueryText(ddl)
                            }
                        })
                        menu.add(JMenuItem("Generate INSERT Template").apply {
                            addActionListener {
                                val template = DdlGenerator.generateInsertTemplate(userObj)
                                consolePanel.setQueryText(template)
                            }
                        })
                        menu.add(JMenuItem("Generate DROP TABLE Statement").apply {
                            addActionListener {
                                val drop = DdlGenerator.generateDropTable(userObj)
                                consolePanel.setQueryText(drop)
                            }
                        })
                        menu.show(tree, e.x, e.y)
                    }
                }
            }
        })
    }

    fun refreshSchemaTree() {
        rootNode.removeAllChildren()
        val configs = DatabaseConnectionManager.getAllConfigs()

        for (cfg in configs) {
            val connNode = DefaultMutableTreeNode("${cfg.name} (${cfg.dialect.displayName})")
            val conn = DatabaseConnectionManager.getConnection(cfg.id)
                ?: runCatching { DatabaseConnectionManager.connect(cfg) }.getOrNull()

            if (conn != null) {
                val catalog = runCatching {
                    SchemaIntrospector.introspect(conn, cfg.id, cfg.name)
                }.getOrNull()

                if (catalog != null) {
                    for (schema in catalog.schemas) {
                        val schemaNode = DefaultMutableTreeNode("📁 ${schema.name}")
                        for (table in schema.tables) {
                            val tableNode = DefaultMutableTreeNode(table)
                            for (col in table.columns) {
                                val pkBadge = if (col.isPrimaryKey) " [PK]" else ""
                                val colNode = DefaultMutableTreeNode("• ${col.name} (${col.typeName})$pkBadge")
                                tableNode.add(colNode)
                            }
                            schemaNode.add(tableNode)
                        }
                        connNode.add(schemaNode)
                    }
                }
            }
            rootNode.add(connNode)
        }

        treeModel.reload()
        tree.expandRow(0)
    }
}
