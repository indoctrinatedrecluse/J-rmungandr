package org.jormungandr.dataframe.ui

import com.intellij.ui.JBColor
import org.jormungandr.core.theme.DataGridThemeTokens
import org.jormungandr.dataframe.model.DataTypeCategory
import java.awt.Color
import java.awt.Component
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.SwingConstants
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellRenderer

fun parseHexColor(hex: String, fallback: Color): Color {
    return runCatching { Color.decode(hex) }.getOrDefault(fallback)
}

/**
 * Custom Header Renderer displaying column name, category badge (e.g., [int], [str]),
 * sort order indicator, and summary statistics tooltip.
 */
class DataFrameHeaderRenderer(
    private var themeTokens: DataGridThemeTokens
) : TableCellRenderer {

    private val label = JLabel()
    var sortColumn: Int = -1
    var sortAscending: Boolean = true

    fun updateTheme(tokens: DataGridThemeTokens) {
        this.themeTokens = tokens
    }

    override fun getTableCellRendererComponent(
        table: JTable?,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): Component {
        val model = table?.model as? DataFrameTableModel
        val meta = model?.getColumnMetadata(column)

        val headerBg = parseHexColor(themeTokens.headerBackground, Color(240, 240, 240))
        val headerFg = parseHexColor(themeTokens.headerForeground, Color(40, 40, 40))

        label.isOpaque = true
        label.background = headerBg
        label.foreground = headerFg
        label.font = label.font.deriveFont(Font.BOLD, 12f)
        label.border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 1, parseHexColor(themeTokens.gridLineColor, Color(220, 220, 220))),
            BorderFactory.createEmptyBorder(6, 8, 6, 8)
        )

        if (column == 0) {
            label.text = "#"
            label.horizontalAlignment = SwingConstants.CENTER
            label.toolTipText = "Row index"
        } else {
            val colName = value?.toString() ?: ""
            val badge = meta?.category?.badgeShort ?: ""
            val sortIndicator = if (column == sortColumn) (if (sortAscending) " ▲" else " ▼") else ""

            label.text = "$colName ($badge)$sortIndicator"
            label.horizontalAlignment = if (meta?.category == DataTypeCategory.INTEGER || meta?.category == DataTypeCategory.FLOAT) {
                SwingConstants.RIGHT
            } else {
                SwingConstants.LEFT
            }

            // Rich summary tooltip
            if (meta != null) {
                label.toolTipText = buildString {
                    append("<html><body style='padding: 4px;'>")
                    append("<b>Column:</b> ${meta.name} (<i>${meta.typeName}</i>)<br/>")
                    append("<b>Total Rows:</b> ${meta.totalCount}<br/>")
                    append("<b>Nulls:</b> ${meta.nullCount} (%.1f%%)<br/>".format(meta.nullPercentage))
                    append("<b>Distinct:</b> ${meta.distinctCount}<br/>")
                    if (meta.minVal != null) append("<b>Min:</b> ${meta.minVal}<br/>")
                    if (meta.maxVal != null) append("<b>Max:</b> ${meta.maxVal}<br/>")
                    if (meta.meanVal != null) append("<b>Mean:</b> %.2f<br/>".format(meta.meanVal))
                    if (meta.stdDev != null) append("<b>StdDev:</b> %.2f<br/>".format(meta.stdDev))
                    append("</body></html>")
                }
            }
        }

        return label
    }
}

/**
 * Custom Cell Renderer applying alternating row styling, right/left alignment,
 * and distinct rendering for missing/null values.
 */
class DataFrameCellRenderer(
    private var themeTokens: DataGridThemeTokens
) : DefaultTableCellRenderer() {

    fun updateTheme(tokens: DataGridThemeTokens) {
        this.themeTokens = tokens
    }

    override fun getTableCellRendererComponent(
        table: JTable?,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): Component {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)

        val model = table?.model as? DataFrameTableModel
        val meta = model?.getColumnMetadata(column)

        val rowEven = parseHexColor(themeTokens.rowEvenBackground, Color(255, 255, 255))
        val rowOdd = parseHexColor(themeTokens.rowOddBackground, Color(248, 248, 248))
        val selBg = parseHexColor(themeTokens.selectionBackground, Color(38, 139, 210, 80))
        val selFg = parseHexColor(themeTokens.selectionForeground, Color(0, 0, 0))

        if (isSelected) {
            background = selBg
            foreground = selFg
        } else {
            background = if (row % 2 == 0) rowEven else rowOdd
            foreground = parseHexColor(themeTokens.headerForeground, Color(30, 30, 30))
        }

        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 1, parseHexColor(themeTokens.gridLineColor, Color(230, 230, 230))),
            BorderFactory.createEmptyBorder(4, 6, 4, 6)
        )

        if (column == 0) {
            // Row index gutter
            horizontalAlignment = SwingConstants.CENTER
            foreground = Color(140, 140, 140)
            font = font.deriveFont(Font.PLAIN, 11f)
            text = value?.toString() ?: ""
        } else if (value == null) {
            horizontalAlignment = SwingConstants.LEFT
            font = font.deriveFont(Font.ITALIC)
            foreground = Color(160, 160, 160)
            text = "null"
        } else {
            font = font.deriveFont(Font.PLAIN, 12f)
            when (meta?.category) {
                DataTypeCategory.INTEGER -> {
                    horizontalAlignment = SwingConstants.RIGHT
                    text = value.toString()
                }
                DataTypeCategory.FLOAT -> {
                    horizontalAlignment = SwingConstants.RIGHT
                    text = when (value) {
                        is Double -> "%.4f".format(value)
                        is Float -> "%.4f".format(value)
                        else -> value.toString()
                    }
                }
                DataTypeCategory.BOOLEAN -> {
                    horizontalAlignment = SwingConstants.CENTER
                    text = value.toString()
                }
                else -> {
                    horizontalAlignment = SwingConstants.LEFT
                    text = value.toString()
                }
            }
        }

        return this
    }
}
