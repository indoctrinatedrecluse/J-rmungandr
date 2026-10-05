package org.jormungandr.dataframe.io

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.jormungandr.dataframe.model.DataFrame

/**
 * Exporter utilities for DataFrame instances across CSV, TSV, Markdown, JSON, and HTML.
 */
object DataFrameExporter {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().serializeNulls().create()
    private val compactGson: Gson = GsonBuilder().serializeNulls().create()

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

    fun toJsonLines(df: DataFrame): String {
        val sb = StringBuilder()
        for (row in df.rows) {
            val map = linkedMapOf<String, Any?>()
            df.columns.forEachIndexed { idx, col ->
                map[col.name] = row.getOrNull(idx)
            }
            sb.append(compactGson.toJson(map)).append("\n")
        }
        return sb.toString()
    }

    fun toExcelXml(df: DataFrame): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\"?>\n")
        sb.append("<?mso-application progid=\"Excel.Sheet\"?>\n")
        sb.append("<Workbook xmlns=\"urn:schemas-microsoft-com:office:spreadsheet\"\n")
        sb.append(" xmlns:ss=\"urn:schemas-microsoft-com:office:spreadsheet\">\n")
        sb.append("  <Styles>\n")
        sb.append("    <Style ss:ID=\"Header\"><Font ss:Bold=\"1\"/><Interior ss:Color=\"#E2E8F0\" ss:Pattern=\"Solid\"/></Style>\n")
        sb.append("  </Styles>\n")
        sb.append("  <Worksheet ss:Name=\"").append(escapeHtml(df.name)).append("\">\n")
        sb.append("    <Table>\n")

        // Header Row
        sb.append("      <Row ss:StyleID=\"Header\">\n")
        for (col in df.columns) {
            sb.append("        <Cell><Data ss:Type=\"String\">").append(escapeHtml(col.name)).append("</Data></Cell>\n")
        }
        sb.append("      </Row>\n")

        // Data Rows
        for (row in df.rows) {
            sb.append("      <Row>\n")
            for (idx in df.columns.indices) {
                val value = row.getOrNull(idx)
                val col = df.columns[idx]
                if (value == null) {
                    sb.append("        <Cell><Data ss:Type=\"String\"></Data></Cell>\n")
                } else if (col.isNumeric && value is Number) {
                    sb.append("        <Cell><Data ss:Type=\"Number\">").append(value).append("</Data></Cell>\n")
                } else {
                    sb.append("        <Cell><Data ss:Type=\"String\">").append(escapeHtml(value.toString())).append("</Data></Cell>\n")
                }
            }
            sb.append("      </Row>\n")
        }

        sb.append("    </Table>\n")
        sb.append("  </Worksheet>\n")
        sb.append("</Workbook>\n")
        return sb.toString()
    }

    fun toSqlInsert(df: DataFrame, tableName: String = df.name): String {
        val cleanTable = tableName.replace(" ", "_").lowercase()
        val sb = StringBuilder()
        sb.append("-- Generated by Jörmungandr DataFrame Studio\n")
        sb.append("CREATE TABLE IF NOT EXISTS ").append(cleanTable).append(" (\n")
        val colDefs = df.columns.joinToString(",\n") { col ->
            val sqlType = when {
                col.isNumeric && col.typeName.contains("int", ignoreCase = true) -> "BIGINT"
                col.isNumeric -> "DOUBLE PRECISION"
                col.typeName.contains("bool", ignoreCase = true) -> "BOOLEAN"
                col.typeName.contains("time", ignoreCase = true) -> "TIMESTAMP"
                else -> "TEXT"
            }
            "    ${col.name.replace(" ", "_")} $sqlType"
        }
        sb.append(colDefs).append("\n);\n\n")

        for (chunk in df.rows.chunked(100)) {
            sb.append("INSERT INTO ").append(cleanTable).append(" (")
            sb.append(df.columns.joinToString(", ") { it.name.replace(" ", "_") })
            sb.append(") VALUES\n")

            val valueRows = chunk.joinToString(",\n") { row ->
                val vals = row.mapIndexed { idx, v ->
                    val col = df.columns.getOrNull(idx)
                    when {
                        v == null -> "NULL"
                        col != null && col.isNumeric -> v.toString()
                        v is Boolean -> if (v) "TRUE" else "FALSE"
                        else -> "'" + v.toString().replace("'", "''") + "'"
                    }
                }
                "    (" + vals.joinToString(", ") + ")"
            }
            sb.append(valueRows).append(";\n\n")
        }
        return sb.toString()
    }

    fun toParquetBytes(df: DataFrame): ByteArray {
        return ParquetDataLoader.createSampleParquetBytes(
            columns = df.columns.map { it.name },
            rows = df.rows
        )
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
