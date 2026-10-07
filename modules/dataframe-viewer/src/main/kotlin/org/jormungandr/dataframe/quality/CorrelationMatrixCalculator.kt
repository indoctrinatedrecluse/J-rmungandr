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
import kotlin.math.sqrt

enum class CorrelationMethod(val displayName: String) {
    PEARSON("Pearson (Linear)"),
    SPEARMAN("Spearman (Rank)"),
    KENDALL("Kendall's Tau (Concordance)")
}

data class CorrelationMatrix(
    val columnNames: List<String>,
    val matrix: Array<DoubleArray>,
    val method: CorrelationMethod
) {
    fun getCorrelation(col1: String, col2: String): Double {
        val idx1 = columnNames.indexOf(col1)
        val idx2 = columnNames.indexOf(col2)
        if (idx1 < 0 || idx2 < 0) return 0.0
        return matrix[idx1][idx2]
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CorrelationMatrix) return false
        return columnNames == other.columnNames && method == other.method && matrix.contentDeepEquals(other.matrix)
    }

    override fun hashCode(): Int {
        var result = columnNames.hashCode()
        result = 31 * result + matrix.contentDeepHashCode()
        result = 31 * result + method.hashCode()
        return result
    }
}

/**
 * High-performance statistical correlation calculator supporting Pearson, Spearman, and Kendall.
 */
object CorrelationMatrixCalculator {

    /**
     * Computes correlation matrix across all numeric columns of the dataframe.
     */
    fun compute(df: DataFrame, method: CorrelationMethod = CorrelationMethod.PEARSON): CorrelationMatrix {
        val numericCols = df.columns.filter { it.isNumeric }
        val colNames = numericCols.map { it.name }
        val n = colNames.size

        if (n == 0 || df.rowCount < 2) {
            return CorrelationMatrix(colNames, Array(n) { DoubleArray(n) { 1.0 } }, method)
        }

        // Extract clean double arrays per column, taking only rows where neither value is null
        val colVectors = numericCols.map { col ->
            val idx = df.getColumnIndex(col.name)
            df.rows.map { row ->
                val cell = row.getOrNull(idx)
                when (cell) {
                    is Number -> cell.toDouble()
                    is String -> cell.toDoubleOrNull()
                    else -> null
                }
            }
        }

        val matrix = Array(n) { DoubleArray(n) }

        for (i in 0 until n) {
            matrix[i][i] = 1.0
            for (j in i + 1 until n) {
                val paired = mutableListOf<Pair<Double, Double>>()
                for (r in 0 until df.rowCount) {
                    val v1 = colVectors[i].getOrNull(r)
                    val v2 = colVectors[j].getOrNull(r)
                    if (v1 != null && v2 != null) {
                        paired.add(Pair(v1, v2))
                    }
                }

                val corr = if (paired.size < 2) 0.0 else when (method) {
                    CorrelationMethod.PEARSON -> calculatePearson(paired)
                    CorrelationMethod.SPEARMAN -> calculateSpearman(paired)
                    CorrelationMethod.KENDALL -> calculateKendall(paired)
                }

                val clamped = corr.coerceIn(-1.0, 1.0)
                matrix[i][j] = clamped
                matrix[j][i] = clamped
            }
        }

        return CorrelationMatrix(colNames, matrix, method)
    }

    fun calculatePearson(pairs: List<Pair<Double, Double>>): Double {
        val n = pairs.size.toDouble()
        if (n < 2.0) return 0.0

        val meanX = pairs.map { it.first }.average()
        val meanY = pairs.map { it.second }.average()

        var num = 0.0
        var denX = 0.0
        var denY = 0.0

        for (p in pairs) {
            val dx = p.first - meanX
            val dy = p.second - meanY
            num += dx * dy
            denX += dx * dx
            denY += dy * dy
        }

        val den = sqrt(denX * denY)
        if (den == 0.0) return 0.0
        return num / den
    }

    fun calculateSpearman(pairs: List<Pair<Double, Double>>): Double {
        if (pairs.size < 2) return 0.0
        val ranksX = computeRanks(pairs.map { it.first })
        val ranksY = computeRanks(pairs.map { it.second })

        val rankPairs = pairs.indices.map { Pair(ranksX[it], ranksY[it]) }
        return calculatePearson(rankPairs)
    }

    fun calculateKendall(pairs: List<Pair<Double, Double>>): Double {
        val n = pairs.size
        if (n < 2) return 0.0

        var concordant = 0L
        var discordant = 0L

        for (i in 0 until n) {
            val xi = pairs[i].first
            val yi = pairs[i].second
            for (j in i + 1 until n) {
                val xj = pairs[j].first
                val yj = pairs[j].second

                val diffX = xi.compareTo(xj)
                val diffY = yi.compareTo(yj)

                if (diffX * diffY > 0) {
                    concordant++
                } else if (diffX * diffY < 0) {
                    discordant++
                }
            }
        }

        val totalPairs = (n.toLong() * (n - 1)) / 2L
        if (totalPairs == 0L) return 0.0

        return (concordant - discordant).toDouble() / totalPairs.toDouble()
    }

    private fun computeRanks(values: List<Double>): List<Double> {
        val indexed = values.mapIndexed { idx, v -> Pair(idx, v) }.sortedBy { it.second }
        val ranks = DoubleArray(values.size)

        var i = 0
        while (i < indexed.size) {
            var j = i
            while (j < indexed.size && indexed[j].second == indexed[i].second) {
                j++
            }
            // Average rank for ties (1-based ranking)
            val avgRank = (i + 1 + j).toDouble() / 2.0
            for (k in i until j) {
                ranks[indexed[k].first] = avgRank
            }
            i = j
        }

        return ranks.toList()
    }
}
