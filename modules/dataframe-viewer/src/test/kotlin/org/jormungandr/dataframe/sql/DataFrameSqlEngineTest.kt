package org.jormungandr.dataframe.sql

import org.jormungandr.dataframe.model.DataFrame
import org.jormungandr.dataframe.model.DataTypeCategory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DataFrameSqlEngineTest {

    private val salesDf = DataFrame.buildWithStatistics(
        "sales",
        listOf(
            Pair("id", DataTypeCategory.INTEGER),
            Pair("region", DataTypeCategory.STRING),
            Pair("category", DataTypeCategory.STRING),
            Pair("amount", DataTypeCategory.FLOAT),
            Pair("units", DataTypeCategory.INTEGER)
        ),
        listOf(
            listOf(1L, "North", "Tech", 1200.0, 5L),
            listOf(2L, "North", "Office", 300.0, 10L),
            listOf(3L, "South", "Tech", 800.0, 3L),
            listOf(4L, "South", "Tech", 1500.0, 7L),
            listOf(5L, "East", "Office", 150.0, 2L),
            listOf(6L, "West", "Furniture", 2200.0, 4L)
        )
    )

    @Test
    fun `test select all with limit`() {
        val result = DataFrameSqlEngine.execute("SELECT * FROM df LIMIT 3", salesDf)
        assertTrue(result is SqlExecutionResult.Success)
        val success = result as SqlExecutionResult.Success
        assertEquals(3, success.dataFrame.rowCount)
        assertEquals(5, success.dataFrame.columnCount)
    }

    @Test
    fun `test where filtering with comparisons`() {
        val result = DataFrameSqlEngine.execute(
            "SELECT id, region, amount FROM df WHERE amount > 1000",
            salesDf
        )
        assertTrue(result is SqlExecutionResult.Success)
        val df = (result as SqlExecutionResult.Success).dataFrame
        assertEquals(3, df.rowCount)
        assertEquals(listOf("id", "region", "amount"), df.columns.map { it.name })
    }

    @Test
    fun `test where filtering with string equality and AND condition`() {
        val result = DataFrameSqlEngine.execute(
            "SELECT id, region, category FROM df WHERE category = 'Tech' AND region = 'North'",
            salesDf
        )
        assertTrue(result is SqlExecutionResult.Success)
        val df = (result as SqlExecutionResult.Success).dataFrame
        assertEquals(1, df.rowCount)
        assertEquals("North", df.rows[0][1])
    }

    @Test
    fun `test where filtering with LIKE and IN operators`() {
        val likeResult = DataFrameSqlEngine.execute(
            "SELECT id, category FROM df WHERE category LIKE '%ffice%'",
            salesDf
        )
        assertTrue(likeResult is SqlExecutionResult.Success)
        val dfLike = (likeResult as SqlExecutionResult.Success).dataFrame
        assertEquals(2, dfLike.rowCount)

        val inResult = DataFrameSqlEngine.execute(
            "SELECT id, region FROM df WHERE region IN ('East', 'West')",
            salesDf
        )
        assertTrue(inResult is SqlExecutionResult.Success)
        val dfIn = (inResult as SqlExecutionResult.Success).dataFrame
        assertEquals(2, dfIn.rowCount)
    }

    @Test
    fun `test group by with aggregations and order by`() {
        val result = DataFrameSqlEngine.execute(
            "SELECT category, COUNT(*) AS count, SUM(amount) AS total_amount, AVG(units) AS avg_units " +
                    "FROM df " +
                    "GROUP BY category " +
                    "ORDER BY total_amount DESC",
            salesDf
        )
        assertTrue(result is SqlExecutionResult.Success)
        val df = (result as SqlExecutionResult.Success).dataFrame
        assertEquals(3, df.rowCount) // Tech (3500), Furniture (2200), Office (450)

        assertEquals("Tech", df.rows[0][0])
        assertEquals(3L, df.rows[0][1])
        assertEquals(3500.0, df.rows[0][2])

        assertEquals("Furniture", df.rows[1][0])
        assertEquals(2200.0, df.rows[1][2])

        assertEquals("Office", df.rows[2][0])
        assertEquals(450.0, df.rows[2][2])
    }

    @Test
    fun `test group by with having clause`() {
        val result = DataFrameSqlEngine.execute(
            "SELECT category, COUNT(*) AS cnt " +
                    "FROM df " +
                    "GROUP BY category " +
                    "HAVING cnt > 1",
            salesDf
        )
        assertTrue(result is SqlExecutionResult.Success)
        val df = (result as SqlExecutionResult.Success).dataFrame
        // Tech (3) and Office (2) have cnt > 1. Furniture has 1.
        assertEquals(2, df.rowCount)
        val categories = df.rows.map { it[0] }
        assertTrue(categories.contains("Tech"))
        assertTrue(categories.contains("Office"))
        assertFalse(categories.contains("Furniture"))
    }

    @Test
    fun `test invalid query returns error gracefully`() {
        val badTable = DataFrameSqlEngine.execute("SELECT * FROM non_existent_table", salesDf)
        assertTrue(badTable is SqlExecutionResult.Error)

        val badCol = DataFrameSqlEngine.execute("SELECT non_existent_col FROM df", salesDf)
        assertTrue(badCol is SqlExecutionResult.Error)
    }
}
