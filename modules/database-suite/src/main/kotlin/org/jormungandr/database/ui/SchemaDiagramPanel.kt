package org.jormungandr.database.ui

import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import org.jormungandr.database.engine.DdlGenerator
import org.jormungandr.database.model.CatalogMetadata
import org.jormungandr.database.model.TableMetadata
import java.awt.*
import java.awt.datatransfer.StringSelection
import javax.swing.*
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class SchemaDiagramPanel(
    private var catalog: CatalogMetadata? = null,
    private val onTableSelected: ((TableMetadata) -> Unit)? = null
) : JPanel(BorderLayout()) {

    private val searchField = JBTextField(16)
    private val tablesContainer = JPanel()
    private val summaryLabel = JBLabel("No catalog loaded")

    init {
        background = Color(241, 245, 249)

        // Top Toolbar
        val topToolbar = JPanel(BorderLayout()).apply {
            background = Color(248, 250, 252)
            border = CompoundBorder(
                LineBorder(Color(226, 232, 240), 1),
                EmptyBorder(6, 12, 6, 12)
            )
        }

        val left = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply { isOpaque = false }
        searchField.emptyText.text = "Filter schema tables..."
        searchField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = rebuildCards()
            override fun removeUpdate(e: DocumentEvent?) = rebuildCards()
            override fun changedUpdate(e: DocumentEvent?) = rebuildCards()
        })
        left.add(JBLabel("🔍 Search:"))
        left.add(searchField)

        val right = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply { isOpaque = false }
        val ddlBtn = JButton("📋 Export All DDL").apply {
            font = font.deriveFont(Font.PLAIN, 11f)
            addActionListener { exportAllDdl() }
        }
        right.add(ddlBtn)
        right.add(summaryLabel)

        topToolbar.add(left, BorderLayout.WEST)
        topToolbar.add(right, BorderLayout.EAST)
        add(topToolbar, BorderLayout.NORTH)

        // Cards Grid
        tablesContainer.isOpaque = false
        tablesContainer.layout = FlowLayout(FlowLayout.LEFT, 16, 16)

        val scroll = JBScrollPane(tablesContainer).apply {
            border = null
            viewport.isOpaque = false
            verticalScrollBar.unitIncrement = 16
        }
        add(scroll, BorderLayout.CENTER)

        rebuildCards()
    }

    fun setCatalog(newCatalog: CatalogMetadata) {
        this.catalog = newCatalog
        rebuildCards()
    }

    private fun rebuildCards() {
        tablesContainer.removeAll()
        val cat = catalog
        if (cat == null) {
            summaryLabel.text = "No active connection metadata"
            tablesContainer.revalidate()
            tablesContainer.repaint()
            return
        }

        val allTables = cat.schemas.flatMap { it.tables }
        val query = searchField.text.trim().lowercase()
        val filtered = if (query.isEmpty()) {
            allTables
        } else {
            allTables.filter { t ->
                t.name.lowercase().contains(query) ||
                        t.columns.any { it.name.lowercase().contains(query) }
            }
        }

        val totalFks = allTables.sumOf { it.foreignKeys.size }
        summaryLabel.text = "Tables: ${filtered.size}/${allTables.size} | Relations: $totalFks"

        for (table in filtered) {
            tablesContainer.add(createTableCard(table))
        }

        tablesContainer.revalidate()
        tablesContainer.repaint()
    }

    private fun createTableCard(table: TableMetadata): JPanel {
        val card = JPanel(BorderLayout()).apply {
            preferredSize = Dimension(280, 240)
            background = Color(255, 255, 255)
            border = CompoundBorder(
                LineBorder(Color(203, 213, 225), 1, true),
                EmptyBorder(0, 0, 4, 0)
            )
        }

        // Header
        val header = JPanel(BorderLayout()).apply {
            background = if (table.type == "VIEW") Color(241, 245, 249) else Color(238, 242, 255)
            border = EmptyBorder(6, 10, 6, 10)
        }
        val nameLabel = JLabel("${if (table.type == "VIEW") "👁️" else "📦"} ${table.name}").apply {
            font = font.deriveFont(Font.BOLD, 12f)
            foreground = Color(30, 41, 59)
        }
        val typeBadge = JLabel(table.type).apply {
            font = font.deriveFont(Font.PLAIN, 10f)
            foreground = Color(100, 116, 139)
        }
        header.add(nameLabel, BorderLayout.WEST)
        header.add(typeBadge, BorderLayout.EAST)
        card.add(header, BorderLayout.NORTH)

        // Columns List
        val colsBox = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            background = Color(255, 255, 255)
            border = EmptyBorder(4, 8, 4, 8)
        }

        for (col in table.columns) {
            val colRow = JPanel(BorderLayout()).apply {
                isOpaque = false
                border = EmptyBorder(2, 0, 2, 0)
            }

            val prefix = when {
                col.isPrimaryKey -> "🔑 "
                table.foreignKeys.any { it.fromColumn == col.name } -> "🔗 "
                else -> "• "
            }

            val colName = JLabel("$prefix${col.name}").apply {
                font = font.deriveFont(if (col.isPrimaryKey) Font.BOLD else Font.PLAIN, 11f)
                foreground = if (col.isPrimaryKey) Color(180, 83, 9) else Color(51, 65, 85)
            }
            val colType = JLabel(col.typeName).apply {
                font = font.deriveFont(Font.PLAIN, 10f)
                foreground = Color(148, 163, 184)
            }
            colRow.add(colName, BorderLayout.WEST)
            colRow.add(colType, BorderLayout.EAST)
            colsBox.add(colRow)
        }

        // Foreign Key Relationships
        if (table.foreignKeys.isNotEmpty()) {
            colsBox.add(Box.createVerticalStrut(4))
            val relHeader = JLabel("Relationships:").apply {
                font = font.deriveFont(Font.BOLD, 10f)
                foreground = Color(99, 102, 241)
            }
            colsBox.add(relHeader)
            for (fk in table.foreignKeys) {
                val relLabel = JLabel(" ↳ ${fk.fromColumn} → ${fk.toTable}.${fk.toColumn}").apply {
                    font = font.deriveFont(Font.PLAIN, 10f)
                    foreground = Color(79, 70, 229)
                }
                colsBox.add(relLabel)
            }
        }

        val scroll = JBScrollPane(colsBox).apply {
            border = null
            viewport.background = Color(255, 255, 255)
        }
        card.add(scroll, BorderLayout.CENTER)

        // Actions footer
        val footer = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 2)).apply {
            isOpaque = false
            border = EmptyBorder(2, 4, 2, 4)
        }
        val queryBtn = JButton("Query").apply {
            font = font.deriveFont(Font.PLAIN, 10f)
            addActionListener { onTableSelected?.invoke(table) }
        }
        footer.add(queryBtn)
        card.add(footer, BorderLayout.SOUTH)

        return card
    }

    private fun exportAllDdl() {
        val cat = catalog ?: return
        val allTables = cat.schemas.flatMap { it.tables }
        val sb = StringBuilder()
        sb.append("-- ==========================================\n")
        sb.append("-- Jörmungandr Database Schema DDL Export\n")
        sb.append("-- Connection: ${cat.connectionName}\n")
        sb.append("-- ==========================================\n\n")

        for (table in allTables) {
            sb.append(DdlGenerator.generateCreateTable(table)).append("\n\n")
        }

        Toolkit.getDefaultToolkit().systemClipboard.setContents(
            StringSelection(sb.toString()),
            null
        )
        JOptionPane.showMessageDialog(
            this,
            "Copied full schema DDL for ${allTables.size} tables to clipboard!",
            "Schema DDL Export",
            JOptionPane.INFORMATION_MESSAGE
        )
    }
}
