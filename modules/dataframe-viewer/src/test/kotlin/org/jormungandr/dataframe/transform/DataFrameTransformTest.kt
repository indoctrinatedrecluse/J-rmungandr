package org.jormungandr.dataframe.transform

import org.jormungandr.dataframe.model.DataFrame
import org.jormungandr.dataframe.model.DataTypeCategory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DataFrameTransformTest {

    private val df = DataFrame.buildWithStatistics(
        "sales",
        listOf(
            Pair("category", DataTypeCategory.STRING),
            Pair("amount", DataTypeCategory.FLOAT)
        ),
        listOf(
            listOf("Tech", 100.0),
            listOf("Tech", 200.0),
            listOf("Books", 50.0),
            listOf("Books", 30.0)
        )
    )

    @Test
    fun `test groupBy sum aggregation`() {
        val grouped = DataFrameTransform.groupBy(df, "category", "amount", AggregationType.SUM)
        assertEquals(2, grouped.rowCount)

        val techRow = grouped.rows.find { it[0] == "Tech" }
        assertNotNull(techRow)
        assertEquals(300.0, techRow?.get(1))

        val booksRow = grouped.rows.find { it[0] == "Books" }
        assertNotNull(booksRow)
        assertEquals(80.0, booksRow?.get(1))
    }

    @Test
    fun `test df describe returns summary metrics across numeric columns`() {
        val desc = DataFrameTransform.describe(df)
        assertFalse(desc.isEmpty)
        assertEquals(8, desc.rowCount) // count, mean, std, min, 25%, 50%, 75%, max

        val meanRow = desc.rows.find { it[0] == "mean" }
        assertNotNull(meanRow)
        val meanVal = meanRow?.get(1) as? Double
        assertEquals(95.0, meanVal)
    }
}
