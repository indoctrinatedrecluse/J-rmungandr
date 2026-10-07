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

package org.jormungandr.dataframe.webapp

import org.jormungandr.dataframe.model.DataFrame
import org.jormungandr.dataframe.model.DataTypeCategory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class InteractiveWebAppGeneratorServiceTest {

    private fun createSampleDf(): DataFrame {
        return DataFrame.buildWithStatistics(
            name = "houses",
            rawColumns = listOf(
                Pair("price", DataTypeCategory.FLOAT),
                Pair("sqft", DataTypeCategory.INTEGER),
                Pair("neighborhood", DataTypeCategory.STRING)
            ),
            rows = listOf(
                listOf(100.0, 1200L, "Downtown"),
                listOf(250.0, 1800L, "Suburbs"),
                listOf(310.0, 2400L, "Downtown")
            )
        )
    }

    @Test
    fun testGenerateStreamlitDashboard() {
        val df = createSampleDf()
        val code = InteractiveWebAppGeneratorService.generateStreamlitDashboard(
            df = df,
            appName = "Real Estate Dashboard",
            modelName = "Ridge Price Predictor",
            targetCol = "price",
            featureCols = listOf("sqft")
        )

        assertTrue(code.contains("import streamlit as st"))
        assertTrue(code.contains("st.set_page_config"))
        assertTrue(code.contains("Real Estate Dashboard"))
        assertTrue(code.contains("st.plotly_chart"))
        assertTrue(code.contains("st.dataframe"))
        assertTrue(code.contains("Ridge Price Predictor"))
    }

    @Test
    fun testGenerateGradioPlayground() {
        val df = createSampleDf()
        val code = InteractiveWebAppGeneratorService.generateGradioPlayground(
            df = df,
            appName = "House Valuation Playground",
            modelName = "Valuation Model",
            targetCol = "price",
            featureCols = listOf("sqft", "neighborhood")
        )

        assertTrue(code.contains("import gradio as gr"))
        assertTrue(code.contains("gr.Interface"))
        assertTrue(code.contains("House Valuation Playground"))
        assertTrue(code.contains("predict_fn"))
        assertTrue(code.contains("gr.Slider"))
    }
}
