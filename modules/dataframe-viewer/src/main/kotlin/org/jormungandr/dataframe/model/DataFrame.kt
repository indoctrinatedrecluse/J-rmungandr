package org.jormungandr.dataframe.model

import java.time.format.DateTimeFormatter
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * High-performance immutable tabular DataFrame model for Jörmungandr.
 * Supports typed columns, metadata statistics, vectorized sorting, and filtering.
 */
data class DataFrame(
    val name: String,
    val columns: List<ColumnMetadata>,
    val rows: List<List<Any?>>
) {
    val rowCount: Int get() = rows.size
    val columnCount: Int get() = columns.size
    val shape: Pair<Int, Int> get() = Pair(rowCount, columnCount)
    val isEmpty: Boolean get() = rows.isEmpty() || columns.isEmpty()

    fun getColumnIndex(name: String): Int {
        return columns.indexOfFirst { it.name.equals(name, ignoreCase = true) }
    }

    fun getColumn(index: Int): ColumnMetadata = columns[index]

    fun getColumnValues(index: Int): List<Any?> {
        if (index !in columns.indices) return emptyList()
        return rows.map { it.getOrNull(index) }
    }

    fun getColumnValues(name: String): List<Any?> {
        val idx = getColumnIndex(name)
        return if (idx >= 0) getColumnValues(idx) else emptyList()
    }

    /**
     * Filters rows by a custom predicate.
     */
    fun filter(predicate: (List<Any?>) -> Boolean): DataFrame {
        val filteredRows = rows.filter(predicate)
        return copy(rows = filteredRows)
    }

    /**
     * Quick textual filter across all columns or targeted `col:query`.
     */
    fun filterText(query: String): DataFrame {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return this

        if (trimmed.contains(":")) {
            val parts = trimmed.split(":", limit = 2)
            val colName = parts[0].trim()
            val colVal = parts[1].trim().lowercase()
            val colIdx = getColumnIndex(colName)
            if (colIdx >= 0) {
                return filter { row ->
                    val cell = row.getOrNull(colIdx)?.toString()?.lowercase() ?: ""
                    cell.contains(colVal)
                }
            }
        }

        val lowerQuery = trimmed.lowercase()
        return filter { row ->
            row.any { cell -> cell?.toString()?.lowercase()?.contains(lowerQuery) == true }
        }
    }

    /**
     * Sorts dataframe rows by the specified column index.
     */
    fun sort(columnIndex: Int, ascending: Boolean = true): DataFrame {
        if (columnIndex !in columns.indices) return this

        val sortedRows = rows.sortedWith { rowA, rowB ->
            val valA = rowA.getOrNull(columnIndex)
            val valB = rowB.getOrNull(columnIndex)

            val comparison = compareValuesGeneric(valA, valB)
            if (ascending) comparison else -comparison
        }

        return copy(rows = sortedRows)
    }

    /**
     * Slices the dataframe rows by offset and limit.
     */
    fun slice(offset: Int, limit: Int): DataFrame {
        val start = offset.coerceIn(0, rowCount)
        val end = (start + limit).coerceIn(start, rowCount)
        return copy(rows = rows.subList(start, end))
    }

    fun head(n: Int = 10): DataFrame = slice(0, n)
    fun tail(n: Int = 10): DataFrame = slice((rowCount - n).coerceAtLeast(0), n)

    /**
     * Estimates in-memory size of data in bytes.
     */
    fun estimateMemoryBytes(): Long {
        var bytes = 0L
        for (col in columns) {
            bytes += col.name.length * 2L + 64L
        }
        for (row in rows) {
            bytes += 32L // row overhead
            for (cell in row) {
                bytes += when (cell) {
                    null -> 8L
                    is Number -> 16L
                    is Boolean -> 8L
                    is String -> 24L + (cell.length * 2L)
                    else -> 48L
                }
            }
        }
        return bytes
    }

    companion object {
        fun empty(name: String = "empty"): DataFrame = DataFrame(name, emptyList(), emptyList())

        /**
         * Builds a DataFrame and automatically computes column statistics (min, max, mean, stdDev, histograms).
         */
        fun buildWithStatistics(
            name: String,
            rawColumns: List<Pair<String, DataTypeCategory>>,
            rows: List<List<Any?>>
        ): DataFrame {
            val totalRows = rows.size.toLong()
            val computedColumns = rawColumns.mapIndexed { idx, (colName, category) ->
                val colValues = rows.map { it.getOrNull(idx) }
                val nullCount = colValues.count { it == null }.toLong()
                val nonNull = colValues.filterNotNull()
                val distinctCount = nonNull.distinct().size.toLong()

                var minStr: String? = null
                var maxStr: String? = null
                var mean: Double? = null
                var stdDev: Double? = null
                var median: Double? = null
                val bins = mutableListOf<HistogramBin>()

                if (nonNull.isNotEmpty()) {
                    when (category) {
                        DataTypeCategory.INTEGER, DataTypeCategory.FLOAT -> {
                            val numbers = nonNull.mapNotNull {
                                when (it) {
                                    is Number -> it.toDouble()
                                    is String -> it.toDoubleOrNull()
                                    else -> null
                                }
                            }
                            if (numbers.isNotEmpty()) {
                                val minNum = numbers.minOrNull() ?: 0.0
                                val maxNum = numbers.maxOrNull() ?: 0.0
                                val avg = numbers.average()
                                val variance = numbers.map { (it - avg).pow(2) }.average()
                                val sd = sqrt(variance)

                                val sortedNums = numbers.sorted()
                                val med = if (sortedNums.size % 2 == 0) {
                                    (sortedNums[sortedNums.size / 2 - 1] + sortedNums[sortedNums.size / 2]) / 2.0
                                } else {
                                    sortedNums[sortedNums.size / 2]
                                }

                                minStr = if (category == DataTypeCategory.INTEGER) minNum.toLong().toString() else "%.4f".format(minNum)
                                maxStr = if (category == DataTypeCategory.INTEGER) maxNum.toLong().toString() else "%.4f".format(maxNum)
                                mean = avg
                                stdDev = sd
                                median = med

                                // Compute 5-bin histogram
                                val binCount = 5
                                val step = if (maxNum > minNum) (maxNum - minNum) / binCount else 1.0
                                val counts = IntArray(binCount)
                                for (num in numbers) {
                                    val b = if (step == 0.0) 0 else ((num - minNum) / step).toInt().coerceIn(0, binCount - 1)
                                    counts[b]++
                                }
                                for (i in 0 until binCount) {
                                    val lower = minNum + (i * step)
                                    val upper = lower + step
                                    bins.add(HistogramBin("[%.1f-%.1f]".format(lower, upper), counts[i]))
                                }
                            }
                        }
                        else -> {
                            val strings = nonNull.map { it.toString() }
                            minStr = strings.minOrNull()
                            maxStr = strings.maxOrNull()

                            // Frequency for top distinct values
                            val freq = strings.groupingBy { it }.eachCount().entries
                                .sortedByDescending { it.value }
                                .take(5)
                            for ((k, count) in freq) {
                                bins.add(HistogramBin(k, count))
                            }
                        }
                    }
                }

                ColumnMetadata(
                    name = colName,
                    typeName = category.displayName,
                    category = category,
                    nullCount = nullCount,
                    totalCount = totalRows,
                    distinctCount = distinctCount,
                    minVal = minStr,
                    maxVal = maxStr,
                    meanVal = mean,
                    stdDev = stdDev,
                    medianVal = median,
                    histogramBins = bins
                )
            }

            return DataFrame(name, computedColumns, rows)
        }

        private fun compareValuesGeneric(a: Any?, b: Any?): Int {
            if (a == null && b == null) return 0
            if (a == null) return -1
            if (b == null) return 1

            if (a is Number && b is Number) {
                return a.toDouble().compareTo(b.toDouble())
            }

            if (a is Comparable<*> && b is Comparable<*>) {
                @Suppress("UNCHECKED_CAST")
                val compA = a as? Comparable<Any>
                if (compA != null && a.javaClass.isAssignableFrom(b.javaClass)) {
                    return compA.compareTo(b)
                }
            }

            return a.toString().compareTo(b.toString(), ignoreCase = true)
        }
    }
}
