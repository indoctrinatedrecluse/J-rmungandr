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
import java.awt.Font
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
    private val tabbedPane = com.intellij.ui.components.JBTabbedPane()
    private val schemaDiagramPanel = SchemaDiagramPanel(null) { table ->
        consolePanel.setQueryText("SELECT * FROM ${table.name} LIMIT 100;")
        tabbedPane.selectedIndex = 0
        consolePanel.executeCurrentQuery()
    }

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

            // Register DuckDB in-memory analytical sample
            val duckConfig = ConnectionConfig(
                id = UUID.randomUUID().toString(),
                name = "DuckDB In-Memory (Analytics)",
                dialect = DatabaseDialect.DUCKDB,
                databaseName = ":memory:"
            )
            runCatching {
                val duckConn = DatabaseConnectionManager.connect(duckConfig)
                duckConn.createStatement().use { st ->
                    st.execute("CREATE TABLE sales (id INTEGER, category VARCHAR, item VARCHAR, price DOUBLE, quantity INTEGER);")
                    st.execute("INSERT INTO sales VALUES (1, 'Electronics', 'Laptop', 1299.99, 2), (2, 'Electronics', 'Headphones', 149.50, 5), (3, 'Furniture', 'Desk Chair', 249.00, 3), (4, 'Furniture', 'Standing Desk', 499.00, 1), (5, 'Electronics', '4K Monitor', 320.00, 4);")
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
        val addDuckBtn = JButton("+ 🦆 DuckDB").apply {
            isFocusable = false
            font = font.deriveFont(Font.PLAIN, 11f)
            toolTipText = "Launch instant in-memory DuckDB analytical session"
            addActionListener {
                val id = UUID.randomUUID().toString()
                val duckCfg = ConnectionConfig(
                    id = id,
                    name = "DuckDB Session (${id.take(4)})",
                    dialect = DatabaseDialect.DUCKDB,
                    databaseName = ":memory:"
                )
                DatabaseConnectionManager.connect(duckCfg)
                refreshSchemaTree()
                consolePanel.refreshConnections()
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
        explorerToolbar.add(addDuckBtn)
        explorerToolbar.add(refreshBtn)

        explorerPanel.add(explorerToolbar, BorderLayout.NORTH)
        explorerPanel.add(JBScrollPane(tree), BorderLayout.CENTER)

        // Right Tabs: SQL Console and Visual Schema Diagram
        tabbedPane.addTab("💻 SQL Console", consolePanel)
        tabbedPane.addTab("🗺️ Schema Diagram", schemaDiagramPanel)

        // Split pane
        val split = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, explorerPanel, tabbedPane).apply {
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

        var primaryCatalog: org.jormungandr.database.model.CatalogMetadata? = null
        for (cfg in configs) {
            val connNode = DefaultMutableTreeNode("${cfg.name} (${cfg.dialect.displayName})")
            val conn = DatabaseConnectionManager.getConnection(cfg.id)
                ?: runCatching { DatabaseConnectionManager.connect(cfg) }.getOrNull()

            if (conn != null) {
                val catalog = runCatching {
                    SchemaIntrospector.introspect(conn, cfg.id, cfg.name)
                }.getOrNull()

                if (catalog != null) {
                    if (primaryCatalog == null) {
                        primaryCatalog = catalog
                    }
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

        primaryCatalog?.let { schemaDiagramPanel.setCatalog(it) }

        treeModel.reload()
        tree.expandRow(0)
    }
}
