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

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Column metadata for schema-aware Gemini prompts.
 */
data class CopilotColumnInfo(
    val name: String,
    val type: String,
    val isNumeric: Boolean = false,
    val nullCount: Long = 0,
    val minVal: String? = null,
    val maxVal: String? = null,
    val sampleValues: List<String> = emptyList()
)

/**
 * Dataset or schema context provided to the copilot.
 */
data class CopilotDataContext(
    val datasetName: String,
    val columns: List<CopilotColumnInfo> = emptyList(),
    val rowCount: Long = 0,
    val targetEngine: String = "SQL", // SQL, Pandas, Polars, DuckDB, Python
    val dialect: String = "SQLite",
    val ddlSchema: String? = null
)

/**
 * Message exchange in Copilot conversation.
 */
data class CopilotMessage(
    val role: String, // "user" or "model"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val extractedCode: String? = null,
    val codeLanguage: String? = null
)

/**
 * Settings and state for Gemini AI Copilot.
 */
object GeminiCopilotSettings {
    var customApiKey: String? = null
    var selectedModel: String = "gemini-2.5-flash" // gemini-2.5-flash, gemini-2.5-pro

    val activeApiKey: String?
        get() = customApiKey?.takeIf { it.isNotBlank() }
            ?: System.getenv("GEMINI_API_KEY")?.takeIf { it.isNotBlank() }
            ?: System.getProperty("gemini.api.key")?.takeIf { it.isNotBlank() }

    val hasApiKey: Boolean
        get() = activeApiKey != null
}

/**
 * In-IDE Data Science Copilot powered by Google Gemini API with fallback
 * offline semantic intelligence engine.
 */
object GeminiCopilotService {

    private val gson = Gson()
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build()

    /**
     * Sends a query to Gemini with automatic dataset schema context injection.
     */
    fun askCopilot(
        userPrompt: String,
        context: CopilotDataContext? = null,
        conversationHistory: List<CopilotMessage> = emptyList()
    ): CopilotMessage {
        val apiKey = GeminiCopilotSettings.activeApiKey

        return if (!apiKey.isNullOrBlank()) {
            callGeminiApi(userPrompt, apiKey, context, conversationHistory)
        } else {
            generateOfflineResponse(userPrompt, context)
        }
    }

    private fun callGeminiApi(
        userPrompt: String,
        apiKey: String,
        context: CopilotDataContext?,
        conversationHistory: List<CopilotMessage>
    ): CopilotMessage {
        val model = GeminiCopilotSettings.selectedModel
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

        val systemPrompt = buildSystemPrompt(context)

        val rootJson = JsonObject()

        // System Instruction
        val systemInstruction = JsonObject()
        val systemParts = JsonArray()
        val systemPart = JsonObject()
        systemPart.addProperty("text", systemPrompt)
        systemParts.add(systemPart)
        systemInstruction.add("parts", systemParts)
        rootJson.add("systemInstruction", systemInstruction)

        // Contents (conversation history + latest message)
        val contentsArray = JsonArray()
        for (msg in conversationHistory.takeLast(6)) {
            val role = if (msg.role == "model" || msg.role == "assistant") "model" else "user"
            val turn = JsonObject()
            turn.addProperty("role", role)
            val parts = JsonArray()
            val part = JsonObject()
            part.addProperty("text", msg.content)
            parts.add(part)
            turn.add("parts", parts)
            contentsArray.add(turn)
        }

        val currentTurn = JsonObject()
        currentTurn.addProperty("role", "user")
        val curParts = JsonArray()
        val curPart = JsonObject()
        curPart.addProperty("text", userPrompt)
        curParts.add(curPart)
        currentTurn.add("parts", curParts)
        contentsArray.add(currentTurn)

        rootJson.add("contents", contentsArray)

        // Generation Config
        val genConfig = JsonObject()
        genConfig.addProperty("temperature", 0.2)
        rootJson.add("generationConfig", genConfig)

        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(25))
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(rootJson)))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() == 200) {
                val parsed = gson.fromJson(response.body(), JsonObject::class.java)
                val candidates = parsed.getAsJsonArray("candidates")
                if (candidates != null && candidates.size() > 0) {
                    val candidate = candidates[0].asJsonObject
                    val contentObj = candidate.getAsJsonObject("content")
                    val parts = contentObj?.getAsJsonArray("parts")
                    val text = parts?.get(0)?.asJsonObject?.get("text")?.asString ?: "No response generated."
                    parseModelResponse(text)
                } else {
                    CopilotMessage("model", "Gemini did not return any candidates.")
                }
            } else {
                CopilotMessage(
                    "model",
                    "⚠️ Gemini API returned status ${response.statusCode()}: ${response.body().take(200)}\n\n" +
                    "*(Falling back to offline intelligence)*\n\n" +
                    generateOfflineResponse(userPrompt, context).content
                )
            }
        } catch (e: Exception) {
            CopilotMessage(
                "model",
                "⚠️ Network request to Gemini failed: ${e.message}\n\n" +
                "*(Generated via Offline Data Intelligence Engine)*\n\n" +
                generateOfflineResponse(userPrompt, context).content
            )
        }
    }

    private fun buildSystemPrompt(context: CopilotDataContext?): String {
        val sb = StringBuilder()
        sb.append("You are Jörmungandr Data Science Copilot, an expert AI assistant specialized in SQL, Pandas, Polars, DuckDB, Python visualization, and predictive modeling.\n")
        sb.append("Provide concise, high-quality answers with clean, ready-to-run code blocks.\n")

        if (context != null) {
            sb.append("\n### ACTIVE DATASET CONTEXT:\n")
            sb.append("- Dataset / Table Name: `${context.datasetName}`\n")
            sb.append("- Total Rows: ${context.rowCount}\n")
            sb.append("- Target Engine/Dialect: ${context.dialect} (${context.targetEngine})\n")
            sb.append("- Schema & Columns:\n")
            for (col in context.columns) {
                val stats = mutableListOf<String>()
                if (col.isNumeric) stats.add("numeric")
                if (col.nullCount > 0) stats.add("nulls=${col.nullCount}")
                if (col.minVal != null) stats.add("min=${col.minVal}")
                if (col.maxVal != null) stats.add("max=${col.maxVal}")
                val statsStr = if (stats.isNotEmpty()) " [${stats.joinToString(", ")}]" else ""
                val sampleStr = if (col.sampleValues.isNotEmpty()) " (samples: ${col.sampleValues.take(3).joinToString(", ")})" else ""
                sb.append("  * `${col.name}` (${col.type})$statsStr$sampleStr\n")
            }
            if (!context.ddlSchema.isNullOrBlank()) {
                sb.append("- DDL Schema:\n```sql\n${context.ddlSchema}\n```\n")
            }
        }

        sb.append("\nWhen providing code, format it in markdown code blocks: ```sql or ```python so the user can execute it directly.")
        return sb.toString()
    }

    /**
     * Extracts code and language from markdown formatted response.
     */
    fun parseModelResponse(text: String): CopilotMessage {
        val codeFenceRegex = Regex("```(sql|python|bash|json)?\\s*\\n([\\s\\S]*?)\\n```", RegexOption.IGNORE_CASE)
        val match = codeFenceRegex.find(text)

        val codeLang = match?.groupValues?.get(1)?.lowercase()?.takeIf { it.isNotBlank() }
        val code = match?.groupValues?.get(2)?.trim()

        return CopilotMessage(
            role = "model",
            content = text,
            extractedCode = code,
            codeLanguage = codeLang
        )
    }

    /**
     * Offline heuristic intelligence engine delivering intelligent SQL, Pandas, and
     * EDA recommendations when no API key or Internet connection is available.
     */
    fun generateOfflineResponse(userPrompt: String, context: CopilotDataContext?): CopilotMessage {
        val lower = userPrompt.lowercase()
        val tableName = context?.datasetName ?: "dataset"
        val cols = context?.columns ?: emptyList()
        val numericCols = cols.filter { it.isNumeric }
        val categoricalCols = cols.filter { !it.isNumeric }

        val sb = StringBuilder()

        when {
            // 1. Natural Language to SQL
            lower.contains("sql") || lower.contains("query") || lower.contains("select") || lower.contains("top") || lower.contains("count") -> {
                sb.append("Here is the generated SQL query for your dataset:\n\n")
                sb.append("```sql\n")
                if (lower.contains("top") || lower.contains("limit")) {
                    val orderCol = numericCols.firstOrNull()?.name ?: cols.firstOrNull()?.name ?: "id"
                    sb.append("SELECT *\nFROM $tableName\nORDER BY $orderCol DESC\nLIMIT 10;\n")
                } else if (lower.contains("group by") || lower.contains("count") || lower.contains("distribution")) {
                    val groupCol = categoricalCols.firstOrNull()?.name ?: cols.firstOrNull()?.name ?: "category"
                    sb.append("SELECT $groupCol, COUNT(*) AS count\nFROM $tableName\nGROUP BY $groupCol\nORDER BY count DESC\nLIMIT 20;\n")
                } else if (lower.contains("average") || lower.contains("avg") || lower.contains("sum")) {
                    val numCol = numericCols.firstOrNull()?.name ?: "amount"
                    val groupCol = categoricalCols.firstOrNull()?.name ?: "category"
                    sb.append("SELECT $groupCol, AVG($numCol) AS avg_$numCol, SUM($numCol) AS total_$numCol\nFROM $tableName\nGROUP BY $groupCol;\n")
                } else {
                    sb.append("SELECT *\nFROM $tableName\nLIMIT 100;\n")
                }
                sb.append("```\n\n")
                sb.append("💡 *Tip: Click '▶ Run SQL' below to execute this query immediately.*")
            }

            // 2. Pandas / Python transformations
            lower.contains("pandas") || lower.contains("python") || lower.contains("dataframe") || lower.contains("polars") -> {
                sb.append("Here is the Python Pandas transformation pipeline:\n\n")
                sb.append("```python\n")
                sb.append("import pandas as pd\n\n")
                sb.append("# 1. Load data\n")
                sb.append("df = pd.read_csv('$tableName.csv')\n\n")
                if (numericCols.isNotEmpty()) {
                    val col = numericCols.first().name
                    sb.append("# 2. Numerical aggregation & filtering\n")
                    sb.append("filtered_df = df[df['$col'] > df['$col'].median()]\n")
                    if (categoricalCols.isNotEmpty()) {
                        val cat = categoricalCols.first().name
                        sb.append("summary = filtered_df.groupby('$cat')['$col'].agg(['mean', 'count']).reset_index()\n")
                    }
                }
                sb.append("print(df.head())\n")
                sb.append("```\n")
            }

            // 3. Visualization suggestions
            lower.contains("plot") || lower.contains("chart") || lower.contains("visual") || lower.contains("graph") -> {
                sb.append("### Recommended Visualizations for `${tableName}`:\n\n")
                if (numericCols.size >= 2) {
                    val x = numericCols[0].name
                    val y = numericCols[1].name
                    sb.append("1. **3D Scatter / Correlation Plot**: `${x}` vs `${y}`\n")
                    sb.append("2. **Distribution Histogram**: Histogram of `${x}` to check skewness\n")
                }
                if (categoricalCols.isNotEmpty() && numericCols.isNotEmpty()) {
                    val cat = categoricalCols[0].name
                    val num = numericCols[0].name
                    sb.append("3. **Category Breakdown**: Bar chart of `${cat}` aggregated by mean `${num}`\n")
                }
                sb.append("\n```python\n")
                sb.append("import plotly.express as px\n\n")
                if (numericCols.size >= 2) {
                    sb.append("fig = px.scatter(df, x='${numericCols[0].name}', y='${numericCols[1].name}', title='Interactive Scatter Analysis')\n")
                    sb.append("fig.show()\n")
                } else {
                    sb.append("fig = px.histogram(df, x='${cols.firstOrNull()?.name ?: "col"}', title='Distribution')\n")
                    sb.append("fig.show()\n")
                }
                sb.append("```\n")
            }

            // 4. Data Cleaning & Outlier Strategy
            lower.contains("clean") || lower.contains("missing") || lower.contains("outlier") || lower.contains("null") -> {
                sb.append("### Data Cleaning & Preprocessing Strategy for `${tableName}`:\n\n")
                val colsWithNulls = cols.filter { it.nullCount > 0 }
                if (colsWithNulls.isNotEmpty()) {
                    sb.append("- **Missing Values**: Detected null values in ${colsWithNulls.joinToString { "`${it.name}` (${it.nullCount})" }}.\n")
                    sb.append("  * Recommendation: Use median imputation for skewed numerical variables, or mode for categorical features.\n")
                } else {
                    sb.append("- **Missing Values**: Dataset has 0 missing values reported in metadata.\n")
                }
                if (numericCols.isNotEmpty()) {
                    sb.append("- **Outliers**: Inspect `${numericCols.first().name}` via IQR clipping (1.5 × IQR bounds) to prevent distortion in modeling.\n")
                }
                sb.append("\n```python\n")
                sb.append("# Robust Cleaning Pipeline\n")
                sb.append("df_clean = df.drop_duplicates()\n")
                if (numericCols.isNotEmpty()) {
                    val col = numericCols.first().name
                    sb.append("q1, q3 = df_clean['$col'].quantile([0.25, 0.75])\n")
                    sb.append("iqr = q3 - q1\n")
                    sb.append("df_clean = df_clean[(df_clean['$col'] >= q1 - 1.5 * iqr) & (df_clean['$col'] <= q3 + 1.5 * iqr)]\n")
                }
                sb.append("```\n")
            }

            // Default
            else -> {
                sb.append("I can assist with natural language queries, SQL generation, Pandas code, and data science workflows for `${tableName}`.\n\n")
                sb.append("- **Columns**: ${cols.joinToString { "`${it.name}` (${it.type})" }}\n")
                sb.append("- **Total Records**: ${context?.rowCount ?: 0}\n\n")
                sb.append("Try asking:\n")
                sb.append("- *\"Show me top 10 records sorted by ${numericCols.firstOrNull()?.name ?: "column"}\"*\n")
                sb.append("- *\"Group by ${categoricalCols.firstOrNull()?.name ?: "category"} and calculate average\"*\n")
                sb.append("- *\"Generate a machine learning training pipeline\"*\n")
            }
        }

        sb.append("\n\n*(⚡ Generated via Offline Data Intelligence Engine. Set `GEMINI_API_KEY` for multi-turn Gemini 2.5 LLM reasoning)*")

        return parseModelResponse(sb.toString())
    }
}
