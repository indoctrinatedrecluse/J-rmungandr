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

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MlExperimentTrackerServiceTest {

    @Test
    fun `test preseeded experiments exist`() {
        val allExp = MlExperimentTrackerService.getAllExperiments()
        assertTrue(allExp.size >= 2)
        assertTrue(allExp.any { it.name.contains("Customer Churn") })
        assertTrue(allExp.any { it.name.contains("LLM") })

        val allRuns = MlExperimentTrackerService.getAllRuns()
        assertTrue(allRuns.size >= 4)
        assertTrue(allRuns.any { it.runName.contains("XGBoost") })
        assertTrue(allRuns.any { it.runName.contains("Qwen") })
    }

    @Test
    fun `test create experiment and record run lifecycle`() {
        val exp = MlExperimentTrackerService.createExperiment("BERT Sentiment Benchmark", "Fine-tuning transformer")
        assertNotNull(exp.experimentId)

        val run = MlExperimentTrackerService.startRun(
            exp.experimentId,
            "bert-base-uncased-lr2e5",
            mapOf("learning_rate" to 2e-5, "batch_size" to 32)
        )

        assertEquals(RunStatus.RUNNING, run.status)
        assertEquals("2.0E-5", run.parameters["learning_rate"])
        assertEquals("32", run.parameters["batch_size"])

        // Log steps
        MlExperimentTrackerService.logMetric(run.runId, "loss", 0.65, 1)
        MlExperimentTrackerService.logMetric(run.runId, "loss", 0.42, 2)
        MlExperimentTrackerService.logMetric(run.runId, "loss", 0.28, 3)

        MlExperimentTrackerService.logMetric(run.runId, "val_accuracy", 0.81, 1)
        MlExperimentTrackerService.logMetric(run.runId, "val_accuracy", 0.89, 2)
        MlExperimentTrackerService.logMetric(run.runId, "val_accuracy", 0.94, 3)

        assertEquals(0.28, run.getLatestMetric("loss"))
        assertEquals(0.28, run.getBestMetric("loss", minimize = true))
        assertEquals(0.94, run.getBestMetric("val_accuracy", minimize = false))

        MlExperimentTrackerService.endRun(run.runId, RunStatus.COMPLETED)
        assertEquals(RunStatus.COMPLETED, run.status)
        assertNotNull(run.endTime)
        assertTrue(run.durationMs >= 0)
    }

    @Test
    fun `test log multiple metrics simultaneously`() {
        val exp = MlExperimentTrackerService.createExperiment("Metrics Multi Test")
        val run = MlExperimentTrackerService.startRun(exp.experimentId, "test-multi")

        MlExperimentTrackerService.logMetrics(
            run.runId,
            mapOf("train_loss" to 0.45, "val_loss" to 0.52, "f1" to 0.88),
            step = 10
        )

        assertEquals(0.45, run.getLatestMetric("train_loss"))
        assertEquals(0.52, run.getLatestMetric("val_loss"))
        assertEquals(0.88, run.getLatestMetric("f1"))
    }

    @Test
    fun `test python logger snippet contains experiment name`() {
        val snippet = MlExperimentTrackerService.getPythonLoggerSnippet("Vision Benchmark")
        assertTrue(snippet.contains("JormungandrTracker"))
        assertTrue(snippet.contains("Vision Benchmark"))
        assertTrue(snippet.contains("log_param"))
        assertTrue(snippet.contains("log_metric"))
    }
}
