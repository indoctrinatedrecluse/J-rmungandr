package org.jormungandr.dataframe.transform

import org.jormungandr.dataframe.model.DataFrame
import org.jormungandr.dataframe.model.DataTypeCategory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PivotTableEngineTest {

    private val sampleDf = DataFrame.buildWithStatistics(
        "sales_data",
        listOf(
            Pair("category", DataTypeCategory.STRING),
            Pair("region", DataTypeCategory.STRING),
            Pair("revenue", DataTypeCategory.FLOAT)
        ),
        listOf(
            listOf("Tech", "North", 100.0),
            listOf("Tech", "North", 200.0),
            listOf("Tech", "South", 300.0),
            listOf("Books", "North", 50.0),
            listOf("Books", "South", 150.0)
        )
    )

    @Test
    fun `test pivot table generation with SUM and totals`() {
        val config = PivotConfig(
            rowField = "category",
            columnField = "region",
            valueField = "revenue",
            aggregation = PivotAggType.SUM,
            includeTotals = true
        )

        val pivot = PivotTableEngine.generatePivot(sampleDf, config)
        assertFalse(pivot.isEmpty)

        // Columns should be: category, North, South, Total
        val colNames = pivot.columns.map { it.name }
        assertEquals(listOf("category", "North", "South", "Total"), colNames)

        // Rows should be: Books, Tech, Grand Total
        assertEquals(3, pivot.rowCount)

        val booksRow = pivot.rows.find { it[0] == "Books" }
        assertNotNull(booksRow)
        assertEquals(50.0, booksRow?.get(1))  // North
        assertEquals(150.0, booksRow?.get(2)) // South
        assertEquals(200.0, booksRow?.get(3)) // Row Total

        val techRow = pivot.rows.find { it[0] == "Tech" }
        assertNotNull(techRow)
        assertEquals(300.0, techRow?.get(1))  // North (100 + 200)
        assertEquals(300.0, techRow?.get(2))  // South
        assertEquals(600.0, techRow?.get(3))  // Row Total

        val grandTotalRow = pivot.rows.find { it[0] == "Grand Total" }
        assertNotNull(grandTotalRow)
        assertEquals(350.0, grandTotalRow?.get(1)) // North total (50 + 300)
        assertEquals(450.0, grandTotalRow?.get(2)) // South total (150 + 300)
        assertEquals(800.0, grandTotalRow?.get(3)) // Grand total (200 + 600)
    }

    @Test
    fun `test pivot table generation with COUNT without totals`() {
        val config = PivotConfig(
            rowField = "category",
            columnField = "region",
            valueField = "revenue",
            aggregation = PivotAggType.COUNT,
            includeTotals = false
        )

        val pivot = PivotTableEngine.generatePivot(sampleDf, config)
        assertEquals(listOf("category", "North", "South"), pivot.columns.map { it.name })
        assertEquals(2, pivot.rowCount) // Books, Tech (no Grand Total)

        val techRow = pivot.rows.find { it[0] == "Tech" }
        assertEquals(2L, techRow?.get(1)) // 2 in North
        assertEquals(1L, techRow?.get(2)) // 1 in South
    }

    @Test
    fun `test pivot table with AVERAGE aggregation`() {
        val config = PivotConfig(
            rowField = "category",
            columnField = "region",
            valueField = "revenue",
            aggregation = PivotAggType.AVERAGE,
            includeTotals = true
        )

        val pivot = PivotTableEngine.generatePivot(sampleDf, config)
        val techRow = pivot.rows.find { it[0] == "Tech" }
        assertEquals(150.0, techRow?.get(1)) // North average (100+200)/2 = 150
        assertEquals(200.0, techRow?.get(3)) // Tech row average (100+200+300)/3 = 200
    }

    @Test
    fun `test edge cases like missing column or empty dataframe`() {
        val emptyDf = DataFrame.empty()
        val config = PivotConfig("category", "region", "revenue")
        val pivotEmpty = PivotTableEngine.generatePivot(emptyDf, config)
        assertTrue(pivotEmpty.isEmpty)

        val badColConfig = PivotConfig("missing_dim", "region", "revenue")
        val pivotBad = PivotTableEngine.generatePivot(sampleDf, badColConfig)
        assertTrue(pivotBad.isEmpty)
    }
}
