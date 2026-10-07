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

package org.jormungandr.dataframe.drift

import org.jormungandr.dataframe.model.DataFrame
import org.jormungandr.dataframe.model.DataTypeCategory
import kotlin.math.*

enum class DriftSeverity {
    STABLE,    // PSI < 0.1
    MODERATE,  // 0.1 <= PSI < 0.25
    SEVERE     // PSI >= 0.25
}

data class FeatureDriftResult(
    val featureName: String,
    val psi: Double, // Population Stability Index
    val ksStatistic: Double, // Kolmogorov-Smirnov 2-sample statistic
    val ksPValue: Double,
    val severity: DriftSeverity,
    val referenceMean: Double?,
    val targetMean: Double?,
    val meanShiftPct: Double?,
    val referenceMissingPct: Double,
    val targetMissingPct: Double,
    val referenceBins: DoubleArray,
    val targetBins: DoubleArray,
    val binEdges: DoubleArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FeatureDriftResult) return false
        return featureName == other.featureName && psi == other.psi
    }

    override fun hashCode(): Int = featureName.hashCode() * 31 + psi.hashCode()
}

data class SchemaComparisonResult(
    val commonColumns: List<String>,
    val addedInTarget: List<String>,
    val missingInTarget: List<String>,
    val typeMismatches: Map<String, Pair<DataTypeCategory, DataTypeCategory>>
)

data class DatasetDriftReport(
    val referenceName: String,
    val targetName: String,
    val referenceRows: Int,
    val targetRows: Int,
    val schemaDiff: SchemaComparisonResult,
    val featureDrifts: List<FeatureDriftResult>,
    val overallDriftSeverity: DriftSeverity,
    val driftDetectedCount: Int
)

/**
 * High-performance Statistical Dataset Drift and Distribution Comparison Service.
 * Computes Population Stability Index (PSI), 2-Sample Kolmogorov-Smirnov (K-S) test,
 * and empirical cumulative distribution shifts between train/test or prod/staging datasets.
 */
object DatasetDriftDetectorService {

    /**
     * Compares reference (baseline) DataFrame against target (new/current) DataFrame.
     */
    fun compareDatasets(
        reference: DataFrame,
        target: DataFrame,
        referenceName: String = "Reference (Train)",
        targetName: String = "Target (Test/Prod)",
        numBins: Int = 10
    ): DatasetDriftReport {
        val refColMap = reference.columns.associateBy { it.name }
        val tgtColMap = target.columns.associateBy { it.name }

        val common = refColMap.keys.intersect(tgtColMap.keys).sorted()
        val added = tgtColMap.keys.minus(refColMap.keys).sorted()
        val missing = refColMap.keys.minus(tgtColMap.keys).sorted()

        val typeMismatches = mutableMapOf<String, Pair<DataTypeCategory, DataTypeCategory>>()
        for (col in common) {
            val t1 = refColMap[col]?.category ?: DataTypeCategory.STRING
            val t2 = tgtColMap[col]?.category ?: DataTypeCategory.STRING
            if (t1 != t2) {
                typeMismatches[col] = Pair(t1, t2)
            }
        }

        val schemaDiff = SchemaComparisonResult(common, added, missing, typeMismatches)

        val featureDrifts = mutableListOf<FeatureDriftResult>()
        for (colName in common) {
            val refCol = refColMap[colName] ?: continue
            val tgtCol = tgtColMap[colName] ?: continue

            val refVals = reference.getColumnValues(colName)
            val tgtVals = target.getColumnValues(colName)

            val refNullPct = refVals.count { it == null } * 100.0 / refVals.size.coerceAtLeast(1)
            val tgtNullPct = tgtVals.count { it == null } * 100.0 / tgtVals.size.coerceAtLeast(1)

            val refDoubles = refVals.mapNotNull { (it as? Number)?.toDouble() }
            val tgtDoubles = tgtVals.mapNotNull { (it as? Number)?.toDouble() }

            if (refDoubles.size >= 5 && tgtDoubles.size >= 5) {
                val drift = computeNumericDrift(
                    featureName = colName,
                    refVals = refDoubles,
                    tgtVals = tgtDoubles,
                    refNullPct = refNullPct,
                    tgtNullPct = tgtNullPct,
                    numBins = numBins
                )
                featureDrifts.add(drift)
            }
        }

        val severeCount = featureDrifts.count { it.severity == DriftSeverity.SEVERE }
        val modCount = featureDrifts.count { it.severity == DriftSeverity.MODERATE }
        val overallSeverity = when {
            severeCount > 0 -> DriftSeverity.SEVERE
            modCount > 0 -> DriftSeverity.MODERATE
            else -> DriftSeverity.STABLE
        }

        return DatasetDriftReport(
            referenceName = referenceName,
            targetName = targetName,
            referenceRows = reference.rowCount,
            targetRows = target.rowCount,
            schemaDiff = schemaDiff,
            featureDrifts = featureDrifts.sortedByDescending { it.psi },
            overallDriftSeverity = overallSeverity,
            driftDetectedCount = severeCount + modCount
        )
    }

    private fun computeNumericDrift(
        featureName: String,
        refVals: List<Double>,
        tgtVals: List<Double>,
        refNullPct: Double,
        tgtNullPct: Double,
        numBins: Int
    ): FeatureDriftResult {
        val refMean = refVals.average()
        val tgtMean = tgtVals.average()
        val meanShiftPct = if (abs(refMean) > 1e-6) ((tgtMean - refMean) / refMean) * 100.0 else 0.0

        // Determine bin edges across combined data range
        val minV = min(refVals.minOrNull() ?: 0.0, tgtVals.minOrNull() ?: 0.0)
        val maxV = max(refVals.maxOrNull() ?: 1.0, tgtVals.maxOrNull() ?: 1.0)
        val span = (maxV - minV).coerceAtLeast(1e-6)
        val step = span / numBins
        val binEdges = DoubleArray(numBins + 1) { minV + it * step }

        // Bin counts
        val refCounts = DoubleArray(numBins)
        for (v in refVals) {
            val binIdx = ((v - minV) / step).toInt().coerceIn(0, numBins - 1)
            refCounts[binIdx]++
        }

        val tgtCounts = DoubleArray(numBins)
        for (v in tgtVals) {
            val binIdx = ((v - minV) / step).toInt().coerceIn(0, numBins - 1)
            tgtCounts[binIdx]++
        }

        // Calculate Population Stability Index (PSI)
        // With Laplace smoothing (1e-4) to avoid division by zero or log(0)
        val eps = 1e-4
        val refTotal = refVals.size.toDouble()
        val tgtTotal = tgtVals.size.toDouble()

        var psi = 0.0
        val refFractions = DoubleArray(numBins)
        val tgtFractions = DoubleArray(numBins)

        for (i in 0 until numBins) {
            val pRef = (refCounts[i] / refTotal).coerceAtLeast(eps)
            val pTgt = (tgtCounts[i] / tgtTotal).coerceAtLeast(eps)
            refFractions[i] = pRef
            tgtFractions[i] = pTgt
            psi += (pTgt - pRef) * ln(pTgt / pRef)
        }

        // Two-Sample Kolmogorov-Smirnov Test (K-S test)
        val (ksStat, ksPVal) = computeTwoSampleKs(refVals.sorted(), tgtVals.sorted())

        val severity = when {
            psi >= 0.25 -> DriftSeverity.SEVERE
            psi >= 0.10 -> DriftSeverity.MODERATE
            else -> DriftSeverity.STABLE
        }

        return FeatureDriftResult(
            featureName = featureName,
            psi = psi,
            ksStatistic = ksStat,
            ksPValue = ksPVal,
            severity = severity,
            referenceMean = refMean,
            targetMean = tgtMean,
            meanShiftPct = meanShiftPct,
            referenceMissingPct = refNullPct,
            targetMissingPct = tgtNullPct,
            referenceBins = refFractions,
            targetBins = tgtFractions,
            binEdges = binEdges
        )
    }

    /**
     * Computes the 2-Sample Kolmogorov-Smirnov statistic D = max |F1(x) - F2(x)|.
     */
    fun computeTwoSampleKs(sortedRef: List<Double>, sortedTgt: List<Double>): Pair<Double, Double> {
        val n1 = sortedRef.size
        val n2 = sortedTgt.size
        if (n1 == 0 || n2 == 0) return Pair(0.0, 1.0)

        var i1 = 0
        var i2 = 0
        var maxD = 0.0

        while (i1 < n1 && i2 < n2) {
            val v1 = sortedRef[i1]
            val v2 = sortedTgt[i2]
            val v = min(v1, v2)

            while (i1 < n1 && sortedRef[i1] <= v) i1++
            while (i2 < n2 && sortedTgt[i2] <= v) i2++

            val cdf1 = i1.toDouble() / n1
            val cdf2 = i2.toDouble() / n2
            val diff = abs(cdf1 - cdf2)
            if (diff > maxD) maxD = diff
        }

        // Asymptotic Kolmogorov-Smirnov p-value approximation
        val en = sqrt((n1 * n2).toDouble() / (n1 + n2))
        val lambda = (en + 0.12 + 0.11 / en) * maxD
        val pValue = (2.0 * exp(-2.0 * lambda * lambda)).coerceIn(0.0, 1.0)

        return Pair(maxD, pValue)
    }
}
