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

package org.jormungandr.database.lakehouse

import org.jormungandr.dataframe.model.DataFrame
import org.jormungandr.dataframe.model.DataTypeCategory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class DuckDbLakehouseEngineTest {

    @Test
    fun `test DuckDB connection initializes and returns active connection`() {
        val conn = DuckDbLakehouseEngine.getConnection()
        assertNotNull(conn)
        assertFalse(conn.isClosed)
    }

    @Test
    fun `test execute standalone analytical SQL`() {
        val query = "SELECT 42 AS answer, 'Jormungandr' AS ide_name, 3.14159 AS pi_val"
        val result = DuckDbLakehouseEngine.executeQuery(query)

        assertNotNull(result)
        assertEquals(1L, result.rowCount)
        assertEquals(3, result.columnNames.size)
        assertEquals(listOf("answer", "ide_name", "pi_val"), result.columnNames)

        val df = result.dataFrame
        assertEquals(1, df.rowCount)
        assertEquals(42L, df.rows[0][0])
        assertEquals("Jormungandr", df.rows[0][1])
        assertEquals(3.14159, df.rows[0][2])
    }

    @Test
    fun `test register DataFrame and query with analytical SQL`() {
        val testDf = DataFrame.buildWithStatistics(
            name = "customer_sales",
            rawColumns = listOf(
                Pair("customer_id", DataTypeCategory.INTEGER),
                Pair("region", DataTypeCategory.STRING),
                Pair("revenue", DataTypeCategory.FLOAT),
                Pair("is_active", DataTypeCategory.BOOLEAN)
            ),
            rows = listOf(
                listOf(1L, "North", 150.0, true),
                listOf(2L, "South", 220.5, false),
                listOf(3L, "North", 310.0, true),
                listOf(4L, "West", 89.2, true)
            )
        )

        DuckDbLakehouseEngine.registerDataFrame("customer_sales", testDf)
        assertTrue(DuckDbLakehouseEngine.getRegisteredTableNames().contains("customer_sales"))

        val query = """
            SELECT region, COUNT(*) AS txn_count, SUM(revenue) AS total_rev, AVG(revenue) AS avg_rev
            FROM customer_sales
            GROUP BY region
            ORDER BY total_rev DESC
        """.trimIndent()

        val result = DuckDbLakehouseEngine.executeQuery(query)
        assertNotNull(result)
        assertEquals(3L, result.rowCount) // North, South, West

        val df = result.dataFrame
        val northRow = df.rows.find { it[0] == "North" }
        assertNotNull(northRow)
        assertEquals(2L, northRow!![1]) // 2 North transactions
        assertEquals(460.0, northRow[2]) // 150 + 310 = 460
    }

    @Test
    fun `test scan local data lakes finds parquet and csv files`() {
        val tempDir = Files.createTempDirectory("lakehouse_scan_test").toFile()
        try {
            val parquetFile = File(tempDir, "metrics.parquet").apply { writeText("mock parquet content") }
            val csvFile = File(tempDir, "customers.csv").apply { writeText("id,name\n1,Alice") }
            val txtFile = File(tempDir, "notes.txt").apply { writeText("ignore me") }

            val scanned = DuckDbLakehouseEngine.scanLocalDataLakes(tempDir)
            assertEquals(2, scanned.size)
            assertTrue(scanned.any { it.file.name == "metrics.parquet" && it.format == "PARQUET" })
            assertTrue(scanned.any { it.file.name == "customers.csv" && it.format == "CSV" })
            assertFalse(scanned.any { it.file.name == "notes.txt" })
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `test pipeToScientificPlots generates plot without throwing`() {
        val df = DataFrame.buildWithStatistics(
            name = "plot_data",
            rawColumns = listOf(
                Pair("category", DataTypeCategory.STRING),
                Pair("score", DataTypeCategory.FLOAT)
            ),
            rows = listOf(
                listOf("Alpha", 45.0),
                listOf("Beta", 82.5),
                listOf("Gamma", 96.0)
            )
        )
        val result = DuckDbLakehouseResult(
            dataFrame = df,
            executionTimeMs = 12L,
            sql = "SELECT category, score FROM plot_data",
            rowCount = 3L,
            columnNames = listOf("category", "score")
        )

        assertDoesNotThrow {
            DuckDbLakehouseEngine.pipeToScientificPlots(result, "Test Plot", "BAR")
            DuckDbLakehouseEngine.pipeToScientificPlots(result, "Test Line Plot", "LINE")
        }
    }
}
