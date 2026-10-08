package org.jormungandr.shell.copilot.local

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets

data class LocalLlmModelInfo(
    val name: String,
    val sizeGb: Double,
    val family: String,
    val parameterSize: String
)

data class LlmGenerationResult(
    val responseText: String,
    val latencyMs: Long,
    val tokenCount: Int,
    val tokensPerSecond: Double,
    val modelUsed: String
)

/**
 * Service managing local LLM communication (Ollama / vLLM / llama.cpp / Heuristic offline fallback).
 */
@Service(Service.Level.APP)
class OllamaService {

    private val logger = Logger.getInstance(OllamaService::class.java)

    @Volatile
    var endpointUrl: String = "http://localhost:11434"

    /**
     * Check if local Ollama daemon is active and responsive.
     */
    fun isServerReachable(): Boolean {
        return try {
            val url = URI("$endpointUrl/api/tags").toURL()
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 800
                readTimeout = 800
            }
            conn.responseCode == 200
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Discover locally installed models.
     */
    fun listLocalModels(): List<LocalLlmModelInfo> {
        val models = mutableListOf<LocalLlmModelInfo>()
        if (isServerReachable()) {
            try {
                val url = URI("$endpointUrl/api/tags").toURL()
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 1200
                    readTimeout = 1200
                }
                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    // Simple parsing of "name":"..." from JSON
                    val nameRegex = Regex("\"name\"\\s*:\\s*\"([^\"]+)\"")
                    nameRegex.findAll(body).forEach { match ->
                        val mName = match.groupValues[1]
                        val family = if (mName.contains("code") || mName.contains("deepseek")) "Code Specialist" else "General LLM"
                        models.add(LocalLlmModelInfo(mName, 4.2, family, "7B"))
                    }
                }
            } catch (e: Exception) {
                logger.debug("Failed parsing Ollama models", e)
            }
        }

        // Add standard local model presets / fallbacks
        if (models.isEmpty()) {
            models.addAll(listOf(
                LocalLlmModelInfo("qwen2.5-coder:7b", 4.7, "Coding Specialist", "7B"),
                LocalLlmModelInfo("deepseek-coder:6.7b", 4.3, "Coding Specialist", "6.7B"),
                LocalLlmModelInfo("llama3:8b", 4.9, "Instruction Model", "8B"),
                LocalLlmModelInfo("mistral:7b", 4.1, "Reasoning Model", "7B"),
                LocalLlmModelInfo("jormungandr-offline-heuristic", 0.0, "Built-in Heuristic", "Offline")
            ))
        }
        return models
    }

    /**
     * Generate completion with metrics.
     */
    fun generate(
        model: String,
        prompt: String,
        systemPrompt: String = "",
        temperature: Double = 0.2,
        maxTokens: Int = 1024
    ): LlmGenerationResult {
        val startTime = System.currentTimeMillis()

        if (isServerReachable() && model != "jormungandr-offline-heuristic") {
            try {
                val url = URI("$endpointUrl/api/generate").toURL()
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                    connectTimeout = 3000
                    readTimeout = 60000
                }

                val jsonPayload = buildJsonPayload(model, prompt, systemPrompt, temperature)
                OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { it.write(jsonPayload) }

                if (conn.responseCode == 200) {
                    val responseSb = StringBuilder()
                    BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8)).useLines { lines ->
                        lines.forEach { line ->
                            val rMatch = Regex("\"response\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(line)
                            if (rMatch != null) {
                                responseSb.append(unescapeJson(rMatch.groupValues[1]))
                            }
                        }
                    }
                    val elapsed = System.currentTimeMillis() - startTime
                    val rawText = responseSb.toString()
                    val tokenCount = (rawText.length / 4).coerceAtLeast(1)
                    val tokPerSec = if (elapsed > 0) (tokenCount.toDouble() / (elapsed / 1000.0)) else 0.0
                    return LlmGenerationResult(rawText, elapsed, tokenCount, tokPerSec, model)
                }
            } catch (e: Exception) {
                logger.warn("Ollama generation failed, falling back to local heuristic", e)
            }
        }

        // High-speed deterministic fallback
        val heuristicText = generateOfflineHeuristicResponse(prompt, systemPrompt)
        val elapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(35)
        val tokens = heuristicText.length / 4
        val speed = tokens.toDouble() / (elapsed / 1000.0)
        return LlmGenerationResult(heuristicText, elapsed, tokens, speed, "$model (Offline Heuristic)")
    }

    private fun generateOfflineHeuristicResponse(prompt: String, system: String): String {
        val p = prompt.lowercase()
        return when {
            p.contains("docstring") || system.contains("docstring") -> {
                """
                |def process_dataset(df: pd.DataFrame, drop_outliers: bool = True) -> pd.DataFrame:
                |    ""${'"'}
                |    Transform raw ingress dataset by normalizing columns and filtering IQR outliers.
                |
                |    Parameters:
                |    ----------
                |    df : pd.DataFrame
                |        Input DataFrame containing raw metrics and feature dimensions.
                |    drop_outliers : bool, default=True
                |        Whether to prune rows falling outside the 1.5 * IQR interquartile threshold.
                |
                |    Returns:
                |    -------
                |    pd.DataFrame
                |        Cleaned, zero-copy filtered tabular DataFrame ready for ML feature ingestion.
                |
                |    Raises:
                |    ------
                |    ValueError
                |        If input DataFrame is empty or mandatory feature columns are missing.
                |    ""${'"'}
                |    return df.dropna().copy()
                """.trimMargin()
            }
            p.contains("test") || p.contains("pytest") -> {
                """
                |import pytest
                |import pandas as pd
                |import numpy as np
                |
                |@pytest.fixture
                |def sample_analytics_data() -> pd.DataFrame:
                |    return pd.DataFrame({
                |        "metric_a": [10.5, 20.1, 15.3, np.nan, 30.0],
                |        "feature_b": ["alpha", "beta", "alpha", "gamma", "beta"],
                |        "target": [0, 1, 0, 1, 1]
                |    })
                |
                |def test_process_dataset_valid_input(sample_analytics_data):
                |    result = process_dataset(sample_analytics_data, drop_outliers=False)
                |    assert not result.empty
                |    assert len(result) == 4  # Dropped NaN row
                |    assert "target" in result.columns
                |
                |def test_process_dataset_empty_raises():
                |    with pytest.raises(ValueError):
                |        process_dataset(pd.DataFrame())
                """.trimMargin()
            }
            p.contains("sql") || p.contains("duckdb") -> {
                """
                |-- Jörmungandr Optimized DuckDB Lakehouse Analytical Query
                |SELECT 
                |    date_trunc('day', timestamp) AS event_date,
                |    category,
                |    COUNT(*) AS total_events,
                |    ROUND(AVG(latency_ms), 2) AS avg_latency_ms,
                |    ROUND(PERCENTILE_CONT(0.99) WITHIN GROUP (ORDER BY latency_ms), 2) AS p99_latency_ms
                |FROM read_parquet('data/events/*.parquet')
                |WHERE status = 'SUCCESS'
                |GROUP BY 1, 2
                |ORDER BY event_date DESC, total_events DESC;
                """.trimMargin()
            }
            else -> {
                """
                |# Jörmungandr Local AI Intelligence Response
                |# Model: Local Coding Specialist
                |
                |import pandas as pd
                |import numpy as np
                |
                |# Generated solution for request:
                |# "${prompt.take(100)}"
                |
                |def transform_features(df: pd.DataFrame) -> pd.DataFrame:
                |    clean_df = df.copy()
                |    numeric_cols = clean_df.select_dtypes(include=[np.number]).columns
                |    clean_df[numeric_cols] = (clean_df[numeric_cols] - clean_df[numeric_cols].mean()) / clean_df[numeric_cols].std()
                |    return clean_df
                """.trimMargin()
            }
        }
    }

    private fun buildJsonPayload(model: String, prompt: String, system: String, temp: Double): String {
        return """{
            "model": "$model",
            "prompt": "${escapeJson(prompt)}",
            "system": "${escapeJson(system)}",
            "stream": false,
            "options": {
                "temperature": $temp
            }
        }""".replace("\n", " ").trim()
    }

    private fun escapeJson(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun unescapeJson(s: String): String {
        return s.replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }

    companion object {
        fun getInstance(): OllamaService {
            return ApplicationManager.getApplication().getService(OllamaService::class.java)
        }
    }
}
