/*
 * Copyright (c) 2025-2026 indoctrinatedrecluse
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

package org.jormungandr.dataframe.quality

import org.jormungandr.dataframe.model.DataFrame
import org.jormungandr.dataframe.model.DataTypeCategory
import kotlin.math.abs

/**
 * Missing value and sparsity patterns across columns and rows.
 */
data class DataSparsityReport(
    val totalCells: Long,
    val missingCells: Long,
    val completenessPercentage: Double,
    val columnMissingCounts: Map<String, Long>,
    val columnMissingPercentages: Map<String, Double>,
    val rowMissingDistribution: Map<String, Int>
)

/**
 * Statistical outlier detection results for a numeric column.
 */
data class OutlierReport(
    val columnName: String,
    val zScoreOutliersCount: Int,
    val iqrOutliersCount: Int,
    val sampleOutlierValues: List<Double>
)

/**
 * Declarative assertion rule specification inspired by Pandera and Great Expectations.
 */
sealed class DataValidationRule(val ruleType: String, open val column: String) {
    data class NonNull(override val column: String) : DataValidationRule("NON_NULL", column)
    data class Range(override val column: String, val min: Double?, val max: Double?) : DataValidationRule("RANGE", column)
    data class RegexPattern(override val column: String, val pattern: String) : DataValidationRule("REGEX", column)
    data class Unique(override val column: String) : DataValidationRule("UNIQUE", column)
    data class EnumSet(override val column: String, val allowedValues: Set<String>) : DataValidationRule("ENUM_SET", column)
}

/**
 * Outcome of evaluating an assertion rule against a dataframe.
 */
data class RuleValidationResult(
    val ruleType: String,
    val column: String,
    val description: String,
    val passed: Boolean,
    val violationsCount: Int,
    val totalEvaluated: Int,
    val passPercentage: Double,
    val sampleViolations: List<String>
)

/**
 * Complete Data Quality and Profiling Health Scorecard.
 */
data class DataQualityScorecard(
    val overallHealthScore: Double, // 0.0 to 100.0
    val healthGrade: String,        // "A+ (Excellent)", "B (Good)", "C (Warning)", "D (Critical)"
    val sparsity: DataSparsityReport,
    val outliers: List<OutlierReport>,
    val validationResults: List<RuleValidationResult>,
    val panderaCode: String
)

/**
 * Data Quality and Automated Profiling Engine.
 */
object DataQualityProfilerService {

    /**
     * Performs end-to-end data quality audit: sparsity, outliers, automated rule inference, and execution.
     */
    fun analyzeQuality(df: DataFrame, customRules: List<DataValidationRule> = emptyList()): DataQualityScorecard {
        val sparsity = analyzeSparsity(df)
        val outliers = detectOutliers(df)

        val rulesToRun = if (customRules.isNotEmpty()) customRules else inferRules(df)
        val validationResults = rulesToRun.map { evaluateRule(df, it) }

        // Compute overall health score
        val completenessWeight = 0.4
        val rulesWeight = 0.4
        val outliersWeight = 0.2

        val completenessScore = sparsity.completenessPercentage
        val rulesScore = if (validationResults.isNotEmpty()) {
            validationResults.map { it.passPercentage }.average()
        } else 100.0

        val totalOutliers = outliers.sumOf { it.iqrOutliersCount }
        val totalNumericCells = df.rowCount * df.columns.count { it.isNumeric }
        val outlierCleanliness = if (totalNumericCells > 0) {
            (1.0 - (totalOutliers.toDouble() / totalNumericCells).coerceAtMost(1.0)) * 100.0
        } else 100.0

        val healthScore = (completenessScore * completenessWeight +
                rulesScore * rulesWeight +
                outlierCleanliness * outliersWeight).coerceIn(0.0, 100.0)

        val grade = when {
            healthScore >= 95.0 -> "A+ (Excellent)"
            healthScore >= 85.0 -> "A (Great)"
            healthScore >= 75.0 -> "B (Good)"
            healthScore >= 60.0 -> "C (Needs Attention)"
            else -> "D (Critical Issues)"
        }

        val panderaCode = generatePanderaSchema(df, rulesToRun)

        return DataQualityScorecard(
            overallHealthScore = healthScore,
            healthGrade = grade,
            sparsity = sparsity,
            outliers = outliers,
            validationResults = validationResults,
            panderaCode = panderaCode
        )
    }

    /**
     * Computes dataset sparsity and missing value distributions.
     */
    fun analyzeSparsity(df: DataFrame): DataSparsityReport {
        val totalCells = (df.rowCount.toLong() * df.columnCount.toLong()).coerceAtLeast(1L)
        var totalMissing = 0L

        val colMissingCounts = mutableMapOf<String, Long>()
        val colMissingPercentages = mutableMapOf<String, Double>()

        df.columns.forEachIndexed { colIdx, col ->
            var missingInCol = 0L
            for (row in df.rows) {
                if (row.getOrNull(colIdx) == null) {
                    missingInCol++
                }
            }
            totalMissing += missingInCol
            colMissingCounts[col.name] = missingInCol
            val pct = if (df.rowCount > 0) (missingInCol.toDouble() / df.rowCount.toDouble()) * 100.0 else 0.0
            colMissingPercentages[col.name] = pct
        }

        val completeness = ((totalCells - totalMissing).toDouble() / totalCells.toDouble()) * 100.0

        // Row missing histogram
        var zeroMissing = 0
        var oneMissing = 0
        var twoOrMoreMissing = 0

        for (row in df.rows) {
            val nullsInRow = row.count { it == null }
            when (nullsInRow) {
                0 -> zeroMissing++
                1 -> oneMissing++
                else -> twoOrMoreMissing++
            }
        }

        val rowMissingMap = mapOf(
            "0 Missing (Complete)" to zeroMissing,
            "1 Missing" to oneMissing,
            "2+ Missing" to twoOrMoreMissing
        )

        return DataSparsityReport(
            totalCells = totalCells,
            missingCells = totalMissing,
            completenessPercentage = completeness,
            columnMissingCounts = colMissingCounts,
            columnMissingPercentages = colMissingPercentages,
            rowMissingDistribution = rowMissingMap
        )
    }

    /**
     * Detects outliers via Z-Score (> 3.0) and IQR (1.5 * IQR) rules.
     */
    fun detectOutliers(df: DataFrame): List<OutlierReport> {
        val reports = mutableListOf<OutlierReport>()

        for (col in df.columns) {
            if (!col.isNumeric || col.meanVal == null || col.stdDev == null || col.iqr == null) continue

            val colIdx = df.getColumnIndex(col.name)
            val mean = col.meanVal
            val std = col.stdDev
            val q25 = col.q25 ?: 0.0
            val q75 = col.q75 ?: 0.0
            val iqr = col.iqr

            val lowerBound = q25 - 1.5 * iqr
            val upperBound = q75 + 1.5 * iqr

            var zCount = 0
            var iqrCount = 0
            val sampleOutliers = mutableListOf<Double>()

            for (row in df.rows) {
                val cell = row.getOrNull(colIdx)
                val num = when (cell) {
                    is Number -> cell.toDouble()
                    is String -> cell.toDoubleOrNull()
                    else -> null
                } ?: continue

                val isIqrOutlier = num < lowerBound || num > upperBound
                val isZOutlier = std > 0.0 && abs(num - mean) / std > 3.0

                if (isIqrOutlier) {
                    iqrCount++
                    if (sampleOutliers.size < 5) sampleOutliers.add(num)
                }
                if (isZOutlier) {
                    zCount++
                }
            }

            reports.add(
                OutlierReport(
                    columnName = col.name,
                    zScoreOutliersCount = zCount,
                    iqrOutliersCount = iqrCount,
                    sampleOutlierValues = sampleOutliers
                )
            )
        }

        return reports
    }

    /**
     * Automatically infers validation rules from dataset statistics.
     */
    fun inferRules(df: DataFrame): List<DataValidationRule> {
        val rules = mutableListOf<DataValidationRule>()

        for (col in df.columns) {
            // 1. Non-null assertion if column has 0 nulls
            if (col.nullCount == 0L) {
                rules.add(DataValidationRule.NonNull(col.name))
            }

            // 2. Uniqueness check if distinct count equals row count
            if (col.distinctCount.toInt() == df.rowCount && df.rowCount > 1) {
                rules.add(DataValidationRule.Unique(col.name))
            }

            // 3. Numeric range check
            if (col.isNumeric && col.minVal != null && col.maxVal != null) {
                val minD = col.minVal.toDoubleOrNull()
                val maxD = col.maxVal.toDoubleOrNull()
                if (minD != null && maxD != null) {
                    rules.add(DataValidationRule.Range(col.name, minD, maxD))
                }
            }

            // 4. Low-cardinality enum check (categorical strings with <= 6 distinct values)
            if (col.category == DataTypeCategory.STRING && col.distinctCount in 2..6) {
                val colIdx = df.getColumnIndex(col.name)
                val distinctVals = df.rows.mapNotNull { it.getOrNull(colIdx)?.toString() }.distinct().toSet()
                if (distinctVals.isNotEmpty()) {
                    rules.add(DataValidationRule.EnumSet(col.name, distinctVals))
                }
            }
        }

        return rules
    }

    /**
     * Evaluates a single rule against the dataset.
     */
    fun evaluateRule(df: DataFrame, rule: DataValidationRule): RuleValidationResult {
        val colIdx = df.getColumnIndex(rule.column)
        if (colIdx < 0) {
            return RuleValidationResult(
                ruleType = rule.ruleType,
                column = rule.column,
                description = "Column not found in dataset",
                passed = false,
                violationsCount = df.rowCount,
                totalEvaluated = df.rowCount,
                passPercentage = 0.0,
                sampleViolations = listOf("Missing column: ${rule.column}")
            )
        }

        var violations = 0
        val sampleViolations = mutableListOf<String>()

        when (rule) {
            is DataValidationRule.NonNull -> {
                for ((idx, row) in df.rows.withIndex()) {
                    if (row.getOrNull(colIdx) == null) {
                        violations++
                        if (sampleViolations.size < 5) sampleViolations.add("Row $idx is null")
                    }
                }
            }

            is DataValidationRule.Range -> {
                for ((idx, row) in df.rows.withIndex()) {
                    val cell = row.getOrNull(colIdx)
                    val num = when (cell) {
                        is Number -> cell.toDouble()
                        is String -> cell.toDoubleOrNull()
                        else -> null
                    }
                    if (num != null) {
                        val minOk = rule.min == null || num >= rule.min
                        val maxOk = rule.max == null || num <= rule.max
                        if (!minOk || !maxOk) {
                            violations++
                            if (sampleViolations.size < 5) sampleViolations.add("Row $idx value $num out of range [${rule.min}, ${rule.max}]")
                        }
                    }
                }
            }

            is DataValidationRule.RegexPattern -> {
                val regex = Regex(rule.pattern)
                for ((idx, row) in df.rows.withIndex()) {
                    val str = row.getOrNull(colIdx)?.toString()
                    if (str != null && !regex.matches(str)) {
                        violations++
                        if (sampleViolations.size < 5) sampleViolations.add("Row $idx '$str' does not match pattern")
                    }
                }
            }

            is DataValidationRule.Unique -> {
                val seen = mutableSetOf<Any>()
                for ((idx, row) in df.rows.withIndex()) {
                    val cell = row.getOrNull(colIdx)
                    if (cell != null) {
                        if (seen.contains(cell)) {
                            violations++
                            if (sampleViolations.size < 5) sampleViolations.add("Row $idx duplicate: '$cell'")
                        } else {
                            seen.add(cell)
                        }
                    }
                }
            }

            is DataValidationRule.EnumSet -> {
                for ((idx, row) in df.rows.withIndex()) {
                    val str = row.getOrNull(colIdx)?.toString()
                    if (str != null && !rule.allowedValues.contains(str)) {
                        violations++
                        if (sampleViolations.size < 5) sampleViolations.add("Row $idx value '$str' not in allowed set")
                    }
                }
            }
        }

        val total = df.rowCount.coerceAtLeast(1)
        val passed = violations == 0
        val passPct = ((total - violations).toDouble() / total.toDouble()) * 100.0

        val desc = when (rule) {
            is DataValidationRule.NonNull -> "Must not be null"
            is DataValidationRule.Range -> "Values within [${rule.min}, ${rule.max}]"
            is DataValidationRule.RegexPattern -> "Matches regex '${rule.pattern}'"
            is DataValidationRule.Unique -> "Must be strictly unique"
            is DataValidationRule.EnumSet -> "Must be in {${rule.allowedValues.take(4).joinToString(", ")}}"
        }

        return RuleValidationResult(
            ruleType = rule.ruleType,
            column = rule.column,
            description = desc,
            passed = passed,
            violationsCount = violations,
            totalEvaluated = total,
            passPercentage = passPct,
            sampleViolations = sampleViolations
        )
    }

    /**
     * Generates a complete Python Pandera schema verification script.
     */
    fun generatePanderaSchema(df: DataFrame, rules: List<DataValidationRule>): String {
        val sb = StringBuilder()
        sb.appendLine("import pandera as pa")
        sb.appendLine("from pandera import Column, Check, DataFrameSchema\n")
        sb.appendLine("schema = DataFrameSchema({")

        for (col in df.columns) {
            val typeStr = when (col.category) {
                DataTypeCategory.INTEGER -> "pa.Int64"
                DataTypeCategory.FLOAT -> "pa.Float64"
                DataTypeCategory.BOOLEAN -> "pa.Bool"
                DataTypeCategory.DATETIME -> "pa.DateTime"
                else -> "pa.String"
            }

            val nullable = col.nullCount > 0
            val checks = mutableListOf<String>()

            rules.filter { it.column == col.name }.forEach { r ->
                when (r) {
                    is DataValidationRule.Range -> {
                        if (r.min != null && r.max != null) {
                            checks.add("Check.in_range(${r.min}, ${r.max})")
                        }
                    }
                    is DataValidationRule.RegexPattern -> checks.add("Check.str_matches(r'${r.pattern}')")
                    is DataValidationRule.Unique -> checks.add("Check(lambda s: s.is_unique, name='unique')")
                    is DataValidationRule.EnumSet -> {
                        val setStr = r.allowedValues.joinToString(", ") { "\"$it\"" }
                        checks.add("Check.isin([$setStr])")
                    }
                    else -> {}
                }
            }

            val checkStr = if (checks.isNotEmpty()) ", checks=[${checks.joinToString(", ")}]" else ""
            sb.appendLine("    \"${col.name}\": Column($typeStr, nullable=$nullable$checkStr),")
        }

        sb.appendLine("})")
        sb.appendLine("\n# Validate DataFrame:")
        sb.appendLine("# verified_df = schema.validate(df)")
        return sb.toString()
    }
}
