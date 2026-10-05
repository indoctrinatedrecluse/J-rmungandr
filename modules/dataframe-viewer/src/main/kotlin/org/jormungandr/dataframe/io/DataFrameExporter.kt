package org.jormungandr.dataframe.io

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.jormungandr.dataframe.model.DataFrame

/**
 * Exporter utilities for DataFrame instances across CSV, TSV, Markdown, JSON, and HTML.
 */
object DataFrameExporter {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().serializeNulls().create()

    fun toCsv(df: DataFrame, delimiter: Char = ','): String {
        val sb = StringBuilder()
        // Header
        sb.append(df.columns.joinToString(delimiter.toString()) { escapeCell(it.name, delimiter) })
        sb.append("\n")

        // Rows
        for (row in df.rows) {
            sb.append(row.joinToString(delimiter.toString()) { escapeCell(it?.toString() ?: "", delimiter) })
            sb.append("\n")
        }
        return sb.toString()
    }

    fun toTsv(df: DataFrame): String = toCsv(df, '\t')

    fun toMarkdown(df: DataFrame, maxRows: Int = 100): String {
        if (df.isEmpty) return "*Empty DataFrame*"
        val sb = StringBuilder()

        val displayRows = if (df.rowCount > maxRows) df.rows.take(maxRows) else df.rows

        // Header
        sb.append("| ").append(df.columns.joinToString(" | ") { it.name.replace("|", "\\|") }).append(" |\n")
        // Separator
        sb.append("| ").append(df.columns.joinToString(" | ") { "---" }).append(" |\n")

        // Rows
        for (row in displayRows) {
            sb.append("| ").append(row.joinToString(" | ") { (it?.toString() ?: "").replace("|", "\\|") }).append(" |\n")
        }

        if (df.rowCount > maxRows) {
            sb.append("\n*Showing top $maxRows of ${df.rowCount} rows*\n")
        }
        return sb.toString()
    }

    fun toJson(df: DataFrame): String {
        val records = df.rows.map { row ->
            val map = linkedMapOf<String, Any?>()
            df.columns.forEachIndexed { idx, col ->
                map[col.name] = row.getOrNull(idx)
            }
            map
        }
        return gson.toJson(records)
    }

    fun toHtmlTable(df: DataFrame): String {
        val sb = StringBuilder()
        sb.append("<table border=\"1\" cellpadding=\"4\" cellspacing=\"0\">\n")
        sb.append("<thead><tr>")
        for (col in df.columns) {
            sb.append("<th>").append(escapeHtml(col.name)).append("</th>")
        }
        sb.append("</tr></thead>\n<tbody>\n")

        for (row in df.rows) {
            sb.append("<tr>")
            for (cell in row) {
                sb.append("<td>").append(escapeHtml(cell?.toString() ?: "")).append("</td>")
            }
            sb.append("</tr>\n")
        }
        sb.append("</tbody></table>")
        return sb.toString()
    }

    private fun escapeCell(cell: String, delimiter: Char): String {
        return if (cell.contains(delimiter) || cell.contains('"') || cell.contains('\n') || cell.contains('\r')) {
            "\"" + cell.replace("\"", "\"\"") + "\""
        } else {
            cell
        }
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }
}
