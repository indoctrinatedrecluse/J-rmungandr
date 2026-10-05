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

class MlAlgorithmsTest {

    @Test
    fun testRidgeRegressionOnLinearData() {
        // y = 2.0 * x1 + 3.0 * x2 + 5.0
        val trainX = arrayOf(
            doubleArrayOf(1.0, 1.0),
            doubleArrayOf(2.0, 1.0),
            doubleArrayOf(3.0, 2.0),
            doubleArrayOf(4.0, 2.0),
            doubleArrayOf(5.0, 3.0),
            doubleArrayOf(6.0, 4.0),
            doubleArrayOf(7.0, 4.0),
            doubleArrayOf(8.0, 5.0)
        )
        val trainY = DoubleArray(trainX.size) { i ->
            2.0 * trainX[i][0] + 3.0 * trainX[i][1] + 5.0
        }

        val testX = arrayOf(
            doubleArrayOf(2.5, 1.5),
            doubleArrayOf(5.5, 3.5)
        )
        val testY = DoubleArray(testX.size) { i ->
            2.0 * testX[i][0] + 3.0 * testX[i][1] + 5.0
        }

        val res = MlAlgorithms.trainRidgeRegression(
            trainX = trainX,
            trainY = trainY,
            testX = testX,
            testY = testY,
            featureNames = listOf("x1", "x2"),
            l2Penalty = 0.0001
        )

        assertNotNull(res)
        assertTrue(res.r2 > 0.99, "R² should exceed 0.99 on pure linear data, got ${res.r2}")
        assertTrue(res.rmse < 0.2, "RMSE should be near zero, got ${res.rmse}")
        assertEquals(2, res.coefficients.size)
        assertTrue(abs(res.coefficients[0] - 2.0) < 0.2, "x1 coefficient should be approx 2.0")
        assertTrue(abs(res.coefficients[1] - 3.0) < 0.2, "x2 coefficient should be approx 3.0")
    }

    @Test
    fun testPolynomialRegressionOnQuadraticData() {
        // y = 1.5 * x^2 + 0.5 * x + 2.0
        val n = 20
        val trainX = Array(n) { i -> doubleArrayOf(i.toDouble()) }
        val trainY = DoubleArray(n) { i -> 1.5 * i * i + 0.5 * i + 2.0 }

        val res = MlAlgorithms.trainPolynomialRegression(
            trainX = trainX,
            trainY = trainY,
            testX = trainX,
            testY = trainY,
            featureNames = listOf("x"),
            l2Penalty = 0.001
        )

        assertNotNull(res)
        assertTrue(res.r2 > 0.98, "Polynomial R² should be high on quadratic data, got ${res.r2}")
        assertEquals(2, res.coefficients.size) // x and x^2
    }

    @Test
    fun testLogisticRegressionClassification() {
        // Two linearly separable 2D classes
        val class0 = (1..15).map { doubleArrayOf(1.0 + it * 0.1, 1.0 + it * 0.1) }
        val class1 = (1..15).map { doubleArrayOf(8.0 + it * 0.1, 8.0 + it * 0.1) }

        val allX = (class0 + class1).toTypedArray()
        val allY = DoubleArray(allX.size) { if (it < 15) 0.0 else 1.0 }

        val res = MlAlgorithms.trainLogisticRegression(
            trainX = allX,
            trainY = allY,
            testX = allX,
            testY = allY,
            classLabels = listOf("Class A", "Class B"),
            epochs = 100,
            learningRate = 0.1
        )

        assertNotNull(res)
        assertTrue(res.accuracy >= 0.90, "Accuracy should be >= 90% on linearly separable data, got ${res.accuracy}")
        assertEquals(2, res.confusionMatrix.size)
        assertEquals(2, res.confusionMatrix[0].size)
        assertTrue(res.rocCurve.isNotEmpty(), "ROC curve points should not be empty")
        assertTrue(res.rocCurveAuc >= 0.85, "ROC AUC should be high, got ${res.rocCurveAuc}")
    }

    @Test
    fun testKMeansClustering() {
        // 3 distinct cluster blobs in 2D
        val blob1 = (1..10).map { doubleArrayOf(1.0 + it * 0.05, 1.0 + it * 0.05) }
        val blob2 = (1..10).map { doubleArrayOf(10.0 + it * 0.05, 10.0 + it * 0.05) }
        val blob3 = (1..10).map { doubleArrayOf(20.0 + it * 0.05, 2.0 + it * 0.05) }

        val data = (blob1 + blob2 + blob3).toTypedArray()
        val res = MlAlgorithms.trainKMeans(
            data = data,
            k = 3,
            maxIterations = 30,
            featureNames = listOf("f1", "f2")
        )

        assertNotNull(res)
        assertEquals(3, res.k)
        assertEquals(30, res.clusterAssignments.size)
        assertEquals(3, res.centroids.size)
        assertTrue(res.inertia > 0.0, "Inertia should be positive")
        assertEquals(3, res.clusterSizes.size)
        assertTrue(res.elbowCurve.isNotEmpty(), "Elbow curve should be generated")
    }

    @Test
    fun testPcaDimensionalityReduction() {
        // 3D data where first 2 dimensions contain most variance
        val n = 30
        val data = Array(n) { i ->
            doubleArrayOf(
                i * 2.0,
                i * 1.5,
                (i % 3) * 0.1
            )
        }

        val res = MlAlgorithms.trainPca(
            data = data,
            maxComponents = 3,
            featureNames = listOf("x", "y", "z")
        )

        assertNotNull(res)
        assertTrue(res.explainedVarianceRatio.isNotEmpty())
        assertTrue(res.explainedVarianceRatio[0] > 0.5, "PC1 should explain > 50% variance, got ${res.explainedVarianceRatio[0]}")
        assertEquals(n, res.projectedData2D.size)
        assertEquals(2, res.projectedData2D[0].size)
    }

    @Test
    fun testMatrixInversion() {
        val mat = arrayOf(
            doubleArrayOf(4.0, 7.0),
            doubleArrayOf(2.0, 6.0)
        )
        val inv = MlAlgorithms.invertMatrix(mat)
        assertNotNull(inv)
        // [4 7; 2 6] det = 24 - 14 = 10 -> inv = [0.6 -0.7; -0.2 0.4]
        assertTrue(abs(inv!![0][0] - 0.6) < 1e-4)
        assertTrue(abs(inv[0][1] - (-0.7)) < 1e-4)
        assertTrue(abs(inv[1][0] - (-0.2)) < 1e-4)
        assertTrue(abs(inv[1][1] - 0.4) < 1e-4)
    }
}
