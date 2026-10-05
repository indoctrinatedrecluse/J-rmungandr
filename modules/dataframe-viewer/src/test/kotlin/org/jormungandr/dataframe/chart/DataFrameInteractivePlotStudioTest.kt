/*
 * Copyright © 2025–2026 indoctrinatedrecluse
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

package org.jormungandr.dataframe.chart

import org.jormungandr.core.plot.WebPlotType
import org.jormungandr.dataframe.model.ColumnMetadata
import org.jormungandr.dataframe.model.DataFrame
import org.jormungandr.dataframe.model.DataTypeCategory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import javax.swing.JComboBox

class DataFrameInteractivePlotStudioTest {

    private fun sampleDataFrame(): DataFrame {
        val cols = listOf(
            ColumnMetadata("x_pos", "float", DataTypeCategory.FLOAT),
            ColumnMetadata("y_pos", "float", DataTypeCategory.FLOAT),
            ColumnMetadata("z_elevation", "float", DataTypeCategory.FLOAT),
            ColumnMetadata("category", "string", DataTypeCategory.STRING),
            ColumnMetadata("latitude", "float", DataTypeCategory.FLOAT),
            ColumnMetadata("longitude", "float", DataTypeCategory.FLOAT),
            ColumnMetadata("city_name", "string", DataTypeCategory.STRING)
        )
        val rows = listOf(
            listOf(1.0, 2.0, 15.5, "A", 37.7749, -122.4194, "San Francisco"),
            listOf(2.5, 3.8, 22.0, "B", 40.7128, -74.0060, "New York"),
            listOf(4.0, 1.2, 8.4, "A", 51.5074, -0.1278, "London"),
            listOf(5.5, 6.1, 35.2, "C", 35.6762, 139.6503, "Tokyo")
        )
        return DataFrame("geo_points", cols, rows)
    }

    @Test
    fun `test interactive chart types enum`() {
        val types = InteractiveChartType.values()
        assertEquals(5, types.size)
        assertTrue(types.any { it == InteractiveChartType.PLOTLY_3D_SCATTER && it.webType == WebPlotType.PLOTLY })
        assertTrue(types.any { it == InteractiveChartType.PLOTLY_3D_MESH && it.webType == WebPlotType.PLOTLY })
        assertTrue(types.any { it == InteractiveChartType.PLOTLY_HEATMAP && it.webType == WebPlotType.PLOTLY })
        assertTrue(types.any { it == InteractiveChartType.VEGA_LITE_REACTIVE && it.webType == WebPlotType.VEGA_LITE })
        assertTrue(types.any { it == InteractiveChartType.FOLIUM_MAP && it.webType == WebPlotType.FOLIUM })
    }

    @Test
    fun `test interactive studio initialization and generation`() {
        val df = sampleDataFrame()
        val studio = DataFrameInteractivePlotStudio(df)
        assertNotNull(studio)

        // Generate default (Plotly 3D Scatter)
        studio.generateVisualization()

        // Update dataFrame
        val newCols = listOf(
            ColumnMetadata("lat", "float", DataTypeCategory.FLOAT),
            ColumnMetadata("lon", "float", DataTypeCategory.FLOAT),
            ColumnMetadata("name", "string", DataTypeCategory.STRING)
        )
        val newRows = listOf(
            listOf(48.8566, 2.3522, "Paris"),
            listOf(52.5200, 13.4050, "Berlin")
        )
        val geoDf = DataFrame("capitals", newCols, newRows)
        studio.setDataFrame(geoDf)
        assertNotNull(studio)
    }

    @Test
    fun `test empty dataframe does not throw exception`() {
        val emptyDf = DataFrame.empty("empty_test")
        val studio = DataFrameInteractivePlotStudio(emptyDf)
        assertDoesNotThrow {
            studio.generateVisualization()
        }
    }
}
