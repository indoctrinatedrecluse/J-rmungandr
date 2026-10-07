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
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DataQualityProfilerServiceTest {

    private val testDf = DataFrame.buildWithStatistics(
        name = "user_profiles",
        rawColumns = listOf(
            Pair("id", DataTypeCategory.INTEGER),
            Pair("age", DataTypeCategory.INTEGER),
            Pair("country", DataTypeCategory.STRING),
            Pair("salary", DataTypeCategory.FLOAT),
            Pair("status", DataTypeCategory.STRING)
        ),
        rows = listOf(
            listOf(1L, 25L, "US", 50000.0, "ACTIVE"),
            listOf(2L, 30L, "UK", 60000.0, "ACTIVE"),
            listOf(3L, 45L, "DE", 75000.0, "INACTIVE"),
            listOf(4L, 22L, null, 45000.0, "PENDING"),
            listOf(5L, 38L, "FR", 250000.0, "ACTIVE") // 250000 is an outlier
        )
    )

    @Test
    fun `test analyzeSparsity correctly computes missing counts and percentages`() {
        val sparsity = DataQualityProfilerService.analyzeSparsity(testDf)
        assertNotNull(sparsity)
        assertEquals(25L, sparsity.totalCells) // 5 rows * 5 cols
        assertEquals(1L, sparsity.missingCells) // 1 null in country
        assertEquals(96.0, sparsity.completenessPercentage, 0.001)

        assertEquals(0L, sparsity.columnMissingCounts["id"])
        assertEquals(1L, sparsity.columnMissingCounts["country"])
        assertEquals(20.0, sparsity.columnMissingPercentages["country"]!!, 0.001)

        assertEquals(4, sparsity.rowMissingDistribution["0 Missing (Complete)"])
        assertEquals(1, sparsity.rowMissingDistribution["1 Missing"])
    }

    @Test
    fun `test detectOutliers tags extreme values`() {
        val outliers = DataQualityProfilerService.detectOutliers(testDf)
        assertNotNull(outliers)
        val salaryReport = outliers.find { it.columnName == "salary" }
        assertNotNull(salaryReport)
        assertTrue(salaryReport!!.iqrOutliersCount > 0)
        assertTrue(salaryReport.sampleOutlierValues.contains(250000.0))
    }

    @Test
    fun `test evaluateRule validates NonNull Range and Unique assertions`() {
        val nonNullPass = DataQualityProfilerService.evaluateRule(testDf, DataValidationRule.NonNull("id"))
        assertTrue(nonNullPass.passed)
        assertEquals(0, nonNullPass.violationsCount)

        val nonNullFail = DataQualityProfilerService.evaluateRule(testDf, DataValidationRule.NonNull("country"))
        assertFalse(nonNullFail.passed)
        assertEquals(1, nonNullFail.violationsCount)

        val uniquePass = DataQualityProfilerService.evaluateRule(testDf, DataValidationRule.Unique("id"))
        assertTrue(uniquePass.passed)

        val rangePass = DataQualityProfilerService.evaluateRule(testDf, DataValidationRule.Range("age", 18.0, 100.0))
        assertTrue(rangePass.passed)

        val rangeFail = DataQualityProfilerService.evaluateRule(testDf, DataValidationRule.Range("age", 30.0, 100.0))
        assertFalse(rangeFail.passed) // 25 and 22 fail
        assertEquals(2, rangeFail.violationsCount)
    }

    @Test
    fun `test analyzeQuality computes health scorecard and generates Pandera schema`() {
        val scorecard = DataQualityProfilerService.analyzeQuality(testDf)
        assertNotNull(scorecard)
        assertTrue(scorecard.overallHealthScore > 60.0)
        assertFalse(scorecard.healthGrade.isBlank())
        assertTrue(scorecard.validationResults.isNotEmpty())

        val panderaCode = scorecard.panderaCode
        assertTrue(panderaCode.contains("import pandera as pa"))
        assertTrue(panderaCode.contains("schema = DataFrameSchema({"))
        assertTrue(panderaCode.contains("\"id\": Column(pa.Int64"))
        assertTrue(panderaCode.contains("\"salary\": Column(pa.Float64"))
    }
}
