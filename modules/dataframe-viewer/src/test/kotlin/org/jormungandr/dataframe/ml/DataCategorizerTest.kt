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
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.abs

class DataCategorizerTest {

    @Test
    fun testPreliminaryCategorizationIdentifiesRoles() {
        val n = 30
        val cols = listOf(
            "id" to DataTypeCategory.INTEGER,
            "feature_cont" to DataTypeCategory.FLOAT,
            "category_str" to DataTypeCategory.STRING,
            "constant_col" to DataTypeCategory.STRING,
            "target" to DataTypeCategory.FLOAT
        )

        val rows = (1..n).map { i ->
            listOf(
                i,                          // unique ID
                i * 2.5 + (i % 3),         // continuous
                if (i % 2 == 0) "A" else "B",// categorical
                "FIXED_VALUE",              // constant zero-variance
                i * 5.0                     // target
            )
        }

        val df = DataFrame.buildWithStatistics("test.csv", cols, rows)
        val report = DataCategorizer.analyze(df, "target")

        assertNotNull(report)
        assertEquals("target", report.targetName)

        val idAudit = report.features.find { it.columnName == "id" }
        assertNotNull(idAudit)
        assertEquals(FeatureRole.HIGH_CARDINALITY_ID, idAudit!!.role)
        assertFalse(idAudit.isRecommendedForTraining)

        val constAudit = report.features.find { it.columnName == "constant_col" }
        assertNotNull(constAudit)
        assertEquals(FeatureRole.CONSTANT_ZERO_VARIANCE, constAudit!!.role)
        assertFalse(constAudit.isRecommendedForTraining)

        val contAudit = report.features.find { it.columnName == "feature_cont" }
        assertNotNull(contAudit)
        assertEquals(FeatureRole.CONTINUOUS_NUMERIC, contAudit!!.role)
        assertTrue(contAudit.isRecommendedForTraining)
        assertTrue(contAudit.predictiveScoreWithTarget > 0.90, "feature_cont should have strong correlation with target")

        val catAudit = report.features.find { it.columnName == "category_str" }
        assertNotNull(catAudit)
        assertEquals(FeatureRole.CATEGORICAL, catAudit!!.role)
        assertTrue(catAudit.isRecommendedForTraining)

        assertTrue(report.recommendedFeatures.contains("feature_cont"))
        assertTrue(report.discardFeatures.contains("id"))
        assertTrue(report.discardFeatures.contains("constant_col"))
    }

    @Test
    fun testMulticollinearityDetection() {
        val n = 40
        val cols = listOf(
            "feat_1" to DataTypeCategory.FLOAT,
            "feat_collinear" to DataTypeCategory.FLOAT,
            "target" to DataTypeCategory.FLOAT
        )
        val rows = (1..n).map { i ->
            val v = i.toDouble()
            listOf(
                v,
                v * 1.01 + 0.05, // Almost 100% collinear with feat_1
                v * 3.0
            )
        }
        val df = DataFrame.buildWithStatistics("collinear.csv", cols, rows)
        val report = DataCategorizer.analyze(df, "target")

        val pair = report.pairwiseCorrelations.find {
            (it.featureA == "feat_1" && it.featureB == "feat_collinear") ||
            (it.featureA == "feat_collinear" && it.featureB == "feat_1")
        }
        assertNotNull(pair)
        assertTrue(abs(pair!!.correlation) > 0.95, "Correlation should be > 0.95, got ${pair.correlation}")
        assertTrue(pair.isMulticollinear, "Should be flagged as multicollinear")
        assertTrue(report.insights.any { it.contains("Multicollinearity") })
    }

    @Test
    fun testSampleDatasetGeneration() {
        for (kind in SampleDatasetKind.values()) {
            val df = MlDataExtractor.createSampleDataset(kind)
            assertNotNull(df)
            assertTrue(df.rowCount > 50, "Sample dataset ${kind.name} should have > 50 rows")
            assertTrue(df.columnCount >= 3, "Sample dataset ${kind.name} should have >= 3 columns")
        }
    }
}
