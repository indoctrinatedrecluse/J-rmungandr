/*
 * Copyright © 2025–2026 indoctrinatedrecluse
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jormungandr.dataframe.transform

import org.jormungandr.dataframe.model.DataFrame
import org.jormungandr.dataframe.model.DataTypeCategory
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

enum class DropNaHow(val displayName: String) {
    ANY("Any (drop if any specified column is null)"),
    ALL("All (drop if all specified columns are null)")
}

enum class FillStrategy(val displayName: String) {
    CONSTANT("Constant Value"),
    MEAN("Mean (Average)"),
    MEDIAN("Median"),
    MODE("Mode (Most Frequent)"),
    FORWARD_FILL("Forward Fill (ffill)"),
    BACKWARD_FILL("Backward Fill (bfill)")
}

enum class StringCleanOp(val displayName: String) {
    TRIM("Trim Whitespace"),
    LOWERCASE("Convert to Lowercase"),
    UPPERCASE("Convert to Uppercase"),
    TITLECASE("Convert to Title Case"),
    REMOVE_PUNCTUATION("Remove Punctuation"),
    REGEX_REPLACE("Regex Replace")
}

enum class ScaleMethod(val displayName: String) {
    MIN_MAX("Min-Max Normalization [0, 1]"),
    Z_SCORE("Standard Z-Score (Mean=0, Std=1)"),
    LOG1P("Log Transform log(1 + x)")
}

enum class ClipMethod(val displayName: String) {
    IQR_1_5("IQR 1.5× Threshold [Q1 - 1.5×IQR, Q3 + 1.5×IQR]"),
    Z_SCORE_3("3-Sigma Threshold [-3σ, +3σ]"),
    CUSTOM_BOUNDS("Custom Min / Max Bounds")
}

sealed class DataPrepStep {
    abstract val description: String

    data class DropNa(val columns: List<String>, val how: DropNaHow = DropNaHow.ANY) : DataPrepStep() {
        override val description: String get() = "Drop NA (${how.name.lowercase()} in ${if (columns.isEmpty()) "all columns" else columns.joinToString()})"
    }

    data class FillNa(val column: String, val strategy: FillStrategy, val constantValue: String = "") : DataPrepStep() {
        override val description: String get() = "Fill NA in '$column' with ${strategy.displayName}${if (strategy == FillStrategy.CONSTANT) " ('$constantValue')" else ""}"
    }

    data class TypeCast(val column: String, val targetType: DataTypeCategory) : DataPrepStep() {
        override val description: String get() = "Cast '$column' to ${targetType.displayName}"
    }

    data class StringClean(val column: String, val operation: StringCleanOp, val regexPattern: String = "", val replacement: String = "") : DataPrepStep() {
        override val description: String get() = "Text Clean '$column': ${operation.displayName}"
    }

    data class NumericalScale(val column: String, val method: ScaleMethod) : DataPrepStep() {
        override val description: String get() = "Scale '$column' via ${method.displayName}"
    }

    data class OutlierClip(val column: String, val method: ClipMethod, val lowerBound: Double? = null, val upperBound: Double? = null) : DataPrepStep() {
        override val description: String get() = "Clip Outliers in '$column' (${method.displayName})"
    }

    data class Deduplicate(val subsetColumns: List<String> = emptyList()) : DataPrepStep() {
        override val description: String get() = "Remove Duplicate Rows${if (subsetColumns.isNotEmpty()) " on [${subsetColumns.joinToString()}]" else ""}"
    }

    data class OneHotEncode(val column: String, val maxCategories: Int = 10) : DataPrepStep() {
        override val description: String get() = "One-Hot Encode '$column' (top $maxCategories)"
    }

    data class RenameColumn(val oldName: String, val newName: String) : DataPrepStep() {
        override val description: String get() = "Rename '$oldName' → '$newName'"
    }

    data class DropColumn(val columns: List<String>) : DataPrepStep() {
        override val description: String get() = "Drop Columns: ${columns.joinToString()}"
    }
}

/**
 * Robust Data Cleaning and Transformation Pipeline engine for Jörmungandr Data Prep Studio.
 * Executes reproducible sequences of transformations and generates synchronized Pandas, Polars,
 * and SQL pipeline code.
 */
class DataPrepPipeline(
    val steps: MutableList<DataPrepStep> = mutableListOf()
) {

    fun addStep(step: DataPrepStep) {
        steps.add(step)
    }

    fun removeStep(index: Int) {
        if (index in steps.indices) steps.removeAt(index)
    }

    fun moveStepUp(index: Int) {
        if (index > 0 && index < steps.size) {
            val s = steps.removeAt(index)
            steps.add(index - 1, s)
        }
    }

    fun moveStepDown(index: Int) {
        if (index >= 0 && index < steps.size - 1) {
            val s = steps.removeAt(index)
            steps.add(index + 1, s)
        }
    }

    fun clear() {
        steps.clear()
    }

    /**
     * Executes the complete pipeline sequentially on the given [inputDf] and returns a new transformed [DataFrame].
     */
    fun apply(inputDf: DataFrame): DataFrame {
        var current = inputDf
        for (step in steps) {
            current = applyStep(current, step)
        }
        return current
    }

    private fun applyStep(df: DataFrame, step: DataPrepStep): DataFrame {
        if (df.rowCount == 0 && df.columnCount == 0) return df

        return when (step) {
            is DataPrepStep.DropNa -> applyDropNa(df, step)
            is DataPrepStep.FillNa -> applyFillNa(df, step)
            is DataPrepStep.TypeCast -> applyTypeCast(df, step)
            is DataPrepStep.StringClean -> applyStringClean(df, step)
            is DataPrepStep.NumericalScale -> applyNumericalScale(df, step)
            is DataPrepStep.OutlierClip -> applyOutlierClip(df, step)
            is DataPrepStep.Deduplicate -> applyDeduplicate(df, step)
            is DataPrepStep.OneHotEncode -> applyOneHotEncode(df, step)
            is DataPrepStep.RenameColumn -> applyRenameColumn(df, step)
            is DataPrepStep.DropColumn -> applyDropColumn(df, step)
        }
    }

    private fun applyDropNa(df: DataFrame, step: DataPrepStep.DropNa): DataFrame {
        val targetIndices = if (step.columns.isEmpty()) {
            df.columns.indices.toList()
        } else {
            step.columns.mapNotNull { name -> df.getColumnIndex(name).takeIf { it >= 0 } }
        }
        if (targetIndices.isEmpty()) return df

        val filteredRows = df.rows.filter { row ->
            val nullCount = targetIndices.count { idx ->
                val v = row.getOrNull(idx)
                v == null || (v is String && v.trim().isEmpty())
            }
            if (step.how == DropNaHow.ANY) {
                nullCount == 0
            } else {
                nullCount < targetIndices.size
            }
        }

        val colDefs = df.columns.map { Pair(it.name, it.category) }
        return DataFrame.buildWithStatistics("${df.name}_dropna", colDefs, filteredRows)
    }

    private fun applyFillNa(df: DataFrame, step: DataPrepStep.FillNa): DataFrame {
        val colIdx = df.getColumnIndex(step.column)
        if (colIdx < 0) return df

        val colValues = df.rows.map { it.getOrNull(colIdx) }
        val replacementValue: Any? = when (step.strategy) {
            FillStrategy.CONSTANT -> step.constantValue
            FillStrategy.MEAN -> {
                val nums = colValues.mapNotNull { toDouble(it) }
                if (nums.isNotEmpty()) nums.average() else null
            }
            FillStrategy.MEDIAN -> {
                val nums = colValues.mapNotNull { toDouble(it) }.sorted()
                if (nums.isEmpty()) null
                else if (nums.size % 2 == 0) (nums[nums.size / 2 - 1] + nums[nums.size / 2]) / 2.0
                else nums[nums.size / 2]
            }
            FillStrategy.MODE -> {
                colValues.filterNotNull().groupingBy { it.toString() }.eachCount()
                    .maxByOrNull { it.value }?.key
            }
            FillStrategy.FORWARD_FILL, FillStrategy.BACKWARD_FILL -> null // handled dynamically per row
        }

        val newRows = mutableListOf<List<Any?>>()
        if (step.strategy == FillStrategy.FORWARD_FILL) {
            var lastValid: Any? = null
            for (row in df.rows) {
                val currentVal = row.getOrNull(colIdx)
                val fillVal = if (currentVal != null && currentVal.toString().isNotBlank()) {
                    lastValid = currentVal
                    currentVal
                } else {
                    lastValid
                }
                val mutableRow = row.toMutableList()
                mutableRow[colIdx] = fillVal
                newRows.add(mutableRow)
            }
        } else if (step.strategy == FillStrategy.BACKWARD_FILL) {
            val filledCol = colValues.toMutableList()
            var nextValid: Any? = null
            for (i in filledCol.indices.reversed()) {
                val v = filledCol[i]
                if (v != null && v.toString().isNotBlank()) {
                    nextValid = v
                } else {
                    filledCol[i] = nextValid
                }
            }
            for ((i, row) in df.rows.withIndex()) {
                val mutableRow = row.toMutableList()
                mutableRow[colIdx] = filledCol[i]
                newRows.add(mutableRow)
            }
        } else {
            for (row in df.rows) {
                val currentVal = row.getOrNull(colIdx)
                val mutableRow = row.toMutableList()
                if (currentVal == null || currentVal.toString().isBlank()) {
                    mutableRow[colIdx] = replacementValue
                }
                newRows.add(mutableRow)
            }
        }

        val colDefs = df.columns.map { Pair(it.name, it.category) }
        return DataFrame.buildWithStatistics("${df.name}_fillna", colDefs, newRows)
    }

    private fun applyTypeCast(df: DataFrame, step: DataPrepStep.TypeCast): DataFrame {
        val colIdx = df.getColumnIndex(step.column)
        if (colIdx < 0) return df

        val newRows = df.rows.map { row ->
            val v = row.getOrNull(colIdx)
            val converted: Any? = when (step.targetType) {
                DataTypeCategory.INTEGER -> toDouble(v)?.toLong()
                DataTypeCategory.FLOAT -> toDouble(v)
                DataTypeCategory.STRING -> v?.toString()
                DataTypeCategory.BOOLEAN -> when (v?.toString()?.trim()?.lowercase()) {
                    "true", "1", "t", "yes", "y" -> true
                    "false", "0", "f", "no", "n" -> false
                    else -> null
                }
                else -> v?.toString()
            }
            val m = row.toMutableList()
            m[colIdx] = converted
            m
        }

        val colDefs = df.columns.mapIndexed { i, col ->
            if (i == colIdx) Pair(col.name, step.targetType) else Pair(col.name, col.category)
        }
        return DataFrame.buildWithStatistics("${df.name}_cast", colDefs, newRows)
    }

    private fun applyStringClean(df: DataFrame, step: DataPrepStep.StringClean): DataFrame {
        val colIdx = df.getColumnIndex(step.column)
        if (colIdx < 0) return df

        val regex = if (step.operation == StringCleanOp.REGEX_REPLACE && step.regexPattern.isNotBlank()) {
            runCatching { Regex(step.regexPattern) }.getOrNull()
        } else null

        val punctRegex = Regex("[\\p{Punct}]")

        val newRows = df.rows.map { row ->
            val v = row.getOrNull(colIdx)?.toString()
            val cleaned = if (v == null) null else when (step.operation) {
                StringCleanOp.TRIM -> v.trim()
                StringCleanOp.LOWERCASE -> v.lowercase()
                StringCleanOp.UPPERCASE -> v.uppercase()
                StringCleanOp.TITLECASE -> v.split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                StringCleanOp.REMOVE_PUNCTUATION -> v.replace(punctRegex, "")
                StringCleanOp.REGEX_REPLACE -> regex?.replace(v, step.replacement) ?: v
            }
            val m = row.toMutableList()
            m[colIdx] = cleaned
            m
        }

        val colDefs = df.columns.map { Pair(it.name, it.category) }
        return DataFrame.buildWithStatistics("${df.name}_clean", colDefs, newRows)
    }

    private fun applyNumericalScale(df: DataFrame, step: DataPrepStep.NumericalScale): DataFrame {
        val colIdx = df.getColumnIndex(step.column)
        if (colIdx < 0) return df

        val nums = df.rows.mapNotNull { toDouble(it.getOrNull(colIdx)) }
        if (nums.isEmpty()) return df

        val minVal = nums.minOrNull() ?: 0.0
        val maxVal = nums.maxOrNull() ?: 1.0
        val meanVal = nums.average()
        val variance = nums.map { (it - meanVal).pow(2) }.average()
        val stdDev = sqrt(variance)

        val newRows = df.rows.map { row ->
            val v = toDouble(row.getOrNull(colIdx))
            val scaled = if (v == null) null else when (step.method) {
                ScaleMethod.MIN_MAX -> if (maxVal > minVal) (v - minVal) / (maxVal - minVal) else 0.0
                ScaleMethod.Z_SCORE -> if (stdDev > 0.0) (v - meanVal) / stdDev else 0.0
                ScaleMethod.LOG1P -> if (v >= 0.0) ln(1.0 + v) else null
            }
            val m = row.toMutableList()
            m[colIdx] = scaled
            m
        }

        val colDefs = df.columns.mapIndexed { i, col ->
            if (i == colIdx) Pair(col.name, DataTypeCategory.FLOAT) else Pair(col.name, col.category)
        }
        return DataFrame.buildWithStatistics("${df.name}_scaled", colDefs, newRows)
    }

    private fun applyOutlierClip(df: DataFrame, step: DataPrepStep.OutlierClip): DataFrame {
        val colIdx = df.getColumnIndex(step.column)
        if (colIdx < 0) return df

        val nums = df.rows.mapNotNull { toDouble(it.getOrNull(colIdx)) }.sorted()
        if (nums.isEmpty()) return df

        val (low, high) = when (step.method) {
            ClipMethod.IQR_1_5 -> {
                val q25 = nums[(nums.size * 0.25).toInt().coerceIn(0, nums.size - 1)]
                val q75 = nums[(nums.size * 0.75).toInt().coerceIn(0, nums.size - 1)]
                val iqr = q75 - q25
                Pair(q25 - 1.5 * iqr, q75 + 1.5 * iqr)
            }
            ClipMethod.Z_SCORE_3 -> {
                val mean = nums.average()
                val sd = sqrt(nums.map { (it - mean).pow(2) }.average())
                Pair(mean - 3.0 * sd, mean + 3.0 * sd)
            }
            ClipMethod.CUSTOM_BOUNDS -> Pair(step.lowerBound ?: Double.MIN_VALUE, step.upperBound ?: Double.MAX_VALUE)
        }

        val newRows = df.rows.map { row ->
            val v = toDouble(row.getOrNull(colIdx))
            val clipped = if (v == null) null else v.coerceIn(low, high)
            val m = row.toMutableList()
            m[colIdx] = clipped
            m
        }

        val colDefs = df.columns.map { Pair(it.name, it.category) }
        return DataFrame.buildWithStatistics("${df.name}_clipped", colDefs, newRows)
    }

    private fun applyDeduplicate(df: DataFrame, step: DataPrepStep.Deduplicate): DataFrame {
        val subsetIndices = if (step.subsetColumns.isEmpty()) {
            df.columns.indices.toList()
        } else {
            step.subsetColumns.mapNotNull { name -> df.getColumnIndex(name).takeIf { it >= 0 } }
        }

        val seen = mutableSetOf<String>()
        val dedupedRows = df.rows.filter { row ->
            val key = subsetIndices.map { row.getOrNull(it)?.toString() ?: "" }.joinToString("\u0001")
            seen.add(key)
        }

        val colDefs = df.columns.map { Pair(it.name, it.category) }
        return DataFrame.buildWithStatistics("${df.name}_dedup", colDefs, dedupedRows)
    }

    private fun applyOneHotEncode(df: DataFrame, step: DataPrepStep.OneHotEncode): DataFrame {
        val colIdx = df.getColumnIndex(step.column)
        if (colIdx < 0) return df

        val distinctVals = df.rows.mapNotNull { it.getOrNull(colIdx)?.toString() }
            .groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }
            .take(step.maxCategories)
            .map { it.key }

        val newCols = mutableListOf<Pair<String, DataTypeCategory>>()
        for ((i, col) in df.columns.withIndex()) {
            if (i == colIdx) {
                for (cat in distinctVals) {
                    val sanitized = cat.replace(" ", "_").replace(Regex("[^a-zA-Z0-9_]"), "")
                    newCols.add(Pair("${step.column}_$sanitized", DataTypeCategory.INTEGER))
                }
            } else {
                newCols.add(Pair(col.name, col.category))
            }
        }

        val newRows = df.rows.map { row ->
            val targetVal = row.getOrNull(colIdx)?.toString()
            val m = mutableListOf<Any?>()
            for ((i, cell) in row.withIndex()) {
                if (i == colIdx) {
                    for (cat in distinctVals) {
                        m.add(if (targetVal == cat) 1L else 0L)
                    }
                } else {
                    m.add(cell)
                }
            }
            m
        }

        return DataFrame.buildWithStatistics("${df.name}_ohe", newCols, newRows)
    }

    private fun applyRenameColumn(df: DataFrame, step: DataPrepStep.RenameColumn): DataFrame {
        val colDefs = df.columns.map {
            if (it.name.equals(step.oldName, ignoreCase = true)) Pair(step.newName, it.category)
            else Pair(it.name, it.category)
        }
        return DataFrame.buildWithStatistics("${df.name}_renamed", colDefs, df.rows)
    }

    private fun applyDropColumn(df: DataFrame, step: DataPrepStep.DropColumn): DataFrame {
        val dropSet = step.columns.map { it.lowercase() }.toSet()
        val keepIndices = df.columns.indices.filter { idx -> df.columns[idx].name.lowercase() !in dropSet }
        if (keepIndices.isEmpty()) return df

        val newCols = keepIndices.map { Pair(df.columns[it].name, df.columns[it].category) }
        val newRows = df.rows.map { row -> keepIndices.map { row.getOrNull(it) } }
        return DataFrame.buildWithStatistics("${df.name}_dropped", newCols, newRows)
    }

    /**
     * Generates clean, reproducible Python Pandas pipeline code.
     */
    fun generatePandasCode(): String {
        val sb = StringBuilder()
        sb.appendLine("import pandas as pd")
        sb.appendLine("import numpy as np")
        sb.appendLine()
        sb.appendLine("# Jörmungandr Data Prep Pipeline")
        sb.appendLine("def prepare_dataset(df: pd.DataFrame) -> pd.DataFrame:")
        sb.appendLine("    df = df.copy()")
        sb.appendLine()

        for (step in steps) {
            when (step) {
                is DataPrepStep.DropNa -> {
                    val subsetParam = if (step.columns.isNotEmpty()) "subset=${step.columns.map { "'$it'" }}" else ""
                    val howParam = "how='${step.how.name.lowercase()}'"
                    val params = listOf(subsetParam, howParam).filter { it.isNotBlank() }.joinToString(", ")
                    sb.appendLine("    # ${step.description}")
                    sb.appendLine("    df = df.dropna($params)")
                }
                is DataPrepStep.FillNa -> {
                    sb.appendLine("    # ${step.description}")
                    when (step.strategy) {
                        FillStrategy.CONSTANT -> sb.appendLine("    df['${step.column}'] = df['${step.column}'].fillna('${step.constantValue}')")
                        FillStrategy.MEAN -> sb.appendLine("    df['${step.column}'] = df['${step.column}'].fillna(df['${step.column}'].mean())")
                        FillStrategy.MEDIAN -> sb.appendLine("    df['${step.column}'] = df['${step.column}'].fillna(df['${step.column}'].median())")
                        FillStrategy.MODE -> sb.appendLine("    df['${step.column}'] = df['${step.column}'].fillna(df['${step.column}'].mode()[0])")
                        FillStrategy.FORWARD_FILL -> sb.appendLine("    df['${step.column}'] = df['${step.column}'].ffill()")
                        FillStrategy.BACKWARD_FILL -> sb.appendLine("    df['${step.column}'] = df['${step.column}'].bfill()")
                    }
                }
                is DataPrepStep.TypeCast -> {
                    sb.appendLine("    # ${step.description}")
                    val pyType = when (step.targetType) {
                        DataTypeCategory.INTEGER -> "'int64'"
                        DataTypeCategory.FLOAT -> "'float64'"
                        DataTypeCategory.STRING -> "'string'"
                        DataTypeCategory.BOOLEAN -> "'bool'"
                        DataTypeCategory.DATETIME -> "pd.to_datetime"
                        else -> "'object'"
                    }
                    if (step.targetType == DataTypeCategory.DATETIME) {
                        sb.appendLine("    df['${step.column}'] = pd.to_datetime(df['${step.column}'])")
                    } else {
                        sb.appendLine("    df['${step.column}'] = df['${step.column}'].astype($pyType)")
                    }
                }
                is DataPrepStep.StringClean -> {
                    sb.appendLine("    # ${step.description}")
                    when (step.operation) {
                        StringCleanOp.TRIM -> sb.appendLine("    df['${step.column}'] = df['${step.column}'].str.strip()")
                        StringCleanOp.LOWERCASE -> sb.appendLine("    df['${step.column}'] = df['${step.column}'].str.lower()")
                        StringCleanOp.UPPERCASE -> sb.appendLine("    df['${step.column}'] = df['${step.column}'].str.upper()")
                        StringCleanOp.TITLECASE -> sb.appendLine("    df['${step.column}'] = df['${step.column}'].str.title()")
                        StringCleanOp.REMOVE_PUNCTUATION -> sb.appendLine("    df['${step.column}'] = df['${step.column}'].str.replace(r'[^\\w\\s]', '', regex=True)")
                        StringCleanOp.REGEX_REPLACE -> sb.appendLine("    df['${step.column}'] = df['${step.column}'].str.replace(r'${step.regexPattern}', '${step.replacement}', regex=True)")
                    }
                }
                is DataPrepStep.NumericalScale -> {
                    sb.appendLine("    # ${step.description}")
                    when (step.method) {
                        ScaleMethod.MIN_MAX -> sb.appendLine("    df['${step.column}'] = (df['${step.column}'] - df['${step.column}'].min()) / (df['${step.column}'].max() - df['${step.column}'].min())")
                        ScaleMethod.Z_SCORE -> sb.appendLine("    df['${step.column}'] = (df['${step.column}'] - df['${step.column}'].mean()) / df['${step.column}'].std()")
                        ScaleMethod.LOG1P -> sb.appendLine("    df['${step.column}'] = np.log1p(df['${step.column}'].clip(lower=0))")
                    }
                }
                is DataPrepStep.OutlierClip -> {
                    sb.appendLine("    # ${step.description}")
                    when (step.method) {
                        ClipMethod.IQR_1_5 -> {
                            sb.appendLine("    q25, q75 = df['${step.column}'].quantile([0.25, 0.75])")
                            sb.appendLine("    iqr = q75 - q25")
                            sb.appendLine("    df['${step.column}'] = df['${step.column}'].clip(lower=q25 - 1.5 * iqr, upper=q75 + 1.5 * iqr)")
                        }
                        ClipMethod.Z_SCORE_3 -> {
                            sb.appendLine("    mean, std = df['${step.column}'].mean(), df['${step.column}'].std()")
                            sb.appendLine("    df['${step.column}'] = df['${step.column}'].clip(lower=mean - 3 * std, upper=mean + 3 * std)")
                        }
                        ClipMethod.CUSTOM_BOUNDS -> {
                            sb.appendLine("    df['${step.column}'] = df['${step.column}'].clip(lower=${step.lowerBound ?: "None"}, upper=${step.upperBound ?: "None"})")
                        }
                    }
                }
                is DataPrepStep.Deduplicate -> {
                    val subsetParam = if (step.subsetColumns.isNotEmpty()) "subset=${step.subsetColumns.map { "'$it'" }}" else ""
                    sb.appendLine("    # ${step.description}")
                    sb.appendLine("    df = df.drop_duplicates($subsetParam)")
                }
                is DataPrepStep.OneHotEncode -> {
                    sb.appendLine("    # ${step.description}")
                    sb.appendLine("    df = pd.get_dummies(df, columns=['${step.column}'], dtype=int)")
                }
                is DataPrepStep.RenameColumn -> {
                    sb.appendLine("    # ${step.description}")
                    sb.appendLine("    df = df.rename(columns={'${step.oldName}': '${step.newName}'})")
                }
                is DataPrepStep.DropColumn -> {
                    sb.appendLine("    # ${step.description}")
                    sb.appendLine("    df = df.drop(columns=${step.columns.map { "'$it'" }})")
                }
            }
            sb.appendLine()
        }

        sb.appendLine("    return df")
        sb.appendLine()
        sb.appendLine("# transformed_df = prepare_dataset(df)")
        return sb.toString()
    }

    /**
     * Generates clean, high-performance Polars pipeline code.
     */
    fun generatePolarsCode(): String {
        val sb = StringBuilder()
        sb.appendLine("import polars as pl")
        sb.appendLine()
        sb.appendLine("# Jörmungandr Data Prep Pipeline (Polars)")
        sb.appendLine("def prepare_dataset_polars(df: pl.DataFrame) -> pl.DataFrame:")
        sb.appendLine("    return (")
        sb.appendLine("        df")

        for (step in steps) {
            when (step) {
                is DataPrepStep.DropNa -> {
                    val cols = if (step.columns.isNotEmpty()) "subset=${step.columns.map { "\"$it\"" }}" else ""
                    sb.appendLine("        .drop_nulls($cols)")
                }
                is DataPrepStep.FillNa -> {
                    when (step.strategy) {
                        FillStrategy.CONSTANT -> sb.appendLine("        .with_columns(pl.col(\"${step.column}\").fill_null(\"${step.constantValue}\"))")
                        FillStrategy.MEAN -> sb.appendLine("        .with_columns(pl.col(\"${step.column}\").fill_null(pl.col(\"${step.column}\").mean()))")
                        FillStrategy.MEDIAN -> sb.appendLine("        .with_columns(pl.col(\"${step.column}\").fill_null(pl.col(\"${step.column}\").median()))")
                        FillStrategy.FORWARD_FILL -> sb.appendLine("        .with_columns(pl.col(\"${step.column}\").forward_fill())")
                        FillStrategy.BACKWARD_FILL -> sb.appendLine("        .with_columns(pl.col(\"${step.column}\").backward_fill())")
                        else -> sb.appendLine("        .with_columns(pl.col(\"${step.column}\").fill_null(\"${step.constantValue}\"))")
                    }
                }
                is DataPrepStep.TypeCast -> {
                    val plType = when (step.targetType) {
                        DataTypeCategory.INTEGER -> "pl.Int64"
                        DataTypeCategory.FLOAT -> "pl.Float64"
                        DataTypeCategory.STRING -> "pl.Utf8"
                        DataTypeCategory.BOOLEAN -> "pl.Boolean"
                        DataTypeCategory.DATETIME -> "pl.Datetime"
                        else -> "pl.Utf8"
                    }
                    sb.appendLine("        .with_columns(pl.col(\"${step.column}\").cast($plType))")
                }
                is DataPrepStep.StringClean -> {
                    when (step.operation) {
                        StringCleanOp.TRIM -> sb.appendLine("        .with_columns(pl.col(\"${step.column}\").str.strip_chars())")
                        StringCleanOp.LOWERCASE -> sb.appendLine("        .with_columns(pl.col(\"${step.column}\").str.to_lowercase())")
                        StringCleanOp.UPPERCASE -> sb.appendLine("        .with_columns(pl.col(\"${step.column}\").str.to_uppercase())")
                        StringCleanOp.TITLECASE -> sb.appendLine("        .with_columns(pl.col(\"${step.column}\").str.to_titlecase())")
                        StringCleanOp.REMOVE_PUNCTUATION -> sb.appendLine("        .with_columns(pl.col(\"${step.column}\").str.replace_all(r\"[^\\w\\s]\", \"\"))")
                        StringCleanOp.REGEX_REPLACE -> sb.appendLine("        .with_columns(pl.col(\"${step.column}\").str.replace_all(r\"${step.regexPattern}\", \"${step.replacement}\"))")
                    }
                }
                is DataPrepStep.NumericalScale -> {
                    when (step.method) {
                        ScaleMethod.MIN_MAX -> sb.appendLine("        .with_columns((pl.col(\"${step.column}\") - pl.col(\"${step.column}\").min()) / (pl.col(\"${step.column}\").max() - pl.col(\"${step.column}\").min()))")
                        ScaleMethod.Z_SCORE -> sb.appendLine("        .with_columns((pl.col(\"${step.column}\") - pl.col(\"${step.column}\").mean()) / pl.col(\"${step.column}\").std())")
                        ScaleMethod.LOG1P -> sb.appendLine("        .with_columns((pl.col(\"${step.column}\") + 1).log())")
                    }
                }
                is DataPrepStep.OutlierClip -> {
                    sb.appendLine("        .with_columns(pl.col(\"${step.column}\").clip(${step.lowerBound ?: "None"}, ${step.upperBound ?: "None"}))")
                }
                is DataPrepStep.Deduplicate -> {
                    val subsetParam = if (step.subsetColumns.isNotEmpty()) "subset=${step.subsetColumns.map { "\"$it\"" }}" else ""
                    sb.appendLine("        .unique($subsetParam)")
                }
                is DataPrepStep.OneHotEncode -> {
                    sb.appendLine("        .to_dummies(columns=[\"${step.column}\"])")
                }
                is DataPrepStep.RenameColumn -> {
                    sb.appendLine("        .rename({\"${step.oldName}\": \"${step.newName}\"})")
                }
                is DataPrepStep.DropColumn -> {
                    sb.appendLine("        .drop(${step.columns.map { "\"$it\"" }})")
                }
            }
        }

        sb.appendLine("    )")
        return sb.toString()
    }

    /**
     * Generates standard SQL transformations.
     */
    fun generateSqlCode(tableName: String = "source_table"): String {
        val sb = StringBuilder()
        sb.appendLine("-- Jörmungandr SQL Transformation Pipeline")
        sb.appendLine("WITH step_0 AS (")
        sb.appendLine("    SELECT * FROM $tableName")
        sb.appendLine(")")

        var stepNum = 1
        for (step in steps) {
            val prevStep = "step_${stepNum - 1}"
            val currStep = "step_$stepNum"
            sb.appendLine(", $currStep AS (")
            when (step) {
                is DataPrepStep.DropNa -> {
                    val targets = if (step.columns.isNotEmpty()) step.columns else listOf("*")
                    val cond = if (step.columns.isNotEmpty()) {
                        step.columns.joinToString(if (step.how == DropNaHow.ANY) " AND " else " OR ") { "$it IS NOT NULL" }
                    } else {
                        "1=1"
                    }
                    sb.appendLine("    SELECT * FROM $prevStep WHERE $cond")
                }
                is DataPrepStep.Deduplicate -> {
                    if (step.subsetColumns.isNotEmpty()) {
                        val part = step.subsetColumns.joinToString(", ")
                        sb.appendLine("    SELECT * FROM (")
                        sb.appendLine("        SELECT *, ROW_NUMBER() OVER (PARTITION BY $part ORDER BY 1) as __rn")
                        sb.appendLine("        FROM $prevStep")
                        sb.appendLine("    ) WHERE __rn = 1")
                    } else {
                        sb.appendLine("    SELECT DISTINCT * FROM $prevStep")
                    }
                }
                is DataPrepStep.DropColumn -> {
                    sb.appendLine("    -- Dropped: ${step.columns.joinToString()}")
                    sb.appendLine("    SELECT * FROM $prevStep")
                }
                else -> {
                    sb.appendLine("    -- ${step.description}")
                    sb.appendLine("    SELECT * FROM $prevStep")
                }
            }
            sb.appendLine(")")
            stepNum++
        }

        sb.appendLine("SELECT * FROM step_${stepNum - 1};")
        return sb.toString()
    }

    private fun toDouble(v: Any?): Double? = when (v) {
        is Number -> v.toDouble()
        is String -> v.toDoubleOrNull()
        else -> null
    }
}
