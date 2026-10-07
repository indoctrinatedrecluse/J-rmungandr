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
import kotlin.math.abs

class CorrelationMatrixCalculatorTest {

    private val sampleDf = DataFrame.buildWithStatistics(
        name = "test_metrics",
        rawColumns = listOf(
            Pair("x", DataTypeCategory.FLOAT),
            Pair("y_pos", DataTypeCategory.FLOAT),
            Pair("y_neg", DataTypeCategory.FLOAT),
            Pair("label", DataTypeCategory.STRING)
        ),
        rows = listOf(
            listOf(1.0, 2.0, 10.0, "A"),
            listOf(2.0, 4.0, 8.0, "B"),
            listOf(3.0, 6.0, 6.0, "C"),
            listOf(4.0, 8.0, 4.0, "D"),
            listOf(5.0, 10.0, 2.0, "E")
        )
    )

    @Test
    fun `test Pearson correlation calculates perfect positive and negative correlation`() {
        val corr = CorrelationMatrixCalculator.compute(sampleDf, CorrelationMethod.PEARSON)
        assertEquals(3, corr.columnNames.size) // x, y_pos, y_neg (excludes non-numeric label)
        assertTrue(corr.columnNames.containsAll(listOf("x", "y_pos", "y_neg")))

        // Diagonal must be exactly 1.0
        assertEquals(1.0, corr.getCorrelation("x", "x"), 0.0001)
        assertEquals(1.0, corr.getCorrelation("y_pos", "y_pos"), 0.0001)

        // Perfect positive correlation between x and y_pos
        val rPos = corr.getCorrelation("x", "y_pos")
        assertEquals(1.0, rPos, 0.0001)

        // Perfect negative correlation between x and y_neg
        val rNeg = corr.getCorrelation("x", "y_neg")
        assertEquals(-1.0, rNeg, 0.0001)
    }

    @Test
    fun `test Spearman rank correlation handles monotonic relationships`() {
        val monotonicDf = DataFrame.buildWithStatistics(
            name = "monotonic",
            rawColumns = listOf(
                Pair("a", DataTypeCategory.FLOAT),
                Pair("b", DataTypeCategory.FLOAT)
            ),
            rows = listOf(
                listOf(1.0, 1.0),
                listOf(2.0, 10.0),
                listOf(3.0, 100.0),
                listOf(4.0, 1000.0)
            )
        )

        val corr = CorrelationMatrixCalculator.compute(monotonicDf, CorrelationMethod.SPEARMAN)
        val rho = corr.getCorrelation("a", "b")
        assertEquals(1.0, rho, 0.0001)
    }

    @Test
    fun `test Kendall tau rank correlation calculates concordant pairs`() {
        val corr = CorrelationMatrixCalculator.compute(sampleDf, CorrelationMethod.KENDALL)
        val tauPos = corr.getCorrelation("x", "y_pos")
        val tauNeg = corr.getCorrelation("x", "y_neg")

        assertEquals(1.0, tauPos, 0.0001)
        assertEquals(-1.0, tauNeg, 0.0001)
    }
}
