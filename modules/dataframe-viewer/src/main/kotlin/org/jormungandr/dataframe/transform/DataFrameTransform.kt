package org.jormungandr.dataframe.transform

import org.jormungandr.dataframe.model.DataFrame
import org.jormungandr.dataframe.model.DataTypeCategory

enum class AggregationType(val displayName: String) {
    COUNT("Count"),
    SUM("Sum"),
    MEAN("Mean (Average)"),
    MIN("Minimum"),
    MAX("Maximum"),
    MEDIAN("Median"),
    DISTINCT_COUNT("Distinct Count")
}

object DataFrameTransform {

    /**
     * Performs SQL-style GROUP BY aggregation on a DataFrame.
     * Groups by [groupColumn] and calculates [aggType] of [metricColumn].
     */
    fun groupBy(
        df: DataFrame,
        groupColumn: String,
        metricColumn: String,
        aggType: AggregationType
    ): DataFrame {
        val groupIdx = df.getColumnIndex(groupColumn)
        val metricIdx = df.getColumnIndex(metricColumn)
        if (groupIdx < 0 || metricIdx < 0) return df

        val groups = df.rows.groupBy { it.getOrNull(groupIdx)?.toString() ?: "(null)" }
        val resultRows = mutableListOf<List<Any?>>()

        for ((groupKey, rowList) in groups) {
            val metricValues = rowList.mapNotNull { it.getOrNull(metricIdx) }
            val aggResult: Any? = when (aggType) {
                AggregationType.COUNT -> rowList.size.toLong()
                AggregationType.DISTINCT_COUNT -> metricValues.distinct().size.toLong()
                AggregationType.SUM -> {
                    val nums = metricValues.mapNotNull { toDouble(it) }
                    nums.sum()
                }
                AggregationType.MEAN -> {
                    val nums = metricValues.mapNotNull { toDouble(it) }
                    if (nums.isNotEmpty()) nums.average() else null
                }
                AggregationType.MIN -> {
                    val nums = metricValues.mapNotNull { toDouble(it) }
                    nums.minOrNull()
                }
                AggregationType.MAX -> {
                    val nums = metricValues.mapNotNull { toDouble(it) }
                    nums.maxOrNull()
                }
                AggregationType.MEDIAN -> {
                    val nums = metricValues.mapNotNull { toDouble(it) }.sorted()
                    if (nums.isEmpty()) null
                    else if (nums.size % 2 == 0) (nums[nums.size / 2 - 1] + nums[nums.size / 2]) / 2.0
                    else nums[nums.size / 2]
                }
            }

            resultRows.add(listOf(groupKey, aggResult))
        }

        val resultMetricCat = when (aggType) {
            AggregationType.COUNT, AggregationType.DISTINCT_COUNT -> DataTypeCategory.INTEGER
            AggregationType.MEAN, AggregationType.SUM, AggregationType.MEDIAN -> DataTypeCategory.FLOAT
            else -> df.columns[metricIdx].category
        }

        val colDescs = listOf(
            Pair(groupColumn, DataTypeCategory.STRING),
            Pair("${metricColumn}_${aggType.name.lowercase()}", resultMetricCat)
        )

        return DataFrame.buildWithStatistics("${df.name}_groupby", colDescs, resultRows)
    }

    /**
     * Computes full summary statistics (count, mean, std, min, 25%, 50%, 75%, max)
     * across all numeric columns, matching Pandas df.describe().
     */
    fun describe(df: DataFrame): DataFrame {
        val numericCols = df.columns.filter { it.isNumeric }
        if (numericCols.isEmpty()) return DataFrame.empty("describe")

        val metricNames = listOf("count", "mean", "std", "min", "25%", "50%", "75%", "max")
        val statRows = mutableListOf<List<Any?>>()

        for (m in metricNames) {
            val rowValues = mutableListOf<Any?>(m)
            for (col in numericCols) {
                val value: Any? = when (m) {
                    "count" -> (col.totalCount - col.nullCount).toDouble()
                    "mean" -> col.meanVal
                    "std" -> col.stdDev
                    "min" -> col.minVal?.toDoubleOrNull()
                    "25%" -> col.q25
                    "50%" -> col.medianVal
                    "75%" -> col.q75
                    "max" -> col.maxVal?.toDoubleOrNull()
                    else -> null
                }
                rowValues.add(value)
            }
            statRows.add(rowValues)
        }

        val cols = mutableListOf(Pair("stat", DataTypeCategory.STRING))
        for (col in numericCols) {
            cols.add(Pair(col.name, DataTypeCategory.FLOAT))
        }

        return DataFrame.buildWithStatistics("${df.name}_describe", cols, statRows)
    }

    private fun toDouble(v: Any?): Double? = when (v) {
        is Number -> v.toDouble()
        is String -> v.toDoubleOrNull()
        else -> null
    }
}
