package org.jormungandr.dataframe.filter

import org.jormungandr.dataframe.model.DataFrame
import org.jormungandr.dataframe.model.DataTypeCategory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DataFrameFilterTest {

    private val df = DataFrame.buildWithStatistics(
        "sales",
        listOf(
            Pair("rep", DataTypeCategory.STRING),
            Pair("region", DataTypeCategory.STRING),
            Pair("revenue", DataTypeCategory.FLOAT),
            Pair("deals", DataTypeCategory.INTEGER)
        ),
        listOf(
            listOf("Alice", "East", 15000.0, 5L),
            listOf("Bob", "West", 8000.0, 2L),
            listOf("Charlie", "East", 24000.0, 8L),
            listOf("David", "West", 12000.0, 4L)
        )
    )

    @Test
    fun `test numeric greater than filter`() {
        val cond = FilterCondition("revenue", FilterOperator.GREATER_THAN, "10000")
        val filter = CompoundFilter(listOf(cond))
        val result = filter.applyTo(df)

        assertEquals(3, result.rowCount)
        assertFalse(result.rows.any { it[0] == "Bob" })
    }

    @Test
    fun `test string contains and multi-condition AND conjunction`() {
        val c1 = FilterCondition("region", FilterOperator.EQUALS, "East")
        val c2 = FilterCondition("deals", FilterOperator.GREATER_OR_EQUAL, "6")
        val filter = CompoundFilter(listOf(c1, c2), Conjunction.AND)
        val result = filter.applyTo(df)

        assertEquals(1, result.rowCount)
        assertEquals("Charlie", result.rows[0][0])
    }

    @Test
    fun `test multi-condition OR conjunction`() {
        val c1 = FilterCondition("rep", FilterOperator.EQUALS, "Alice")
        val c2 = FilterCondition("rep", FilterOperator.EQUALS, "Bob")
        val filter = CompoundFilter(listOf(c1, c2), Conjunction.OR)
        val result = filter.applyTo(df)

        assertEquals(2, result.rowCount)
    }
}
