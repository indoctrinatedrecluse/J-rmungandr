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

package org.jormungandr.core.plot

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage

class PlotManagerServiceTest {

    @Test
    fun `test plot lifecycle adding selecting and removing plots`() {
        val service = PlotManagerService()
        assertEquals(0, service.plots.value.size)
        assertNull(service.activePlot.value)

        val img1 = BufferedImage(200, 150, BufferedImage.TYPE_INT_ARGB)
        val plot1 = PlotItem(
            title = "Figure 1",
            source = "Matplotlib",
            image = img1
        )

        service.addPlot(plot1)
        assertEquals(1, service.plots.value.size)
        assertEquals(plot1, service.activePlot.value)
        assertEquals(200, plot1.width)
        assertEquals(150, plot1.height)

        val img2 = BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB)
        val plot2 = PlotItem(
            title = "Figure 2",
            source = "DataFrame Studio",
            image = img2
        )

        service.addPlot(plot2)
        assertEquals(2, service.plots.value.size)
        assertEquals(plot2, service.activePlot.value)

        service.selectPlot(plot1)
        assertEquals(plot1, service.activePlot.value)

        service.removePlot(plot1.id)
        assertEquals(1, service.plots.value.size)
        assertEquals(plot2, service.activePlot.value)

        service.clearAll()
        assertEquals(0, service.plots.value.size)
        assertNull(service.activePlot.value)
    }

    @Test
    fun `test web plot lifecycle and detection`() {
        val service = PlotManagerService()
        assertEquals(0, service.webPlots.value.size)
        assertNull(service.activeWebPlot.value)

        val plotlySnippet = "<div id='plot'></div><script>Plotly.newPlot('plot', []);</script>"
        assertEquals(WebPlotType.PLOTLY, WebPlotItem.detectType(plotlySnippet))

        val vegaSnippet = "{\"\$schema\": \"https://vega.github.io/schema/vega-lite/v5.json\"}"
        assertEquals(WebPlotType.VEGA_LITE, WebPlotItem.detectType(vegaSnippet))

        val foliumSnippet = "<div id='map'></div><script>L.map('map');</script>"
        assertEquals(WebPlotType.FOLIUM, WebPlotItem.detectType(foliumSnippet))

        val customHtmlSnippet = "<div><h1>Hello World</h1></div>"
        assertEquals(WebPlotType.CUSTOM_HTML, WebPlotItem.detectType(customHtmlSnippet))

        val webPlot1 = WebPlotItem(
            title = "3D Scatter Plot",
            plotType = WebPlotType.PLOTLY,
            htmlContent = plotlySnippet,
            source = "DataFrame Studio"
        )

        service.addWebPlot(webPlot1)
        assertEquals(1, service.webPlots.value.size)
        assertEquals(webPlot1, service.activeWebPlot.value)

        val exportedFile = webPlot1.exportHtmlFile()
        assertTrue(exportedFile.exists())
        assertTrue(exportedFile.readText().contains("<!DOCTYPE html>"))
        assertTrue(exportedFile.readText().contains("3D Scatter Plot"))
        assertTrue(exportedFile.readText().contains("Plotly.newPlot"))

        val webPlot2 = WebPlotItem(
            title = "Geospatial Map",
            plotType = WebPlotType.FOLIUM,
            htmlContent = foliumSnippet,
            source = "Jupyter Cell"
        )
        service.addWebPlot(webPlot2)
        assertEquals(2, service.webPlots.value.size)
        assertEquals(webPlot2, service.activeWebPlot.value)

        service.selectWebPlot(webPlot1)
        assertEquals(webPlot1, service.activeWebPlot.value)

        service.removeWebPlot(webPlot1.id)
        assertEquals(1, service.webPlots.value.size)
        assertEquals(webPlot2, service.activeWebPlot.value)

        service.clearAllWebPlots()
        assertEquals(0, service.webPlots.value.size)
        assertNull(service.activeWebPlot.value)
    }
}
