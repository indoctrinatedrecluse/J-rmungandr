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

package org.jormungandr.core.ml

/**
 * Execution status for an ML experiment run.
 */
enum class RunStatus(val displayName: String, val badge: String) {
    RUNNING("Running", "🟡 RUNNING"),
    COMPLETED("Completed", "🟢 COMPLETED"),
    FAILED("Failed", "🔴 FAILED"),
    KILLED("Killed", "⏹️ KILLED")
}

/**
 * Time-series metric value recorded at a specific step or epoch.
 */
data class MetricStep(
    val step: Int,
    val value: Double,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Model artifact or output asset produced during a training run.
 */
data class RunArtifact(
    val name: String,
    val path: String,
    val artifactType: String = "file", // "model", "chart", "metrics_json"
    val sizeBytes: Long = 0L
)

/**
 * Encapsulates an individual ML model training run (hyperparameters, metrics, curves, artifacts).
 */
data class MlRun(
    val runId: String,
    val experimentId: String,
    val runName: String,
    var status: RunStatus = RunStatus.RUNNING,
    val startTime: Long = System.currentTimeMillis(),
    var endTime: Long? = null,
    val parameters: MutableMap<String, String> = mutableMapOf(),
    val metrics: MutableMap<String, MutableList<MetricStep>> = mutableMapOf(),
    val artifacts: MutableList<RunArtifact> = mutableListOf(),
    val tags: MutableMap<String, String> = mutableMapOf()
) {
    val durationMs: Long
        get() = (endTime ?: System.currentTimeMillis()) - startTime

    val durationFormatted: String
        get() {
            val s = durationMs / 1000
            val m = s / 60
            val remS = s % 60
            return if (m > 0) "${m}m ${remS}s" else "${remS}s"
        }

    fun getLatestMetric(metricName: String): Double? = metrics[metricName]?.lastOrNull()?.value

    fun getBestMetric(metricName: String, minimize: Boolean = true): Double? {
        val values = metrics[metricName]?.map { it.value } ?: return null
        return if (minimize) values.minOrNull() else values.maxOrNull()
    }
}

/**
 * Group of ML runs sharing an objective or model domain.
 */
data class MlExperiment(
    val experimentId: String,
    val name: String,
    val description: String = "",
    val createdTimestamp: Long = System.currentTimeMillis(),
    val tags: Map<String, String> = emptyMap()
)
