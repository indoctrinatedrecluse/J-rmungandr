package org.jormungandr.dataframe.ui

import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import org.jormungandr.core.theme.DataGridThemeTokens
import org.jormungandr.core.theme.JormungandrTheme
import org.jormungandr.dataframe.io.DataFrameExporter
import org.jormungandr.dataframe.model.DataFrame
import org.jormungandr.dataframe.sql.DataFrameSqlEngine
import org.jormungandr.dataframe.sql.SqlExecutionResult
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.*
import javax.swing.border.EmptyBorder

/**
 * Interactive In-Memory SQL Console tab for DataFrame Viewer.
 * Allows users to write and execute analytical SQL queries directly against the loaded dataset.
 */
class DataFrameSqlPanel(
    private var sourceDataFrame: DataFrame,
    private var gridTheme: DataGridThemeTokens = JormungandrTheme.SOLARIZED_LIGHT.dataGrid
) : JPanel(BorderLayout()) {

    private val sqlTextArea = JTextArea()
    private val resultTableModel = DataFrameTableModel(DataFrame.empty("Query Results"))
    private val resultTable = JBTable(resultTableModel)
    private val statusLabel = JBLabel("Ready. Type SQL query and press Run or Ctrl+Enter.")
    private val errorLabel = JBLabel()
    private val resultsCardLayout = CardLayout()
    private val resultsContainer = JPanel(resultsCardLayout)

    private var lastResultDf: DataFrame? = null

    init {
        setupEditor()
        setupResultsTable()

        val splitPane = JSplitPane(JSplitPane.VERTICAL_SPLIT)
        splitPane.resizeWeight = 0.35
        splitPane.dividerSize = 4

        val editorPanel = JPanel(BorderLayout())
        editorPanel.add(createToolbar(), BorderLayout.NORTH)
        editorPanel.add(JBScrollPane(sqlTextArea), BorderLayout.CENTER)

        val resultsPanel = JPanel(BorderLayout())
        val resultsHeader = JPanel(BorderLayout())
        resultsHeader.border = EmptyBorder(6, 12, 6, 12)
        resultsHeader.background = Color(248, 250, 252)

        val headerLeft = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply { isOpaque = false }
        val resultsTitle = JBLabel("Query Results").apply { font = font.deriveFont(Font.BOLD, 12f) }
        headerLeft.add(resultsTitle)
        headerLeft.add(statusLabel)
        resultsHeader.add(headerLeft, BorderLayout.WEST)

        val headerActions = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply { isOpaque = false }
        val copyBtn = JButton("Copy CSV").apply {
            toolTipText = "Copy query results to clipboard as CSV"
            addActionListener { copyResultToClipboard() }
        }
        val exportBtn = JButton("Export...").apply {
            toolTipText = "Export query results"
            addActionListener { exportResult() }
        }
        headerActions.add(copyBtn)
        headerActions.add(exportBtn)
        resultsHeader.add(headerActions, BorderLayout.EAST)

        resultsPanel.add(resultsHeader, BorderLayout.NORTH)

        // Cards: Table vs Error
        val tableScroll = JBScrollPane(resultTable)
        val errorPanel = JPanel(BorderLayout()).apply {
            border = EmptyBorder(24, 24, 24, 24)
            add(errorLabel, BorderLayout.NORTH)
        }
        resultsContainer.add(tableScroll, "TABLE")
        resultsContainer.add(errorPanel, "ERROR")

        resultsPanel.add(resultsContainer, BorderLayout.CENTER)

        splitPane.topComponent = editorPanel
        splitPane.bottomComponent = resultsPanel

        add(splitPane, BorderLayout.CENTER)

        // Run initial query
        executeSql()
    }

    fun setDataFrame(df: DataFrame) {
        this.sourceDataFrame = df
        updateDefaultQuery()
    }

    fun applyTheme(themeTokens: DataGridThemeTokens) {
        this.gridTheme = themeTokens
        resultTable.tableHeader.defaultRenderer = DataFrameHeaderRenderer(gridTheme)
        resultTable.setDefaultRenderer(Any::class.java, DataFrameCellRenderer(gridTheme))
        resultTable.tableHeader.repaint()
        resultTable.repaint()
    }

    private fun setupEditor() {
        sqlTextArea.font = Font("JetBrains Mono", Font.PLAIN, 13)
        sqlTextArea.margin = Insets(8, 8, 8, 8)
        sqlTextArea.tabSize = 4
        updateDefaultQuery()

        sqlTextArea.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.isControlDown && e.keyCode == KeyEvent.VK_ENTER) {
                    executeSql()
                    e.consume()
                }
            }
        })
    }

    private fun updateDefaultQuery() {
        val firstCol = sourceDataFrame.columns.firstOrNull()?.name ?: "*"
        val catCol = sourceDataFrame.columns.find { it.category.name == "STRING" }?.name
        val numCol = sourceDataFrame.columns.find { it.isNumeric }?.name

        val defaultSql = if (catCol != null && numCol != null) {
            "SELECT $catCol, COUNT(*) AS count, AVG($numCol) AS avg_$numCol\nFROM df\nGROUP BY $catCol\nORDER BY count DESC\nLIMIT 50"
        } else {
            "SELECT * FROM df LIMIT 100"
        }
        sqlTextArea.text = defaultSql
    }

    private fun setupResultsTable() {
        resultTable.autoResizeMode = JTable.AUTO_RESIZE_OFF
        resultTable.rowHeight = 24
        resultTable.tableHeader.reorderingAllowed = false
        resultTable.tableHeader.defaultRenderer = DataFrameHeaderRenderer(gridTheme)
        resultTable.setDefaultRenderer(Any::class.java, DataFrameCellRenderer(gridTheme))
    }

    private fun createToolbar(): JComponent {
        val bar = JPanel(BorderLayout())
        bar.border = EmptyBorder(6, 12, 6, 12)
        bar.background = Color(241, 245, 249)

        val left = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply { isOpaque = false }
        val runBtn = JButton("▶ Run Query (Ctrl+Enter)").apply {
            font = font.deriveFont(Font.BOLD)
            foreground = Color(30, 64, 175)
            addActionListener { executeSql() }
        }
        val templateCombo = JComboBox(arrayOf(
            "Quick Templates...",
            "Select All (Top 100)",
            "Group By & Aggregations",
            "Filtered Slices (WHERE)",
            "Top 10 Distinct Values"
        )).apply {
            addActionListener {
                when (selectedIndex) {
                    1 -> sqlTextArea.text = "SELECT * FROM df LIMIT 100"
                    2 -> {
                        val cat = sourceDataFrame.columns.find { !it.isNumeric }?.name ?: sourceDataFrame.columns.firstOrNull()?.name ?: "col"
                        val num = sourceDataFrame.columns.find { it.isNumeric }?.name ?: "val"
                        sqlTextArea.text = "SELECT $cat, COUNT(*), SUM($num), AVG($num)\nFROM df\nGROUP BY $cat\nORDER BY COUNT(*) DESC"
                    }
                    3 -> {
                        val num = sourceDataFrame.columns.find { it.isNumeric }?.name
                        if (num != null) {
                            sqlTextArea.text = "SELECT * FROM df\nWHERE $num > 0\nORDER BY $num DESC\nLIMIT 50"
                        } else {
                            sqlTextArea.text = "SELECT * FROM df WHERE 1=1 LIMIT 50"
                        }
                    }
                    4 -> {
                        val col = sourceDataFrame.columns.firstOrNull()?.name ?: "col"
                        sqlTextArea.text = "SELECT DISTINCT $col FROM df LIMIT 10"
                    }
                }
            }
        }

        val clearBtn = JButton("Clear").apply {
            addActionListener { sqlTextArea.text = "SELECT * FROM df LIMIT 100" }
        }

        left.add(runBtn)
        left.add(templateCombo)
        left.add(clearBtn)

        val right = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply { isOpaque = false }
        val hint = JBLabel("Direct in-memory SQL execution over Arrow buffers").apply {
            foreground = Color(100, 116, 139)
            font = font.deriveFont(Font.ITALIC, 11f)
        }
        right.add(hint)

        bar.add(left, BorderLayout.WEST)
        bar.add(right, BorderLayout.EAST)
        return bar
    }

    private fun executeSql() {
        val query = sqlTextArea.text
        when (val result = DataFrameSqlEngine.execute(sourceDataFrame, query)) {
            is SqlExecutionResult.Success -> {
                lastResultDf = result.dataFrame
                resultTableModel.updateDataFrame(result.dataFrame)
                statusLabel.text = "✓ Executed in ${result.executionTimeMs} ms (${result.rowCount} rows returned)"
                statusLabel.foreground = Color(22, 101, 52)
                resultsCardLayout.show(resultsContainer, "TABLE")
            }
            is SqlExecutionResult.Error -> {
                lastResultDf = null
                errorLabel.text = "<html><font color='#dc2626'><b>SQL Execution Error:</b></font><br/>${result.message}</html>"
                statusLabel.text = "✕ Query failed"
                statusLabel.foreground = Color(220, 38, 38)
                resultsCardLayout.show(resultsContainer, "ERROR")
            }
        }
    }

    private fun copyResultToClipboard() {
        val df = lastResultDf ?: return
        val csv = DataFrameExporter.toCsv(df)
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(csv), null)
        statusLabel.text = "✓ Copied ${df.rowCount} rows to clipboard!"
    }

    private fun exportResult() {
        val df = lastResultDf ?: return
        val options = arrayOf("CSV (.csv)", "Excel Spreadsheet (.xml)", "JSON Lines (.jsonl)", "SQL Insert Statements (.sql)")
        val choice = JOptionPane.showOptionDialog(
            this,
            "Choose export format for current query result (${df.rowCount} rows):",
            "Export Query Result",
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.PLAIN_MESSAGE,
            null,
            options,
            options[0]
        )
        if (choice >= 0) {
            val content = when (choice) {
                0 -> DataFrameExporter.toCsv(df)
                1 -> DataFrameExporter.toExcelXml(df)
                2 -> DataFrameExporter.toJsonLines(df)
                3 -> DataFrameExporter.toSqlInsert(df, "query_results")
                else -> ""
            }
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(content), null)
            JOptionPane.showMessageDialog(this, "Exported successfully and copied to clipboard!", "Export Complete", JOptionPane.INFORMATION_MESSAGE)
        }
    }
}
