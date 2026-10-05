package org.jormungandr.dataframe.chart

import org.jormungandr.dataframe.model.ColumnMetadata
import org.jormungandr.dataframe.model.DataFrame
import org.jormungandr.dataframe.model.DataTypeCategory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.awt.GraphicsEnvironment

class DataFrameChartViewTest {

    @Test
    fun `test chart types enum contains all expected visualization types`() {
        val types = ChartType.values()
        assertEquals(8, types.size)
        assertTrue(types.any { it == ChartType.BAR })
        assertTrue(types.any { it == ChartType.LINE })
        assertTrue(types.any { it == ChartType.AREA })
        assertTrue(types.any { it == ChartType.SCATTER })
        assertTrue(types.any { it == ChartType.HISTOGRAM })
        assertTrue(types.any { it == ChartType.BOX })
        assertTrue(types.any { it == ChartType.DONUT })
        assertTrue(types.any { it == ChartType.CORRELATION })
    }

    @Test
    fun `test renderChartImage generates valid buffered image in headless environment`() {
        val cols = listOf(
            ColumnMetadata("category", "string", DataTypeCategory.STRING),
            ColumnMetadata("revenue", "float", DataTypeCategory.FLOAT),
            ColumnMetadata("cost", "float", DataTypeCategory.FLOAT)
        )
        val rows = listOf(
            listOf("Hardware", 12000.0, 7500.0),
            listOf("Software", 34000.0, 11000.0),
            listOf("Services", 18500.0, 9200.0),
            listOf("Consulting", 8200.0, 4100.0)
        )
        val df = DataFrame("test_sales", cols, rows)

        val chartView = DataFrameChartView(df)
        val img = chartView.renderChartImage()

        assertNotNull(img)
        assertTrue(img.width >= 600)
        assertTrue(img.height >= 400)
    }

    @Test
    fun `test chart rendering handles empty data without errors`() {
        val emptyDf = DataFrame.empty("empty_test")
        val chartView = DataFrameChartView(emptyDf)
        val img = chartView.renderChartImage()

        assertNotNull(img)
        assertTrue(img.width >= 600)
        assertTrue(img.height >= 400)
    }
}
