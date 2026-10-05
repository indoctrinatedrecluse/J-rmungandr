package org.jormungandr.dataframe.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DataFrameTest {

    @Test
    fun `test statistics computation for numerical columns`() {
        val columns = listOf(
            Pair("age", DataTypeCategory.INTEGER),
            Pair("rating", DataTypeCategory.FLOAT)
        )
        val rows: List<List<Any?>> = listOf(
            listOf(20L, 4.0),
            listOf(30L, 5.0),
            listOf(40L, 3.0),
            listOf(50L, 2.0)
        )

        val df = DataFrame.buildWithStatistics("test", columns, rows)

        assertEquals(4, df.rowCount)
        val ageCol = df.columns[0]
        assertEquals("20", ageCol.minVal)
        assertEquals("50", ageCol.maxVal)
        assertEquals(35.0, ageCol.meanVal)
        assertEquals(35.0, ageCol.medianVal)
        assertNotNull(ageCol.stdDev)
        assertTrue(ageCol.histogramBins.isNotEmpty())
    }

    @Test
    fun `test sorting and text filtering`() {
        val columns = listOf(
            Pair("name", DataTypeCategory.STRING),
            Pair("score", DataTypeCategory.INTEGER)
        )
        val rows: List<List<Any?>> = listOf(
            listOf("Charlie", 50L),
            listOf("Alice", 90L),
            listOf("Bob", 70L)
        )

        val df = DataFrame.buildWithStatistics("students", columns, rows)

        // Sort ascending by score (index 1)
        val sortedAsc = df.sort(1, ascending = true)
        assertEquals("Charlie", sortedAsc.rows[0][0])
        assertEquals("Bob", sortedAsc.rows[1][0])
        assertEquals("Alice", sortedAsc.rows[2][0])

        // Filter text
        val filtered = df.filterText("Alice")
        assertEquals(1, filtered.rowCount)
        assertEquals("Alice", filtered.rows[0][0])
    }
}
