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

import java.io.File
import java.util.UUID

enum class WebPlotType(val displayName: String, val badgeIcon: String) {
    PLOTLY("Plotly 3D / Web", "📈"),
    VEGA_LITE("Vega-Lite / Altair", "📊"),
    FOLIUM("Folium / Leaflet Map", "🗺️"),
    ECHART("Apache ECharts", "📉"),
    CUSTOM_HTML("Interactive Web Visual", "🌐")
}

/**
 * Encapsulates an interactive web / 3D visualization artifact generated in Jörmungandr
 * (Plotly 3D scatter/surface, Altair / Vega-Lite reactive charts, Folium geospatial maps, etc.).
 */
data class WebPlotItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val plotType: WebPlotType,
    val htmlContent: String,
    val source: String, // e.g. "Jupyter Cell", "DataFrame Studio 3D", "Altair"
    val timestamp: Long = System.currentTimeMillis(),
    val metadata: Map<String, String> = emptyMap()
) {
    /**
     * Saves the plot as a temporary or target standalone HTML file ready to be opened in any web browser.
     */
    fun exportHtmlFile(targetFile: File? = null): File {
        val file = targetFile ?: File.createTempFile("jormungandr_web_plot_${id.take(8)}_", ".html")
        file.writeText(ensureCompleteHtml(htmlContent, title), Charsets.UTF_8)
        file.deleteOnExit()
        return file
    }

    companion object {
        fun detectType(htmlOrJson: String): WebPlotType {
            val lower = htmlOrJson.lowercase()
            return when {
                lower.contains("plotly") || lower.contains("plot.ly") -> WebPlotType.PLOTLY
                lower.contains("vega-lite") || lower.contains("vegaembed") || lower.contains("\"\$schema\": \"https://vega.github.io") -> WebPlotType.VEGA_LITE
                lower.contains("folium") || lower.contains("leaflet") || lower.contains("l.map") -> WebPlotType.FOLIUM
                lower.contains("echarts") -> WebPlotType.ECHART
                else -> WebPlotType.CUSTOM_HTML
            }
        }

        fun ensureCompleteHtml(snippetOrHtml: String, pageTitle: String = "Jörmungandr Interactive Plot"): String {
            val trimmed = snippetOrHtml.trim()
            if (trimmed.startsWith("<!DOCTYPE html>", ignoreCase = true) || trimmed.startsWith("<html>", ignoreCase = true)) {
                return snippetOrHtml
            }
            return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>${escapeXml(pageTitle)}</title>
                    <style>
                        html, body {
                            margin: 0;
                            padding: 0;
                            width: 100%;
                            height: 100%;
                            overflow: auto;
                            background-color: #ffffff;
                            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                        }
                    </style>
                </head>
                <body>
                    $snippetOrHtml
                </body>
                </html>
            """.trimIndent()
        }

        private fun escapeXml(text: String): String {
            return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
        }
    }
}
