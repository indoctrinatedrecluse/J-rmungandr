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

package org.jormungandr.dataframe.ml

import org.jormungandr.dataframe.model.DataFrame
import org.jormungandr.dataframe.model.DataTypeCategory
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

enum class FeatureRole(val displayName: String, val badgeShort: String) {
    TARGET_CANDIDATE("Target Candidate", "TARGET"),
    CONTINUOUS_NUMERIC("Continuous Numeric", "NUM"),
    CATEGORICAL("Categorical", "CAT"),
    HIGH_CARDINALITY_ID("High-Cardinality Key / ID", "ID"),
    CONSTANT_ZERO_VARIANCE("Constant (Zero Variance)", "CONST"),
    HIGH_NULL_LEAKAGE("High Null Leakage (>40%)", "NULL")
}

data class FeatureAuditItem(
    val columnName: String,
    val role: FeatureRole,
    val dataType: DataTypeCategory,
    val nullPercentage: Double,
    val distinctCount: Long,
    val predictiveScoreWithTarget: Double, // 0.0 to 1.0
    val associationKind: String,
    val isRecommendedForTraining: Boolean,
    val recommendationReason: String
)

data class PairwiseCorrelationItem(
    val featureA: String,
    val featureB: String,
    val correlation: Double,
    val isMulticollinear: Boolean
)

data class PreliminaryDataReport(
    val targetName: String?,
    val isClassification: Boolean,
    val features: List<FeatureAuditItem>,
    val recommendedFeatures: List<String>,
    val discardFeatures: List<String>,
    val pairwiseCorrelations: List<PairwiseCorrelationItem>,
    val insights: List<String>
)

/**
 * Preliminary on-demand data categorization and feature health analysis engine.
 * Profiles column roles, predicts association with target, flags identifiers,
 * zero-variance dead features, and multicollinearity without requiring any model training.
 */
object DataCategorizer {

    fun analyze(df: DataFrame, preferredTarget: String? = null): PreliminaryDataReport {
        if (df.rowCount == 0 || df.columnCount == 0) {
            return PreliminaryDataReport(
                targetName = preferredTarget,
                isClassification = false,
                features = emptyList(),
                recommendedFeatures = emptyList(),
                discardFeatures = emptyList(),
                pairwiseCorrelations = emptyList(),
                insights = listOf("Dataset is empty. Load or import data to run preliminary categorization.")
            )
        }

        // 1. Detect candidate target if not provided
        val targetColName = preferredTarget ?: detectBestTargetCandidate(df)
        val targetIdx = if (targetColName != null) df.getColumnIndex(targetColName) else -1
        val targetMeta = if (targetIdx >= 0) df.columns[targetIdx] else null

        val isClassification = targetMeta != null && (
            targetMeta.category == DataTypeCategory.STRING ||
            targetMeta.category == DataTypeCategory.BOOLEAN ||
            (targetMeta.distinctCount in 2..8 && targetMeta.totalCount > 20)
        )

        // 2. Classify each column's role
        val totalRows = df.rowCount.toLong()
        val audits = mutableListOf<FeatureAuditItem>()
        val insights = mutableListOf<String>()

        for (col in df.columns) {
            if (col.name == targetColName) continue

            val nullPct = col.nullPercentage
            val distinct = col.distinctCount
            val distinctRatio = if (totalRows > 0) distinct.toDouble() / totalRows.toDouble() else 0.0

            val isId = col.name.matches(Regex("(?i).*(^id$|_id$|guid|uuid|index|row_num|key).*")) ||
                (!col.isNumeric && distinctRatio > 0.90 && totalRows >= 15) ||
                (col.category == DataTypeCategory.INTEGER && distinctRatio > 0.95 && totalRows >= 30 && col.name.contains("id", ignoreCase = true))

            val role = when {
                col.nullPercentage > 40.0 -> FeatureRole.HIGH_NULL_LEAKAGE
                distinct <= 1 -> FeatureRole.CONSTANT_ZERO_VARIANCE
                isId -> FeatureRole.HIGH_CARDINALITY_ID
                col.isNumeric && distinct > 8 -> FeatureRole.CONTINUOUS_NUMERIC
                else -> FeatureRole.CATEGORICAL
            }

            // Compute predictive association with target
            var score = 0.0
            var assocStr = "No target set"
            if (targetIdx >= 0) {
                if (col.isNumeric && !isClassification) {
                    val r = computePearsonCorrelation(df, col.name, targetColName!!)
                    val absR = abs(r)
                    score = absR
                    assocStr = "Pearson r = %+.3f".format(r)
                } else if (!isClassification && role == FeatureRole.CATEGORICAL) {
                    val fRatio = computeAnovaRatio(df, col.name, targetColName!!)
                    score = (fRatio / (fRatio + 10.0)).coerceIn(0.0, 1.0)
                    assocStr = "ANOVA F = %.2f".format(fRatio)
                } else {
                    // Classification target association (variance or group entropy difference)
                    val catScore = computeClassificationAssociation(df, col.name, targetColName!!)
                    score = catScore
                    assocStr = "Class Association = %.2f".format(catScore)
                }
            }

            val isRecommended = role != FeatureRole.HIGH_CARDINALITY_ID &&
                role != FeatureRole.CONSTANT_ZERO_VARIANCE &&
                role != FeatureRole.HIGH_NULL_LEAKAGE

            val reason = when (role) {
                FeatureRole.HIGH_CARDINALITY_ID -> "⚠️ Unique key/ID (${(distinctRatio * 100).toInt()}% distinct). Exclude to prevent memorization/overfitting."
                FeatureRole.CONSTANT_ZERO_VARIANCE -> "⚠️ Constant feature with only $distinct distinct value. Provides zero statistical variance."
                FeatureRole.HIGH_NULL_LEAKAGE -> "⚠️ High missing rate (%.1f%% null). Impute or drop before training.".format(nullPct)
                FeatureRole.CONTINUOUS_NUMERIC -> "✓ Continuous feature. Predictive strength: $assocStr."
                FeatureRole.CATEGORICAL -> "✓ Discrete feature with $distinct categories. Suitable for encoding."
                FeatureRole.TARGET_CANDIDATE -> "Potential target variable."
            }

            audits.add(
                FeatureAuditItem(
                    columnName = col.name,
                    role = role,
                    dataType = col.category,
                    nullPercentage = nullPct,
                    distinctCount = distinct,
                    predictiveScoreWithTarget = score,
                    associationKind = assocStr,
                    isRecommendedForTraining = isRecommended,
                    recommendationReason = reason
                )
            )
        }

        // Sort by predictive score descending for recommended features
        audits.sortByDescending { if (it.isRecommendedForTraining) it.predictiveScoreWithTarget else -1.0 }

        val recommended = audits.filter { it.isRecommendedForTraining }.map { it.columnName }
        val discarded = audits.filter { !it.isRecommendedForTraining }.map { it.columnName }

        // 3. Compute pairwise correlations among top numeric features to detect multicollinearity
        val numericCols = audits.filter { it.role == FeatureRole.CONTINUOUS_NUMERIC }.map { it.columnName }.take(8)
        val pairwise = mutableListOf<PairwiseCorrelationItem>()

        for (i in numericCols.indices) {
            for (j in (i + 1) until numericCols.size) {
                val r = computePearsonCorrelation(df, numericCols[i], numericCols[j])
                val isMulti = abs(r) > 0.85
                pairwise.add(PairwiseCorrelationItem(numericCols[i], numericCols[j], r, isMulti))
                if (isMulti) {
                    insights.add("⚠️ Multicollinearity detected between '${numericCols[i]}' and '${numericCols[j]}' (r = %+.2f). Consider dropping one to prevent variance inflation.".format(r))
                }
            }
        }

        pairwise.sortByDescending { abs(it.correlation) }

        // 4. Generate general insights
        if (targetColName != null) {
            val topFeature = audits.firstOrNull { it.isRecommendedForTraining }
            if (topFeature != null && topFeature.predictiveScoreWithTarget > 0.25) {
                insights.add("🌟 Strongest predictor for '$targetColName': '${topFeature.columnName}' (${topFeature.associationKind}).")
            }
        }
        if (discarded.isNotEmpty()) {
            insights.add("💡 Auto-filtered ${discarded.size} non-informative columns (${discarded.joinToString(", ")}).")
        }
        if (isClassification) {
            insights.add("🎯 Classification target '$targetColName' has ${targetMeta!!.distinctCount} distinct classes.")
        }

        return PreliminaryDataReport(
            targetName = targetColName,
            isClassification = isClassification,
            features = audits,
            recommendedFeatures = recommended,
            discardFeatures = discarded,
            pairwiseCorrelations = pairwise.take(10),
            insights = insights
        )
    }

    private fun detectBestTargetCandidate(df: DataFrame): String? {
        val lastCol = df.columns.lastOrNull() ?: return null
        // Common target names
        val targetKeywords = listOf("target", "label", "price", "medhouseval", "churn", "species", "class", "outcome", "survived")
        val match = df.columns.find { col -> targetKeywords.any { col.name.equals(it, ignoreCase = true) } }
        if (match != null) return match.name
        // Default to last column if it is numeric or low distinct count
        return lastCol.name
    }

    private fun computePearsonCorrelation(df: DataFrame, colA: String, colB: String): Double {
        val idxA = df.getColumnIndex(colA)
        val idxB = df.getColumnIndex(colB)
        if (idxA < 0 || idxB < 0) return 0.0

        val pairs = mutableListOf<Pair<Double, Double>>()
        for (row in df.rows) {
            val a = toDoubleOrNull(row.getOrNull(idxA))
            val b = toDoubleOrNull(row.getOrNull(idxB))
            if (a != null && b != null) {
                pairs.add(Pair(a, b))
            }
        }

        val n = pairs.size
        if (n < 3) return 0.0

        val meanA = pairs.map { it.first }.average()
        val meanB = pairs.map { it.second }.average()

        var num = 0.0
        var denA = 0.0
        var denB = 0.0

        for ((a, b) in pairs) {
            val da = a - meanA
            val db = b - meanB
            num += da * db
            denA += da * da
            denB += db * db
        }

        val denom = sqrt(denA * denB)
        return if (denom > 1e-12) (num / denom).coerceIn(-1.0, 1.0) else 0.0
    }

    private fun computeAnovaRatio(df: DataFrame, catCol: String, numTargetCol: String): Double {
        val catIdx = df.getColumnIndex(catCol)
        val numIdx = df.getColumnIndex(numTargetCol)
        if (catIdx < 0 || numIdx < 0) return 0.0

        val groups = mutableMapOf<String, MutableList<Double>>()
        for (row in df.rows) {
            val cat = row.getOrNull(catIdx)?.toString()?.trim() ?: continue
            val num = toDoubleOrNull(row.getOrNull(numIdx)) ?: continue
            groups.computeIfAbsent(cat) { mutableListOf() }.add(num)
        }

        val k = groups.size
        val totalN = groups.values.sumOf { it.size }
        if (k < 2 || totalN <= k) return 0.0

        val grandMean = groups.values.flatten().average()
        var ssBetween = 0.0
        var ssWithin = 0.0

        for ((_, vals) in groups) {
            val n_g = vals.size
            val mean_g = vals.average()
            ssBetween += n_g * (mean_g - grandMean).pow(2)
            for (v in vals) {
                ssWithin += (v - mean_g).pow(2)
            }
        }

        val msBetween = ssBetween / (k - 1)
        val msWithin = if (totalN - k > 0) ssWithin / (totalN - k) else 1.0
        return if (msWithin > 1e-9) (msBetween / msWithin).coerceIn(0.0, 1000.0) else 0.0
    }

    private fun computeClassificationAssociation(df: DataFrame, featureCol: String, targetCol: String): Double {
        val fIdx = df.getColumnIndex(featureCol)
        val tIdx = df.getColumnIndex(targetCol)
        if (fIdx < 0 || tIdx < 0) return 0.0

        // Ratio of between-class mean differences over variance
        val classGroups = mutableMapOf<String, MutableList<Double>>()
        for (row in df.rows) {
            val t = row.getOrNull(tIdx)?.toString() ?: continue
            val f = toDoubleOrNull(row.getOrNull(fIdx)) ?: continue
            classGroups.computeIfAbsent(t) { mutableListOf() }.add(f)
        }

        if (classGroups.size < 2) return 0.0
        val means = classGroups.values.map { it.average() }
        val maxDiff = (means.maxOrNull() ?: 0.0) - (means.minOrNull() ?: 0.0)
        val allStds = classGroups.values.map { vals ->
            val m = vals.average()
            val v = vals.map { (it - m).pow(2) }.average()
            sqrt(v)
        }
        val avgStd = if (allStds.isNotEmpty()) allStds.average() else 1.0
        return if (avgStd > 1e-9) (maxDiff / (avgStd * 3.0)).coerceIn(0.0, 1.0) else 0.0
    }

    private fun toDoubleOrNull(v: Any?): Double? {
        return when (v) {
            null -> null
            is Number -> v.toDouble()
            is Boolean -> if (v) 1.0 else 0.0
            is String -> v.trim().toDoubleOrNull()
            else -> null
        }
    }
}
