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

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.abs

class ModelExplainabilityEngineTest {

    @Test
    fun testExplainRegressionShapAdditivityAndPdp() {
        val featureNames = listOf("feature_a", "feature_b", "feature_c")
        val coefficients = doubleArrayOf(3.0, -1.5, 0.5)
        val intercept = 2.0

        val nRows = 20
        val xMatrix = Array(nRows) { row ->
            doubleArrayOf(row.toDouble(), (row * 2).toDouble(), (row % 3).toDouble())
        }

        val yTrue = DoubleArray(nRows) { r ->
            intercept + coefficients[0] * xMatrix[r][0] + coefficients[1] * xMatrix[r][1] + coefficients[2] * xMatrix[r][2]
        }
        val yPred = yTrue.clone()
        val residuals = DoubleArray(nRows) { 0.0 }

        val regressionResult = RegressionResult(
            modelName = "Linear Regression (OLS)",
            intercept = intercept,
            coefficients = coefficients,
            r2 = 0.99,
            mae = 0.01,
            mse = 0.01,
            rmse = 0.01,
            yTrue = yTrue,
            yPred = yPred,
            residuals = residuals,
            featureNames = featureNames
        )

        val report = ModelExplainabilityEngine.explainRegression(regressionResult, xMatrix)

        assertEquals("Linear Regression (OLS)", report.modelName)
        assertEquals(nRows, report.instanceExplanations.size)
        assertEquals(3, report.globalImportance.size)

        // Verify Efficiency Property of Shapley values: sum(phi_i) + E[f(X)] == f(x)
        for (inst in report.instanceExplanations) {
            val sumShap = inst.attributions.sumOf { it.shapValue }
            val reconstructedPred = inst.baseValue + sumShap
            assertTrue(
                abs(reconstructedPred - inst.prediction) < 1e-4,
                "Sum of SHAP values plus base value must equal predicted output. Expected: ${inst.prediction}, Got: $reconstructedPred"
            )
        }

        // Global importance should rank feature_a or feature_b highest
        val topGlobal = report.globalImportance.first()
        assertTrue(topGlobal.featureName == "feature_a" || topGlobal.featureName == "feature_b")

        // Partial dependence curves
        assertTrue(report.partialDependenceCurves.containsKey("feature_a"))
        val pdpA = report.partialDependenceCurves["feature_a"]!!
        assertEquals(20, pdpA.gridPoints.size)
        assertEquals(20, pdpA.pdpValues.size)
        assertTrue(pdpA.iceCurves.isNotEmpty())
    }

    @Test
    fun testExplainClassificationAndCodeGeneration() {
        val classLabels = listOf("Benign", "Malignant")
        val nRows = 15
        val xMatrix = Array(nRows) { r -> doubleArrayOf(r * 0.1, r * 0.5) }
        val yTrue = DoubleArray(nRows) { if (it > 7) 1.0 else 0.0 }
        val yPred = yTrue.clone()
        val probs = Array(nRows) { r -> doubleArrayOf(1.0 - (r / 15.0), r / 15.0) }

        val classificationResult = ClassificationResult(
            modelName = "Logistic Regression",
            accuracy = 0.93,
            precision = 0.90,
            recall = 0.92,
            f1 = 0.91,
            confusionMatrix = arrayOf(intArrayOf(7, 1), intArrayOf(0, 7)),
            classLabels = classLabels,
            rocCurve = listOf(0.0 to 0.0, 0.1 to 0.9, 1.0 to 1.0),
            rocCurveAuc = 0.95,
            yTrue = yTrue,
            yPred = yPred,
            probabilities = probs
        )

        val report = ModelExplainabilityEngine.explainClassification(
            result = classificationResult,
            xMatrix = xMatrix,
            predictProba = { row -> 1.0 / (1.0 + kotlin.math.exp(-(row[0] * 2.0 + row[1]))) }
        )

        assertNotNull(report)
        assertEquals(nRows, report.instanceExplanations.size)
        assertTrue(report.globalImportance.isNotEmpty())

        val pyCode = ModelExplainabilityEngine.generatePythonShapCode(
            modelName = "Logistic Regression",
            featureNames = listOf("radius_mean", "texture_mean")
        )

        assertTrue(pyCode.contains("import shap"))
        assertTrue(pyCode.contains("shap.summary_plot"))
        assertTrue(pyCode.contains("PartialDependenceDisplay"))
    }
}
