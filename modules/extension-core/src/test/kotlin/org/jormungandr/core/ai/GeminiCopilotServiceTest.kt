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

package org.jormungandr.core.ai

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GeminiCopilotServiceTest {

    private val sampleContext = CopilotDataContext(
        datasetName = "churn_customers",
        columns = listOf(
            CopilotColumnInfo(name = "customer_id", type = "INTEGER", isNumeric = true),
            CopilotColumnInfo(name = "plan_tier", type = "VARCHAR", isNumeric = false),
            CopilotColumnInfo(name = "monthly_charges", type = "FLOAT", isNumeric = true, minVal = "19.99", maxVal = "120.00"),
            CopilotColumnInfo(name = "churned", type = "BOOLEAN", isNumeric = false, nullCount = 2)
        ),
        rowCount = 5000,
        targetEngine = "SQL"
    )

    @Test
    fun `test offline NL-to-SQL generation`() {
        val response = GeminiCopilotService.askCopilot(
            userPrompt = "Write a SQL query to find top 10 customers by monthly_charges",
            context = sampleContext
        )

        assertEquals("model", response.role)
        assertNotNull(response.extractedCode)
        assertEquals("sql", response.codeLanguage)
        assertTrue(response.extractedCode!!.contains("SELECT"))
        assertTrue(response.extractedCode!!.contains("FROM churn_customers"))
        assertTrue(response.extractedCode!!.contains("LIMIT"))
    }

    @Test
    fun `test offline NL-to-SQL group by query`() {
        val response = GeminiCopilotService.askCopilot(
            userPrompt = "Group by plan_tier and calculate count of users",
            context = sampleContext
        )

        assertNotNull(response.extractedCode)
        assertTrue(response.extractedCode!!.contains("GROUP BY plan_tier"))
        assertTrue(response.extractedCode!!.contains("COUNT(*)"))
    }

    @Test
    fun `test offline NL-to-Pandas generation`() {
        val response = GeminiCopilotService.askCopilot(
            userPrompt = "Create a pandas dataframe pipeline to filter high spenders",
            context = sampleContext
        )

        assertNotNull(response.extractedCode)
        assertEquals("python", response.codeLanguage)
        assertTrue(response.extractedCode!!.contains("import pandas as pd"))
        assertTrue(response.extractedCode!!.contains("churn_customers"))
    }

    @Test
    fun `test offline visualization recommendation`() {
        val response = GeminiCopilotService.askCopilot(
            userPrompt = "What plots and charts should I create for this dataset?",
            context = sampleContext
        )

        assertTrue(response.content.contains("Recommended Visualizations"))
        assertNotNull(response.extractedCode)
        assertEquals("python", response.codeLanguage)
        assertTrue(response.extractedCode!!.contains("import plotly.express as px"))
    }

    @Test
    fun `test offline cleaning and outlier strategy`() {
        val response = GeminiCopilotService.askCopilot(
            userPrompt = "What cleaning and missing value imputation strategy do you recommend?",
            context = sampleContext
        )

        assertTrue(response.content.contains("Cleaning & Preprocessing Strategy"))
        assertTrue(response.content.contains("churned")) // Detected nulls
        assertNotNull(response.extractedCode)
        assertTrue(response.extractedCode!!.contains("quantile"))
    }

    @Test
    fun `test markdown code fence extraction`() {
        val markdown = """
            Here is your requested query:
            ```sql
            SELECT id, name FROM users WHERE active = 1;
            ```
            Hope this helps!
        """.trimIndent()

        val parsed = GeminiCopilotService.parseModelResponse(markdown)
        assertEquals("sql", parsed.codeLanguage)
        assertEquals("SELECT id, name FROM users WHERE active = 1;", parsed.extractedCode)
    }

    @Test
    fun `test settings custom API key`() {
        val originalKey = GeminiCopilotSettings.customApiKey
        try {
            GeminiCopilotSettings.customApiKey = "TEST-API-KEY-123"
            assertTrue(GeminiCopilotSettings.hasApiKey)
            assertEquals("TEST-API-KEY-123", GeminiCopilotSettings.activeApiKey)
        } finally {
            GeminiCopilotSettings.customApiKey = originalKey
        }
    }
}
