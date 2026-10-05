package org.jormungandr.database.ui

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.treeStructure.Tree
import org.jormungandr.database.engine.DatabaseConnectionManager
import org.jormungandr.database.engine.DdlGenerator
import org.jormungandr.database.engine.SchemaIntrospector
import org.jormungandr.database.model.ConnectionConfig
import org.jormungandr.database.model.DatabaseDialect
import org.jormungandr.database.model.DialectCategory
import org.jormungandr.database.model.TableMetadata
import org.jormungandr.database.nosql.cassandra.CassandraEngine
import org.jormungandr.database.nosql.cassandra.CassandraStudioPanel
import org.jormungandr.database.nosql.kafka.KafkaEngine
import org.jormungandr.database.nosql.kafka.KafkaStudioPanel
import org.jormungandr.database.nosql.mongo.MongoEngine
import org.jormungandr.database.nosql.mongo.MongoStudioPanel
import org.jormungandr.database.nosql.redis.RedisEngine
import org.jormungandr.database.nosql.redis.RedisStudioPanel
import org.jormungandr.database.datalake.DataLakeEngine
import org.jormungandr.database.datalake.RemoteDataLakeStudioPanel
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

    // Specialized Studio Panels
    private val consolePanel = QueryConsolePanel(project)
    private val visualBuilderPanel = VisualQueryBuilderPanel(project) { sql ->
        consolePanel.setQueryText(sql)
        tabbedPane.selectedIndex = 0
    }
    private val mongoStudioPanel = MongoStudioPanel(project)
    private val redisStudioPanel = RedisStudioPanel(project)
    private val cassandraStudioPanel = CassandraStudioPanel(project)
    private val kafkaStudioPanel = KafkaStudioPanel(project)
    private val dataLakeStudioPanel = RemoteDataLakeStudioPanel(project) { sql ->
        consolePanel.setQueryText(sql)
        tabbedPane.selectedIndex = 0
    }
    private val tabbedPane = JBTabbedPane()

    private val schemaDiagramPanel = SchemaDiagramPanel(null) { table ->
        consolePanel.setQueryText("SELECT * FROM ${table.name} LIMIT 100;")
        tabbedPane.selectedIndex = 0
        consolePanel.executeCurrentQuery()
    }

    init {
        // Ensure default sample connections across all supported SQL and NoSQL engines are registered
        if (DatabaseConnectionManager.getAllConfigs().isEmpty()) {
            seedInitialConnections()
        }

        // Explorer Left Panel
        val explorerPanel = JPanel(BorderLayout()).apply {
            border = EmptyBorder(4, 4, 4, 4)
        }
        val explorerToolbar = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0))

        val addConnBtn = JButton("+ Add Connection").apply {
            isFocusable = false
            font = font.deriveFont(Font.BOLD, 11f)
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
                tabbedPane.selectedIndex = 0
            }
        }

        val addVisualSqlBtn = JButton("+ 🧩 Visual Builder").apply {
            isFocusable = false
            font = font.deriveFont(Font.PLAIN, 11f)
            toolTipText = "Open Visual No-Code / Low-Code SQL & Join Builder"
            addActionListener {
                tabbedPane.selectedIndex = 1
                visualBuilderPanel.refreshConnections()
            }
        }

        val addMongoBtn = JButton("+ 🍃 Mongo").apply {
            isFocusable = false
            font = font.deriveFont(Font.PLAIN, 11f)
            toolTipText = "Open MongoDB Document Studio"
            addActionListener {
                tabbedPane.selectedIndex = 2
                mongoStudioPanel.refreshMetadata()
            }
        }

        val addRedisBtn = JButton("+ ⚡ Redis").apply {
            isFocusable = false
            font = font.deriveFont(Font.PLAIN, 11f)
            toolTipText = "Open Redis Keyspace & Command Studio"
            addActionListener {
                tabbedPane.selectedIndex = 3
                redisStudioPanel.refreshKeys()
            }
        }

        val addCassandraBtn = JButton("+ 🪐 Cassandra").apply {
            isFocusable = false
            font = font.deriveFont(Font.PLAIN, 11f)
            toolTipText = "Open Apache Cassandra CQL Studio"
            addActionListener {
                tabbedPane.selectedIndex = 4
                cassandraStudioPanel.refreshKeyspaces()
            }
        }

        val addKafkaBtn = JButton("+ 📨 Kafka").apply {
            isFocusable = false
            font = font.deriveFont(Font.PLAIN, 11f)
            toolTipText = "Open Apache Kafka Stream Studio"
            addActionListener {
                tabbedPane.selectedIndex = 5
                kafkaStudioPanel.refreshTopics()
            }
        }

        val addDataLakeBtn = JButton("+ 🌊 Data Lake").apply {
            isFocusable = false
            font = font.deriveFont(Font.PLAIN, 11f)
            toolTipText = "Open Remote S3 / GCS Data Lake Explorer"
            addActionListener {
                tabbedPane.selectedIndex = 6
                dataLakeStudioPanel.refreshConnections()
            }
        }

        val refreshBtn = JButton("🔄").apply {
            isFocusable = false
            toolTipText = "Refresh All Schemas & Data Sources"
            addActionListener {
                refreshSchemaTree()
                consolePanel.refreshConnections()
                visualBuilderPanel.refreshConnections()
                mongoStudioPanel.refreshMetadata()
                redisStudioPanel.refreshKeys()
                cassandraStudioPanel.refreshKeyspaces()
                kafkaStudioPanel.refreshTopics()
                dataLakeStudioPanel.refreshConnections()
            }
        }

        explorerToolbar.add(addConnBtn)
        explorerToolbar.add(addDuckBtn)
        explorerToolbar.add(addVisualSqlBtn)
        explorerToolbar.add(addMongoBtn)
        explorerToolbar.add(addRedisBtn)
        explorerToolbar.add(addCassandraBtn)
        explorerToolbar.add(addKafkaBtn)
        explorerToolbar.add(addDataLakeBtn)
        explorerToolbar.add(refreshBtn)

        explorerPanel.add(explorerToolbar, BorderLayout.NORTH)
        explorerPanel.add(JBScrollPane(tree), BorderLayout.CENTER)

        // Right Tabs: SQL & PL/SQL, Visual SQL Builder, MongoDB, Redis, Cassandra, Kafka, Remote Data Lakes, Schema Diagram
        tabbedPane.addTab("💻 SQL & PL/SQL Console", consolePanel)
        tabbedPane.addTab("🧩 Visual SQL Builder", visualBuilderPanel)
        tabbedPane.addTab("🍃 MongoDB Studio", mongoStudioPanel)
        tabbedPane.addTab("⚡ Redis Studio", redisStudioPanel)
        tabbedPane.addTab("🪐 Cassandra CQL", cassandraStudioPanel)
        tabbedPane.addTab("📨 Kafka Streams", kafkaStudioPanel)
        tabbedPane.addTab("🌊 Remote Data Lakes", dataLakeStudioPanel)
        tabbedPane.addTab("🗺️ Schema Diagram", schemaDiagramPanel)

        // Main Horizontal Split
        val split = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, explorerPanel, tabbedPane).apply {
            resizeWeight = 0.28
            isContinuousLayout = true
            border = null
        }

        add(split, BorderLayout.CENTER)

        setupTreeInteractions()
        refreshSchemaTree()
    }

    private fun seedInitialConnections() {
        // 1. SQLite Sample
        val sampleConfig = ConnectionConfig(
            id = UUID.randomUUID().toString(),
            name = "SQLite In-Memory (Sample)",
            dialect = DatabaseDialect.SQLITE,
            databaseName = ":memory:"
        )
        val conn = DatabaseConnectionManager.connect(sampleConfig)
        runCatching {
            conn?.createStatement()?.use { st ->
                st.execute("CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT, email TEXT, active BOOLEAN);")
                st.execute("INSERT INTO users VALUES (1, 'Alice Smith', 'alice@jormungandr.org', 1);")
                st.execute("INSERT INTO users VALUES (2, 'Bob Jones', 'bob@jormungandr.org', 1);")
                st.execute("INSERT INTO users VALUES (3, 'Charlie Brown', 'charlie@jormungandr.org', 0);")
                st.execute("CREATE TABLE metrics (metric_id INTEGER PRIMARY KEY, name TEXT, value REAL);")
                st.execute("INSERT INTO metrics VALUES (101, 'accuracy', 0.942);")
                st.execute("INSERT INTO metrics VALUES (102, 'loss', 0.058);")
            }
        }

        // 2. DuckDB Analytics Sample
        val duckConfig = ConnectionConfig(
            id = UUID.randomUUID().toString(),
            name = "DuckDB In-Memory (Analytics)",
            dialect = DatabaseDialect.DUCKDB,
            databaseName = ":memory:"
        )
        runCatching {
            val duckConn = DatabaseConnectionManager.connect(duckConfig)
            duckConn?.createStatement()?.use { st ->
                st.execute("CREATE TABLE sales (id INTEGER, category VARCHAR, item VARCHAR, price DOUBLE, quantity INTEGER);")
                st.execute("INSERT INTO sales VALUES (1, 'Electronics', 'Laptop', 1299.99, 2), (2, 'Electronics', 'Headphones', 149.50, 5), (3, 'Furniture', 'Desk Chair', 249.00, 3), (4, 'Furniture', 'Standing Desk', 499.00, 1), (5, 'Electronics', '4K Monitor', 320.00, 4);")
            }
        }

        // 3. Oracle PL/SQL Sample
        val plsqlConfig = ConnectionConfig(
            id = UUID.randomUUID().toString(),
            name = "Oracle PL/SQL (Payroll)",
            dialect = DatabaseDialect.ORACLE_PLSQL,
            databaseName = "ORCL"
        )
        DatabaseConnectionManager.registerConfig(plsqlConfig)

        // 4. MongoDB Sample
        val mongoConfig = ConnectionConfig(
            id = UUID.randomUUID().toString(),
            name = "MongoDB (ecom_store)",
            dialect = DatabaseDialect.MONGODB,
            databaseName = "ecom_store"
        )
        DatabaseConnectionManager.registerConfig(mongoConfig)

        // 5. Redis Sample
        val redisConfig = ConnectionConfig(
            id = UUID.randomUUID().toString(),
            name = "Redis (In-Memory Cache)",
            dialect = DatabaseDialect.REDIS,
            databaseName = "0"
        )
        DatabaseConnectionManager.registerConfig(redisConfig)

        // 6. Cassandra Sample
        val cassandraConfig = ConnectionConfig(
            id = UUID.randomUUID().toString(),
            name = "Cassandra (ecommerce_ks)",
            dialect = DatabaseDialect.CASSANDRA,
            databaseName = "ecommerce_ks"
        )
        DatabaseConnectionManager.registerConfig(cassandraConfig)

        // 7. Kafka Sample
        val kafkaConfig = ConnectionConfig(
            id = UUID.randomUUID().toString(),
            name = "Kafka (Cluster Stream)",
            dialect = DatabaseDialect.KAFKA,
            databaseName = ""
        )
        DatabaseConnectionManager.registerConfig(kafkaConfig)

        // 8. Amazon S3 Data Lake Sample
        val s3Config = ConnectionConfig(
            id = UUID.randomUUID().toString(),
            name = "Amazon S3 (Lakehouse Analytics)",
            dialect = DatabaseDialect.S3_DATA_LAKE,
            databaseName = "analytics-data-lake"
        )
        DatabaseConnectionManager.registerConfig(s3Config)

        // 9. Google Cloud Storage Sample
        val gcsConfig = ConnectionConfig(
            id = UUID.randomUUID().toString(),
            name = "GCS (Genomics & Public Data)",
            dialect = DatabaseDialect.GCS_DATA_LAKE,
            databaseName = "genomics-public-data"
        )
        DatabaseConnectionManager.registerConfig(gcsConfig)
    }

    private fun setupTreeInteractions() {
        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val path = tree.getPathForLocation(e.x, e.y) ?: return
                val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
                val userObj = node.userObject

                // Context menu for SQL TableMetadata
                if (userObj is TableMetadata && SwingUtilities.isRightMouseButton(e)) {
                    val menu = JPopupMenu()
                    menu.add(JMenuItem("SELECT * FROM ${userObj.name} LIMIT 100").apply {
                        addActionListener {
                            consolePanel.setQueryText("SELECT * FROM ${userObj.name} LIMIT 100;")
                            tabbedPane.selectedIndex = 0
                            consolePanel.executeCurrentQuery()
                        }
                    })
                    menu.add(JMenuItem("SELECT COUNT(*) FROM ${userObj.name}").apply {
                        addActionListener {
                            consolePanel.setQueryText("SELECT COUNT(*) AS total_count FROM ${userObj.name};")
                            tabbedPane.selectedIndex = 0
                            consolePanel.executeCurrentQuery()
                        }
                    })
                    menu.add(JMenuItem("🧩 Open in Visual SQL Builder").apply {
                        addActionListener {
                            visualBuilderPanel.loadTable(userObj.name)
                            tabbedPane.selectedIndex = 1
                        }
                    })
                    menu.addSeparator()
                    menu.add(JMenuItem("Generate CREATE TABLE DDL").apply {
                        addActionListener {
                            val ddl = DdlGenerator.generateCreateTable(userObj)
                            consolePanel.setQueryText(ddl)
                            tabbedPane.selectedIndex = 0
                        }
                    })
                    menu.add(JMenuItem("Generate INSERT Template").apply {
                        addActionListener {
                            val template = DdlGenerator.generateInsertTemplate(userObj)
                            consolePanel.setQueryText(template)
                            tabbedPane.selectedIndex = 0
                        }
                    })
                    menu.show(tree, e.x, e.y)
                    return
                }

                // Interactive tab selection on double click or single click
                val str = userObj?.toString() ?: ""
                when {
                    str.contains("MongoDB") || str.startsWith("🍃") -> {
                        tabbedPane.selectedIndex = 2
                    }
                    str.contains("Redis") || str.startsWith("⚡") -> {
                        tabbedPane.selectedIndex = 3
                    }
                    str.contains("Cassandra") || str.startsWith("🪐") -> {
                        tabbedPane.selectedIndex = 4
                    }
                    str.contains("Kafka") || str.startsWith("📨") -> {
                        tabbedPane.selectedIndex = 5
                    }
                    str.contains("Data Lake") || str.startsWith("🌊") -> {
                        tabbedPane.selectedIndex = 6
                        dataLakeStudioPanel.refreshConnections()
                    }
                    userObj is TableMetadata -> {
                        if (e.clickCount == 2) {
                            consolePanel.setQueryText("SELECT * FROM ${userObj.name} LIMIT 100;")
                            tabbedPane.selectedIndex = 0
                            consolePanel.executeCurrentQuery()
                        }
                    }
                }
            }
        })
    }

    fun refreshSchemaTree() {
        rootNode.removeAllChildren()
        val configs = DatabaseConnectionManager.getAllConfigs()

        val relationalCategoryNode = DefaultMutableTreeNode("📁 Relational & Analytical SQL")
        val mongoCategoryNode = DefaultMutableTreeNode("🍃 Document Stores (MongoDB)")
        val redisCategoryNode = DefaultMutableTreeNode("⚡ In-Memory Key-Value (Redis)")
        val cassandraCategoryNode = DefaultMutableTreeNode("🪐 Wide-Column Stores (Cassandra)")
        val kafkaCategoryNode = DefaultMutableTreeNode("📨 Event Streaming Brokers (Kafka)")
        val dataLakeCategoryNode = DefaultMutableTreeNode("🌊 Remote Data Lakes (S3 / GCS / HTTP)")

        var primaryCatalog: org.jormungandr.database.model.CatalogMetadata? = null

        for (cfg in configs) {
            when (cfg.dialect.category) {
                DialectCategory.RELATIONAL -> {
                    val connNode = DefaultMutableTreeNode("${cfg.name} (${cfg.dialect.displayName})")
                    val conn = DatabaseConnectionManager.getConnection(cfg.id)
                        ?: runCatching { DatabaseConnectionManager.connect(cfg) }.getOrNull()

                    if (conn != null) {
                        val catalog = runCatching {
                            SchemaIntrospector.introspect(conn, cfg.id, cfg.name)
                        }.getOrNull()

                        if (catalog != null) {
                            if (primaryCatalog == null) primaryCatalog = catalog
                            for (schema in catalog.schemas) {
                                val schemaNode = DefaultMutableTreeNode("📁 ${schema.name}")
                                for (table in schema.tables) {
                                    val tableNode = DefaultMutableTreeNode(table)
                                    for (col in table.columns) {
                                        val pkBadge = if (col.isPrimaryKey) " [PK]" else ""
                                        tableNode.add(DefaultMutableTreeNode("• ${col.name} (${col.typeName})$pkBadge"))
                                    }
                                    schemaNode.add(tableNode)
                                }
                                connNode.add(schemaNode)
                            }
                        }
                    }
                    relationalCategoryNode.add(connNode)
                }

                DialectCategory.DOCUMENT_STORE -> {
                    val connNode = DefaultMutableTreeNode("🍃 ${cfg.name}")
                    val dbs = MongoEngine.listDatabases(cfg)
                    for (db in dbs) {
                        val dbNode = DefaultMutableTreeNode("📁 $db")
                        val collections = MongoEngine.listCollections(cfg, db)
                        for (coll in collections) {
                            val count = MongoEngine.countDocuments(cfg, db, coll)
                            dbNode.add(DefaultMutableTreeNode("📄 $coll ($count docs)"))
                        }
                        connNode.add(dbNode)
                    }
                    mongoCategoryNode.add(connNode)
                }

                DialectCategory.KEY_VALUE -> {
                    val connNode = DefaultMutableTreeNode("⚡ ${cfg.name}")
                    val keys = RedisEngine.scanKeys(cfg, "*")
                    val db0Node = DefaultMutableTreeNode("📦 db0 (${keys.size} keys)")
                    for (k in keys.take(20)) {
                        val ttlStr = if (k.ttlSeconds >= 0) "${k.ttlSeconds}s" else "persistent"
                        db0Node.add(DefaultMutableTreeNode("[${k.type.code}] ${k.key} ($ttlStr)"))
                    }
                    if (keys.size > 20) {
                        db0Node.add(DefaultMutableTreeNode("... and ${keys.size - 20} more keys"))
                    }
                    connNode.add(db0Node)
                    redisCategoryNode.add(connNode)
                }

                DialectCategory.WIDE_COLUMN -> {
                    val connNode = DefaultMutableTreeNode("🪐 ${cfg.name}")
                    val keyspaces = CassandraEngine.listKeyspaces(cfg)
                    for (ks in keyspaces) {
                        val ksNode = DefaultMutableTreeNode("📁 ${ks.name}")
                        for (tbl in ks.tables) {
                            val tblNode = DefaultMutableTreeNode("📊 ${tbl.name}")
                            for (c in tbl.columns) {
                                val badge = when {
                                    c.isPartitionKey -> " [PK]"
                                    c.isClusteringKey -> " [CK]"
                                    else -> ""
                                }
                                tblNode.add(DefaultMutableTreeNode("• ${c.name} (${c.typeName})$badge"))
                            }
                            ksNode.add(tblNode)
                        }
                        connNode.add(ksNode)
                    }
                    cassandraCategoryNode.add(connNode)
                }

                DialectCategory.EVENT_STREAMING -> {
                    val connNode = DefaultMutableTreeNode("📨 ${cfg.name}")
                    val topics = KafkaEngine.listTopics(cfg)
                    for (t in topics) {
                        connNode.add(DefaultMutableTreeNode("📡 ${t.name} [${t.partitionCount}P] (${t.messageCount} msgs)"))
                    }
                    kafkaCategoryNode.add(connNode)
                }

                DialectCategory.DATA_LAKE -> {
                    val connNode = DefaultMutableTreeNode("🌊 ${cfg.name}")
                    val objects = DataLakeEngine.listObjects(cfg, "")
                    for (obj in objects) {
                        connNode.add(DefaultMutableTreeNode("${if (obj.isPrefix) "📁" else "📄"} ${obj.name} (${obj.sizeFormatted})"))
                    }
                    dataLakeCategoryNode.add(connNode)
                }
            }
        }

        if (relationalCategoryNode.childCount > 0) rootNode.add(relationalCategoryNode)
        if (mongoCategoryNode.childCount > 0) rootNode.add(mongoCategoryNode)
        if (redisCategoryNode.childCount > 0) rootNode.add(redisCategoryNode)
        if (cassandraCategoryNode.childCount > 0) rootNode.add(cassandraCategoryNode)
        if (kafkaCategoryNode.childCount > 0) rootNode.add(kafkaCategoryNode)
        if (dataLakeCategoryNode.childCount > 0) rootNode.add(dataLakeCategoryNode)

        primaryCatalog?.let { schemaDiagramPanel.setCatalog(it) }

        treeModel.reload()
        tree.expandRow(0)
        tree.expandRow(1)
    }
}
