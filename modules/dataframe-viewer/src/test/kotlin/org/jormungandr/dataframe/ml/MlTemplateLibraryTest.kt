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

class MlTemplateLibraryTest {

    @Test
    fun testGenerateAllTemplates() {
        val features = listOf("feature_a", "feature_b", "feature_c")
        val target = "price_target"
        val testSplit = 0.25
        val filename = "housing_test.csv"

        for (templateType in MlTemplateType.values()) {
            val script = MlTemplateLibrary.generateScript(templateType, features, target, testSplit, filename)
            assertNotNull(script)
            assertTrue(script.isNotBlank())
            assertTrue(script.contains("housing_test.csv"), "Script should reference the target dataset")
            assertTrue(script.contains("'feature_a'"), "Script should include feature_a")
            assertTrue(script.contains("'feature_b'"), "Script should include feature_b")

            when (templateType) {
                MlTemplateType.SKLEARN_REGRESSION -> {
                    assertTrue(script.contains("RandomForestRegressor"))
                    assertTrue(script.contains("Ridge"))
                }
                MlTemplateType.SKLEARN_CLASSIFICATION -> {
                    assertTrue(script.contains("RandomForestClassifier"))
                    assertTrue(script.contains("confusion_matrix"))
                }
                MlTemplateType.XGBOOST_GRADIENT_BOOSTING -> {
                    assertTrue(script.contains("xgb.XGBRegressor"))
                }
                MlTemplateType.PYTORCH_NEURAL_NETWORK -> {
                    assertTrue(script.contains("TabularMLP"))
                    assertTrue(script.contains("nn.Module"))
                }
                MlTemplateType.KMEANS_PCA_CLUSTERING -> {
                    assertTrue(script.contains("KMeans"))
                    assertTrue(script.contains("PCA"))
                }
            }
        }
    }
}
