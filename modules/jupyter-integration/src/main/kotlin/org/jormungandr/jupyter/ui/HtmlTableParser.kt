package org.jormungandr.jupyter.ui

/**
 * Lightweight extractor for tabular HTML data (e.g. Pandas and Polars _repr_html_ tables).
 * Converts HTML tables into structured column names, rows, and CSV format for Jörmungandr DataFrame Studio.
 */
object HtmlTableParser {

    data class ParsedTable(
        val headers: List<String>,
        val rows: List<List<String>>
    ) {
        fun toCsv(): String {
            val sb = StringBuilder()
            sb.append(headers.joinToString(",") { escapeCsv(it) }).append("\n")
            for (row in rows) {
                sb.append(row.joinToString(",") { escapeCsv(it) }).append("\n")
            }
            return sb.toString()
        }

        private fun escapeCsv(cell: String): String {
            return if (cell.contains(",") || cell.contains("\"") || cell.contains("\n")) {
                "\"" + cell.replace("\"", "\"\"") + "\""
            } else {
                cell
            }
        }
    }

    fun isHtmlTable(html: String): Boolean {
        val lower = html.lowercase()
        return lower.contains("<table") && lower.contains("</table>") && lower.contains("<tr")
    }

    fun parse(html: String): ParsedTable? {
        if (!isHtmlTable(html)) return null

        val tableContent = extractBetween(html, "<table", "</table>") ?: return null

        val rows = mutableListOf<List<String>>()
        val rowRegex = Regex("<tr[^>]*>([\\s\\S]*?)</tr>", RegexOption.IGNORE_CASE)
        val thRegex = Regex("<th[^>]*>([\\s\\S]*?)</th>", RegexOption.IGNORE_CASE)
        val tdRegex = Regex("<td[^>]*>([\\s\\S]*?)</td>", RegexOption.IGNORE_CASE)

        var headerRow: List<String>? = null

        val matches = rowRegex.findAll(tableContent)
        for (match in matches) {
            val rowHtml = match.groupValues[1]

            val thCells = thRegex.findAll(rowHtml).map { cleanCell(it.groupValues[1]) }.toList()
            val tdCells = tdRegex.findAll(rowHtml).map { cleanCell(it.groupValues[1]) }.toList()

            if (headerRow == null && thCells.isNotEmpty() && tdCells.isEmpty()) {
                headerRow = thCells
            } else if (thCells.isNotEmpty() || tdCells.isNotEmpty()) {
                val fullRow = mutableListOf<String>()
                fullRow.addAll(thCells)
                fullRow.addAll(tdCells)
                rows.add(fullRow)
            }
        }

        if (rows.isEmpty()) return null

        val finalHeaders = if (headerRow != null && headerRow.isNotEmpty()) {
            headerRow
        } else {
            val first = rows.removeAt(0)
            first
        }

        // Align row sizes with header length if index columns are present
        val normalizedHeaders = finalHeaders.mapIndexed { idx, h -> if (h.isBlank()) "index" else h }

        return ParsedTable(
            headers = normalizedHeaders,
            rows = rows
        )
    }

    private fun cleanCell(html: String): String {
        return html
            .replace(Regex("<[^>]*>"), "") // strip nested tags
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .trim()
    }

    private fun extractBetween(source: String, startTag: String, endTag: String): String? {
        val sIdx = source.indexOf(startTag, ignoreCase = true)
        if (sIdx < 0) return null
        val eIdx = source.indexOf(endTag, sIdx, ignoreCase = true)
        if (eIdx < 0) return null
        return source.substring(sIdx, eIdx + endTag.length)
    }
}
