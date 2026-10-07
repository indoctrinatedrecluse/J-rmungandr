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
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DatasetDriftDetectorServiceTest {

    private fun createSampleDf(values: List<Double>, colName: String = "feature"): DataFrame {
        return DataFrame.buildWithStatistics("sample", listOf(Pair(colName, DataTypeCategory.FLOAT)), values.map { listOf(it) })
    }

    @Test
    fun testStableDatasetComparison() {
        val values = (1..50).map { it.toDouble() }
        val refDf = createSampleDf(values)
        val tgtDf = createSampleDf(values)

        val report = DatasetDriftDetectorService.compareDatasets(refDf, tgtDf)

        assertEquals(DriftSeverity.STABLE, report.overallDriftSeverity)
        assertEquals(0, report.driftDetectedCount)
        assertTrue(report.featureDrifts.isNotEmpty())

        val featureDrift = report.featureDrifts.first()
        assertTrue(featureDrift.psi < 0.05, "Identical distributions should have near-zero PSI")
        assertEquals(0.0, featureDrift.ksStatistic, 1e-4)
        assertEquals(DriftSeverity.STABLE, featureDrift.severity)
    }

    @Test
    fun testSevereDriftDetection() {
        val refValues = (1..50).map { it.toDouble() }
        val tgtValues = (50..100).map { it.toDouble() }

        val refDf = createSampleDf(refValues)
        val tgtDf = createSampleDf(tgtValues)

        val report = DatasetDriftDetectorService.compareDatasets(refDf, tgtDf)

        assertTrue(
            report.overallDriftSeverity == DriftSeverity.SEVERE || report.overallDriftSeverity == DriftSeverity.MODERATE,
            "Shifted distribution must trigger drift warning"
        )
        val featureDrift = report.featureDrifts.first()
        assertTrue(featureDrift.psi > 0.10, "PSI should indicate noticeable drift")
        assertTrue(featureDrift.ksStatistic > 0.5, "K-S statistic should be large for non-overlapping data")
    }

    @Test
    fun testSchemaDifferenceDetection() {
        val refCols = listOf(Pair("col_common", DataTypeCategory.FLOAT), Pair("col_only_in_ref", DataTypeCategory.STRING))
        val refRows = listOf(listOf(1.0, "a"), listOf(2.0, "b"))
        val refDf = DataFrame.buildWithStatistics("ref", refCols, refRows)

        val tgtCols = listOf(Pair("col_common", DataTypeCategory.FLOAT), Pair("col_only_in_tgt", DataTypeCategory.INTEGER))
        val tgtRows = listOf(listOf(1.0, 10L), listOf(2.0, 20L))
        val tgtDf = DataFrame.buildWithStatistics("tgt", tgtCols, tgtRows)

        val report = DatasetDriftDetectorService.compareDatasets(refDf, tgtDf)

        assertTrue(report.schemaDiff.commonColumns.contains("col_common"))
        assertTrue(report.schemaDiff.missingInTarget.contains("col_only_in_ref"))
        assertTrue(report.schemaDiff.addedInTarget.contains("col_only_in_tgt"))
    }
}
