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
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AgenticDataStudioServiceTest {

    private val sampleDf = DataFrame.buildWithStatistics(
        name = "ecommerce_orders",
        rawColumns = listOf(
            Pair("order_id", DataTypeCategory.INTEGER),
            Pair("country", DataTypeCategory.STRING),
            Pair("total_amount", DataTypeCategory.FLOAT),
            Pair("is_returned", DataTypeCategory.BOOLEAN)
        ),
        rows = listOf(
            listOf(101L, "USA", 150.0, false),
            listOf(102L, "Germany", 280.5, true),
            listOf(103L, "USA", 95.0, false),
            listOf(104L, "France", 340.0, false),
            listOf(105L, "Japan", 210.0, false)
        )
    )

    @Test
    fun `test generateDataDictionary accurately infers column semantic roles`() {
        val dict = AgenticDataStudioService.generateDataDictionary(sampleDf)
        assertNotNull(dict)
        assertEquals(5, dict.totalRows)
        assertEquals(4, dict.totalColumns)

        val orderIdCol = dict.columns.find { it.name == "order_id" }
        assertNotNull(orderIdCol)
        assertEquals(ColumnSemanticRole.PRIMARY_KEY, orderIdCol!!.role)

        val countryCol = dict.columns.find { it.name == "country" }
        assertNotNull(countryCol)
        assertEquals(ColumnSemanticRole.CATEGORICAL_DIMENSION, countryCol!!.role)

        val amountCol = dict.columns.find { it.name == "total_amount" }
        assertNotNull(amountCol)
        assertEquals(ColumnSemanticRole.NUMERICAL_METRIC, amountCol!!.role)

        val returnedCol = dict.columns.find { it.name == "is_returned" }
        assertNotNull(returnedCol)
        assertEquals(ColumnSemanticRole.BINARY_FLAG, returnedCol!!.role)
    }

    @Test
    fun `test formatDictionaryAsMarkdown outputs markdown table`() {
        val dict = AgenticDataStudioService.generateDataDictionary(sampleDf)
        val md = AgenticDataStudioService.formatDictionaryAsMarkdown(dict)

        assertTrue(md.contains("## 📚 Semantic Data Dictionary: `ecommerce_orders`"))
        assertTrue(md.contains("| **order_id** |"))
        assertTrue(md.contains("| **country** |"))
        assertTrue(md.contains("| **total_amount** |"))
    }

    @Test
    fun `test translateNaturalLanguageQuery generates DuckDB SQL and Pandas for aggregation`() {
        val query = "Show total amount breakdown by country"
        val result = AgenticDataStudioService.translateNaturalLanguageQuery(sampleDf, query)

        assertNotNull(result)
        assertTrue(result.duckDbSql.contains("SELECT \"country\""))
        assertTrue(result.duckDbSql.contains("GROUP BY \"country\""))
        assertTrue(result.pandasCode.contains("df.groupby('country')"))
        assertEquals("BAR", result.suggestedChartType)
        assertEquals("country", result.suggestedXCol)
    }

    @Test
    fun `test translateNaturalLanguageQuery generates ranking query`() {
        val query = "Find top 3 orders by total_amount"
        val result = AgenticDataStudioService.translateNaturalLanguageQuery(sampleDf, query)

        assertNotNull(result)
        assertTrue(result.duckDbSql.contains("ORDER BY \"total_amount\" DESC"))
        assertTrue(result.duckDbSql.contains("LIMIT 3"))
        assertTrue(result.pandasCode.contains("sort_values"))
    }

    @Test
    fun `test translateNaturalLanguageQuery generates filter query`() {
        val query = "Filter orders where total_amount > 200"
        val result = AgenticDataStudioService.translateNaturalLanguageQuery(sampleDf, query)

        assertNotNull(result)
        assertTrue(result.duckDbSql.contains("WHERE \"total_amount\" > 200.0"))
        assertTrue(result.pandasCode.contains("df['total_amount'] > 200.0"))
    }
}
