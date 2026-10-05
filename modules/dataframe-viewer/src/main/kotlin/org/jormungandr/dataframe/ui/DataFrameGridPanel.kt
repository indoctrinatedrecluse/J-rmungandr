package org.jormungandr.dataframe.ui

import com.intellij.openapi.ui.JBPopupMenu
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import org.jormungandr.core.theme.DataGridThemeTokens
import org.jormungandr.core.theme.JormungandrTheme
import org.jormungandr.dataframe.io.DataFrameExporter
import org.jormungandr.dataframe.model.ColumnMetadata
import org.jormungandr.dataframe.model.DataFrame
import org.jormungandr.dataframe.model.DataTypeCategory
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * Interactive Swing Dataframe Grid Panel.
 * Provides virtualized scrolling, live multi-column search, column sorting,
 * side summary statistics inspector, and tabular data export.
 */
class DataFrameGridPanel(
    initialDataFrame: DataFrame = DataFrame.empty(),
    private var gridTheme: DataGridThemeTokens = JormungandrTheme.SOLARIZED_LIGHT.dataGrid
) : JPanel(BorderLayout()) {

    private val tableModel = DataFrameTableModel(initialDataFrame)
    private val table = JBTable(tableModel)
    private val headerRenderer = DataFrameHeaderRenderer(gridTheme)
    private val cellRenderer = DataFrameCellRenderer(gridTheme)

    private val searchField = JBTextField(16)
    private val shapeLabel = JBLabel()
    private val statusLabel = JBLabel()

    private val inspectorPanel = JPanel(BorderLayout())
    private var isInspectorVisible = true

    private var currentSortColumn: Int = -1
    private var currentSortAsc: Boolean = true

    init {
        setupTable()
        val toolbar = createToolbar()
        val centerSplit = createCenterPanel()
        val statusBar = createStatusBar()

        add(toolbar, BorderLayout.NORTH)
        add(centerSplit, BorderLayout.CENTER)
        add(statusBar, BorderLayout.SOUTH)

        updateMetadataViews()
    }

    var dataFrame: DataFrame
        get() = tableModel.dataFrame
        set(value) {
            tableModel.dataFrame = value
            currentSortColumn = -1
            headerRenderer.sortColumn = -1
            updateMetadataViews()
        }

    fun applyTheme(themeTokens: DataGridThemeTokens) {
        this.gridTheme = themeTokens
        headerRenderer.updateTheme(themeTokens)
        cellRenderer.updateTheme(themeTokens)
        table.tableHeader.repaint()
        table.repaint()
    }

    private fun setupTable() {
        table.autoResizeMode = JTable.AUTO_RESIZE_OFF
        table.rowHeight = 26
        table.setShowGrid(true)
        table.gridColor = parseHexColor(gridTheme.gridLineColor, Color(225, 225, 225))
        table.tableHeader.defaultRenderer = headerRenderer
        table.tableHeader.reorderingAllowed = false

        table.setDefaultRenderer(Any::class.java, cellRenderer)
        table.columnModel.addColumnModelListener(object : javax.swing.event.TableColumnModelListener {
            override fun columnAdded(e: javax.swing.event.TableColumnModelEvent?) {}
            override fun columnRemoved(e: javax.swing.event.TableColumnModelEvent?) {}
            override fun columnMoved(e: javax.swing.event.TableColumnModelEvent?) {}
            override fun columnMarginChanged(e: javax.swing.event.ChangeEvent?) {}
            override fun columnSelectionChanged(e: javax.swing.event.ListSelectionEvent?) {
                updateInspectorForSelectedColumn()
            }
        })

        table.tableHeader.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val col = table.columnAtPoint(e.point)
                if (col > 0) { // Column 0 is row index gutter
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

        // Column click updates inspector
        table.selectionModel.addListSelectionListener {
            updateInspectorForSelectedColumn()
        }
    }

    private fun createToolbar(): JComponent {
        val bar = JPanel(BorderLayout(8, 0)).apply {
            border = EmptyBorder(6, 8, 6, 8)
            background = parseHexColor(gridTheme.headerBackground, Color(245, 245, 245))
        }

        // Left: Search Field
        val left = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply { isOpaque = false }
        val searchLabel = JBLabel("Filter:")
        searchField.emptyText.text = "Search values or col:val..."
        searchField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = onFilterChanged()
            override fun removeUpdate(e: DocumentEvent?) = onFilterChanged()
            override fun changedUpdate(e: DocumentEvent?) = onFilterChanged()
        })
        val clearBtn = JButton("Clear").apply {
            isFocusable = false
            addActionListener {
                searchField.text = ""
                tableModel.resetFilterAndSort()
                currentSortColumn = -1
                headerRenderer.sortColumn = -1
                table.tableHeader.repaint()
                updateMetadataViews()
            }
        }
        left.add(searchLabel)
        left.add(searchField)
        left.add(clearBtn)

        // Right: Shape, Inspector Toggle, Export
        val right = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply { isOpaque = false }
        shapeLabel.font = shapeLabel.font.deriveFont(Font.BOLD, 11f)

        val inspectorToggleBtn = JButton("📊 Stats").apply {
            isFocusable = false
            toolTipText = "Toggle column statistics panel"
            addActionListener {
                isInspectorVisible = !isInspectorVisible
                inspectorPanel.isVisible = isInspectorVisible
                revalidate()
                repaint()
            }
        }

        val exportBtn = JButton("💾 Export ▾").apply {
            isFocusable = false
            addActionListener {
                val menu = JBPopupMenu()
                menu.add(JMenuItem("Copy as TSV").apply {
                    addActionListener { copyToClipboard(DataFrameExporter.toTsv(dataFrame)) }
                })
                menu.add(JMenuItem("Copy as Markdown Table").apply {
                    addActionListener { copyToClipboard(DataFrameExporter.toMarkdown(dataFrame)) }
                })
                menu.add(JMenuItem("Export as JSON").apply {
                    addActionListener { copyToClipboard(DataFrameExporter.toJson(dataFrame)) }
                })
                menu.show(this, 0, height)
            }
        }

        right.add(shapeLabel)
        right.add(inspectorToggleBtn)
        right.add(exportBtn)

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
            preferredSize = Dimension(230, 300)
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

    private fun onFilterChanged() {
        val text = searchField.text
        tableModel.applyFilter(text)
        updateMetadataViews()
    }

    private fun updateMetadataViews() {
        val df = tableModel.dataFrame
        shapeLabel.text = "[${df.rowCount} rows × ${df.columnCount} cols]"
        val memKb = df.estimateMemoryBytes() / 1024
        statusLabel.text = "Dataset: ${df.name} | Total Rows: ${df.rowCount} | Memory: ~${memKb} KB"
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
                if (meta.stdDev != null) add(statRow("StdDev:", "%.2f".format(meta.stdDev)))

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
