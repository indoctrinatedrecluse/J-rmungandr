package org.jormungandr.database.nosql.kafka

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.table.JBTable
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import org.jormungandr.dataframe.io.DataFrameExporter
import org.jormungandr.dataframe.model.DataFrame
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.io.File
import java.time.Instant
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel

class KafkaStudioPanel(private val project: Project? = null) : JPanel(BorderLayout()) {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    // Left Topics Explorer
    private val topicsModel = object : DefaultTableModel(arrayOf("Topic", "Parts", "Msgs"), 0) {
        override fun isCellEditable(row: Int, column: Int): Boolean = false
    }
    private val topicsTable = JBTable(topicsModel)

    // Stream Inspector
    private val topicCombo = JComboBox<String>()
    private val partitionCombo = JComboBox(arrayOf("All", "0", "1", "2", "3"))
    private val limitCombo = JComboBox(arrayOf(25, 50, 100, 200))
    private val statusLabel = JBLabel("Ready")

    private val recordsModel = object : DefaultTableModel(arrayOf("Part", "Offset", "Timestamp", "Key", "Payload"), 0) {
        override fun isCellEditable(row: Int, column: Int): Boolean = false
    }
    private val recordsTable = JBTable(recordsModel)
    private val payloadViewer = JBTextArea(6, 40).apply {
        isEditable = false
        font = Font("Monospaced", Font.PLAIN, 12)
    }

    // Produce Form
    private val produceTopicCombo = JComboBox<String>()
    private val producePartCombo = JComboBox(arrayOf("0", "1", "2", "3"))
    private val produceKeyField = JBTextField("user_1001", 16)
    private val producePayloadArea = JBTextArea(8, 40).apply {
        font = Font("Monospaced", Font.PLAIN, 12)
        text = """{
  "event": "item_purchased",
  "user_id": 1001,
  "sku": "TECH-LAPTOP-16",
  "amount": 1899.00,
  "currency": "USD"
}"""
    }

    // Consumer Groups Table
    private val groupsModel = object : DefaultTableModel(arrayOf("Group ID", "Topic", "Part", "Current", "End", "Lag"), 0) {
        override fun isCellEditable(row: Int, column: Int): Boolean = false
    }
    private val groupsTable = JBTable(groupsModel)

    private var currentRecords: List<KafkaRecord> = emptyList()

    init {
        // Left Topics Panel
        val leftPanel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createTitledBorder("📨 Kafka Topics")
        }
        val leftToolbar = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2))
        val refreshTopicsBtn = JButton("🔄").apply {
            isFocusable = false
            addActionListener { refreshTopics() }
        }
        val newTopicBtn = JButton("+ Topic").apply {
            isFocusable = false
            font = font.deriveFont(Font.PLAIN, 11f)
            addActionListener { showCreateTopicDialog() }
        }
        leftToolbar.add(refreshTopicsBtn)
        leftToolbar.add(newTopicBtn)
        leftPanel.add(leftToolbar, BorderLayout.NORTH)
        leftPanel.add(JBScrollPane(topicsTable), BorderLayout.CENTER)

        topicsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        topicsTable.columnModel.getColumn(1).maxWidth = 50
        topicsTable.columnModel.getColumn(2).maxWidth = 60
        topicsTable.selectionModel.addListSelectionListener { e: javax.swing.event.ListSelectionEvent ->
            if (!e.valueIsAdjusting) {
                val row = topicsTable.selectedRow
                if (row >= 0) {
                    val topicName = topicsModel.getValueAt(row, 0) as String
                    topicCombo.selectedItem = topicName
                    produceTopicCombo.selectedItem = topicName
                    pollRecords(fromBeginning = false)
                }
            }
        }

        // Right Tabs
        val rightTabbedPane = JBTabbedPane()

        // Tab 1: Event Stream Inspector
        val inspectorPanel = JPanel(BorderLayout())
        val inspectorToolbar = JPanel(BorderLayout()).apply {
            border = EmptyBorder(4, 6, 4, 6)
        }
        val iLeft = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0))
        val iRight = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0))

        iLeft.add(JBLabel("Topic:"))
        iLeft.add(topicCombo)
        iLeft.add(JBLabel("Partition:"))
        iLeft.add(partitionCombo)
        iLeft.add(JBLabel("Limit:"))
        iLeft.add(limitCombo)

        val pollBtn = JButton("🔄 Poll New").apply {
            isFocusable = false
            font = font.deriveFont(Font.BOLD, 11f)
            addActionListener { pollRecords(fromBeginning = false) }
        }
        val rewindBtn = JButton("⏮ From Start").apply {
            isFocusable = false
            font = font.deriveFont(Font.PLAIN, 11f)
            addActionListener { pollRecords(fromBeginning = true) }
        }
        val openDfBtn = JButton("📊 Open in DataFrame Studio").apply {
            isFocusable = false
            font = font.deriveFont(Font.BOLD, 11f)
            foreground = Color(30, 64, 175)
            addActionListener { openInDataFrameStudio() }
        }

        iLeft.add(pollBtn)
        iLeft.add(rewindBtn)
        iLeft.add(openDfBtn)
        iRight.add(statusLabel)

        inspectorToolbar.add(iLeft, BorderLayout.WEST)
        inspectorToolbar.add(iRight, BorderLayout.EAST)

        recordsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        recordsTable.columnModel.getColumn(0).maxWidth = 50
        recordsTable.columnModel.getColumn(1).maxWidth = 65
        recordsTable.columnModel.getColumn(2).maxWidth = 160
        recordsTable.columnModel.getColumn(3).maxWidth = 120
        recordsTable.selectionModel.addListSelectionListener { e: javax.swing.event.ListSelectionEvent ->
            if (!e.valueIsAdjusting) {
                val row = recordsTable.selectedRow
                if (row >= 0 && row < currentRecords.size) {
                    val r = currentRecords[row]
                    payloadViewer.text = formatPayload(r.value)
                    payloadViewer.caretPosition = 0
                }
            }
        }

        val payloadPanel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("📄 Selected Message Payload Inspector"),
                EmptyBorder(2, 6, 4, 6)
            )
        }
        val copyPayloadBtn = JButton("📋 Copy Payload").apply {
            isFocusable = false
            addActionListener {
                val sel = StringSelection(payloadViewer.text)
                Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, sel)
            }
        }
        val pToolbar = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0))
        pToolbar.add(copyPayloadBtn)
        payloadPanel.add(pToolbar, BorderLayout.NORTH)
        payloadPanel.add(JBScrollPane(payloadViewer), BorderLayout.CENTER)

        val streamSplit = JSplitPane(JSplitPane.VERTICAL_SPLIT, JBScrollPane(recordsTable), payloadPanel).apply {
            resizeWeight = 0.65
            isContinuousLayout = true
            border = null
        }

        inspectorPanel.add(inspectorToolbar, BorderLayout.NORTH)
        inspectorPanel.add(streamSplit, BorderLayout.CENTER)
        rightTabbedPane.addTab("📡 Event Stream Inspector", inspectorPanel)

        // Tab 2: Produce Event
        val producePanel = JPanel(BorderLayout()).apply {
            border = EmptyBorder(12, 16, 12, 16)
        }
        val produceForm = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            insets = Insets(4, 4, 4, 4)
            anchor = GridBagConstraints.WEST
            fill = GridBagConstraints.HORIZONTAL
        }

        fun addFormRow(label: String, comp: JComponent, row: Int) {
            gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0
            produceForm.add(JBLabel(label), gbc)
            gbc.gridx = 1; gbc.gridy = row; gbc.weightx = 1.0
            produceForm.add(comp, gbc)
        }

        addFormRow("Target Topic:", produceTopicCombo, 0)
        addFormRow("Partition:", producePartCombo, 1)
        addFormRow("Message Key:", produceKeyField, 2)
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0.0
        produceForm.add(JBLabel("Message Payload:"), gbc)
        gbc.gridx = 1; gbc.gridy = 3; gbc.weightx = 1.0; gbc.weighty = 1.0
        gbc.fill = GridBagConstraints.BOTH
        produceForm.add(JBScrollPane(producePayloadArea), gbc)

        val sendEventBtn = JButton("🚀 Produce Event to Topic").apply {
            font = font.deriveFont(Font.BOLD, 12f)
            addActionListener {
                val topic = produceTopicCombo.selectedItem as? String ?: return@addActionListener
                val part = producePartCombo.selectedIndex.coerceAtLeast(0)
                val key = produceKeyField.text.trim().ifBlank { null }
                val payload = producePayloadArea.text.trim()

                val rec = KafkaEngine.produce(topic, part, key, payload)
                JOptionPane.showMessageDialog(this@KafkaStudioPanel, "Event sent successfully!\nTopic: ${rec.topic}\nPartition: ${rec.partition}\nOffset: ${rec.offset}")
                refreshTopics()
                pollRecords(fromBeginning = false)
            }
        }
        val produceBottom = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 4))
        produceBottom.add(sendEventBtn)

        producePanel.add(produceForm, BorderLayout.CENTER)
        producePanel.add(produceBottom, BorderLayout.SOUTH)
        rightTabbedPane.addTab("🚀 Produce Event", producePanel)

        // Tab 3: Consumer Groups & Lag
        val groupsPanel = JPanel(BorderLayout()).apply {
            border = EmptyBorder(6, 6, 6, 6)
        }
        val groupsToolbar = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0))
        val refreshGroupsBtn = JButton("🔄 Refresh Consumer Groups").apply {
            isFocusable = false
            addActionListener { refreshConsumerGroups() }
        }
        groupsToolbar.add(refreshGroupsBtn)
        groupsPanel.add(groupsToolbar, BorderLayout.NORTH)
        groupsPanel.add(JBScrollPane(groupsTable), BorderLayout.CENTER)

        groupsTable.columnModel.getColumn(5).cellRenderer = object : DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(
                table: JTable?, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
            ): Component {
                val c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column) as JLabel
                c.horizontalAlignment = SwingConstants.CENTER
                val lag = (value as? Long) ?: (value?.toString()?.toLongOrNull() ?: 0L)
                when {
                    lag == 0L -> {
                        c.foreground = Color(40, 160, 60)
                        c.text = "0 (Healthy)"
                    }
                    lag <= 5L -> {
                        c.foreground = Color(200, 140, 30)
                        c.text = "$lag (Moderate)"
                    }
                    else -> {
                        c.foreground = Color(200, 40, 40)
                        c.text = "$lag (High Lag)"
                    }
                }
                return c
            }
        }
        rightTabbedPane.addTab("👥 Consumer Groups & Lag", groupsPanel)

        // Main Horizontal Split
        val mainSplit = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightTabbedPane).apply {
            resizeWeight = 0.25
            isContinuousLayout = true
            border = null
        }
        add(mainSplit, BorderLayout.CENTER)

        topicCombo.addActionListener { pollRecords(fromBeginning = false) }
        partitionCombo.addActionListener { pollRecords(fromBeginning = false) }

        refreshTopics()
        refreshConsumerGroups()
    }

    fun refreshTopics() {
        val topics = KafkaEngine.listTopics()
        topicsModel.rowCount = 0
        topicCombo.removeAllItems()
        produceTopicCombo.removeAllItems()

        for (t in topics) {
            topicsModel.addRow(arrayOf(t.name, "${t.partitionCount}P", t.messageCount))
            topicCombo.addItem(t.name)
            produceTopicCombo.addItem(t.name)
        }

        if (topics.isNotEmpty()) {
            topicsTable.setRowSelectionInterval(0, 0)
        }
    }

    private fun pollRecords(fromBeginning: Boolean) {
        val topic = topicCombo.selectedItem as? String ?: return
        val partStr = partitionCombo.selectedItem as? String ?: "All"
        val partition = if (partStr == "All") -1 else partStr.toIntOrNull() ?: -1
        val limit = limitCombo.selectedItem as? Int ?: 50

        val records = KafkaEngine.consume(topic, partition, limit, fromBeginning)
        currentRecords = records
        recordsModel.rowCount = 0

        for (r in records) {
            val tsStr = Instant.ofEpochMilli(r.timestamp).toString().substring(11, 19)
            val preview = if (r.value.length > 50) r.value.take(47) + "..." else r.value
            recordsModel.addRow(arrayOf(r.partition, r.offset, tsStr, r.key ?: "null", preview))
        }

        statusLabel.text = "✓ Polled ${records.size} records"
        statusLabel.foreground = Color(40, 160, 60)

        if (records.isNotEmpty()) {
            recordsTable.setRowSelectionInterval(records.size - 1, records.size - 1)
        } else {
            payloadViewer.text = ""
        }
    }

    private fun refreshConsumerGroups() {
        val groups = KafkaEngine.listConsumerGroups()
        groupsModel.rowCount = 0
        for (g in groups) {
            groupsModel.addRow(arrayOf(g.groupId, g.topic, g.partition, g.currentOffset, g.logEndOffset, g.lag))
        }
    }

    private fun formatPayload(raw: String): String {
        return runCatching {
            val el = JsonParser.parseString(raw)
            gson.toJson(el)
        }.getOrDefault(raw)
    }

    private fun showCreateTopicDialog() {
        val nameField = JBTextField("new-topic-stream", 16)
        val partField = JBTextField("3", 6)
        val panel = JPanel(GridLayout(2, 2, 4, 4))
        panel.add(JBLabel("Topic Name:"))
        panel.add(nameField)
        panel.add(JBLabel("Partition Count:"))
        panel.add(partField)

        val res = JOptionPane.showConfirmDialog(this, panel, "Create Kafka Topic", JOptionPane.OK_CANCEL_OPTION)
        if (res == JOptionPane.OK_OPTION) {
            val name = nameField.text.trim()
            val parts = partField.text.trim().toIntOrNull() ?: 1
            if (name.isNotBlank()) {
                KafkaEngine.createTopic(name, parts)
                refreshTopics()
            }
        }
    }

    private fun openInDataFrameStudio() {
        if (currentRecords.isEmpty()) return
        val p = project ?: return
        val df = KafkaEngine.recordsToDataFrame(currentRecords, "kafka_${topicCombo.selectedItem ?: "stream"}")

        runCatching {
            val csv = DataFrameExporter.toCsv(df)
            val tempFile = File.createTempFile("kafka_records_", ".csv")
            tempFile.writeText(csv, Charsets.UTF_8)
            tempFile.deleteOnExit()

            val vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempFile)
            if (vFile != null) {
                FileEditorManager.getInstance(p).openFile(vFile, true)
                statusLabel.text = "✓ Opened ${df.rowCount} records in DataFrame Studio"
                statusLabel.foreground = Color(40, 160, 60)
            }
        }
    }
}
