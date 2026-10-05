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
}
