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

package org.jormungandr.shell.r

import org.jormungandr.core.plot.PlotManagerService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File

class RDiscoveryServiceTest {

    @Test
    fun testRInstallationInfoDescriptorProperties() {
        val simulated = RInstallationInfo.simulated()
        assertTrue(simulated.isAvailable)
        assertEquals("4.4.1", simulated.majorMinorVersion)
        assertTrue(simulated.hasGgplot2)
        assertTrue(simulated.hasDplyr)
        assertTrue(simulated.hasArrow)
        assertTrue(simulated.isIRkernelAvailable)

        val unavail = RInstallationInfo.unavailable()
        assertFalse(unavail.isAvailable)
        assertFalse(unavail.hasGgplot2)
        assertEquals("Unknown", unavail.majorMinorVersion)
    }

    @Test
    fun testRDiscoveryServiceFallback() {
        val service = RDiscoveryService()
        val info = service.getInstallation()
        assertNotNull(info)
        assertNotNull(info.version)
    }

    @Test
    fun testCustomExecutableOverride() {
        val service = RDiscoveryService()
        service.setCustomExecutable("C:\\Custom\\NonExistent\\Rscript.exe")
        val info = service.getInstallation(forceRefresh = true)
        assertFalse(info.isAvailable)
    }

    @Test
    fun testRExecutionEnginePlotBridging() {
        val engine = RReplEngine()
        val plotScript = """
            library(ggplot2)
            ggplot(mtcars, aes(x = wt, y = mpg)) + geom_point() + geom_smooth()
        """.trimIndent()

        val result = engine.executeSimulated(plotScript)

        assertTrue(result.isSuccess)
        assertEquals(1, result.generatedPlotFiles.size)
        val generatedPlot = result.generatedPlotFiles.first()
        assertTrue(generatedPlot.exists())
        assertTrue(generatedPlot.length() > 1000)

        // Verify dispatch into PlotManagerService
        val plots = PlotManagerService.getInstance().plots.value
        assertTrue(plots.any { it.title.contains("ggplot2") || it.title.contains("R:") })
    }

    @Test
    fun testRExecutionEngineDataframeBridging() {
        val engine = RReplEngine()
        val dfScript = "head(mtcars)"

        val result = engine.executeSimulated(dfScript)

        assertTrue(result.isSuccess)
        assertEquals(1, result.exportedCsvFiles.size)
        val exportedCsv = result.exportedCsvFiles.first()
        assertTrue(exportedCsv.exists())
        val csvText = exportedCsv.readText()
        assertTrue(csvText.contains("mpg") || csvText.contains("model"))
        assertTrue(result.stdout.contains("DataFrame Viewer Studio"))
    }

    @Test
    fun testRExecutionEngineStatisticalSummary() {
        val engine = RReplEngine()
        val summaryScript = "summary(mtcars)"

        val result = engine.executeSimulated(summaryScript)

        assertTrue(result.isSuccess)
        assertTrue(result.stdout.contains("Min."))
        assertTrue(result.stdout.contains("Median"))
        assertTrue(result.stdout.contains("Mean"))
    }
}
