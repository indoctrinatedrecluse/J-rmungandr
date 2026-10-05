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

import java.util.Random
import kotlin.math.*

// --- Evaluation Result Data Models ---

data class RegressionResult(
    val modelName: String,
    val intercept: Double,
    val coefficients: DoubleArray,
    val r2: Double,
    val mae: Double,
    val mse: Double,
    val rmse: Double,
    val yTrue: DoubleArray,
    val yPred: DoubleArray,
    val residuals: DoubleArray,
    val featureNames: List<String>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RegressionResult) return false
        return modelName == other.modelName && r2 == other.r2
    }

    override fun hashCode(): Int = modelName.hashCode() * 31 + r2.hashCode()
}

data class ClassificationResult(
    val modelName: String,
    val accuracy: Double,
    val precision: Double,
    val recall: Double,
    val f1: Double,
    val confusionMatrix: Array<IntArray>,
    val classLabels: List<String>,
    val rocCurve: List<Pair<Double, Double>>, // (FPR, TPR)
    val rocCurveAuc: Double,
    val yTrue: DoubleArray,
    val yPred: DoubleArray,
    val probabilities: Array<DoubleArray>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ClassificationResult) return false
        return modelName == other.modelName && accuracy == other.accuracy
    }

    override fun hashCode(): Int = modelName.hashCode() * 31 + accuracy.hashCode()
}

data class ClusteringResult(
    val modelName: String,
    val k: Int,
    val clusterAssignments: IntArray,
    val centroids: Array<DoubleArray>,
    val inertia: Double,
    val elbowCurve: List<Pair<Int, Double>>, // (k, inertia)
    val clusterSizes: Map<Int, Int>,
    val featureNames: List<String>,
    val data2D: Array<DoubleArray> // Project to 2D for visual scatter
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ClusteringResult) return false
        return modelName == other.modelName && k == other.k && inertia == other.inertia
    }

    override fun hashCode(): Int = modelName.hashCode() * 31 + inertia.hashCode()
}

data class PcaResult(
    val explainedVarianceRatio: DoubleArray,
    val cumulativeVariance: DoubleArray,
    val projectedData2D: Array<DoubleArray>,
    val components: Array<DoubleArray>,
    val featureNames: List<String>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PcaResult) return false
        return explainedVarianceRatio.contentEquals(other.explainedVarianceRatio)
    }

    override fun hashCode(): Int = explainedVarianceRatio.contentHashCode()
}

// --- Algorithmic Training Implementations ---

object MlAlgorithms {

    /**
     * Fits an Ordinary Least Squares (OLS) or Ridge Regularized Linear Regression model.
     * Closed-form solution: beta = (X^T * X + lambda * I)^(-1) * X^T * y
     */
    fun trainRidgeRegression(
        trainX: Array<DoubleArray>,
        trainY: DoubleArray,
        testX: Array<DoubleArray>,
        testY: DoubleArray,
        featureNames: List<String>,
        l2Penalty: Double = 0.01,
        modelName: String = "Ridge Regression"
    ): RegressionResult {
        val n = trainX.size
        val p = if (n > 0) trainX[0].size else 0
        if (n == 0 || p == 0) {
            return emptyRegression(modelName, featureNames)
        }

        // Augment X with leading column of 1.0 for bias/intercept
        val xAug = Array(n) { i ->
            DoubleArray(p + 1) { j -> if (j == 0) 1.0 else trainX[i][j - 1] }
        }

        val xt = transpose(xAug)
        val xtx = multiply(xt, xAug)

        // Add L2 penalty to diagonal (except intercept at index 0)
        for (j in 1..p) {
            xtx[j][j] += l2Penalty
        }

        val invXtx = invertMatrix(xtx) ?: return fallbackRegression(trainX, trainY, testX, testY, featureNames, modelName)
        val xty = multiply(xt, trainY)
        val beta = multiply(invXtx, xty)

        val intercept = beta[0]
        val coefs = DoubleArray(p) { j -> beta[j + 1] }

        // Predict on test set (or train if test is empty)
        val evalX = if (testX.isNotEmpty()) testX else trainX
        val evalY = if (testY.isNotEmpty()) testY else trainY

        val evalCount = evalX.size
        val yPred = DoubleArray(evalCount) { i ->
            var sum = intercept
            for (j in 0 until p) {
                sum += coefs[j] * evalX[i][j]
            }
            sum
        }

        val residuals = DoubleArray(evalCount) { i -> evalY[i] - yPred[i] }
        var sse = 0.0
        var sae = 0.0
        val yMean = if (evalCount > 0) evalY.average() else 0.0
        var sst = 0.0

        for (i in 0 until evalCount) {
            val err = evalY[i] - yPred[i]
            sse += err * err
            sae += abs(err)
            val dev = evalY[i] - yMean
            sst += dev * dev
        }

        val mse = if (evalCount > 0) sse / evalCount else 0.0
        val rmse = sqrt(mse)
        val mae = if (evalCount > 0) sae / evalCount else 0.0
        val r2 = if (sst > 1e-12) (1.0 - (sse / sst)).coerceIn(-1.0, 1.0) else 0.0

        return RegressionResult(
            modelName = modelName,
            intercept = intercept,
            coefficients = coefs,
            r2 = r2,
            mae = mae,
            mse = mse,
            rmse = rmse,
            yTrue = evalY,
            yPred = yPred,
            residuals = residuals,
            featureNames = featureNames
        )
    }

    /**
     * Polynomial feature expansion (degree 2) followed by Ridge Regression.
     */
    fun trainPolynomialRegression(
        trainX: Array<DoubleArray>,
        trainY: DoubleArray,
        testX: Array<DoubleArray>,
        testY: DoubleArray,
        featureNames: List<String>,
        l2Penalty: Double = 0.1
    ): RegressionResult {
        val (polyTrainX, polyNames) = expandPolynomialDegree2(trainX, featureNames)
        val (polyTestX, _) = expandPolynomialDegree2(testX, featureNames)
        return trainRidgeRegression(
            trainX = polyTrainX,
            trainY = trainY,
            testX = polyTestX,
            testY = testY,
            featureNames = polyNames,
            l2Penalty = l2Penalty,
            modelName = "Polynomial Regression (deg 2)"
        )
    }

    /**
     * Fits a Logistic Regression classifier (Binary / Multiclass One-vs-Rest) using Gradient Descent.
     */
    fun trainLogisticRegression(
        trainX: Array<DoubleArray>,
        trainY: DoubleArray,
        testX: Array<DoubleArray>,
        testY: DoubleArray,
        classLabels: List<String>,
        epochs: Int = 80,
        learningRate: Double = 0.05,
        l2Penalty: Double = 0.01
    ): ClassificationResult {
        val numClasses = max(classLabels.size, 2)
        val n = trainX.size
        val p = if (n > 0) trainX[0].size else 0

        val evalX = if (testX.isNotEmpty()) testX else trainX
        val evalY = if (testY.isNotEmpty()) testY else trainY
        val evalN = evalX.size

        if (n == 0 || p == 0 || evalN == 0) {
            return emptyClassification(classLabels)
        }

        // Standardize features for gradient descent stability
        val means = DoubleArray(p) { j -> trainX.map { it[j] }.average() }
        val stds = DoubleArray(p) { j ->
            val v = trainX.map { (it[j] - means[j]).pow(2) }.average()
            if (v > 1e-9) sqrt(v) else 1.0
        }

        fun normalizeRow(row: DoubleArray): DoubleArray {
            return DoubleArray(p) { j -> (row[j] - means[j]) / stds[j] }
        }

        val normTrainX = Array(n) { i -> normalizeRow(trainX[i]) }
        val normEvalX = Array(evalN) { i -> normalizeRow(evalX[i]) }

        // Weights: numClasses x (p + 1)
        val weights = Array(numClasses) { DoubleArray(p + 1) }

        // One-vs-Rest training loop
        for (c in 0 until numClasses) {
            val w = weights[c]
            for (ep in 0 until epochs) {
                val grad = DoubleArray(p + 1)
                for (i in 0 until n) {
                    val target = if (trainY[i].toInt() == c) 1.0 else 0.0
                    var logit = w[0]
                    for (j in 0 until p) {
                        logit += w[j + 1] * normTrainX[i][j]
                    }
                    val prob = sigmoid(logit)
                    val err = prob - target
                    grad[0] += err
                    for (j in 0 until p) {
                        grad[j + 1] += err * normTrainX[i][j]
                    }
                }

                // Update with gradient descent and light L2 regularization
                val alpha = max(learningRate, 0.5)
                w[0] -= alpha * (grad[0] / n)
                for (j in 1..p) {
                    w[j] = w[j] * (1.0 - 0.001 * l2Penalty) - alpha * (grad[j] / n)
                }
            }
        }

        // Evaluate predictions and probabilities
        val probMatrix = Array(evalN) { i ->
            val logits = DoubleArray(numClasses) { c ->
                var sum = weights[c][0]
                for (j in 0 until p) {
                    sum += weights[c][j + 1] * normEvalX[i][j]
                }
                sum
            }
            softmax(logits)
        }

        val yPred = DoubleArray(evalN) { i ->
            var maxIdx = 0
            var maxProb = probMatrix[i][0]
            for (c in 1 until numClasses) {
                if (probMatrix[i][c] > maxProb) {
                    maxProb = probMatrix[i][c]
                    maxIdx = c
                }
            }
            maxIdx.toDouble()
        }

        // Confusion matrix: rows = actual, cols = predicted
        val confMatrix = Array(numClasses) { IntArray(numClasses) }
        var correct = 0
        for (i in 0 until evalN) {
            val actual = evalY[i].toInt().coerceIn(0, numClasses - 1)
            val pred = yPred[i].toInt().coerceIn(0, numClasses - 1)
            confMatrix[actual][pred]++
            if (actual == pred) correct++
        }

        val accuracy = if (evalN > 0) correct.toDouble() / evalN else 0.0

        // Macro-averaged precision, recall, f1
        var sumPrec = 0.0
        var sumRec = 0.0
        for (c in 0 until numClasses) {
            val tp = confMatrix[c][c]
            val predCount = (0 until numClasses).sumOf { confMatrix[it][c] }
            val actualCount = (0 until numClasses).sumOf { confMatrix[c][it] }

            val p_c = if (predCount > 0) tp.toDouble() / predCount else 0.0
            val r_c = if (actualCount > 0) tp.toDouble() / actualCount else 0.0
            sumPrec += p_c
            sumRec += r_c
        }

        val precision = sumPrec / numClasses
        val recall = sumRec / numClasses
        val f1 = if (precision + recall > 1e-9) 2.0 * (precision * recall) / (precision + recall) else 0.0

        // Generate exact Wilcoxon ROC-AUC
        val posClass = if (numClasses > 1) 1 else 0
        val posScores = evalY.indices.filter { evalY[it].toInt() == posClass }.map { probMatrix[it][posClass] }
        val negScores = evalY.indices.filter { evalY[it].toInt() != posClass }.map { probMatrix[it][posClass] }

        var auc = 0.5
        if (posScores.isNotEmpty() && negScores.isNotEmpty()) {
            var wins = 0.0
            for (p in posScores) {
                for (neg in negScores) {
                    if (p > neg) wins += 1.0
                    else if (p == neg) wins += 0.5
                }
            }
            auc = (wins / (posScores.size * negScores.size)).coerceIn(0.0, 1.0)
        }

        // Generate ROC curve coordinates
        val rocPoints = mutableListOf<Pair<Double, Double>>()
        rocPoints.add(0.0 to 0.0)
        val sortedProbs = probMatrix.map { it[posClass] }.distinct().sortedDescending()
        for (thresh in sortedProbs) {
            var tp = 0
            var fp = 0
            var fn = 0
            var tn = 0
            for (i in 0 until evalN) {
                val actualPos = evalY[i].toInt() == posClass
                val predPos = probMatrix[i][posClass] >= thresh
                if (actualPos && predPos) tp++
                if (!actualPos && predPos) fp++
                if (actualPos && !predPos) fn++
                if (!actualPos && !predPos) tn++
            }
            val tpr = if (tp + fn > 0) tp.toDouble() / (tp + fn) else 0.0
            val fpr = if (fp + tn > 0) fp.toDouble() / (fp + tn) else 0.0
            rocPoints.add(fpr to tpr)
        }
        rocPoints.add(1.0 to 1.0)
        rocPoints.sortBy { it.first }

        return ClassificationResult(
            modelName = "Logistic Regression",
            accuracy = accuracy,
            precision = precision,
            recall = recall,
            f1 = f1,
            confusionMatrix = confMatrix,
            classLabels = classLabels,
            rocCurve = rocPoints,
            rocCurveAuc = auc,
            yTrue = evalY,
            yPred = yPred,
            probabilities = probMatrix
        )
    }

    /**
     * Fits a K-Means clustering model using Lloyd's iterative centroid refinement.
     * Computes WCSS inertia, cluster assignments, centroid positions, and an elbow curve.
     */
    fun trainKMeans(
        data: Array<DoubleArray>,
        k: Int = 3,
        maxIterations: Int = 50,
        featureNames: List<String> = emptyList(),
        randomSeed: Long = 42L
    ): ClusteringResult {
        val n = data.size
        val p = if (n > 0) data[0].size else 0
        val safeK = k.coerceIn(1, max(n, 1))

        if (n == 0 || p == 0) {
            return ClusteringResult("K-Means", k, IntArray(0), emptyArray(), 0.0, emptyList(), emptyMap(), featureNames, emptyArray())
        }

        val rand = Random(randomSeed)
        val centroids = Array(safeK) { DoubleArray(p) }

        // K-Means++ style initialization
        val firstIdx = rand.nextInt(n)
        for (j in 0 until p) centroids[0][j] = data[firstIdx][j]

        for (c in 1 until safeK) {
            val dists = DoubleArray(n) { i ->
                var minD2 = Double.MAX_VALUE
                for (prev in 0 until c) {
                    val d2 = euclideanDist2(data[i], centroids[prev])
                    if (d2 < minD2) minD2 = d2
                }
                minD2
            }
            val totalDist = dists.sum()
            var r = rand.nextDouble() * totalDist
            var selectedIdx = 0
            for (i in 0 until n) {
                r -= dists[i]
                if (r <= 0) {
                    selectedIdx = i
                    break
                }
            }
            for (j in 0 until p) centroids[c][j] = data[selectedIdx][j]
        }

        // Lloyd iterations
        val assignments = IntArray(n)
        for (iter in 0 until maxIterations) {
            var changed = false
            for (i in 0 until n) {
                var bestCluster = 0
                var bestDist2 = Double.MAX_VALUE
                for (c in 0 until safeK) {
                    val d2 = euclideanDist2(data[i], centroids[c])
                    if (d2 < bestDist2) {
                        bestDist2 = d2
                        bestCluster = c
                    }
                }
                if (assignments[i] != bestCluster) {
                    assignments[i] = bestCluster
                    changed = true
                }
            }

            // Update centroids
            val counts = IntArray(safeK)
            val newCentroids = Array(safeK) { DoubleArray(p) }
            for (i in 0 until n) {
                val c = assignments[i]
                counts[c]++
                for (j in 0 until p) {
                    newCentroids[c][j] += data[i][j]
                }
            }

            for (c in 0 until safeK) {
                if (counts[c] > 0) {
                    for (j in 0 until p) {
                        centroids[c][j] = newCentroids[c][j] / counts[c]
                    }
                }
            }

            if (!changed) break
        }

        // Compute Inertia (WCSS)
        var inertia = 0.0
        for (i in 0 until n) {
            inertia += euclideanDist2(data[i], centroids[assignments[i]])
        }

        val clusterSizes = (0 until safeK).associateWith { c -> assignments.count { it == c } }

        // 2D projection for visualization (first 2 features or PCA projection)
        val data2D = Array(n) { i ->
            doubleArrayOf(
                data[i][0],
                if (p > 1) data[i][1] else 0.0
            )
        }

        // Generate Elbow curve (k=1..8)
        val elbowCurve = (1..min(8, n)).map { testK ->
            val testInertia = computeInertiaFast(data, testK, rand)
            Pair(testK, testInertia)
        }

        return ClusteringResult(
            modelName = "K-Means (k=$safeK)",
            k = safeK,
            clusterAssignments = assignments,
            centroids = centroids,
            inertia = inertia,
            elbowCurve = elbowCurve,
            clusterSizes = clusterSizes,
            featureNames = featureNames,
            data2D = data2D
        )
    }

    /**
     * Fits a Principal Component Analysis (PCA) model using Covariance Power Iteration.
     */
    fun trainPca(
        data: Array<DoubleArray>,
        maxComponents: Int = 4,
        featureNames: List<String> = emptyList()
    ): PcaResult {
        val n = data.size
        val p = if (n > 0) data[0].size else 0
        val k = min(maxComponents, p).coerceAtLeast(1)

        if (n <= 1 || p == 0) {
            return PcaResult(DoubleArray(0), DoubleArray(0), emptyArray(), emptyArray(), featureNames)
        }

        // Center columns
        val means = DoubleArray(p) { j -> data.map { it[j] }.average() }
        val centered = Array(n) { i ->
            DoubleArray(p) { j -> data[i][j] - means[j] }
        }

        // Covariance Matrix S = (1/(n-1)) * X^T * X
        val cov = Array(p) { DoubleArray(p) }
        val denom = (n - 1).toDouble()
        for (i in 0 until p) {
            for (j in i until p) {
                var s = 0.0
                for (r in 0 until n) {
                    s += centered[r][i] * centered[r][j]
                }
                cov[i][j] = s / denom
                cov[j][i] = cov[i][j]
            }
        }

        // Power Iteration with Deflation to find top k eigenvectors
        val eigenvectors = Array(k) { DoubleArray(p) }
        val eigenvalues = DoubleArray(k)
        val workingCov = Array(p) { i -> cov[i].clone() }

        val rand = Random(42L)
        for (comp in 0 until k) {
            var v = DoubleArray(p) { rand.nextGaussian() }
            v = normalizeVector(v)

            for (iter in 0 until 60) {
                val nextV = multiply(workingCov, v)
                val norm = vectorNorm(nextV)
                if (norm < 1e-12) break
                v = DoubleArray(p) { nextV[it] / norm }
            }

            // Rayleigh quotient: lambda = v^T * Cov * v
            val sv = multiply(workingCov, v)
            val lambda = dotProduct(v, sv).coerceAtLeast(0.0)

            eigenvectors[comp] = v
            eigenvalues[comp] = lambda

            // Deflation: Cov = Cov - lambda * v * v^T
            for (i in 0 until p) {
                for (j in 0 until p) {
                    workingCov[i][j] -= lambda * v[i] * v[j]
                }
            }
        }

        val totalVar = eigenvalues.sum().coerceAtLeast(1e-9)
        val varRatios = DoubleArray(k) { i -> eigenvalues[i] / totalVar }
        val cumRatios = DoubleArray(k)
        var runningSum = 0.0
        for (i in 0 until k) {
            runningSum += varRatios[i]
            cumRatios[i] = runningSum.coerceAtMost(1.0)
        }

        // Project dataset onto PC1 and PC2
        val pc1 = eigenvectors[0]
        val pc2 = if (k > 1) eigenvectors[1] else DoubleArray(p)
        val projected2D = Array(n) { i ->
            doubleArrayOf(
                dotProduct(centered[i], pc1),
                dotProduct(centered[i], pc2)
            )
        }

        return PcaResult(
            explainedVarianceRatio = varRatios,
            cumulativeVariance = cumRatios,
            projectedData2D = projected2D,
            components = eigenvectors,
            featureNames = featureNames
        )
    }

    // --- Math & Linear Algebra Helpers ---

    private fun sigmoid(z: Double): Double {
        val clipped = z.coerceIn(-20.0, 20.0)
        return 1.0 / (1.0 + exp(-clipped))
    }

    private fun softmax(logits: DoubleArray): DoubleArray {
        val maxVal = logits.maxOrNull() ?: 0.0
        val expVals = DoubleArray(logits.size) { exp(logits[it] - maxVal) }
        val sumExp = expVals.sum().coerceAtLeast(1e-12)
        return DoubleArray(logits.size) { expVals[it] / sumExp }
    }

    private fun euclideanDist2(a: DoubleArray, b: DoubleArray): Double {
        var sum = 0.0
        for (i in a.indices) {
            val d = a[i] - b[i]
            sum += d * d
        }
        return sum
    }

    private fun computeInertiaFast(data: Array<DoubleArray>, k: Int, rand: Random): Double {
        val n = data.size
        val p = data[0].size
        val centroids = Array(k) { DoubleArray(p) }
        for (c in 0 until k) {
            val idx = (c * (n / k)).coerceIn(0, n - 1)
            for (j in 0 until p) centroids[c][j] = data[idx][j]
        }
        var inertia = 0.0
        for (i in 0 until n) {
            var minD2 = Double.MAX_VALUE
            for (c in 0 until k) {
                val d2 = euclideanDist2(data[i], centroids[c])
                if (d2 < minD2) minD2 = d2
            }
            inertia += minD2
        }
        return inertia
    }

    private fun dotProduct(a: DoubleArray, b: DoubleArray): Double {
        var s = 0.0
        for (i in a.indices) s += a[i] * b[i]
        return s
    }

    private fun vectorNorm(v: DoubleArray): Double = sqrt(dotProduct(v, v))

    private fun normalizeVector(v: DoubleArray): DoubleArray {
        val norm = vectorNorm(v)
        return if (norm > 1e-12) DoubleArray(v.size) { v[it] / norm } else v
    }

    private fun transpose(matrix: Array<DoubleArray>): Array<DoubleArray> {
        val rows = matrix.size
        val cols = matrix[0].size
        return Array(cols) { c -> DoubleArray(rows) { r -> matrix[r][c] } }
    }

    private fun multiply(a: Array<DoubleArray>, b: Array<DoubleArray>): Array<DoubleArray> {
        val rowsA = a.size
        val colsA = a[0].size
        val colsB = b[0].size
        val result = Array(rowsA) { DoubleArray(colsB) }
        for (i in 0 until rowsA) {
            for (k in 0 until colsA) {
                val r = a[i][k]
                for (j in 0 until colsB) {
                    result[i][j] += r * b[k][j]
                }
            }
        }
        return result
    }

    private fun multiply(a: Array<DoubleArray>, v: DoubleArray): DoubleArray {
        val rows = a.size
        val cols = a[0].size
        val result = DoubleArray(rows)
        for (i in 0 until rows) {
            var sum = 0.0
            for (j in 0 until cols) {
                sum += a[i][j] * v[j]
            }
            result[i] = sum
        }
        return result
    }

    /**
     * Inverts a square matrix using Gauss-Jordan elimination with partial pivoting.
     */
    fun invertMatrix(matrix: Array<DoubleArray>): Array<DoubleArray>? {
        val n = matrix.size
        val augmented = Array(n) { i ->
            DoubleArray(2 * n) { j ->
                if (j < n) matrix[i][j] else if (j - n == i) 1.0 else 0.0
            }
        }

        for (i in 0 until n) {
            var pivotRow = i
            var maxVal = abs(augmented[i][i])
            for (k in i + 1 until n) {
                val v = abs(augmented[k][i])
                if (v > maxVal) {
                    maxVal = v
                    pivotRow = k
                }
            }

            if (maxVal < 1e-12) return null // Singular matrix

            if (pivotRow != i) {
                val temp = augmented[i]
                augmented[i] = augmented[pivotRow]
                augmented[pivotRow] = temp
            }

            val pivot = augmented[i][i]
            for (j in 0 until 2 * n) {
                augmented[i][j] /= pivot
            }

            for (k in 0 until n) {
                if (k != i) {
                    val factor = augmented[k][i]
                    for (j in 0 until 2 * n) {
                        augmented[k][j] -= factor * augmented[i][j]
                    }
                }
            }
        }

        return Array(n) { i -> DoubleArray(n) { j -> augmented[i][j + n] } }
    }

    private fun expandPolynomialDegree2(
        x: Array<DoubleArray>,
        featureNames: List<String>
    ): Pair<Array<DoubleArray>, List<String>> {
        val n = x.size
        val p = if (n > 0) x[0].size else 0
        if (n == 0 || p == 0) return Pair(x, featureNames)

        val newCols = mutableListOf<String>()
        newCols.addAll(featureNames)
        for (j in 0 until p) {
            newCols.add("${featureNames[j]}^2")
        }

        val expandedP = p + p
        val expandedX = Array(n) { i ->
            val row = DoubleArray(expandedP)
            for (j in 0 until p) {
                row[j] = x[i][j]
                row[p + j] = x[i][j] * x[i][j]
            }
            row
        }
        return Pair(expandedX, newCols)
    }

    private fun fallbackRegression(
        trainX: Array<DoubleArray>,
        trainY: DoubleArray,
        testX: Array<DoubleArray>,
        testY: DoubleArray,
        featureNames: List<String>,
        modelName: String
    ): RegressionResult {
        val meanY = if (trainY.isNotEmpty()) trainY.average() else 0.0
        val evalY = if (testY.isNotEmpty()) testY else trainY
        val yPred = DoubleArray(evalY.size) { meanY }
        val residuals = DoubleArray(evalY.size) { i -> evalY[i] - meanY }
        return RegressionResult(
            modelName = "$modelName (Baseline Mean)",
            intercept = meanY,
            coefficients = DoubleArray(featureNames.size),
            r2 = 0.0,
            mae = residuals.map { abs(it) }.average(),
            mse = residuals.map { it * it }.average(),
            rmse = sqrt(residuals.map { it * it }.average()),
            yTrue = evalY,
            yPred = yPred,
            residuals = residuals,
            featureNames = featureNames
        )
    }

    private fun emptyRegression(modelName: String, featureNames: List<String>): RegressionResult {
        return RegressionResult(modelName, 0.0, DoubleArray(0), 0.0, 0.0, 0.0, 0.0, DoubleArray(0), DoubleArray(0), DoubleArray(0), featureNames)
    }

    private fun emptyClassification(classLabels: List<String>): ClassificationResult {
        return ClassificationResult("Logistic Regression", 0.0, 0.0, 0.0, 0.0, emptyArray(), classLabels, emptyList(), 0.5, DoubleArray(0), DoubleArray(0), emptyArray())
    }
}
