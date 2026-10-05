/*
 * Copyright (c) 2025-2026 indoctrinatedrecluse
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jormungandr.database.ui

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextArea
import org.jormungandr.database.engine.*
import org.jormungandr.database.model.ConnectionConfig
import org.jormungandr.database.model.DatabaseDialect
import org.jormungandr.database.model.TableMetadata
import org.jormungandr.dataframe.model.DataFrame
import org.jormungandr.dataframe.ui.DataFrameGridPanel
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.awt.event.ItemEvent
import javax.swing.*
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder
import javax.swing.border.TitledBorder
import javax.swing.table.DefaultTableModel

/**
 * Visual No-Code / Low-Code SQL & Join Builder Panel.
 * Enables intuitive drag-and-click relational querying, multi-table joins,
 * aggregations, where/having criteria, and instant multi-dialect execution.
 */
class VisualQueryBuilderPanel(
    private val project: Project? = null,
    private val onSendToConsole: ((String) -> Unit)? = null
) : JPanel(BorderLayout()) {

    private val queryModel = VisualQueryModel()
    private var availableTables: List<TableMetadata> = emptyList()

    // Header Controls
    private val connectionCombo = JComboBox<String>()
    private val primaryTableCombo = JComboBox<String>()
    private val distinctCheck = JCheckBox("DISTINCT").apply { isOpaque = false }
    private val limitCombo = JComboBox(arrayOf(25, 50, 100, 500, 1000, 5000)).apply { selectedItem = 100 }
    private val offsetSpinner = JSpinner(SpinnerNumberModel(0, 0, 1_000_000, 10))

    // Tables & Joins UI
    private val joinsContainer = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
    }

    // Columns & Aggregations Table
    private val columnsTableModel = object : DefaultTableModel(
        arrayOf("Include", "Table", "Column", "Alias (AS)", "Aggregate", "Sort"), 0
    ) {
        override fun getColumnClass(columnIndex: Int): Class<*> {
            return when (columnIndex) {
                0 -> java.lang.Boolean::class.java
                else -> java.lang.String::class.java
            }
        }

        override fun isCellEditable(row: Int, column: Int): Boolean {
            return column != 1 && column != 2 // Table and Column name fixed, rest editable
        }
    }
    private val columnsTable = JTable(columnsTableModel)

    // Filters UI
    private val filtersContainer = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
    }

    // Preview and Execution Tabs
    private val sqlPreviewArea = JBTextArea().apply {
        font = Font("Monospaced", Font.PLAIN, 13)
        isEditable = false
        margin = Insets(6, 6, 6, 6)
    }
    private val pandasPreviewArea = JBTextArea().apply {
        font = Font("Monospaced", Font.PLAIN, 13)
        isEditable = false
        margin = Insets(6, 6, 6, 6)
    }
    private val duckDbPreviewArea = JBTextArea().apply {
        font = Font("Monospaced", Font.PLAIN, 13)
        isEditable = false
        margin = Insets(6, 6, 6, 6)
    }

    private val gridPanel = DataFrameGridPanel(DataFrame.empty("Query Results"))
    private val statusLabel = JBLabel("Ready").apply {
        foreground = Color(110, 110, 110)
    }

    init {
        val mainSplit = JSplitPane(JSplitPane.VERTICAL_SPLIT)
        mainSplit.resizeWeight = 0.45
        mainSplit.isContinuousLayout = true
        mainSplit.border = null

        // 1. Top Builder Section
        val topBuilderPanel = JPanel(BorderLayout())
        topBuilderPanel.add(createHeaderToolbar(), BorderLayout.NORTH)
        topBuilderPanel.add(createConfigTabs(), BorderLayout.CENTER)

        // 2. Bottom Results & Code Tabs Section
        val bottomTabs = JBTabbedPane()
        bottomTabs.addTab("📊 Live Results Grid", gridPanel)
        bottomTabs.addTab("📜 Generated SQL", JBScrollPane(sqlPreviewArea))
        bottomTabs.addTab("🐍 Pandas Code", JBScrollPane(pandasPreviewArea))
        bottomTabs.addTab("🦆 DuckDB Script", JBScrollPane(duckDbPreviewArea))

        mainSplit.topComponent = topBuilderPanel
        mainSplit.bottomComponent = bottomTabs

        add(mainSplit, BorderLayout.CENTER)
        add(createStatusBar(), BorderLayout.SOUTH)

        refreshConnections()
        updateQueryPreview()
    }

    private fun createHeaderToolbar(): JPanel {
        val panel = JPanel(BorderLayout()).apply {
            border = EmptyBorder(6, 8, 6, 8)
        }
        val left = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0))
        val right = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0))

        val runBtn = JButton("▶ Run Query").apply {
            font = font.deriveFont(Font.BOLD, 11f)
            foreground = Color(20, 120, 20)
            isFocusable = false
            addActionListener { executeVisualQuery() }
        }

        val copySqlBtn = JButton("📋 Copy SQL").apply {
            isFocusable = false
            font = font.deriveFont(Font.PLAIN, 11f)
            addActionListener {
                val sql = sqlPreviewArea.text
                Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(sql), null)
                statusLabel.text = "Copied SQL to clipboard"
            }
        }

        val sendConsoleBtn = JButton("💻 Open in Console").apply {
            isFocusable = false
            font = font.deriveFont(Font.PLAIN, 11f)
            toolTipText = "Open generated SQL in the SQL Console"
            addActionListener {
                val sql = sqlPreviewArea.text
                onSendToConsole?.invoke(sql)
            }
        }

        val presetsBtn = JButton("✨ Presets ▾").apply {
            isFocusable = false
            font = font.deriveFont(Font.PLAIN, 11f)
            addActionListener { showPresetsMenu(this) }
        }

        val resetBtn = JButton("🧹 Reset").apply {
            isFocusable = false
            font = font.deriveFont(Font.PLAIN, 11f)
            addActionListener { resetBuilder() }
        }

        connectionCombo.addActionListener {
            onConnectionChanged()
        }

        primaryTableCombo.addActionListener {
            onPrimaryTableChanged()
        }

        limitCombo.addActionListener {
            queryModel.limit = (limitCombo.selectedItem as? Int) ?: 100
            updateQueryPreview()
        }

        offsetSpinner.addChangeListener {
            queryModel.offset = (offsetSpinner.value as? Number)?.toInt() ?: 0
            updateQueryPreview()
        }

        distinctCheck.addItemListener {
            queryModel.isDistinct = distinctCheck.isSelected
            updateQueryPreview()
        }

        left.add(JBLabel("Connection:"))
        left.add(connectionCombo)
        left.add(JBLabel("Table:"))
        left.add(primaryTableCombo)
        left.add(distinctCheck)
        left.add(JBLabel("Limit:"))
        left.add(limitCombo)
        left.add(JBLabel("Offset:"))
        left.add(offsetSpinner)

        right.add(runBtn)
        right.add(copySqlBtn)
        right.add(sendConsoleBtn)
        right.add(presetsBtn)
        right.add(resetBtn)

        panel.add(left, BorderLayout.WEST)
        panel.add(right, BorderLayout.EAST)
        return panel
    }

    private fun createConfigTabs(): JComponent {
        val tabbedPane = JBTabbedPane()

        // Tab 1: Joins & Relational Links
        val joinsPanel = JPanel(BorderLayout()).apply {
            border = EmptyBorder(6, 6, 6, 6)
        }
        val joinsTop = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0))
        val addJoinBtn = JButton("➕ Add Join Table").apply {
            isFocusable = false
            font = font.deriveFont(Font.BOLD, 11f)
            addActionListener { addJoinRow() }
        }
        val autoFkBtn = JButton("🔗 Auto-Detect Foreign Keys").apply {
            isFocusable = false
            font = font.deriveFont(Font.PLAIN, 11f)
            toolTipText = "Inspect schema relationships and suggest joins automatically"
            addActionListener { autoDetectForeignKeys() }
        }
        joinsTop.add(addJoinBtn)
        joinsTop.add(autoFkBtn)

        joinsPanel.add(joinsTop, BorderLayout.NORTH)
        joinsPanel.add(JBScrollPane(joinsContainer), BorderLayout.CENTER)
        tabbedPane.addTab("🔗 Tables & Joins", joinsPanel)

        // Tab 2: Columns & Aggregations
        val columnsPanel = JPanel(BorderLayout()).apply {
            border = EmptyBorder(6, 6, 6, 6)
        }
        val columnsTop = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0))
        val selectAllColsBtn = JButton("Select All").apply {
            isFocusable = false
            font = font.deriveFont(Font.PLAIN, 11f)
            addActionListener { toggleAllColumns(true) }
        }
        val deselectAllColsBtn = JButton("Deselect All").apply {
            isFocusable = false
            font = font.deriveFont(Font.PLAIN, 11f)
            addActionListener { toggleAllColumns(false) }
        }
        columnsTop.add(selectAllColsBtn)
        columnsTop.add(deselectAllColsBtn)

        // Setup combo editors for aggregates and sorting
        setupColumnsTable()

        columnsPanel.add(columnsTop, BorderLayout.NORTH)
        columnsPanel.add(JBScrollPane(columnsTable), BorderLayout.CENTER)
        tabbedPane.addTab("📋 Columns & Aggregates", columnsPanel)

        // Tab 3: Filters (WHERE / HAVING)
        val filtersPanel = JPanel(BorderLayout()).apply {
            border = EmptyBorder(6, 6, 6, 6)
        }
        val filtersTop = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0))
        val addWhereBtn = JButton("➕ Add WHERE Condition").apply {
            isFocusable = false
            font = font.deriveFont(Font.BOLD, 11f)
            addActionListener { addFilterRow(isHaving = false) }
        }
        val addHavingBtn = JButton("➕ Add HAVING Condition").apply {
            isFocusable = false
            font = font.deriveFont(Font.PLAIN, 11f)
            addActionListener { addFilterRow(isHaving = true) }
        }
        filtersTop.add(addWhereBtn)
        filtersTop.add(addHavingBtn)

        filtersPanel.add(filtersTop, BorderLayout.NORTH)
        filtersPanel.add(JBScrollPane(filtersContainer), BorderLayout.CENTER)
        tabbedPane.addTab("🎯 Filters (WHERE / HAVING)", filtersPanel)

        return tabbedPane
    }

    private fun createStatusBar(): JPanel {
        val bar = JPanel(BorderLayout()).apply {
            border = EmptyBorder(3, 8, 3, 8)
        }
        bar.add(statusLabel, BorderLayout.WEST)
        return bar
    }

    private fun setupColumnsTable() {
        val aggCombo = JComboBox(arrayOf("None", "COUNT", "COUNT(DISTINCT)", "SUM", "AVG", "MIN", "MAX"))
        val sortCombo = JComboBox(arrayOf("None", "ASC", "DESC"))

        columnsTable.columnModel.getColumn(4).cellEditor = DefaultCellEditor(aggCombo)
        columnsTable.columnModel.getColumn(5).cellEditor = DefaultCellEditor(sortCombo)

        columnsTableModel.addTableModelListener { e ->
            if (e.firstRow >= 0 && e.firstRow < columnsTableModel.rowCount) {
                syncColumnsFromTable()
                updateQueryPreview()
            }
        }
    }

    fun refreshConnections() {
        val prevSelected = connectionCombo.selectedItem as? String
        connectionCombo.removeAllItems()
        val configs = DatabaseConnectionManager.getAllConfigs()
        for (cfg in configs) {
            connectionCombo.addItem(cfg.name)
        }
        if (prevSelected != null) {
            connectionCombo.selectedItem = prevSelected
        } else if (configs.isNotEmpty()) {
            connectionCombo.selectedIndex = 0
        }
        onConnectionChanged()
    }

    private fun onConnectionChanged() {
        val config = getActiveConfig() ?: return
        val conn = DatabaseConnectionManager.getConnection(config.id)
            ?: runCatching { DatabaseConnectionManager.connect(config) }.getOrNull()

        if (conn != null && config.dialect.isRelational) {
            runCatching {
                val catalog = SchemaIntrospector.introspect(conn, config.id, config.name)
                availableTables = catalog.schemas.flatMap { it.tables }
            }.onFailure {
                availableTables = emptyList()
            }
        } else {
            availableTables = emptyList()
        }

        primaryTableCombo.removeAllItems()
        for (table in availableTables) {
            primaryTableCombo.addItem(table.name)
        }
        if (availableTables.isNotEmpty()) {
            primaryTableCombo.selectedIndex = 0
        } else {
            queryModel.primaryTable = null
            rebuildColumnsTable()
            updateQueryPreview()
        }
    }

    private fun onPrimaryTableChanged() {
        val tableName = primaryTableCombo.selectedItem as? String ?: return
        val tableMeta = availableTables.find { it.name == tableName } ?: return

        val alias = tableName.take(3).lowercase()
        queryModel.primaryTable = QueryTable(tableName, alias, tableMeta.schemaName)

        rebuildColumnsTable()
        updateQueryPreview()
    }

    /**
     * Loads a table directly as primary (e.g., from schema explorer right-click).
     */
    fun loadTable(tableName: String) {
        val idx = (0 until primaryTableCombo.itemCount).firstOrNull {
            primaryTableCombo.getItemAt(it) == tableName
        }
        if (idx != null) {
            primaryTableCombo.selectedIndex = idx
        } else {
            primaryTableCombo.addItem(tableName)
            primaryTableCombo.selectedItem = tableName
        }
    }

    private fun rebuildColumnsTable() {
        while (columnsTableModel.rowCount > 0) {
            columnsTableModel.removeRow(0)
        }
        queryModel.columns.clear()

        // 1. Primary table columns
        val primary = queryModel.primaryTable
        if (primary != null) {
            val meta = availableTables.find { it.name == primary.tableName }
            meta?.columns?.forEach { col ->
                columnsTableModel.addRow(arrayOf(true, primary.alias, col.name, "", "None", "None"))
                queryModel.columns.add(
                    QueryColumn(primary.alias, col.name, "", AggregateFunction.NONE, true)
                )
            }
        }

        // 2. Joined tables columns
        for (join in queryModel.joins) {
            val meta = availableTables.find { it.name == join.table.tableName }
            meta?.columns?.forEach { col ->
                columnsTableModel.addRow(arrayOf(false, join.table.alias, col.name, "", "None", "None"))
                queryModel.columns.add(
                    QueryColumn(join.table.alias, col.name, "", AggregateFunction.NONE, false)
                )
            }
        }
    }

    private fun syncColumnsFromTable() {
        queryModel.columns.clear()
        queryModel.sorts.clear()

        for (row in 0 until columnsTableModel.rowCount) {
            val included = columnsTableModel.getValueAt(row, 0) as? Boolean ?: false
            val tableAlias = columnsTableModel.getValueAt(row, 1)?.toString() ?: ""
            val colName = columnsTableModel.getValueAt(row, 2)?.toString() ?: ""
            val outputAlias = columnsTableModel.getValueAt(row, 3)?.toString()?.trim() ?: ""
            val aggStr = columnsTableModel.getValueAt(row, 4)?.toString() ?: "None"
            val sortStr = columnsTableModel.getValueAt(row, 5)?.toString() ?: "None"

            val agg = when (aggStr) {
                "COUNT" -> AggregateFunction.COUNT
                "COUNT(DISTINCT)" -> AggregateFunction.COUNT_DISTINCT
                "SUM" -> AggregateFunction.SUM
                "AVG" -> AggregateFunction.AVG
                "MIN" -> AggregateFunction.MIN
                "MAX" -> AggregateFunction.MAX
                else -> AggregateFunction.NONE
            }

            queryModel.columns.add(
                QueryColumn(tableAlias, colName, outputAlias, agg, included)
            )

            if (sortStr == "ASC") {
                queryModel.sorts.add(QuerySort(tableAlias, colName, SortDirection.ASC))
            } else if (sortStr == "DESC") {
                queryModel.sorts.add(QuerySort(tableAlias, colName, SortDirection.DESC))
            }
        }
    }

    private fun toggleAllColumns(include: Boolean) {
        for (row in 0 until columnsTableModel.rowCount) {
            columnsTableModel.setValueAt(include, row, 0)
        }
        syncColumnsFromTable()
        updateQueryPreview()
    }

    private fun addJoinRow() {
        val rowPanel = JPanel(FlowLayout(FlowLayout.LEFT, 6, 2)).apply {
            border = CompoundBorder(LineBorder(Color(210, 210, 210), 1, true), EmptyBorder(4, 4, 4, 4))
        }

        val joinTypeCombo = JComboBox(arrayOf("INNER JOIN", "LEFT JOIN", "RIGHT JOIN", "FULL OUTER JOIN", "CROSS JOIN"))
        val targetTableCombo = JComboBox<String>()
        availableTables.forEach { targetTableCombo.addItem(it.name) }

        val aliasField = JTextField(4).apply {
            text = "j${queryModel.joins.size + 1}"
        }

        val leftColCombo = JComboBox<String>()
        val rightColCombo = JComboBox<String>()

        fun populateJoinColumns() {
            leftColCombo.removeAllItems()
            rightColCombo.removeAllItems()

            // Left columns (from primary or previous joins)
            queryModel.primaryTable?.let { p ->
                val meta = availableTables.find { it.name == p.tableName }
                meta?.columns?.forEach { leftColCombo.addItem("${p.alias}.${it.name}") }
            }

            // Right columns
            val targetName = targetTableCombo.selectedItem as? String
            val targetMeta = availableTables.find { it.name == targetName }
            targetMeta?.columns?.forEach { rightColCombo.addItem(it.name) }
        }

        targetTableCombo.addActionListener {
            populateJoinColumns()
            rebuildQueryModelFromJoins()
        }

        populateJoinColumns()

        val removeBtn = JButton("✖").apply {
            isFocusable = false
            foreground = Color(180, 40, 40)
            margin = Insets(1, 4, 1, 4)
            toolTipText = "Remove Join"
        }

        rowPanel.add(joinTypeCombo)
        rowPanel.add(targetTableCombo)
        rowPanel.add(JBLabel("AS"))
        rowPanel.add(aliasField)
        rowPanel.add(JBLabel("ON"))
        rowPanel.add(leftColCombo)
        rowPanel.add(JBLabel("="))
        rowPanel.add(rightColCombo)
        rowPanel.add(removeBtn)

        removeBtn.addActionListener {
            joinsContainer.remove(rowPanel)
            joinsContainer.revalidate()
            joinsContainer.repaint()
            rebuildQueryModelFromJoins()
        }

        val listener = {
            rebuildQueryModelFromJoins()
        }
        joinTypeCombo.addActionListener { listener() }
        leftColCombo.addActionListener { listener() }
        rightColCombo.addActionListener { listener() }

        joinsContainer.add(rowPanel)
        joinsContainer.revalidate()
        joinsContainer.repaint()

        rebuildQueryModelFromJoins()
    }

    private fun rebuildQueryModelFromJoins() {
        queryModel.joins.clear()

        for (comp in joinsContainer.components) {
            val row = comp as? JPanel ?: continue
            val joinTypeStr = (row.getComponent(0) as? JComboBox<*>)?.selectedItem?.toString() ?: "INNER JOIN"
            val targetTable = (row.getComponent(1) as? JComboBox<*>)?.selectedItem?.toString() ?: continue
            val alias = (row.getComponent(3) as? JTextField)?.text?.trim() ?: "j1"
            val leftColFull = (row.getComponent(5) as? JComboBox<*>)?.selectedItem?.toString() ?: ""
            val rightCol = (row.getComponent(7) as? JComboBox<*>)?.selectedItem?.toString() ?: ""

            val joinType = when (joinTypeStr) {
                "LEFT JOIN" -> JoinType.LEFT
                "RIGHT JOIN" -> JoinType.RIGHT
                "FULL OUTER JOIN" -> JoinType.FULL_OUTER
                "CROSS JOIN" -> JoinType.CROSS
                else -> JoinType.INNER
            }

            val leftAlias = leftColFull.substringBefore(".", "")
            val leftCol = leftColFull.substringAfter(".", leftColFull)

            val cond = if (leftCol.isNotBlank() && rightCol.isNotBlank()) {
                JoinCondition(leftAlias, leftCol, "=", alias, rightCol)
            } else null

            queryModel.joins.add(
                QueryJoin(joinType, QueryTable(targetTable, alias), cond)
            )
        }

        rebuildColumnsTable()
        updateQueryPreview()
    }

    private fun autoDetectForeignKeys() {
        val primary = queryModel.primaryTable ?: return
        val primaryMeta = availableTables.find { it.name == primary.tableName } ?: return

        var detectedCount = 0
        for (fk in primaryMeta.foreignKeys) {
            // Find target table
            val targetTable = availableTables.find { it.name.equals(fk.toTable, ignoreCase = true) }
            if (targetTable != null) {
                addJoinRow()
                // Configure last added join row
                val lastComp = joinsContainer.components.lastOrNull() as? JPanel
                if (lastComp != null) {
                    val targetCombo = lastComp.getComponent(1) as? JComboBox<String>
                    targetCombo?.selectedItem = targetTable.name
                    val leftCombo = lastComp.getComponent(5) as? JComboBox<String>
                    val rightCombo = lastComp.getComponent(7) as? JComboBox<String>

                    leftCombo?.selectedItem = "${primary.alias}.${fk.fromColumn}"
                    rightCombo?.selectedItem = fk.toColumn
                    detectedCount++
                }
            }
        }

        statusLabel.text = if (detectedCount > 0) {
            "Auto-detected and configured $detectedCount foreign key join(s)"
        } else {
            "No explicit foreign key relationships found in metadata for table '${primary.tableName}'"
        }
    }

    private fun addFilterRow(isHaving: Boolean = false) {
        val rowPanel = JPanel(FlowLayout(FlowLayout.LEFT, 6, 2)).apply {
            border = CompoundBorder(LineBorder(Color(210, 210, 210), 1, true), EmptyBorder(3, 4, 3, 4))
        }

        val connectorCombo = JComboBox(arrayOf("AND", "OR"))
        val colCombo = JComboBox<String>()

        // Populate available columns
        val primary = queryModel.primaryTable
        if (primary != null) {
            val meta = availableTables.find { it.name == primary.tableName }
            meta?.columns?.forEach { colCombo.addItem("${primary.alias}.${it.name}") }
        }
        for (j in queryModel.joins) {
            val meta = availableTables.find { it.name == j.table.tableName }
            meta?.columns?.forEach { colCombo.addItem("${j.table.alias}.${it.name}") }
        }

        val opCombo = JComboBox(arrayOf("=", "!=", ">", ">=", "<", "<=", "LIKE", "NOT LIKE", "IN", "IS NULL", "IS NOT NULL", "BETWEEN"))
        val valueField = JTextField(12)
        val havingCheck = JCheckBox("HAVING", isHaving).apply { isOpaque = false }

        val removeBtn = JButton("✖").apply {
            isFocusable = false
            foreground = Color(180, 40, 40)
            margin = Insets(1, 4, 1, 4)
            toolTipText = "Remove Filter"
        }

        rowPanel.add(connectorCombo)
        rowPanel.add(colCombo)
        rowPanel.add(opCombo)
        rowPanel.add(valueField)
        rowPanel.add(havingCheck)
        rowPanel.add(removeBtn)

        removeBtn.addActionListener {
            filtersContainer.remove(rowPanel)
            filtersContainer.revalidate()
            filtersContainer.repaint()
            rebuildQueryModelFromFilters()
        }

        val updateListener = { rebuildQueryModelFromFilters() }
        connectorCombo.addActionListener { updateListener() }
        colCombo.addActionListener { updateListener() }
        opCombo.addActionListener { updateListener() }
        havingCheck.addActionListener { updateListener() }
        valueField.addActionListener { updateListener() }

        filtersContainer.add(rowPanel)
        filtersContainer.revalidate()
        filtersContainer.repaint()

        rebuildQueryModelFromFilters()
    }

    private fun rebuildQueryModelFromFilters() {
        queryModel.filters.clear()

        for (comp in filtersContainer.components) {
            val row = comp as? JPanel ?: continue
            val connectorStr = (row.getComponent(0) as? JComboBox<*>)?.selectedItem?.toString() ?: "AND"
            val colFull = (row.getComponent(1) as? JComboBox<*>)?.selectedItem?.toString() ?: continue
            val opStr = (row.getComponent(2) as? JComboBox<*>)?.selectedItem?.toString() ?: "="
            val value = (row.getComponent(3) as? JTextField)?.text?.trim() ?: ""
            val isHaving = (row.getComponent(4) as? JCheckBox)?.isSelected ?: false

            val tableAlias = colFull.substringBefore(".", "")
            val colName = colFull.substringAfter(".", colFull)

            val op = when (opStr) {
                "!=" -> ComparisonOperator.NOT_EQUALS
                ">" -> ComparisonOperator.GREATER
                ">=" -> ComparisonOperator.GREATER_EQUAL
                "<" -> ComparisonOperator.LESS
                "<=" -> ComparisonOperator.LESS_EQUAL
                "LIKE" -> ComparisonOperator.LIKE
                "NOT LIKE" -> ComparisonOperator.NOT_LIKE
                "IN" -> ComparisonOperator.IN
                "IS NULL" -> ComparisonOperator.IS_NULL
                "IS NOT NULL" -> ComparisonOperator.IS_NOT_NULL
                "BETWEEN" -> ComparisonOperator.BETWEEN
                else -> ComparisonOperator.EQUALS
            }

            val connector = if (connectorStr == "OR") LogicalOperator.OR else LogicalOperator.AND

            queryModel.filters.add(
                QueryFilter(tableAlias, colName, op, value, connector, isHaving)
            )
        }

        updateQueryPreview()
    }

    private fun showPresetsMenu(invoker: Component) {
        val menu = JPopupMenu()

        menu.add(JMenuItem("⚡ Select All (Limit 100)").apply {
            addActionListener {
                toggleAllColumns(true)
                queryModel.filters.clear()
                filtersContainer.removeAll()
                filtersContainer.revalidate()
                filtersContainer.repaint()
                updateQueryPreview()
            }
        })

        menu.add(JMenuItem("📊 Aggregate & Group By Count").apply {
            addActionListener {
                if (columnsTableModel.rowCount > 0) {
                    columnsTableModel.setValueAt(true, 0, 0) // include first column
                    columnsTableModel.setValueAt("None", 0, 4) // no agg on 1st
                    if (columnsTableModel.rowCount > 1) {
                        columnsTableModel.setValueAt(true, 1, 0)
                        columnsTableModel.setValueAt("COUNT", 1, 4)
                        columnsTableModel.setValueAt("total_count", 1, 3)
                    }
                    syncColumnsFromTable()
                    updateQueryPreview()
                }
            }
        })

        menu.add(JMenuItem("🎯 Filter Top 25 Highest").apply {
            addActionListener {
                limitCombo.selectedItem = 25
                queryModel.limit = 25
                if (columnsTableModel.rowCount > 0) {
                    columnsTableModel.setValueAt("DESC", 0, 5) // sort desc on 1st col
                    syncColumnsFromTable()
                    updateQueryPreview()
                }
            }
        })

        menu.show(invoker, 0, invoker.height)
    }

    private fun resetBuilder() {
        queryModel.joins.clear()
        queryModel.filters.clear()
        queryModel.sorts.clear()
        queryModel.isDistinct = false
        distinctCheck.isSelected = false
        limitCombo.selectedItem = 100
        offsetSpinner.value = 0

        joinsContainer.removeAll()
        joinsContainer.revalidate()
        joinsContainer.repaint()

        filtersContainer.removeAll()
        filtersContainer.revalidate()
        filtersContainer.repaint()

        rebuildColumnsTable()
        updateQueryPreview()
        statusLabel.text = "Visual query builder reset"
    }

    private fun updateQueryPreview() {
        val config = getActiveConfig()
        val dialect = config?.dialect ?: DatabaseDialect.SQLITE

        val sql = VisualQueryGenerator.generateSql(queryModel, dialect)
        sqlPreviewArea.text = sql

        val pandas = VisualQueryGenerator.generatePandasCode(queryModel)
        pandasPreviewArea.text = pandas

        val duckDb = VisualQueryGenerator.generateDuckDbPythonCode(queryModel)
        duckDbPreviewArea.text = duckDb
    }

    fun executeVisualQuery() {
        val config = getActiveConfig()
        if (config == null) {
            statusLabel.text = "No connection selected"
            return
        }

        val conn = DatabaseConnectionManager.getConnection(config.id)
            ?: runCatching { DatabaseConnectionManager.connect(config) }.getOrNull()

        if (conn == null) {
            statusLabel.text = "Failed to establish connection to ${config.name}"
            return
        }

        val sql = sqlPreviewArea.text.trim()
        if (sql.isBlank() || sql.startsWith("--")) {
            statusLabel.text = "Please select a valid table and columns to query"
            return
        }

        statusLabel.text = "Executing visual query..."
        val startTime = System.currentTimeMillis()

        SwingUtilities.invokeLater {
            val result = SqlQueryExecutor.execute(conn, sql, maxRows = queryModel.limit)
            val elapsed = System.currentTimeMillis() - startTime

            if (result.isSuccess && result.dataFrame != null) {
                gridPanel.dataFrame = result.dataFrame
                statusLabel.text = "Query completed in ${elapsed}ms (${result.dataFrame.rowCount} rows, ${result.dataFrame.columnCount} columns)"
                statusLabel.foreground = Color(20, 120, 20)
            } else {
                statusLabel.text = "Error: ${result.errorMessage ?: "Query failed"}"
                statusLabel.foreground = Color(180, 40, 40)
            }
        }
    }

    private fun getActiveConfig(): ConnectionConfig? {
        val selected = connectionCombo.selectedItem as? String ?: return null
        return DatabaseConnectionManager.getAllConfigs().find { it.name == selected }
    }
}
