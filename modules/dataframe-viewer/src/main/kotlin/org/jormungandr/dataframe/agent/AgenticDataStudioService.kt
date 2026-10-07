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

package org.jormungandr.dataframe.agent

import org.jormungandr.dataframe.model.DataFrame
import org.jormungandr.dataframe.model.DataTypeCategory

/**
 * Inferred semantic role of a column in business analytics.
 */
enum class ColumnSemanticRole(val title: String) {
    PRIMARY_KEY("Primary Key / Identifier"),
    CATEGORICAL_DIMENSION("Categorical Dimension"),
    NUMERICAL_METRIC("Numerical Metric / KPI"),
    DATETIME_AXIS("Temporal Axis / Timestamp"),
    TEXT_ENTITY("Free-form Text Entity"),
    BINARY_FLAG("Boolean / Binary Indicator")
}

/**
 * Rich semantic dictionary entry for a single column.
 */
data class SemanticColumnDefinition(
    val name: String,
    val role: ColumnSemanticRole,
    val inferredDescription: String,
    val summaryStats: String,
    val dataQualityNote: String,
    val sampleValues: List<String>
)

/**
 * Complete dataset dictionary explaining the business schema.
 */
data class SemanticDataDictionary(
    val datasetName: String,
    val totalRows: Int,
    val totalColumns: Int,
    val columns: List<SemanticColumnDefinition>,
    val executiveSummary: String
)

/**
 * Structured translation of natural language query into executable analytics.
 */
data class AgenticQueryResult(
    val originalPrompt: String,
    val duckDbSql: String,
    val pandasCode: String,
    val explanation: String,
    val suggestedChartType: String? = null, // "BAR", "LINE", "SCATTER", "HISTOGRAM"
    val suggestedXCol: String? = null,
    val suggestedYCol: String? = null
)

/**
 * Autonomous Agentic Data Intelligence Engine.
 * Translates natural language questions into precise DuckDB SQL and Pandas code,
 * infers business semantic data dictionaries, and suggests visual renderings.
 */
object AgenticDataStudioService {

    /**
     * Translates a natural language question into SQL, Pandas code, and visualization metadata.
     */
    fun translateNaturalLanguageQuery(df: DataFrame, userQuery: String): AgenticQueryResult {
        val lower = userQuery.lowercase().trim()
        val tbl = "active_df"

        val numericCols = df.columns.filter { it.isNumeric }
        val stringCols = df.columns.filter { it.category == DataTypeCategory.STRING }
        val dateCols = df.columns.filter { it.category == DataTypeCategory.DATETIME }

        val bestNumCol = numericCols.firstOrNull { lower.contains(it.name.lowercase()) }?.name
            ?: numericCols.firstOrNull()?.name ?: "value"
        val bestCatCol = stringCols.firstOrNull { lower.contains(it.name.lowercase()) }?.name
            ?: stringCols.firstOrNull()?.name ?: "category"

        // 1. Top N / Ranking (e.g. "top 3 by sales", "highest revenue")
        if (lower.contains("top") || lower.contains("highest") || lower.contains("largest") || lower.contains("best") || lower.contains("rank")) {
            val limitMatch = Regex("""(?:top|first)\s+(\d+)""").find(lower)
            val limit = limitMatch?.groupValues?.get(1)?.toIntOrNull() ?: 10
            val targetCol = numericCols.find { lower.contains(it.name.lowercase()) }?.name ?: bestNumCol

            val sql = """
                SELECT *
                FROM $tbl
                ORDER BY "$targetCol" DESC
                LIMIT $limit;
            """.trimIndent()

            val pandas = """
                res = df.sort_values(by='$targetCol', ascending=False).head($limit)
                print(res)
            """.trimIndent()

            return AgenticQueryResult(
                originalPrompt = userQuery,
                duckDbSql = sql,
                pandasCode = pandas,
                explanation = "Ranked records by '$targetCol' in descending order (top $limit).",
                suggestedChartType = "BAR",
                suggestedXCol = bestCatCol,
                suggestedYCol = targetCol
            )
        }

        // 2. Group By / Aggregation Query
        if (lower.contains("by ") || lower.contains("group") || lower.contains("breakdown") || lower.contains("per ")) {
            val groupCol = stringCols.find { lower.contains(it.name.lowercase()) }?.name ?: bestCatCol
            val aggNumCol = numericCols.find { lower.contains(it.name.lowercase()) }?.name ?: bestNumCol

            val aggFunc = when {
                lower.contains("sum") || lower.contains("total") -> "SUM"
                lower.contains("min") || lower.contains("lowest") -> "MIN"
                lower.contains("max") || lower.contains("highest") -> "MAX"
                lower.contains("count") -> "COUNT"
                else -> "AVG"
            }

            val sql = """
                SELECT "$groupCol", $aggFunc("$aggNumCol") AS ${aggFunc.lowercase()}_$aggNumCol, COUNT(*) AS count
                FROM $tbl
                GROUP BY "$groupCol"
                ORDER BY ${aggFunc.lowercase()}_$aggNumCol DESC
                LIMIT 20;
            """.trimIndent()

            val pandas = """
                res = df.groupby('$groupCol').agg({'${aggNumCol}': '${aggFunc.lowercase()}', '$groupCol': 'count'})
                res = res.rename(columns={'$groupCol': 'count'}).sort_values(by='${aggNumCol}', ascending=False).head(20)
                print(res)
            """.trimIndent()

            return AgenticQueryResult(
                originalPrompt = userQuery,
                duckDbSql = sql,
                pandasCode = pandas,
                explanation = "Grouped dataset by '$groupCol' and aggregated '$aggNumCol' using $aggFunc.",
                suggestedChartType = "BAR",
                suggestedXCol = groupCol,
                suggestedYCol = "${aggFunc.lowercase()}_$aggNumCol"
            )
        }

        // 3. Filtering / Conditions
        if (lower.contains("where") || lower.contains("filter") || lower.contains("only") || lower.contains(">") || lower.contains("<")) {
            val numCol = numericCols.find { lower.contains(it.name.lowercase()) }?.name ?: bestNumCol
            val threshold = Regex("""(\d+(?:\.\d+)?)""").find(lower)?.groupValues?.get(1)?.toDoubleOrNull()
                ?: (df.columns.find { it.name == numCol }?.meanVal ?: 100.0)

            val op = if (lower.contains("less") || lower.contains("below") || lower.contains("<")) "<" else ">"

            val sql = """
                SELECT *
                FROM $tbl
                WHERE "$numCol" $op $threshold
                LIMIT 100;
            """.trimIndent()

            val pandas = """
                res = df[df['$numCol'] $op $threshold].head(100)
                print(res)
            """.trimIndent()

            return AgenticQueryResult(
                originalPrompt = userQuery,
                duckDbSql = sql,
                pandasCode = pandas,
                explanation = "Filtered records where '$numCol' $op $threshold.",
                suggestedChartType = "SCATTER",
                suggestedXCol = bestCatCol,
                suggestedYCol = numCol
            )
        }

        // 4. Time Series / Trend
        if (dateCols.isNotEmpty() && (lower.contains("trend") || lower.contains("time") || lower.contains("over time") || lower.contains("daily"))) {
            val dateCol = dateCols.first().name
            val valCol = bestNumCol

            val sql = """
                SELECT "$dateCol", AVG("$valCol") AS avg_$valCol, COUNT(*) AS count
                FROM $tbl
                GROUP BY "$dateCol"
                ORDER BY "$dateCol" ASC
                LIMIT 100;
            """.trimIndent()

            val pandas = """
                res = df.groupby('$dateCol')['$valCol'].mean().reset_index()
                print(res)
            """.trimIndent()

            return AgenticQueryResult(
                originalPrompt = userQuery,
                duckDbSql = sql,
                pandasCode = pandas,
                explanation = "Aggregated '$valCol' chronologically along time dimension '$dateCol'.",
                suggestedChartType = "LINE",
                suggestedXCol = dateCol,
                suggestedYCol = "avg_$valCol"
            )
        }

        // Default query: Overview with limit
        val sql = """
            SELECT *
            FROM $tbl
            LIMIT 50;
        """.trimIndent()

        val pandas = """
            print(df.head(50))
        """.trimIndent()

        return AgenticQueryResult(
            originalPrompt = userQuery,
            duckDbSql = sql,
            pandasCode = pandas,
            explanation = "Default overview projection for '${df.name}'.",
            suggestedChartType = "BAR",
            suggestedXCol = bestCatCol,
            suggestedYCol = bestNumCol
        )
    }

    /**
     * Automatically generates a comprehensive Semantic Data Dictionary for the dataset.
     */
    fun generateDataDictionary(df: DataFrame): SemanticDataDictionary {
        val colDefs = df.columns.map { col ->
            val role = when {
                col.category == DataTypeCategory.BOOLEAN -> ColumnSemanticRole.BINARY_FLAG
                col.category == DataTypeCategory.DATETIME -> ColumnSemanticRole.DATETIME_AXIS
                (col.category == DataTypeCategory.INTEGER || col.category == DataTypeCategory.STRING) &&
                        col.distinctCount.toInt() == df.rowCount && df.rowCount > 1 &&
                        (col.name.lowercase().contains("id") || col.name.lowercase().contains("key") || col.category == DataTypeCategory.STRING) -> ColumnSemanticRole.PRIMARY_KEY
                col.isNumeric -> ColumnSemanticRole.NUMERICAL_METRIC
                col.category == DataTypeCategory.STRING && col.distinctCount < 20 -> ColumnSemanticRole.CATEGORICAL_DIMENSION
                col.distinctCount.toInt() == df.rowCount && df.rowCount > 1 -> ColumnSemanticRole.PRIMARY_KEY
                else -> ColumnSemanticRole.TEXT_ENTITY
            }

            val desc = when (role) {
                ColumnSemanticRole.PRIMARY_KEY -> "Unique identifier distinguishing individual records across the dataset."
                ColumnSemanticRole.CATEGORICAL_DIMENSION -> "Categorical feature with ${col.distinctCount} distinct segments for dimensional grouping."
                ColumnSemanticRole.NUMERICAL_METRIC -> "Continuous measurement variable (mean: ${col.meanVal?.let { "%.2f".format(it) } ?: "N/A"})."
                ColumnSemanticRole.DATETIME_AXIS -> "Timestamp feature tracking temporal events or observation epochs."
                ColumnSemanticRole.BINARY_FLAG -> "Binary truth-value indicator representing boolean condition state."
                ColumnSemanticRole.TEXT_ENTITY -> "Textual attribute containing qualitative or descriptive information."
            }

            val stats = if (col.isNumeric) {
                "Range: [${col.minVal} .. ${col.maxVal}] | Mean: ${col.meanVal?.let { "%.2f".format(it) } ?: "-"} | StdDev: ${col.stdDev?.let { "%.2f".format(it) } ?: "-"}"
            } else {
                "Distinct Count: ${col.distinctCount} | Nulls: ${col.nullCount}"
            }

            val quality = if (col.nullCount == 0L) "✅ 100% Complete" else "⚠️ Contains ${col.nullCount} missing values (%.1f%%)".format(col.nullPercentage)

            val samples = df.rows.take(4).mapNotNull { row ->
                val idx = df.getColumnIndex(col.name)
                row.getOrNull(idx)?.toString()
            }.distinct()

            SemanticColumnDefinition(
                name = col.name,
                role = role,
                inferredDescription = desc,
                summaryStats = stats,
                dataQualityNote = quality,
                sampleValues = samples
            )
        }

        val execSummary = "Dataset '${df.name}' comprises ${df.rowCount} rows across ${df.columnCount} columns (${colDefs.count { it.role == ColumnSemanticRole.NUMERICAL_METRIC }} metrics, ${colDefs.count { it.role == ColumnSemanticRole.CATEGORICAL_DIMENSION }} dimensions)."

        return SemanticDataDictionary(
            datasetName = df.name,
            totalRows = df.rowCount,
            totalColumns = df.columnCount,
            columns = colDefs,
            executiveSummary = execSummary
        )
    }

    /**
     * Formats semantic data dictionary as clean GitHub Markdown.
     */
    fun formatDictionaryAsMarkdown(dict: SemanticDataDictionary): String {
        val sb = StringBuilder()
        sb.appendLine("## 📚 Semantic Data Dictionary: `${dict.datasetName}`")
        sb.appendLine("> ${dict.executiveSummary}\n")
        sb.appendLine("| Column | Role | Inferred Semantics | Statistics | Data Quality |")
        sb.appendLine("|---|---|---|---|---|")

        for (c in dict.columns) {
            val sampleStr = if (c.sampleValues.isNotEmpty()) " *(e.g. ${c.sampleValues.take(2).joinToString(", ")})*" else ""
            sb.appendLine("| **${c.name}** | `${c.role.title}` | ${c.inferredDescription}$sampleStr | ${c.summaryStats} | ${c.dataQualityNote} |")
        }

        return sb.toString()
    }
}
