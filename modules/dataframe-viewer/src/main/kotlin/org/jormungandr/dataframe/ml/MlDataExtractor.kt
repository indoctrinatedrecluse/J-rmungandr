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
import java.util.Random

/**
 * Data structures and extraction pipeline preparing tabular [DataFrame] inputs
 * for machine learning regression, classification, clustering, and PCA.
 */
data class PreparedDataset(
    val trainX: Array<DoubleArray>,
    val trainY: DoubleArray,
    val testX: Array<DoubleArray>,
    val testY: DoubleArray,
    val trainYLabels: List<String> = emptyList(),
    val testYLabels: List<String> = emptyList(),
    val featureNames: List<String>,
    val targetName: String?,
    val isClassification: Boolean,
    val classLabels: List<String> = emptyList(),
    val allFeatureData: Array<DoubleArray> = emptyArray()
)

object MlDataExtractor {

    /**
     * Extracts numerical matrices and splits into train and test sets.
     */
    fun extract(
        df: DataFrame,
        featureNames: List<String>,
        targetName: String? = null,
        testRatio: Double = 0.20,
        randomSeed: Long = 42L,
        isClassification: Boolean = false
    ): PreparedDataset {
        val validFeatures = featureNames.filter { df.getColumnIndex(it) >= 0 }
        if (validFeatures.isEmpty() || df.rowCount == 0) {
            return PreparedDataset(
                trainX = emptyArray(),
                trainY = DoubleArray(0),
                testX = emptyArray(),
                testY = DoubleArray(0),
                featureNames = validFeatures,
                targetName = targetName,
                isClassification = isClassification
            )
        }

        val featureIndices = validFeatures.map { df.getColumnIndex(it) }
        val targetIndex = if (targetName != null) df.getColumnIndex(targetName) else -1

        // Precompute feature column means for missing value imputation
        val featureMeans = featureIndices.map { idx ->
            val colVals = df.getColumnValues(idx)
            val nums = colVals.mapNotNull { toDoubleOrNull(it) }
            if (nums.isNotEmpty()) nums.average() else 0.0
        }

        val allX = mutableListOf<DoubleArray>()
        val allY = mutableListOf<Double>()
        val allYLabels = mutableListOf<String>()

        val distinctClasses = mutableListOf<String>()

        for (row in df.rows) {
            val xRow = DoubleArray(featureIndices.size)
            var hasValidData = false

            for (i in featureIndices.indices) {
                val rawVal = row.getOrNull(featureIndices[i])
                val num = toDoubleOrNull(rawVal) ?: featureMeans[i]
                xRow[i] = num
                if (rawVal != null) hasValidData = true
            }

            if (!hasValidData) continue

            if (targetIndex >= 0) {
                val rawTarget = row.getOrNull(targetIndex) ?: continue
                if (isClassification) {
                    val label = rawTarget.toString().trim()
                    if (label.isBlank()) continue
                    if (!distinctClasses.contains(label)) {
                        distinctClasses.add(label)
                    }
                    val classId = distinctClasses.indexOf(label).toDouble()
                    allY.add(classId)
                    allYLabels.add(label)
                } else {
                    val numTarget = toDoubleOrNull(rawTarget) ?: continue
                    allY.add(numTarget)
                    allYLabels.add(numTarget.toString())
                }
            } else {
                allY.add(0.0)
                allYLabels.add("")
            }

            allX.add(xRow)
        }

        val totalSamples = allX.size
        if (totalSamples == 0) {
            return PreparedDataset(
                trainX = emptyArray(),
                trainY = DoubleArray(0),
                testX = emptyArray(),
                testY = DoubleArray(0),
                featureNames = validFeatures,
                targetName = targetName,
                isClassification = isClassification,
                classLabels = distinctClasses
            )
        }

        // Shuffle with seed
        val rand = Random(randomSeed)
        val indices = (0 until totalSamples).toList().shuffled(rand)

        val testCount = if (testRatio in 0.01..0.99) {
            (totalSamples * testRatio).toInt().coerceIn(1, totalSamples - 1)
        } else {
            0
        }
        val trainCount = totalSamples - testCount

        val trainX = Array(trainCount) { i -> allX[indices[i]] }
        val trainY = DoubleArray(trainCount) { i -> allY[indices[i]] }
        val trainYLabels = List(trainCount) { i -> allYLabels[indices[i]] }

        val testX = Array(testCount) { i -> allX[indices[trainCount + i]] }
        val testY = DoubleArray(testCount) { i -> allY[indices[trainCount + i]] }
        val testYLabels = List(testCount) { i -> allYLabels[indices[trainCount + i]] }

        return PreparedDataset(
            trainX = trainX,
            trainY = trainY,
            testX = testX,
            testY = testY,
            trainYLabels = trainYLabels,
            testYLabels = testYLabels,
            featureNames = validFeatures,
            targetName = targetName,
            isClassification = isClassification,
            classLabels = distinctClasses,
            allFeatureData = allX.toTypedArray()
        )
    }

    /**
     * Creates a synthetic benchmark dataset for quick IDE experimentation.
     */
    fun createSampleDataset(kind: SampleDatasetKind): DataFrame {
        val rand = Random(42L)
        return when (kind) {
            SampleDatasetKind.CALIFORNIA_HOUSING -> {
                val cols = listOf(
                    "MedInc" to DataTypeCategory.FLOAT,
                    "HouseAge" to DataTypeCategory.FLOAT,
                    "AveRooms" to DataTypeCategory.FLOAT,
                    "AveBedrms" to DataTypeCategory.FLOAT,
                    "Population" to DataTypeCategory.INTEGER,
                    "AveOccup" to DataTypeCategory.FLOAT,
                    "Latitude" to DataTypeCategory.FLOAT,
                    "Longitude" to DataTypeCategory.FLOAT,
                    "MedHouseVal" to DataTypeCategory.FLOAT
                )
                val rows = (1..150).map {
                    val inc = 2.0 + rand.nextDouble() * 8.0
                    val age = (10.0 + rand.nextDouble() * 40.0)
                    val rooms = 3.0 + inc * 0.4 + rand.nextGaussian() * 0.5
                    val bed = 1.0 + rand.nextDouble() * 0.6
                    val pop = (500 + rand.nextInt(3000))
                    val occup = 2.5 + rand.nextDouble() * 1.5
                    val lat = 34.0 + rand.nextDouble() * 4.0
                    val lon = -120.0 + rand.nextDouble() * 4.0
                    val price = (inc * 0.45 + (age * 0.01) + (rooms * 0.1) + rand.nextGaussian() * 0.25).coerceAtLeast(0.5)
                    listOf(
                        "%.3f".format(inc).toDouble(),
                        "%.1f".format(age).toDouble(),
                        "%.2f".format(rooms).toDouble(),
                        "%.2f".format(bed).toDouble(),
                        pop,
                        "%.2f".format(occup).toDouble(),
                        "%.4f".format(lat).toDouble(),
                        "%.4f".format(lon).toDouble(),
                        "%.3f".format(price).toDouble()
                    )
                }
                DataFrame.buildWithStatistics("california_housing_sample.csv", cols, rows)
            }
            SampleDatasetKind.IRIS_FLOWERS -> {
                val cols = listOf(
                    "sepal_length" to DataTypeCategory.FLOAT,
                    "sepal_width" to DataTypeCategory.FLOAT,
                    "petal_length" to DataTypeCategory.FLOAT,
                    "petal_width" to DataTypeCategory.FLOAT,
                    "species" to DataTypeCategory.STRING
                )
                val speciesList = listOf("setosa", "versicolor", "virginica")
                val rows = mutableListOf<List<Any?>>()
                for (s in speciesList) {
                    val (baseSL, baseSW, basePL, basePW) = when (s) {
                        "setosa" -> listOf(5.0, 3.4, 1.4, 0.2)
                        "versicolor" -> listOf(5.9, 2.7, 4.2, 1.3)
                        else -> listOf(6.5, 3.0, 5.5, 2.0)
                    }
                    for (i in 1..40) {
                        rows.add(
                            listOf(
                                "%.2f".format(baseSL + rand.nextGaussian() * 0.35).toDouble(),
                                "%.2f".format(baseSW + rand.nextGaussian() * 0.3).toDouble(),
                                "%.2f".format(basePL + rand.nextGaussian() * 0.35).toDouble(),
                                "%.2f".format((basePW + rand.nextGaussian() * 0.2).coerceAtLeast(0.1)).toDouble(),
                                s
                            )
                        )
                    }
                }
                DataFrame.buildWithStatistics("iris_flowers_sample.csv", cols, rows)
            }
            SampleDatasetKind.CUSTOMER_CHURN -> {
                val cols = listOf(
                    "tenure_months" to DataTypeCategory.INTEGER,
                    "monthly_charges" to DataTypeCategory.FLOAT,
                    "total_charges" to DataTypeCategory.FLOAT,
                    "support_calls" to DataTypeCategory.INTEGER,
                    "contract_type" to DataTypeCategory.STRING,
                    "churn" to DataTypeCategory.STRING
                )
                val rows = (1..150).map {
                    val tenure = 1 + rand.nextInt(72)
                    val monthly = 20.0 + rand.nextDouble() * 90.0
                    val total = tenure * monthly + rand.nextGaussian() * 50.0
                    val calls = rand.nextInt(6)
                    val contract = if (tenure > 36) "Two Year" else if (tenure > 12) "One Year" else "Month-to-Month"
                    val churnProb = (calls * 0.15) + (if (contract == "Month-to-Month") 0.3 else 0.05) - (tenure * 0.005)
                    val churned = if (rand.nextDouble() < churnProb.coerceIn(0.05, 0.90)) "Yes" else "No"
                    listOf(
                        tenure,
                        "%.2f".format(monthly).toDouble(),
                        "%.2f".format(total.coerceAtLeast(20.0)).toDouble(),
                        calls,
                        contract,
                        churned
                    )
                }
                DataFrame.buildWithStatistics("customer_churn_sample.csv", cols, rows)
            }
            SampleDatasetKind.SYNTHETIC_CLUSTERS -> {
                val cols = listOf(
                    "feature_x" to DataTypeCategory.FLOAT,
                    "feature_y" to DataTypeCategory.FLOAT,
                    "feature_z" to DataTypeCategory.FLOAT
                )
                val centers = listOf(
                    listOf(2.0, 2.0, 1.0),
                    listOf(8.0, 3.0, 7.0),
                    listOf(5.0, 9.0, 4.0)
                )
                val rows = mutableListOf<List<Any?>>()
                for (c in centers) {
                    for (i in 1..40) {
                        rows.add(
                            listOf(
                                "%.2f".format(c[0] + rand.nextGaussian() * 0.8).toDouble(),
                                "%.2f".format(c[1] + rand.nextGaussian() * 0.8).toDouble(),
                                "%.2f".format(c[2] + rand.nextGaussian() * 0.8).toDouble()
                            )
                        )
                    }
                }
                DataFrame.buildWithStatistics("synthetic_clusters.csv", cols, rows)
            }
        }
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

enum class SampleDatasetKind(val displayName: String) {
    CALIFORNIA_HOUSING("California Housing (Regression)"),
    IRIS_FLOWERS("Iris Flowers (Classification)"),
    CUSTOMER_CHURN("Customer Churn (Classification)"),
    SYNTHETIC_CLUSTERS("Synthetic 3D Blobs (Clustering)")
}
