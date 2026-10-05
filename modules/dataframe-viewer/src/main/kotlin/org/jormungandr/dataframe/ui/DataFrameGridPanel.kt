package org.jormungandr.dataframe.ui

import com.intellij.openapi.ui.JBPopupMenu
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import org.jormungandr.core.theme.DataGridThemeTokens
import org.jormungandr.core.theme.JormungandrTheme
import org.jormungandr.dataframe.chart.DataFrameChartView
import org.jormungandr.dataframe.codegen.DataFrameCodeGenerator
import org.jormungandr.dataframe.filter.CompoundFilter
import org.jormungandr.dataframe.io.DataFrameExporter
import org.jormungandr.dataframe.model.ColumnMetadata
import org.jormungandr.dataframe.model.DataFrame
import org.jormungandr.dataframe.model.DataTypeCategory
import org.jormungandr.dataframe.transform.AggregationType
import org.jormungandr.dataframe.transform.DataFrameTransform
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * Top-of-the-line interactive Swing Dataframe Studio & Viewer.
 * Features:
 * - Multi-tab interface: Grid View, 2D Vector Chart View, Column Profiler
 * - Structured multi-condition filter builder and instant search
 * - Numerical heatmap gradient formatting
 * - Column visibility customization
 * - SQL-style GroupBy aggregation and Pandas-like df.describe()
 * - Code generation: Pandas, Polars, SQL DDL & Inserts, CSV, TSV, JSON, Markdown
 * - Rich statistical inspector with distribution histograms
 */
class DataFrameGridPanel(
    initialDataFrame: DataFrame = DataFrame.empty(),
    private var gridTheme: DataGridThemeTokens = JormungandrTheme.SOLARIZED_LIGHT.dataGrid
) : JPanel(BorderLayout()) {

    private var rawDataFrame: DataFrame = initialDataFrame
    private val tableModel = DataFrameTableModel(initialDataFrame)
    private val table = JBTable(tableModel)
    private val headerRenderer = DataFrameHeaderRenderer(gridTheme)
    private val cellRenderer = DataFrameCellRenderer(gridTheme)

    private val searchField = JBTextField(14)
    private val shapeLabel = JBLabel()
    private val statusLabel = JBLabel()

    private val inspectorPanel = JPanel(BorderLayout())
    private var isInspectorVisible = true

    private var currentSortColumn: Int = -1
    private var currentSortAsc: Boolean = true

    // Advanced features
    private val chartView = DataFrameChartView(initialDataFrame)
    private val profilerView = ColumnProfilerPanel(initialDataFrame)
    private val pivotView = PivotTablePanel(initialDataFrame, gridTheme)
    private val sqlView = DataFrameSqlPanel(initialDataFrame, gridTheme)
    private val filterBuilder = FilterBuilderPanel(initialDataFrame) { compoundFilter ->
        applyCompoundFilter(compoundFilter)
    }
    private var isFilterBuilderVisible = false
    private val filterContainer = JPanel(BorderLayout())

    init {
        setupTable()

        val tabbedPane = JBTabbedPane()

        // Tab 1: Grid View
        val gridTab = JPanel(BorderLayout())
        val toolbar = createToolbar()
        val centerSplit = createCenterPanel()
        val statusBar = createStatusBar()

        filterContainer.add(filterBuilder, BorderLayout.CENTER)
        filterContainer.isVisible = false

        val topPanel = JPanel(BorderLayout())
        topPanel.add(toolbar, BorderLayout.NORTH)
        topPanel.add(filterContainer, BorderLayout.SOUTH)

        gridTab.add(topPanel, BorderLayout.NORTH)
        gridTab.add(centerSplit, BorderLayout.CENTER)
        gridTab.add(statusBar, BorderLayout.SOUTH)

        tabbedPane.addTab("Grid View", gridTab)
        tabbedPane.addTab("Chart View", chartView)
        tabbedPane.addTab("Column Profiler", profilerView)
        tabbedPane.addTab("Pivot Studio", pivotView)
        tabbedPane.addTab("In-Memory SQL", sqlView)

        tabbedPane.addChangeListener {
            when (tabbedPane.selectedIndex) {
                1 -> chartView.setDataFrame(dataFrame)
                2 -> profilerView.setDataFrame(dataFrame)
                3 -> pivotView.setDataFrame(dataFrame)
                4 -> sqlView.setDataFrame(dataFrame)
            }
        }

        add(tabbedPane, BorderLayout.CENTER)
        updateMetadataViews()
    }

    var dataFrame: DataFrame
        get() = tableModel.dataFrame
        set(value) {
            rawDataFrame = value
            tableModel.dataFrame = value
            currentSortColumn = -1
            headerRenderer.sortColumn = -1
            chartView.setDataFrame(value)
            profilerView.setDataFrame(value)
            pivotView.setDataFrame(value)
            sqlView.setDataFrame(value)
            filterBuilder.setDataFrame(value)
            updateMetadataViews()
        }

    fun applyTheme(themeTokens: DataGridThemeTokens) {
        this.gridTheme = themeTokens
        headerRenderer.updateTheme(themeTokens)
        cellRenderer.updateTheme(themeTokens)
        table.tableHeader.repaint()
        table.repaint()
        pivotView.applyTheme(themeTokens)
        sqlView.applyTheme(themeTokens)
    }

    private fun setupTable() {
        table.autoResizeMode = JTable.AUTO_RESIZE_OFF
        table.rowHeight = 26
        table.setShowGrid(true)
        table.gridColor = parseHexColor(gridTheme.gridLineColor, Color(225, 225, 225))
        table.tableHeader.defaultRenderer = headerRenderer
        table.tableHeader.reorderingAllowed = false

        table.setDefaultRenderer(Any::class.java, cellRenderer)

        table.tableHeader.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val col = table.columnAtPoint(e.point)
                if (col > 0) {
                    val dataCol = col - 1
                    if (currentSortColumn == col) {
                        currentSortAsc = !currentSortAsc
                    } else {
                        currentSortColumn = col
                        currentSortAsc = true
                    }
                    headerRenderer.sortColumn = currentSortColumn
                    headerRenderer.sortAscending = currentSortAsc
                    tableModel.applySort(dataCol, currentSortAsc)
                    table.tableHeader.repaint()
                }
            }
        })

        table.selectionModel.addListSelectionListener {
            updateInspectorForSelectedColumn()
        }
    }

    private fun createToolbar(): JComponent {
        val bar = JPanel(BorderLayout(8, 0)).apply {
            border = EmptyBorder(6, 8, 6, 8)
            background = parseHexColor(gridTheme.headerBackground, Color(245, 245, 245))
        }

        // Left controls
        val left = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply { isOpaque = false }
        searchField.emptyText.text = "Search values..."
        searchField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = onFilterChanged()
            override fun removeUpdate(e: DocumentEvent?) = onFilterChanged()
            override fun changedUpdate(e: DocumentEvent?) = onFilterChanged()
        })

        val filterToggleBtn = JButton("🔍 Filter Builder").apply {
            isFocusable = false
            addActionListener {
                isFilterBuilderVisible = !isFilterBuilderVisible
                filterContainer.isVisible = isFilterBuilderVisible
                revalidate()
                repaint()
            }
        }

        val columnsBtn = JButton("Columns ▾").apply {
            isFocusable = false
            addActionListener {
                val dlg = ColumnVisibilityDialog(null, rawDataFrame, tableModel.dataFrame.columns.map { it.name })
                if (dlg.showAndGet()) {
                    val selected = dlg.getSelectedColumns()
                    if (selected.isNotEmpty()) {
                        tableModel.dataFrame = rawDataFrame.selectColumns(selected)
                        updateMetadataViews()
                    }
                }
            }
        }

        val heatmapBtn = JButton("🎨 Heatmap").apply {
            isFocusable = false
            toolTipText = "Toggle numeric cell gradient heatmap"
            addActionListener {
                cellRenderer.isHeatmapEnabled = !cellRenderer.isHeatmapEnabled
                table.repaint()
            }
        }

        val describeBtn = JButton("Σ Describe").apply {
            isFocusable = false
            toolTipText = "Generate summary statistics dataset (df.describe())"
            addActionListener {
                val described = DataFrameTransform.describe(rawDataFrame)
                if (!described.isEmpty) {
                    dataFrame = described
                }
            }
        }

        val groupByBtn = JButton("Group By ▾").apply {
            isFocusable = false
            addActionListener { showGroupByDialog() }
        }

        left.add(JBLabel("Filter:"))
        left.add(searchField)
        left.add(filterToggleBtn)
        left.add(columnsBtn)
        left.add(heatmapBtn)
        left.add(describeBtn)
        left.add(groupByBtn)

        // Right controls
        val right = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply { isOpaque = false }
        shapeLabel.font = shapeLabel.font.deriveFont(Font.BOLD, 11f)

        val inspectorToggleBtn = JButton("📊 Stats").apply {
            isFocusable = false
            addActionListener {
                isInspectorVisible = !isInspectorVisible
                inspectorPanel.isVisible = isInspectorVisible
                revalidate()
                repaint()
            }
        }

        val codeGenBtn = JButton("⚡ Code ▾").apply {
            isFocusable = false
            addActionListener {
                val menu = JBPopupMenu()
                menu.add(JMenuItem("Copy as Pandas Code").apply {
                    addActionListener { copyToClipboard(DataFrameCodeGenerator.toPandasCode(dataFrame)) }
                })
                menu.add(JMenuItem("Copy as Polars Code").apply {
                    addActionListener { copyToClipboard(DataFrameCodeGenerator.toPolarsCode(dataFrame)) }
                })
                menu.add(JMenuItem("Copy as SQL DDL & Inserts").apply {
                    addActionListener { copyToClipboard(DataFrameCodeGenerator.toSqlDdlAndInserts(dataFrame)) }
                })
                menu.show(this, 0, height)
            }
        }

        val exportBtn = JButton("💾 Export ▾").apply {
            isFocusable = false
            addActionListener {
                val menu = JBPopupMenu()
                menu.add(JMenuItem("Copy as CSV").apply {
                    addActionListener { copyToClipboard(DataFrameExporter.toCsv(dataFrame)) }
                })
                menu.add(JMenuItem("Copy as TSV").apply {
                    addActionListener { copyToClipboard(DataFrameExporter.toTsv(dataFrame)) }
                })
                menu.add(JMenuItem("Copy as Markdown Table").apply {
                    addActionListener { copyToClipboard(DataFrameExporter.toMarkdown(dataFrame)) }
                })
                menu.add(JMenuItem("Copy as JSON").apply {
                    addActionListener { copyToClipboard(DataFrameExporter.toJson(dataFrame)) }
                })
                menu.add(JMenuItem("Copy as JSON Lines (.jsonl)").apply {
                    addActionListener { copyToClipboard(DataFrameExporter.toJsonLines(dataFrame)) }
                })
                menu.add(JMenuItem("Copy as Excel XML (.xml)").apply {
                    addActionListener { copyToClipboard(DataFrameExporter.toExcelXml(dataFrame)) }
                })
                menu.show(this, 0, height)
            }
        }

        val resetBtn = JButton("↺ Reset").apply {
            isFocusable = false
            addActionListener {
                searchField.text = ""
                tableModel.dataFrame = rawDataFrame
                currentSortColumn = -1
                headerRenderer.sortColumn = -1
                table.tableHeader.repaint()
                updateMetadataViews()
            }
        }

        right.add(shapeLabel)
        right.add(inspectorToggleBtn)
        right.add(codeGenBtn)
        right.add(exportBtn)
        right.add(resetBtn)

        bar.add(left, BorderLayout.WEST)
        bar.add(right, BorderLayout.EAST)
        return bar
    }

    private fun createCenterPanel(): JComponent {
        val split = JSplitPane(JSplitPane.HORIZONTAL_SPLIT).apply {
            isContinuousLayout = true
            resizeWeight = 0.78
            border = BorderFactory.createEmptyBorder()
        }

        val scroll = JBScrollPane(table).apply {
            border = BorderFactory.createMatteBorder(1, 0, 1, 0, parseHexColor(gridTheme.gridLineColor, Color(220, 220, 220)))
        }

        inspectorPanel.apply {
            preferredSize = Dimension(240, 300)
            minimumSize = Dimension(180, 200)
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 1, 1, 0, parseHexColor(gridTheme.gridLineColor, Color(220, 220, 220))),
                EmptyBorder(8, 8, 8, 8)
            )
            background = parseHexColor(gridTheme.rowEvenBackground, Color(255, 255, 255))
        }

        split.leftComponent = scroll
        split.rightComponent = inspectorPanel
        return split
    }

    private fun createStatusBar(): JComponent {
        val bar = JPanel(BorderLayout()).apply {
            border = EmptyBorder(4, 8, 4, 8)
            background = parseHexColor(gridTheme.headerBackground, Color(245, 245, 245))
        }
        statusLabel.font = statusLabel.font.deriveFont(Font.PLAIN, 11f)
        statusLabel.foreground = Color(110, 110, 110)
        bar.add(statusLabel, BorderLayout.WEST)
        return bar
    }

    private fun showGroupByDialog() {
        if (dataFrame.columns.isEmpty()) return
        val panel = JPanel(GridLayout(3, 2, 6, 6))
        val groupCombo = JComboBox(dataFrame.columns.map { it.name }.toTypedArray())
        val metricCombo = JComboBox(dataFrame.columns.map { it.name }.toTypedArray())
        val aggCombo = JComboBox(AggregationType.values())

        val numericCol = dataFrame.columns.find { it.isNumeric }
        if (numericCol != null) metricCombo.selectedItem = numericCol.name

        panel.add(JLabel("Group By Column:"))
        panel.add(groupCombo)
        panel.add(JLabel("Metric Column:"))
        panel.add(metricCombo)
        panel.add(JLabel("Aggregation:"))
        panel.add(aggCombo)

        val res = JOptionPane.showConfirmDialog(this, panel, "Configure Group By Aggregation", JOptionPane.OK_CANCEL_OPTION)
        if (res == JOptionPane.OK_OPTION) {
            val grp = groupCombo.selectedItem as String
            val met = metricCombo.selectedItem as String
            val agg = aggCombo.selectedItem as AggregationType
            val groupedDf = DataFrameTransform.groupBy(rawDataFrame, grp, met, agg)
            dataFrame = groupedDf
        }
    }

    private fun applyCompoundFilter(filter: CompoundFilter) {
        val filtered = filter.applyTo(rawDataFrame)
        tableModel.dataFrame = filtered
        updateMetadataViews()
    }

    private fun onFilterChanged() {
        val text = searchField.text
        tableModel.applyFilter(text)
        updateMetadataViews()
    }

    private fun updateMetadataViews() {
        val df = tableModel.dataFrame
        shapeLabel.text = "[${df.rowCount} rows × ${df.columnCount} cols]"
        val memKb = df.estimateMemoryBytes() / 1024
        statusLabel.text = "Dataset: ${df.name} | Displayed: ${df.rowCount} / ${rawDataFrame.rowCount} rows | Memory: ~${memKb} KB"
        updateInspectorForSelectedColumn()
    }

    private fun updateInspectorForSelectedColumn() {
        inspectorPanel.removeAll()

        val selectedCol = table.selectedColumn
        val dataColIndex = if (selectedCol > 0) selectedCol - 1 else 0
        val meta = tableModel.dataFrame.columns.getOrNull(dataColIndex)

        if (meta == null) {
            val emptyLabel = JBLabel("Select a column to view stats").apply {
                horizontalAlignment = SwingConstants.CENTER
                foreground = Color(130, 130, 130)
            }
            inspectorPanel.add(emptyLabel, BorderLayout.CENTER)
        } else {
            val content = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                isOpaque = false

                val hLabel = JBLabel("Column: ${meta.name}").apply { font = font.deriveFont(Font.BOLD, 13f) }
                val typeLabel = JBLabel("Type: ${meta.typeName} (${meta.category.badgeShort})").apply {
                    font = font.deriveFont(Font.PLAIN, 11f)
                    foreground = Color(100, 100, 100)
                }
                add(hLabel)
                add(typeLabel)
                add(Box.createVerticalStrut(8))

                add(statRow("Total Rows:", "${meta.totalCount}"))
                add(statRow("Nulls:", "${meta.nullCount} (%.1f%%)".format(meta.nullPercentage)))
                add(statRow("Distinct:", "${meta.distinctCount}"))

                if (meta.minVal != null) add(statRow("Min:", meta.minVal))
                if (meta.maxVal != null) add(statRow("Max:", meta.maxVal))
                if (meta.meanVal != null) add(statRow("Mean:", "%.2f".format(meta.meanVal)))
                if (meta.medianVal != null) add(statRow("Median:", "%.2f".format(meta.medianVal)))
                if (meta.q25 != null) add(statRow("Q25:", "%.2f".format(meta.q25)))
                if (meta.q75 != null) add(statRow("Q75:", "%.2f".format(meta.q75)))
                if (meta.iqr != null) add(statRow("IQR:", "%.2f".format(meta.iqr)))
                if (meta.stdDev != null) add(statRow("StdDev:", "%.2f".format(meta.stdDev)))
                if (meta.skewness != null) add(statRow("Skewness:", "%.2f".format(meta.skewness)))

                if (meta.histogramBins.isNotEmpty()) {
                    add(Box.createVerticalStrut(10))
                    val distTitle = JBLabel("Distribution:").apply { font = font.deriveFont(Font.BOLD, 11f) }
                    add(distTitle)
                    add(Box.createVerticalStrut(4))

                    val maxCount = meta.histogramBins.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
                    for (bin in meta.histogramBins) {
                        val barLen = (bin.count.toDouble() / maxCount.toDouble() * 12.0).toInt().coerceIn(1, 12)
                        val barStr = "█".repeat(barLen)
                        val binRow = JPanel(BorderLayout(4, 0)).apply {
                            isOpaque = false
                            val lbl = JBLabel(bin.label).apply { font = font.deriveFont(Font.PLAIN, 10f) }
                            val valLbl = JBLabel("$barStr ${bin.count}").apply {
                                font = font.deriveFont(Font.PLAIN, 10f)
                                foreground = Color(40, 120, 200)
                            }
                            add(lbl, BorderLayout.WEST)
                            add(valLbl, BorderLayout.EAST)
                        }
                        add(binRow)
                        add(Box.createVerticalStrut(2))
                    }
                }
            }
            inspectorPanel.add(JBScrollPane(content).apply { border = null; isOpaque = false }, BorderLayout.CENTER)
        }

        inspectorPanel.revalidate()
        inspectorPanel.repaint()
    }

    private fun statRow(label: String, value: String): JPanel {
        return JPanel(BorderLayout()).apply {
            isOpaque = false
            border = EmptyBorder(2, 0, 2, 0)
            val l = JBLabel(label).apply { font = font.deriveFont(Font.PLAIN, 11f); foreground = Color(110, 110, 110) }
            val v = JBLabel(value).apply { font = font.deriveFont(Font.BOLD, 11f) }
            add(l, BorderLayout.WEST)
            add(v, BorderLayout.EAST)
        }
    }

    private fun copyToClipboard(text: String) {
        val sel = StringSelection(text)
        Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, sel)
    }
}
