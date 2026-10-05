package org.jormungandr.database.nosql.mongo

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import org.jormungandr.dataframe.codegen.DataFrameCodeGenerator
import org.jormungandr.dataframe.io.DataFrameExporter
import org.jormungandr.dataframe.model.DataFrame
import org.jormungandr.dataframe.ui.DataFrameGridPanel
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.io.File
import javax.swing.*
import javax.swing.border.EmptyBorder

class MongoStudioPanel(private val project: Project? = null) : JPanel(BorderLayout()) {

    private val dbCombo = JComboBox<String>()
    private val collCombo = JComboBox<String>()
    private val filterField = JBTextField("{}", 22)
    private val projectionField = JBTextField("{}", 16)
    private val limitCombo = JComboBox(arrayOf(50, 100, 500, 1000))
    private val statusLabel = JBLabel("Ready")
    private val timerLabel = JBLabel("⏱ 0 ms")

    private val gridPanel = DataFrameGridPanel(DataFrame.empty("mongo_documents"))
    private val jsonTextArea = JBTextArea().apply {
        isEditable = false
        font = Font("Monospaced", Font.PLAIN, 12)
    }

    private var currentDocs: List<com.google.gson.JsonObject> = emptyList()

    init {
        val topPanel = JPanel(BorderLayout())

        // Toolbar 1: Selectors & Query inputs
        val toolbar1 = JPanel(FlowLayout(FlowLayout.LEFT, 6, 4))
        toolbar1.add(JBLabel("Database:"))
        toolbar1.add(dbCombo)
        toolbar1.add(JBLabel("Collection:"))
        toolbar1.add(collCombo)

        val refreshBtn = JButton("🔄").apply {
            isFocusable = false
            toolTipText = "Refresh databases and collections"
            addActionListener { refreshMetadata() }
        }
        toolbar1.add(refreshBtn)

        toolbar1.add(JBLabel("Filter (JSON):"))
        toolbar1.add(filterField)
        toolbar1.add(JBLabel("Projection:"))
        toolbar1.add(projectionField)
        toolbar1.add(JBLabel("Limit:"))
        toolbar1.add(limitCombo)

        // Toolbar 2: Action buttons
        val toolbar2 = JPanel(BorderLayout()).apply {
            border = EmptyBorder(2, 6, 4, 6)
        }
        val leftActions = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0))
        val rightActions = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0))

        val findBtn = JButton("▶ Run Find").apply {
            isFocusable = false
            font = font.deriveFont(Font.BOLD, 11f)
            addActionListener { executeFind() }
        }
        val aggBtn = JButton("⚡ Run Aggregate").apply {
            isFocusable = false
            font = font.deriveFont(Font.PLAIN, 11f)
            toolTipText = "Execute aggregation pipeline specified in Filter field as JSON Array [ {...} ]"
            addActionListener { executeAggregate() }
        }
        val countBtn = JButton("🔢 Count").apply {
            isFocusable = false
            font = font.deriveFont(Font.PLAIN, 11f)
            addActionListener { executeCount() }
        }
        val insertBtn = JButton("➕ Insert Doc").apply {
            isFocusable = false
            font = font.deriveFont(Font.PLAIN, 11f)
            addActionListener { showInsertDialog() }
        }
        val openDfBtn = JButton("📊 Open in DataFrame Studio").apply {
            isFocusable = false
            font = font.deriveFont(Font.BOLD, 11f)
            foreground = Color(30, 64, 175)
            toolTipText = "Stream documents directly to DataFrame Studio for pivot tables & charts"
            addActionListener { openInDataFrameStudio() }
        }
        val copyJsonBtn = JButton("📋 Copy JSON").apply {
            isFocusable = false
            font = font.deriveFont(Font.PLAIN, 11f)
            addActionListener { copyJsonToClipboard() }
        }

        leftActions.add(findBtn)
        leftActions.add(aggBtn)
        leftActions.add(countBtn)
        leftActions.add(insertBtn)
        leftActions.add(openDfBtn)
        leftActions.add(copyJsonBtn)

        rightActions.add(timerLabel)
        rightActions.add(statusLabel)

        toolbar2.add(leftActions, BorderLayout.WEST)
        toolbar2.add(rightActions, BorderLayout.EAST)

        topPanel.add(toolbar1, BorderLayout.NORTH)
        topPanel.add(toolbar2, BorderLayout.SOUTH)

        // Center Views: Tabular Grid vs JSON Documents
        val tabbedPane = JBTabbedPane()
        tabbedPane.addTab("📋 Tabular Grid", gridPanel)
        tabbedPane.addTab("📄 JSON Documents View", JBScrollPane(jsonTextArea))

        add(topPanel, BorderLayout.NORTH)
        add(tabbedPane, BorderLayout.CENTER)

        dbCombo.addActionListener {
            updateCollections()
        }
        collCombo.addActionListener {
            executeFind()
        }

        refreshMetadata()
    }

    fun refreshMetadata() {
        val dbs = MongoEngine.listDatabases()
        dbCombo.removeAllItems()
        for (db in dbs) dbCombo.addItem(db)
        if (dbs.contains("ecom_store")) {
            dbCombo.selectedItem = "ecom_store"
        }
        updateCollections()
    }

    private fun updateCollections() {
        val selectedDb = dbCombo.selectedItem as? String ?: return
        val collections = MongoEngine.listCollections(database = selectedDb)
        collCombo.removeAllItems()
        for (c in collections) collCombo.addItem(c)
        if (collections.isNotEmpty()) {
            collCombo.selectedIndex = 0
        }
    }

    fun executeFind() {
        val db = dbCombo.selectedItem as? String ?: return
        val coll = collCombo.selectedItem as? String ?: return
        val filter = filterField.text.trim().ifBlank { "{}" }
        val projection = projectionField.text.trim().ifBlank { "{}" }
        val limit = limitCombo.selectedItem as? Int ?: 100

        val start = System.currentTimeMillis()
        val docs = MongoEngine.find(database = db, collection = coll, filterJson = filter, projectionJson = projection, limit = limit)
        val elapsed = System.currentTimeMillis() - start

        currentDocs = docs
        timerLabel.text = "⏱ $elapsed ms"
        statusLabel.text = "✓ ${docs.size} documents found"
        statusLabel.foreground = Color(40, 160, 60)

        val df = MongoEngine.toDataFrame(docs, "${db}_$coll")
        gridPanel.dataFrame = df
        jsonTextArea.text = MongoEngine.formatPretty(docs)
        jsonTextArea.caretPosition = 0
    }

    fun executeAggregate() {
        val db = dbCombo.selectedItem as? String ?: return
        val coll = collCombo.selectedItem as? String ?: return
        val pipeline = filterField.text.trim().ifBlank { "[]" }

        val start = System.currentTimeMillis()
        val docs = MongoEngine.aggregate(database = db, collection = coll, pipelineJson = pipeline)
        val elapsed = System.currentTimeMillis() - start

        currentDocs = docs
        timerLabel.text = "⏱ $elapsed ms"
        statusLabel.text = "✓ ${docs.size} aggregated documents"
        statusLabel.foreground = Color(40, 160, 60)

        val df = MongoEngine.toDataFrame(docs, "${db}_${coll}_agg")
        gridPanel.dataFrame = df
        jsonTextArea.text = MongoEngine.formatPretty(docs)
        jsonTextArea.caretPosition = 0
    }

    fun executeCount() {
        val db = dbCombo.selectedItem as? String ?: return
        val coll = collCombo.selectedItem as? String ?: return
        val filter = filterField.text.trim().ifBlank { "{}" }

        val start = System.currentTimeMillis()
        val count = MongoEngine.countDocuments(database = db, collection = coll, filterJson = filter)
        val elapsed = System.currentTimeMillis() - start

        timerLabel.text = "⏱ $elapsed ms"
        statusLabel.text = "🔢 Document Count: $count"
        statusLabel.foreground = Color(30, 64, 175)
    }

    private fun showInsertDialog() {
        val db = dbCombo.selectedItem as? String ?: return
        val coll = collCombo.selectedItem as? String ?: return

        val sampleDoc = """{
  "name": "New Entity",
  "category": "General",
  "score": 95.5,
  "active": true
}"""
        val area = JBTextArea(sampleDoc, 8, 30).apply {
            font = Font("Monospaced", Font.PLAIN, 12)
        }
        val panel = JPanel(BorderLayout()).apply {
            border = EmptyBorder(8, 8, 8, 8)
            add(JBLabel("Insert Document into $db.$coll:"), BorderLayout.NORTH)
            add(JBScrollPane(area), BorderLayout.CENTER)
        }

        val result = JOptionPane.showConfirmDialog(
            this,
            panel,
            "Insert MongoDB Document",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE
        )

        if (result == JOptionPane.OK_OPTION) {
            runCatching {
                val newId = MongoEngine.insertOne(database = db, collection = coll, docJson = area.text.trim())
                statusLabel.text = "✓ Inserted document id: $newId"
                statusLabel.foreground = Color(40, 160, 60)
                executeFind()
            }.onFailure { err ->
                statusLabel.text = "✗ Insert error: ${err.message}"
                statusLabel.foreground = Color(200, 50, 50)
            }
        }
    }

    private fun copyJsonToClipboard() {
        val text = jsonTextArea.text
        if (text.isBlank()) return
        val sel = StringSelection(text)
        Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, sel)
        statusLabel.text = "✓ Copied JSON to clipboard"
        statusLabel.foreground = Color(40, 160, 60)
    }

    private fun openInDataFrameStudio() {
        val df = gridPanel.dataFrame
        if (df.rowCount == 0) {
            statusLabel.text = "⚠️ No documents to open in DataFrame Studio"
            statusLabel.foreground = Color(200, 140, 40)
            return
        }

        val p = project
        if (p == null) {
            statusLabel.text = "⚠️ Project context not available"
            return
        }

        runCatching {
            val csv = DataFrameExporter.toCsv(df)
            val tempFile = File.createTempFile("mongo_docs_", ".csv")
            tempFile.writeText(csv, Charsets.UTF_8)
            tempFile.deleteOnExit()

            val vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempFile)
            if (vFile != null) {
                FileEditorManager.getInstance(p).openFile(vFile, true)
                statusLabel.text = "✓ Opened ${df.rowCount} documents in DataFrame Studio"
                statusLabel.foreground = Color(40, 160, 60)
            }
        }.onFailure { e ->
            statusLabel.text = "✗ Could not open in DataFrame Studio: ${e.message}"
            statusLabel.foreground = Color(200, 50, 50)
        }
    }
}
