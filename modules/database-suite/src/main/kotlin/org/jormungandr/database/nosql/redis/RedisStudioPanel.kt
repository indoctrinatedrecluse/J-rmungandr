package org.jormungandr.database.nosql.redis

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import org.jormungandr.dataframe.io.DataFrameExporter
import org.jormungandr.dataframe.model.DataFrame
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.io.File
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel

class RedisStudioPanel(private val project: Project? = null) : JPanel(BorderLayout()) {

    private val patternField = JBTextField("*", 16)
    private val typeCombo = JComboBox(arrayOf("ALL", "STRING", "HASH", "LIST", "SET", "ZSET"))
    private val statusLabel = JBLabel("Ready")

    // Keys Table
    private val keysTableModel = object : DefaultTableModel(arrayOf("Type", "Key Name", "TTL (s)", "Size"), 0) {
        override fun isCellEditable(row: Int, column: Int): Boolean = false
    }
    private val keysTable = JBTable(keysTableModel)

    // Value Inspector
    private val keyHeaderLabel = JBLabel("Select a key to inspect its value").apply {
        font = font.deriveFont(Font.BOLD, 13f)
    }
    private val keyMetaLabel = JBLabel("")
    private val valueViewContainer = JPanel(BorderLayout())

    // Redis CLI Console
    private val cliOutputArea = JBTextArea(8, 40).apply {
        isEditable = false
        font = Font("Monospaced", Font.PLAIN, 12)
        background = Color(24, 26, 32)
        foreground = Color(220, 230, 240)
        margin = Insets(6, 8, 6, 8)
        text = "# Jörmungandr In-Memory / RESP Redis Console\n# Type Redis commands below (e.g. GET key, SET key val, HGETALL key, INFO, PING)\nredis 127.0.0.1:6379> PING\nPONG\n"
    }
    private val cliInputField = JBTextField().apply {
        font = Font("Monospaced", Font.PLAIN, 12)
    }

    private var currentKeys: List<RedisKeyMetadata> = emptyList()

    init {
        // Top Toolbar
        val topPanel = JPanel(BorderLayout()).apply {
            border = EmptyBorder(4, 6, 4, 6)
        }
        val leftTools = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0))
        val rightTools = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0))

        leftTools.add(JBLabel("Match Pattern:"))
        leftTools.add(patternField)
        leftTools.add(JBLabel("Type:"))
        leftTools.add(typeCombo)

        val refreshBtn = JButton("🔄 Refresh").apply {
            isFocusable = false
            addActionListener { refreshKeys() }
        }
        val setKeyBtn = JButton("➕ Set Key").apply {
            isFocusable = false
            addActionListener { showSetKeyDialog() }
        }
        val delKeyBtn = JButton("🗑️ Delete Key").apply {
            isFocusable = false
            addActionListener { deleteSelectedKey() }
        }
        val openDfBtn = JButton("📊 Open in DataFrame Studio").apply {
            isFocusable = false
            font = font.deriveFont(Font.BOLD, 11f)
            foreground = Color(30, 64, 175)
            toolTipText = "Open Redis keyspace metadata table in DataFrame Studio"
            addActionListener { openKeysInDataFrameStudio() }
        }

        leftTools.add(refreshBtn)
        leftTools.add(setKeyBtn)
        leftTools.add(delKeyBtn)
        leftTools.add(openDfBtn)

        rightTools.add(statusLabel)
        topPanel.add(leftTools, BorderLayout.WEST)
        topPanel.add(rightTools, BorderLayout.EAST)

        // Setup Keys Table
        keysTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        keysTable.columnModel.getColumn(0).maxWidth = 65
        keysTable.columnModel.getColumn(2).maxWidth = 85
        keysTable.columnModel.getColumn(3).maxWidth = 85
        keysTable.columnModel.getColumn(0).cellRenderer = object : DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(
                table: JTable?, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
            ): Component {
                val c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column) as JLabel
                c.horizontalAlignment = SwingConstants.CENTER
                val code = value?.toString() ?: ""
                when (code) {
                    "STR" -> c.foreground = Color(40, 160, 60)
                    "HASH" -> c.foreground = Color(30, 120, 220)
                    "LIST" -> c.foreground = Color(140, 60, 200)
                    "SET" -> c.foreground = Color(220, 120, 20)
                    "ZSET" -> c.foreground = Color(10, 180, 180)
                }
                return c
            }
        }

        keysTable.selectionModel.addListSelectionListener { e: javax.swing.event.ListSelectionEvent ->
            if (!e.valueIsAdjusting) {
                val selectedRow = keysTable.selectedRow
                if (selectedRow >= 0 && selectedRow < currentKeys.size) {
                    inspectKey(currentKeys[selectedRow])
                }
            }
        }

        val leftSplitPanel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createTitledBorder("🔑 Keyspace Browser")
            add(JBScrollPane(keysTable), BorderLayout.CENTER)
        }

        // Right Inspector & CLI Panel
        val rightSplitPanel = JPanel(BorderLayout())

        // Top of right: Value Inspector
        val inspectorPanel = JPanel(BorderLayout(0, 6)).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("🔍 Key Value Inspector"),
                EmptyBorder(4, 8, 4, 8)
            )
        }
        val headerBox = JPanel(BorderLayout(4, 2))
        headerBox.add(keyHeaderLabel, BorderLayout.NORTH)
        headerBox.add(keyMetaLabel, BorderLayout.SOUTH)
        inspectorPanel.add(headerBox, BorderLayout.NORTH)
        inspectorPanel.add(valueViewContainer, BorderLayout.CENTER)

        // Bottom of right: Redis CLI Console
        val cliPanel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createTitledBorder("⚡ Interactive Redis Command Console")
        }
        val cliInputBar = JPanel(BorderLayout(4, 0)).apply {
            border = EmptyBorder(4, 4, 4, 4)
        }
        cliInputBar.add(JBLabel("redis> "), BorderLayout.WEST)
        cliInputBar.add(cliInputField, BorderLayout.CENTER)

        val quickCmdPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0))
        val execBtn = JButton("Execute").apply {
            isFocusable = false
            addActionListener { executeCliCommand() }
        }
        val infoBtn = JButton("INFO").apply {
            isFocusable = false
            font = font.deriveFont(Font.PLAIN, 10f)
            addActionListener { runQuickCommand("INFO") }
        }
        val dbsizeBtn = JButton("DBSIZE").apply {
            isFocusable = false
            font = font.deriveFont(Font.PLAIN, 10f)
            addActionListener { runQuickCommand("DBSIZE") }
        }
        val keysBtn = JButton("KEYS *").apply {
            isFocusable = false
            font = font.deriveFont(Font.PLAIN, 10f)
            addActionListener { runQuickCommand("KEYS *") }
        }

        quickCmdPanel.add(execBtn)
        quickCmdPanel.add(infoBtn)
        quickCmdPanel.add(dbsizeBtn)
        quickCmdPanel.add(keysBtn)
        cliInputBar.add(quickCmdPanel, BorderLayout.EAST)

        cliInputField.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER) {
                    executeCliCommand()
                }
            }
        })

        cliPanel.add(JBScrollPane(cliOutputArea), BorderLayout.CENTER)
        cliPanel.add(cliInputBar, BorderLayout.SOUTH)

        // Vertical Split between Inspector and CLI
        val rightSplit = JSplitPane(JSplitPane.VERTICAL_SPLIT, inspectorPanel, cliPanel).apply {
            resizeWeight = 0.55
            isContinuousLayout = true
            border = null
        }

        val mainSplit = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftSplitPanel, rightSplit).apply {
            resizeWeight = 0.35
            isContinuousLayout = true
            border = null
        }

        add(topPanel, BorderLayout.NORTH)
        add(mainSplit, BorderLayout.CENTER)

        patternField.addActionListener { refreshKeys() }
        typeCombo.addActionListener { refreshKeys() }

        refreshKeys()
    }

    fun refreshKeys() {
        val pat = patternField.text.trim().ifBlank { "*" }
        val typeStr = typeCombo.selectedItem as? String ?: "ALL"
        val filterType = if (typeStr == "ALL") null else RedisKeyType.values().find { it.name == typeStr }

        currentKeys = RedisEngine.scanKeys(pattern = pat, typeFilter = filterType)
        keysTableModel.rowCount = 0

        for (k in currentKeys) {
            val ttlStr = if (k.ttlSeconds >= 0) "${k.ttlSeconds}s" else "Persistent"
            keysTableModel.addRow(arrayOf(k.type.code, k.key, ttlStr, "${k.sizeBytes} B"))
        }

        statusLabel.text = "✓ ${currentKeys.size} keys found"
        statusLabel.foreground = Color(40, 160, 60)

        if (currentKeys.isNotEmpty()) {
            keysTable.setRowSelectionInterval(0, 0)
        } else {
            keyHeaderLabel.text = "No keys match filter '$pat'"
            keyMetaLabel.text = ""
            valueViewContainer.removeAll()
            valueViewContainer.revalidate()
            valueViewContainer.repaint()
        }
    }

    private fun inspectKey(keyMeta: RedisKeyMetadata) {
        keyHeaderLabel.text = "Key: ${keyMeta.key}  [${keyMeta.type.displayName}]"
        val ttlStr = if (keyMeta.ttlSeconds >= 0) "${keyMeta.ttlSeconds} seconds" else "Persistent"
        keyMetaLabel.text = "TTL: $ttlStr  •  Size: ${keyMeta.sizeBytes} bytes"

        valueViewContainer.removeAll()
        val value = RedisEngine.getValue(key = keyMeta.key)

        when (value) {
            is RedisValue.StringValue -> {
                val txt = JBTextArea(value.value).apply {
                    isEditable = false
                    font = Font("Monospaced", Font.PLAIN, 12)
                    lineWrap = true
                }
                valueViewContainer.add(JBScrollPane(txt), BorderLayout.CENTER)
            }
            is RedisValue.HashValue -> {
                val model = DefaultTableModel(arrayOf("Field / Key", "Value"), 0)
                for ((f, v) in value.entries) {
                    model.addRow(arrayOf(f, v))
                }
                valueViewContainer.add(JBScrollPane(JBTable(model)), BorderLayout.CENTER)
            }
            is RedisValue.ListValue -> {
                val model = DefaultTableModel(arrayOf("Index", "Item"), 0)
                value.items.forEachIndexed { idx, it ->
                    model.addRow(arrayOf(idx, it))
                }
                valueViewContainer.add(JBScrollPane(JBTable(model)), BorderLayout.CENTER)
            }
            is RedisValue.SetValue -> {
                val model = DefaultTableModel(arrayOf("Member"), 0)
                for (m in value.members) {
                    model.addRow(arrayOf(m))
                }
                valueViewContainer.add(JBScrollPane(JBTable(model)), BorderLayout.CENTER)
            }
            is RedisValue.ZSetValue -> {
                val model = DefaultTableModel(arrayOf("Rank", "Score", "Member"), 0)
                value.scoredMembers.forEachIndexed { idx, (m, score) ->
                    model.addRow(arrayOf(idx + 1, score, m))
                }
                valueViewContainer.add(JBScrollPane(JBTable(model)), BorderLayout.CENTER)
            }
            null -> {
                valueViewContainer.add(JBLabel("(nil / key expired)"), BorderLayout.CENTER)
            }
        }

        valueViewContainer.revalidate()
        valueViewContainer.repaint()
    }

    private fun executeCliCommand() {
        val cmd = cliInputField.text.trim()
        if (cmd.isBlank()) return
        runQuickCommand(cmd)
        cliInputField.text = ""
    }

    private fun runQuickCommand(cmd: String) {
        val result = RedisEngine.executeCommand(commandLine = cmd)
        cliOutputArea.append("redis 127.0.0.1:6379> $cmd\n$result\n")
        cliOutputArea.caretPosition = cliOutputArea.document.length
        if (cmd.startsWith("SET", ignoreCase = true) || cmd.startsWith("DEL", ignoreCase = true) || cmd.startsWith("FLUSHDB", ignoreCase = true)) {
            refreshKeys()
        }
    }

    private fun showSetKeyDialog() {
        val keyField = JBTextField("my_key", 20)
        val valField = JBTextField("my_value", 20)
        val ttlField = JBTextField("-1", 8)

        val form = JPanel(GridLayout(3, 2, 4, 4)).apply {
            border = EmptyBorder(8, 8, 8, 8)
            add(JBLabel("Key Name:"))
            add(keyField)
            add(JBLabel("String Value:"))
            add(valField)
            add(JBLabel("TTL (seconds, -1=none):"))
            add(ttlField)
        }

        val res = JOptionPane.showConfirmDialog(this, form, "Set Redis String Key", JOptionPane.OK_CANCEL_OPTION)
        if (res == JOptionPane.OK_OPTION) {
            val ttl = ttlField.text.toLongOrNull()?.takeIf { it > 0 }
            RedisEngine.setKey(key = keyField.text.trim(), value = valField.text, ttl = ttl)
            refreshKeys()
        }
    }

    private fun deleteSelectedKey() {
        val selectedRow = keysTable.selectedRow
        if (selectedRow < 0 || selectedRow >= currentKeys.size) return
        val k = currentKeys[selectedRow].key
        val res = JOptionPane.showConfirmDialog(this, "Are you sure you want to delete key '$k'?", "Delete Redis Key", JOptionPane.YES_NO_OPTION)
        if (res == JOptionPane.YES_OPTION) {
            RedisEngine.delKey(key = k)
            refreshKeys()
        }
    }

    private fun openKeysInDataFrameStudio() {
        if (currentKeys.isEmpty()) {
            statusLabel.text = "⚠️ No keys to export"
            return
        }

        val p = project ?: return
        val df = RedisEngine.keysToDataFrame(currentKeys)

        runCatching {
            val csv = DataFrameExporter.toCsv(df)
            val tempFile = File.createTempFile("redis_keyspace_", ".csv")
            tempFile.writeText(csv, Charsets.UTF_8)
            tempFile.deleteOnExit()

            val vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempFile)
            if (vFile != null) {
                FileEditorManager.getInstance(p).openFile(vFile, true)
                statusLabel.text = "✓ Opened ${df.rowCount} keys in DataFrame Studio"
                statusLabel.foreground = Color(40, 160, 60)
            }
        }
    }
}
