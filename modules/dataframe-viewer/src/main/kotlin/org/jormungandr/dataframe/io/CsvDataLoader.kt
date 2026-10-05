package org.jormungandr.dataframe.io

import org.jormungandr.dataframe.model.ColumnMetadata
import org.jormungandr.dataframe.model.DataFrame
import org.jormungandr.dataframe.model.DataTypeCategory
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.StringReader
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Robust RFC-4180 CSV & TSV parser with delimiter auto-detection and data type inference.
 */
object CsvDataLoader {

    private val CANDIDATE_DELIMITERS = charArrayOf(',', '\t', ';', '|')

    fun loadFromString(name: String, content: String): DataFrame {
        return loadFromReader(name, BufferedReader(StringReader(content)))
    }

    fun loadFromStream(name: String, inputStream: InputStream): DataFrame {
        return loadFromReader(name, BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)))
    }

    fun loadFromReader(name: String, reader: BufferedReader): DataFrame {
        val lines = mutableListOf<String>()
        var count = 0
        while (count < 20000) { // Read sample/dataset chunk
            val line = reader.readLine() ?: break
            lines.add(line)
            count++
        }

        if (lines.isEmpty()) {
            return DataFrame.empty(name)
        }

        val delimiter = detectDelimiter(lines)
        val rawTable = parseRecords(lines, delimiter)

        if (rawTable.isEmpty()) {
            return DataFrame.empty(name)
        }

        val headerRow = rawTable[0]
        val dataRows = if (rawTable.size > 1) rawTable.subList(1, rawTable.size) else emptyList()

        val columnNames = headerRow.mapIndexed { i, col ->
            val clean = col.trim()
            if (clean.isBlank()) "column_${i + 1}" else clean
        }

        // Infer types
        val inferredTypes = columnNames.indices.map { colIdx ->
            val sampleValues = dataRows.take(500).mapNotNull { it.getOrNull(colIdx) }
            inferType(sampleValues)
        }

        // Parse typed cells
        val typedRows = dataRows.map { row ->
            columnNames.indices.map { colIdx ->
                val rawVal = row.getOrNull(colIdx)
                val type = inferredTypes[colIdx]
                parseCell(rawVal, type)
            }
        }

        val colPairs = columnNames.mapIndexed { idx, colName ->
            Pair(colName, inferredTypes[idx])
        }

        return DataFrame.buildWithStatistics(name, colPairs, typedRows)
    }

    fun detectDelimiter(sampleLines: List<String>): Char {
        val candidates = CANDIDATE_DELIMITERS
        var bestDelimiter = ','
        var maxConsistency = -1

        for (delim in candidates) {
            val counts = sampleLines.take(10).map { countUnquotedChars(it, delim) }
            val first = counts.firstOrNull() ?: 0
            if (first > 0 && counts.all { it == first }) {
                return delim
            }
            val avg = if (counts.isNotEmpty()) counts.average() else 0.0
            if (avg > maxConsistency) {
                maxConsistency = avg.toInt()
                bestDelimiter = delim
            }
        }

        return bestDelimiter
    }

    private fun countUnquotedChars(line: String, target: Char): Int {
        var count = 0
        var inQuotes = false
        for (ch in line) {
            if (ch == '"') inQuotes = !inQuotes
            else if (ch == target && !inQuotes) count++
        }
        return count
    }

    fun parseRecords(lines: List<String>, delimiter: Char): List<List<String>> {
        val records = mutableListOf<List<String>>()
        val currentRecord = mutableListOf<String>()
        val currentField = StringBuilder()
        var inQuotes = false

        for (line in lines) {
            val chars = line.toCharArray()
            var i = 0
            while (i < chars.size) {
                val c = chars[i]
                if (c == '"') {
                    if (inQuotes && i + 1 < chars.size && chars[i + 1] == '"') {
                        currentField.append('"')
                        i++ // Skip escaped quote
                    } else {
                        inQuotes = !inQuotes
                    }
                } else if (c == delimiter && !inQuotes) {
                    currentRecord.add(currentField.toString())
                    currentField.setLength(0)
                } else {
                    currentField.append(c)
                }
                i++
            }

            if (!inQuotes) {
                currentRecord.add(currentField.toString())
                currentField.setLength(0)
                records.add(currentRecord.toList())
                currentRecord.clear()
            } else {
                currentField.append("\n")
            }
        }

        if (currentRecord.isNotEmpty() || currentField.isNotEmpty()) {
            currentRecord.add(currentField.toString())
            records.add(currentRecord.toList())
        }

        return records
    }

    fun inferType(sampleValues: List<String>): DataTypeCategory {
        val nonNulls = sampleValues.map { it.trim() }.filter { !isNullValue(it) }
        if (nonNulls.isEmpty()) return DataTypeCategory.STRING

        if (nonNulls.all { isBoolean(it) }) return DataTypeCategory.BOOLEAN
        if (nonNulls.all { it.toLongOrNull() != null }) return DataTypeCategory.INTEGER
        if (nonNulls.all { it.toDoubleOrNull() != null }) return DataTypeCategory.FLOAT
        if (nonNulls.all { isDateTime(it) }) return DataTypeCategory.DATETIME

        return DataTypeCategory.STRING
    }

    private fun parseCell(raw: String?, type: DataTypeCategory): Any? {
        if (raw == null) return null
        val trimmed = raw.trim()
        if (isNullValue(trimmed)) return null

        return when (type) {
            DataTypeCategory.INTEGER -> trimmed.toLongOrNull() ?: trimmed
            DataTypeCategory.FLOAT -> trimmed.toDoubleOrNull() ?: trimmed
            DataTypeCategory.BOOLEAN -> parseBoolean(trimmed)
            DataTypeCategory.DATETIME -> trimmed
            else -> trimmed
        }
    }

    private fun isNullValue(s: String): Boolean {
        val lower = s.lowercase()
        return s.isEmpty() || lower == "null" || lower == "na" || lower == "nan" || lower == "none" || lower == "\\n"
    }

    private fun isBoolean(s: String): Boolean {
        val lower = s.lowercase()
        return lower == "true" || lower == "false" || lower == "t" || lower == "f" || lower == "yes" || lower == "no"
    }

    private fun parseBoolean(s: String): Boolean {
        val lower = s.lowercase()
        return lower == "true" || lower == "t" || lower == "yes" || lower == "1"
    }

    private fun isDateTime(s: String): Boolean {
        if (s.length < 8) return false
        return runCatching { LocalDate.parse(s) }.isSuccess ||
               runCatching { LocalDateTime.parse(s) }.isSuccess ||
               runCatching { LocalDate.parse(s, DateTimeFormatter.ISO_DATE) }.isSuccess
    }
}
