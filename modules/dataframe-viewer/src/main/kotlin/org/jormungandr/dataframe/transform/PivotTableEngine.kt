package org.jormungandr.dataframe.transform

import org.jormungandr.dataframe.model.ColumnMetadata
import org.jormungandr.dataframe.model.DataFrame
import org.jormungandr.dataframe.model.DataTypeCategory
import kotlin.math.pow
import kotlin.math.sqrt

enum class PivotAggType(val displayName: String) {
    SUM("Sum"),
    AVERAGE("Average (Mean)"),
    COUNT("Count"),
    MIN("Min"),
    MAX("Max"),
    MEDIAN("Median"),
    STDDEV("Std Dev")
}

data class PivotConfig(
    val rowField: String,
    val columnField: String,
    val valueField: String,
    val aggregation: PivotAggType = PivotAggType.SUM,
    val includeTotals: Boolean = true
)

/**
 * High-performance 2D Cross-Tabulation & Pivot Table Calculation Engine.
 */
object PivotTableEngine {

    fun generatePivot(df: DataFrame, config: PivotConfig): DataFrame {
        if (df.isEmpty) return DataFrame.empty("Pivot")

        val rIdx = df.getColumnIndex(config.rowField)
        val cIdx = df.getColumnIndex(config.columnField)
        val vIdx = df.getColumnIndex(config.valueField)

        if (rIdx < 0 || cIdx < 0 || vIdx < 0) {
            return DataFrame.empty("Pivot")
        }

        val rowValues = df.rows.map { it.getOrNull(rIdx)?.toString() ?: "None" }.distinct().sorted()
        val colValues = df.rows.map { it.getOrNull(cIdx)?.toString() ?: "None" }.distinct().sorted()

        // Group rows into a map of (rowVal, colVal) -> list of numeric values
        val cellBuckets = mutableMapOf<Pair<String, String>, MutableList<Double>>()
        val rowBuckets = mutableMapOf<String, MutableList<Double>>()
        val colBuckets = mutableMapOf<String, MutableList<Double>>()
        val grandBucket = mutableListOf<Double>()

        for (row in df.rows) {
            val rVal = row.getOrNull(rIdx)?.toString() ?: "None"
            val cVal = row.getOrNull(cIdx)?.toString() ?: "None"
            val rawNum = row.getOrNull(vIdx)
            val num = rawNum?.toString()?.toDoubleOrNull() ?: if (config.aggregation == PivotAggType.COUNT) 1.0 else 0.0

            cellBuckets.getOrPut(Pair(rVal, cVal)) { mutableListOf() }.add(num)
            rowBuckets.getOrPut(rVal) { mutableListOf() }.add(num)
            colBuckets.getOrPut(cVal) { mutableListOf() }.add(num)
            grandBucket.add(num)
        }

        // Columns of the pivot DataFrame
        val resultCols = mutableListOf<ColumnMetadata>()
        resultCols.add(ColumnMetadata(name = config.rowField, typeName = "String", category = DataTypeCategory.STRING))

        for (col in colValues) {
            resultCols.add(ColumnMetadata(name = col, typeName = "Float", category = DataTypeCategory.FLOAT))
        }

        if (config.includeTotals) {
            resultCols.add(ColumnMetadata(name = "Total", typeName = "Float", category = DataTypeCategory.FLOAT))
        }

        // Construct rows
        val resultRows = mutableListOf<List<Any?>>()
        for (r in rowValues) {
            val rowCells = mutableListOf<Any?>()
            rowCells.add(r)

            for (c in colValues) {
                val vals = cellBuckets[Pair(r, c)] ?: emptyList()
                val aggVal = computeAgg(vals, config.aggregation)
                rowCells.add(aggVal)
            }

            if (config.includeTotals) {
                val rowVals = rowBuckets[r] ?: emptyList()
                rowCells.add(computeAgg(rowVals, config.aggregation))
            }
            resultRows.add(rowCells)
        }

        // Grand Total row
        if (config.includeTotals) {
            val totalRow = mutableListOf<Any?>()
            totalRow.add("Grand Total")

            for (c in colValues) {
                val cVals = colBuckets[c] ?: emptyList()
                totalRow.add(computeAgg(cVals, config.aggregation))
            }
            totalRow.add(computeAgg(grandBucket, config.aggregation))
            resultRows.add(totalRow)
        }

        return DataFrame(
            name = "Pivot_${config.rowField}_vs_${config.columnField}",
            columns = resultCols,
            rows = resultRows
        )
    }

    private fun computeAgg(values: List<Double>, agg: PivotAggType): Any? {
        if (values.isEmpty()) return 0.0

        return when (agg) {
            PivotAggType.SUM -> Math.round(values.sum() * 100.0) / 100.0
            PivotAggType.AVERAGE -> Math.round((values.average()) * 100.0) / 100.0
            PivotAggType.COUNT -> values.size.toLong()
            PivotAggType.MIN -> values.minOrNull() ?: 0.0
            PivotAggType.MAX -> values.maxOrNull() ?: 0.0
            PivotAggType.MEDIAN -> {
                val sorted = values.sorted()
                val mid = sorted.size / 2
                if (sorted.size % 2 == 0) {
                    Math.round(((sorted[mid - 1] + sorted[mid]) / 2.0) * 100.0) / 100.0
                } else {
                    sorted[mid]
                }
            }
            PivotAggType.STDDEV -> {
                if (values.size < 2) return 0.0
                val mean = values.average()
                val variance = values.map { (it - mean).pow(2) }.sum() / (values.size - 1)
                Math.round(sqrt(variance) * 100.0) / 100.0
            }
        }
    }
}
