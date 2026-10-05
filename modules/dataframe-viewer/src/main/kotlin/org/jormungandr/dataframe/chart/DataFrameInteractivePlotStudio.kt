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

import com.google.gson.Gson
import com.intellij.ide.browsers.BrowserLauncher
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import org.jormungandr.core.plot.PlotManagerService
import org.jormungandr.core.plot.WebPlotItem
import org.jormungandr.core.plot.WebPlotType
import org.jormungandr.core.plot.WebPlotViewComponent
import org.jormungandr.dataframe.model.ColumnMetadata
import org.jormungandr.dataframe.model.DataFrame
import org.jormungandr.dataframe.model.DataTypeCategory
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.io.File
import java.util.Locale
import javax.swing.*
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder

enum class InteractiveChartType(val displayName: String, val webType: WebPlotType) {
    PLOTLY_3D_SCATTER("Plotly 3D Scatter Plot", WebPlotType.PLOTLY),
    PLOTLY_3D_MESH("Plotly 3D Surface / Mesh", WebPlotType.PLOTLY),
    PLOTLY_HEATMAP("Plotly Interactive Heatmap", WebPlotType.PLOTLY),
    VEGA_LITE_REACTIVE("Vega-Lite Reactive Scatter", WebPlotType.VEGA_LITE),
    FOLIUM_MAP("Folium / Leaflet Geospatial Map", WebPlotType.FOLIUM)
}

/**
 * Interactive 3D and Web Visualization Studio for Jörmungandr DataFrame Viewer.
 * Generates standalone Plotly (3D Scatter, 3D Mesh, Heatmap), Vega-Lite (reactive brush & zoom),
 * and Folium / Leaflet geospatial maps from live tabular datasets.
 * Includes interactive Chromium/JCEF rendering, instant browser preview, Scientific Plots sync,
 * and reproducible Python code generation (plotly.express, altair, folium).
 */
class DataFrameInteractivePlotStudio(
    private var dataFrame: DataFrame = DataFrame.empty()
) : JPanel(BorderLayout()) {

    private val chartTypeCombo = JComboBox(InteractiveChartType.values())
    private val xAxisCombo = JComboBox<String>()
    private val yAxisCombo = JComboBox<String>()
    private val zAxisCombo = JComboBox<String>()
    private val colorAxisCombo = JComboBox<String>()
    private val styleCombo = JComboBox(arrayOf("Viridis", "Plasma", "Cividis", "Spectral", "Turbo"))

    private val zAxisLabel = JBLabel("Z-Axis:")
    private val colorLabel = JBLabel("Color:")
    private val styleLabel = JBLabel("Palette:")

    private val generateBtn = JButton("⚡ Generate").apply {
        font = font.deriveFont(Font.BOLD, 11f)
        foreground = Color(30, 64, 175)
        isFocusable = false
    }
    private val openBrowserBtn = JButton("🌐 Open Browser").apply { isFocusable = false }
    private val sendPlotsBtn = JButton("🖼️ Send to Plots").apply { isFocusable = false }
    private val copyCodeBtn = JButton("🐍 Copy Python").apply { isFocusable = false }
    private val copyHtmlBtn = JButton("📋 Copy HTML").apply { isFocusable = false }

    private val statusLabel = JBLabel("Ready to generate interactive visualizations.").apply {
        font = font.deriveFont(Font.PLAIN, 11f)
        foreground = Color(100, 116, 139)
    }

    private val webPlotView = WebPlotViewComponent(parentDisposable = null, compactMode = false)
    private var currentPlotItem: WebPlotItem? = null
    private var currentGeneratedPythonCode: String = ""

    init {
        val topPanel = JPanel(BorderLayout()).apply {
            border = CompoundBorder(
                LineBorder(Color(226, 232, 240), 1),
                EmptyBorder(6, 8, 6, 8)
            )
            background = Color(248, 250, 252)
        }

        val controlsRow1 = JPanel(FlowLayout(FlowLayout.LEFT, 6, 2)).apply { isOpaque = false }
        controlsRow1.add(JBLabel("Visual Type:"))
        controlsRow1.add(chartTypeCombo)
        controlsRow1.add(Box.createHorizontalStrut(4))
        controlsRow1.add(JBLabel("X-Axis (or Lat):"))
        controlsRow1.add(xAxisCombo)
        controlsRow1.add(JBLabel("Y-Axis (or Lon):"))
        controlsRow1.add(yAxisCombo)
        controlsRow1.add(zAxisLabel)
        controlsRow1.add(zAxisCombo)
        controlsRow1.add(colorLabel)
        controlsRow1.add(colorAxisCombo)
        controlsRow1.add(styleLabel)
        controlsRow1.add(styleCombo)

        val controlsRow2 = JPanel(FlowLayout(FlowLayout.LEFT, 6, 2)).apply { isOpaque = false }
        controlsRow2.add(generateBtn)
        controlsRow2.add(Box.createHorizontalStrut(8))
        controlsRow2.add(openBrowserBtn)
        controlsRow2.add(sendPlotsBtn)
        controlsRow2.add(copyCodeBtn)
        controlsRow2.add(copyHtmlBtn)

        val controlsContainer = JPanel(GridLayout(2, 1, 2, 2)).apply { isOpaque = false }
        controlsContainer.add(controlsRow1)
        controlsContainer.add(controlsRow2)
        topPanel.add(controlsContainer, BorderLayout.CENTER)

        val bottomBar = JPanel(BorderLayout()).apply {
            border = EmptyBorder(4, 8, 4, 8)
            background = Color(248, 250, 252)
            add(statusLabel, BorderLayout.WEST)
        }

        add(topPanel, BorderLayout.NORTH)
        add(webPlotView, BorderLayout.CENTER)
        add(bottomBar, BorderLayout.SOUTH)

        setupListeners()
        refreshColumns()
        updateControlVisibility()
        if (dataFrame.rowCount > 0) {
            generateVisualization()
        }
    }

    private fun setupListeners() {
        chartTypeCombo.addActionListener {
            updateControlVisibility()
            autoDetectAxes()
            generateVisualization()
        }

        generateBtn.addActionListener { generateVisualization() }

        openBrowserBtn.addActionListener {
            webPlotView.openInExternalBrowser()
        }

        sendPlotsBtn.addActionListener {
            val item = currentPlotItem
            if (item != null) {
                PlotManagerService.getInstance().addWebPlot(item)
                statusLabel.text = "✓ Sent '${item.title}' to Scientific Plots tool window"
            }
        }

        copyCodeBtn.addActionListener {
            if (currentGeneratedPythonCode.isNotBlank()) {
                val sel = StringSelection(currentGeneratedPythonCode)
                Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, sel)
                statusLabel.text = "✓ Copied Python script to clipboard"
            }
        }

        copyHtmlBtn.addActionListener {
            val html = currentPlotItem?.let { WebPlotItem.ensureCompleteHtml(it.htmlContent, it.title) }
            if (html != null) {
                val sel = StringSelection(html)
                Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, sel)
                statusLabel.text = "✓ Copied standalone HTML to clipboard"
            }
        }
    }

    fun setDataFrame(newDf: DataFrame) {
        this.dataFrame = newDf
        refreshColumns()
        if (newDf.rowCount > 0) {
            generateVisualization()
        }
    }

    private fun updateControlVisibility() {
        val selected = chartTypeCombo.selectedItem as? InteractiveChartType ?: InteractiveChartType.PLOTLY_3D_SCATTER
        val is3d = selected == InteractiveChartType.PLOTLY_3D_SCATTER || selected == InteractiveChartType.PLOTLY_3D_MESH
        zAxisLabel.isVisible = is3d
        zAxisCombo.isVisible = is3d

        val isMap = selected == InteractiveChartType.FOLIUM_MAP
        if (isMap) {
            colorLabel.text = "Label:"
            styleLabel.text = "Map Tile:"
            styleCombo.model = DefaultComboBoxModel(arrayOf("OpenStreetMap", "CartoDB Positron", "CartoDB Dark_Matter"))
        } else {
            colorLabel.text = "Color:"
            styleLabel.text = "Palette:"
            styleCombo.model = DefaultComboBoxModel(arrayOf("Viridis", "Plasma", "Cividis", "Spectral", "Turbo"))
        }
    }

    private fun refreshColumns() {
        val allCols = dataFrame.columns.map { it.name }
        val numericCols = dataFrame.columns.filter { it.isNumeric }.map { it.name }

        fun updateCombo(combo: JComboBox<String>, items: List<String>, selected: String? = null) {
            combo.removeAllItems()
            for (item in items) combo.addItem(item)
            if (selected != null && items.contains(selected)) {
                combo.selectedItem = selected
            }
        }

        updateCombo(xAxisCombo, allCols)
        updateCombo(yAxisCombo, allCols)
        updateCombo(zAxisCombo, numericCols)

        colorAxisCombo.removeAllItems()
        colorAxisCombo.addItem("(None)")
        for (col in allCols) colorAxisCombo.addItem(col)

        autoDetectAxes()
    }

    private fun autoDetectAxes() {
        val selected = chartTypeCombo.selectedItem as? InteractiveChartType ?: InteractiveChartType.PLOTLY_3D_SCATTER
        val allCols = dataFrame.columns.map { it.name }
        val numericCols = dataFrame.columns.filter { it.isNumeric }.map { it.name }

        if (selected == InteractiveChartType.FOLIUM_MAP) {
            // Auto detect latitude and longitude
            val latMatch = allCols.firstOrNull { it.lowercase() in listOf("lat", "latitude", "y", "lat_deg", "latitude_deg") }
            val lonMatch = allCols.firstOrNull { it.lowercase() in listOf("lon", "long", "longitude", "x", "lon_deg", "lng") }
            if (latMatch != null) xAxisCombo.selectedItem = latMatch
            if (lonMatch != null) yAxisCombo.selectedItem = lonMatch

            val labelMatch = allCols.firstOrNull { it.lowercase() in listOf("name", "city", "title", "id", "label", "country") }
            if (labelMatch != null) colorAxisCombo.selectedItem = labelMatch
        } else {
            if (numericCols.size >= 3) {
                xAxisCombo.selectedItem = numericCols[0]
                yAxisCombo.selectedItem = numericCols[1]
                zAxisCombo.selectedItem = numericCols[2]
            } else if (numericCols.size == 2) {
                xAxisCombo.selectedItem = numericCols[0]
                yAxisCombo.selectedItem = numericCols[1]
            }
        }
    }

    fun generateVisualization() {
        if (dataFrame.rowCount == 0) {
            statusLabel.text = "DataFrame is empty; no chart generated."
            return
        }

        val chartType = chartTypeCombo.selectedItem as? InteractiveChartType ?: InteractiveChartType.PLOTLY_3D_SCATTER
        val xCol = xAxisCombo.selectedItem as? String ?: return
        val yCol = yAxisCombo.selectedItem as? String ?: return
        val zCol = zAxisCombo.selectedItem as? String ?: ""
        val colorColRaw = colorAxisCombo.selectedItem as? String
        val colorCol = if (colorColRaw == null || colorColRaw == "(None)") null else colorColRaw
        val palette = styleCombo.selectedItem as? String ?: "Viridis"

        val generated = when (chartType) {
            InteractiveChartType.PLOTLY_3D_SCATTER -> generatePlotly3DScatter(xCol, yCol, zCol, colorCol, palette)
            InteractiveChartType.PLOTLY_3D_MESH -> generatePlotly3DMesh(xCol, yCol, zCol, colorCol, palette)
            InteractiveChartType.PLOTLY_HEATMAP -> generatePlotlyHeatmap(palette)
            InteractiveChartType.VEGA_LITE_REACTIVE -> generateVegaLiteReactive(xCol, yCol, colorCol)
            InteractiveChartType.FOLIUM_MAP -> generateFoliumMap(xCol, yCol, colorCol, palette)
        }

        val plotItem = WebPlotItem(
            title = generated.title,
            plotType = chartType.webType,
            htmlContent = generated.html,
            source = "DataFrame Studio [${dataFrame.name.ifBlank { "Active" }}]"
        )

        currentPlotItem = plotItem
        currentGeneratedPythonCode = generated.pythonCode
        webPlotView.updatePlot(plotItem)
        statusLabel.text = "✓ Generated ${chartType.displayName} (${dataFrame.rowCount} records)"
    }

    private data class GeneratedVisual(val title: String, val html: String, val pythonCode: String)

    private fun generatePlotly3DScatter(xCol: String, yCol: String, zCol: String, colorCol: String?, palette: String): GeneratedVisual {
        val xIdx = dataFrame.getColumnIndex(xCol)
        val yIdx = dataFrame.getColumnIndex(yCol)
        val zIdx = if (zCol.isNotBlank()) dataFrame.getColumnIndex(zCol) else -1
        val colorIdx = colorCol?.let { dataFrame.getColumnIndex(it) } ?: -1

        val xVals = mutableListOf<Double>()
        val yVals = mutableListOf<Double>()
        val zVals = mutableListOf<Double>()
        val colorVals = mutableListOf<Any>()
        val hoverText = mutableListOf<String>()

        val limit = minOf(dataFrame.rowCount, 5000)
        for (i in 0 until limit) {
            val row = dataFrame.rows[i]
            val x = row.getOrNull(xIdx)?.toString()?.toDoubleOrNull() ?: continue
            val y = row.getOrNull(yIdx)?.toString()?.toDoubleOrNull() ?: continue
            val z = if (zIdx >= 0) row.getOrNull(zIdx)?.toString()?.toDoubleOrNull() ?: 0.0 else 0.0

            xVals.add(x)
            yVals.add(y)
            zVals.add(z)
            if (colorIdx >= 0) {
                val cv = row.getOrNull(colorIdx) ?: ""
                colorVals.add(cv)
                hoverText.add("$xCol: $x<br>$yCol: $y<br>$zCol: $z<br>$colorCol: $cv")
            } else {
                hoverText.add("$xCol: $x<br>$yCol: $y<br>$zCol: $z")
            }
        }

        val gson = Gson()
        val title = "3D Scatter: $zCol vs ($xCol, $yCol)"

        val traceJson = """
            {
                "type": "scatter3d",
                "mode": "markers",
                "x": ${gson.toJson(xVals)},
                "y": ${gson.toJson(yVals)},
                "z": ${gson.toJson(zVals)},
                "text": ${gson.toJson(hoverText)},
                "hoverinfo": "text",
                "marker": {
                    "size": 4.5,
                    "opacity": 0.85,
                    "colorscale": "$palette"
                    ${if (colorVals.isNotEmpty() && colorVals.firstOrNull() is Number) ", \"color\": ${gson.toJson(colorVals)}, \"colorbar\": {\"title\": \"$colorCol\"}" else ""}
                }
            }
        """.trimIndent()

        val layoutJson = """
            {
                "title": "$title",
                "margin": {"l": 0, "r": 0, "b": 0, "t": 40},
                "scene": {
                    "xaxis": {"title": "$xCol", "backgroundcolor": "#f8fafc", "gridcolor": "#e2e8f0"},
                    "yaxis": {"title": "$yCol", "backgroundcolor": "#f8fafc", "gridcolor": "#e2e8f0"},
                    "zaxis": {"title": "$zCol", "backgroundcolor": "#f8fafc", "gridcolor": "#e2e8f0"},
                    "camera": {"eye": {"x": 1.5, "y": 1.5, "z": 1.2}}
                }
            }
        """.trimIndent()

        val html = """
            <div id="chart-div" style="width:100%;height:100%;min-height:450px;"></div>
            <script src="https://cdn.plot.ly/plotly-2.35.2.min.js"></script>
            <script>
            var data = [$traceJson];
            var layout = $layoutJson;
            Plotly.newPlot('chart-div', data, layout, {responsive: true});
            </script>
        """.trimIndent()

        val pyCode = """
            import plotly.express as px
            # Reproduce this 3D Scatter in Python
            fig = px.scatter_3d(
                df,
                x='$xCol',
                y='$yCol',
                z='$zCol'${if (colorCol != null) ", color='$colorCol'" else ""},
                color_continuous_scale='$palette',
                title='$title'
            )
            fig.show()
        """.trimIndent()

        return GeneratedVisual(title, html, pyCode)
    }

    private fun generatePlotly3DMesh(xCol: String, yCol: String, zCol: String, colorCol: String?, palette: String): GeneratedVisual {
        val xIdx = dataFrame.getColumnIndex(xCol)
        val yIdx = dataFrame.getColumnIndex(yCol)
        val zIdx = if (zCol.isNotBlank()) dataFrame.getColumnIndex(zCol) else -1

        val xVals = mutableListOf<Double>()
        val yVals = mutableListOf<Double>()
        val zVals = mutableListOf<Double>()

        val limit = minOf(dataFrame.rowCount, 3000)
        for (i in 0 until limit) {
            val row = dataFrame.rows[i]
            val x = row.getOrNull(xIdx)?.toString()?.toDoubleOrNull() ?: continue
            val y = row.getOrNull(yIdx)?.toString()?.toDoubleOrNull() ?: continue
            val z = if (zIdx >= 0) row.getOrNull(zIdx)?.toString()?.toDoubleOrNull() ?: 0.0 else 0.0
            xVals.add(x)
            yVals.add(y)
            zVals.add(z)
        }

        val gson = Gson()
        val title = "3D Mesh Surface: $zCol vs ($xCol, $yCol)"

        val traceJson = """
            {
                "type": "mesh3d",
                "x": ${gson.toJson(xVals)},
                "y": ${gson.toJson(yVals)},
                "z": ${gson.toJson(zVals)},
                "opacity": 0.8,
                "colorscale": "$palette"
            }
        """.trimIndent()

        val layoutJson = """
            {
                "title": "$title",
                "margin": {"l": 0, "r": 0, "b": 0, "t": 40},
                "scene": {
                    "xaxis": {"title": "$xCol"},
                    "yaxis": {"title": "$yCol"},
                    "zaxis": {"title": "$zCol"}
                }
            }
        """.trimIndent()

        val html = """
            <div id="chart-div" style="width:100%;height:100%;min-height:450px;"></div>
            <script src="https://cdn.plot.ly/plotly-2.35.2.min.js"></script>
            <script>
            var data = [$traceJson];
            var layout = $layoutJson;
            Plotly.newPlot('chart-div', data, layout, {responsive: true});
            </script>
        """.trimIndent()

        val pyCode = """
            import plotly.graph_objects as go
            fig = go.Figure(data=[go.Mesh3d(
                x=df['$xCol'],
                y=df['$yCol'],
                z=df['$zCol'],
                opacity=0.8,
                colorscale='$palette'
            )])
            fig.update_layout(title='$title')
            fig.show()
        """.trimIndent()

        return GeneratedVisual(title, html, pyCode)
    }

    private fun generatePlotlyHeatmap(palette: String): GeneratedVisual {
        val numCols = dataFrame.columns.filter { it.isNumeric }.map { it.name }
        val names = numCols.take(12)
        val matrix = Array(names.size) { DoubleArray(names.size) }

        // Compute Pearson correlation matrix
        for (i in names.indices) {
            val iIdx = dataFrame.getColumnIndex(names[i])
            val iVals = (0 until dataFrame.rowCount).mapNotNull { dataFrame.rows[it].getOrNull(iIdx)?.toString()?.toDoubleOrNull() }
            for (j in names.indices) {
                if (i == j) {
                    matrix[i][j] = 1.0
                } else if (j > i) {
                    val jIdx = dataFrame.getColumnIndex(names[j])
                    val jVals = (0 until dataFrame.rowCount).mapNotNull { dataFrame.rows[it].getOrNull(jIdx)?.toString()?.toDoubleOrNull() }
                    val corr = computeCorrelation(iVals, jVals)
                    matrix[i][j] = corr
                    matrix[j][i] = corr
                }
            }
        }

        val gson = Gson()
        val title = "Correlation Matrix (${names.size} Numeric Columns)"

        val html = """
            <div id="chart-div" style="width:100%;height:100%;min-height:450px;"></div>
            <script src="https://cdn.plot.ly/plotly-2.35.2.min.js"></script>
            <script>
            var data = [{
                z: ${gson.toJson(matrix)},
                x: ${gson.toJson(names)},
                y: ${gson.toJson(names)},
                type: 'heatmap',
                colorscale: '$palette',
                hoverongaps: false
            }];
            var layout = {
                title: '$title',
                margin: {t: 40, b: 60, l: 80, r: 20}
            };
            Plotly.newPlot('chart-div', data, layout, {responsive: true});
            </script>
        """.trimIndent()

        val pyCode = """
            import plotly.express as px
            numeric_cols = df.select_dtypes(include='number')
            corr = numeric_cols.corr()
            fig = px.imshow(corr, text_auto=True, color_continuous_scale='$palette', title='$title')
            fig.show()
        """.trimIndent()

        return GeneratedVisual(title, html, pyCode)
    }

    private fun generateVegaLiteReactive(xCol: String, yCol: String, colorCol: String?): GeneratedVisual {
        val xIdx = dataFrame.getColumnIndex(xCol)
        val yIdx = dataFrame.getColumnIndex(yCol)
        val colorIdx = colorCol?.let { dataFrame.getColumnIndex(it) } ?: -1

        val records = mutableListOf<Map<String, Any>>()
        val limit = minOf(dataFrame.rowCount, 1500)
        for (i in 0 until limit) {
            val row = dataFrame.rows[i]
            val x = row.getOrNull(xIdx)?.toString()?.toDoubleOrNull() ?: continue
            val y = row.getOrNull(yIdx)?.toString()?.toDoubleOrNull() ?: continue
            val map = mutableMapOf<String, Any>(xCol to x, yCol to y)
            if (colorIdx >= 0) {
                map[colorCol!!] = row.getOrNull(colorIdx) ?: ""
            }
            records.add(map)
        }

        val gson = Gson()
        val title = "Vega-Lite Reactive: $yCol vs $xCol"

        val specJson = """
            {
                "${'$'}schema": "https://vega.github.io/schema/vega-lite/v5.json",
                "description": "$title",
                "width": "container",
                "height": 380,
                "data": {"values": ${gson.toJson(records)}},
                "params": [
                    {
                        "name": "grid",
                        "select": "interval",
                        "bind": "scales"
                    }
                ],
                "mark": {"type": "circle", "size": 60, "tooltip": true},
                "encoding": {
                    "x": {"field": "$xCol", "type": "quantitative"},
                    "y": {"field": "$yCol", "type": "quantitative"}
                    ${if (colorCol != null) ", \"color\": {\"field\": \"$colorCol\", \"type\": \"nominal\"}" else ""}
                }
            }
        """.trimIndent()

        val html = """
            <div id="vis" style="width:100%;height:100%;min-height:450px;"></div>
            <script src="https://cdn.jsdelivr.net/npm/vega@5"></script>
            <script src="https://cdn.jsdelivr.net/npm/vega-lite@5"></script>
            <script src="https://cdn.jsdelivr.net/npm/vega-embed@6"></script>
            <script>
            var spec = $specJson;
            vegaEmbed('#vis', spec, {mode: "vega-lite"}).catch(console.warn);
            </script>
        """.trimIndent()

        val pyCode = """
            import altair as alt
            chart = alt.Chart(df).mark_circle(size=60).encode(
                x='$xCol:Q',
                y='$yCol:Q'${if (colorCol != null) ", color='$colorCol:N'" else ""},
                tooltip=['$xCol', '$yCol'${if (colorCol != null) ", '$colorCol'" else ""}]
            ).interactive()
            chart.show()
        """.trimIndent()

        return GeneratedVisual(title, html, pyCode)
    }

    private fun generateFoliumMap(latCol: String, lonCol: String, labelCol: String?, tileProvider: String): GeneratedVisual {
        val latIdx = dataFrame.getColumnIndex(latCol)
        val lonIdx = dataFrame.getColumnIndex(lonCol)
        val labelIdx = labelCol?.let { dataFrame.getColumnIndex(it) } ?: -1

        val markers = mutableListOf<Map<String, Any>>()
        var latSum = 0.0
        var lonSum = 0.0
        var count = 0

        val limit = minOf(dataFrame.rowCount, 2000)
        for (i in 0 until limit) {
            val row = dataFrame.rows[i]
            val lat = row.getOrNull(latIdx)?.toString()?.toDoubleOrNull() ?: continue
            val lon = row.getOrNull(lonIdx)?.toString()?.toDoubleOrNull() ?: continue
            val label = if (labelIdx >= 0) row.getOrNull(labelIdx)?.toString() ?: "" else "Point #$i"
            markers.add(mapOf("lat" to lat, "lon" to lon, "label" to label))
            latSum += lat
            lonSum += lon
            count++
        }

        val centerLat = if (count > 0) latSum / count else 0.0
        val centerLon = if (count > 0) lonSum / count else 0.0
        val gson = Gson()
        val title = "Folium / Leaflet Map ($count Coordinates)"

        val tileUrl = when (tileProvider) {
            "CartoDB Positron" -> "https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png"
            "CartoDB Dark_Matter" -> "https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png"
            else -> "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        }

        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
                <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
                <style>
                    html, body, #map { width: 100%; height: 100%; margin: 0; padding: 0; }
                </style>
            </head>
            <body>
                <div id="map"></div>
                <script>
                var map = L.map('map').setView([$centerLat, $centerLon], 10);
                L.tileLayer('$tileUrl', {
                    maxZoom: 19,
                    attribution: '&copy; OpenStreetMap contributors'
                }).addTo(map);

                var points = ${gson.toJson(markers)};
                var group = L.featureGroup();
                points.forEach(function(p) {
                    var m = L.circleMarker([p.lat, p.lon], {
                        radius: 6,
                        fillColor: "#3b82f6",
                        color: "#1d4ed8",
                        weight: 1.5,
                        opacity: 1,
                        fillOpacity: 0.8
                    }).bindPopup("<b>" + p.label + "</b><br>Lat: " + p.lat + "<br>Lon: " + p.lon);
                    group.addLayer(m);
                });
                group.addTo(map);
                if (points.length > 0) {
                    map.fitBounds(group.getBounds().pad(0.1));
                }
                </script>
            </body>
            </html>
        """.trimIndent()

        val pyCode = """
            import folium
            m = folium.Map(location=[$centerLat, $centerLon], zoom_start=10, tiles='$tileProvider')
            for _, row in df.iterrows():
                folium.CircleMarker(
                    location=[row['$latCol'], row['$lonCol']],
                    radius=5,
                    popup=str(row.get('$labelCol', '')),
                    color='#1d4ed8',
                    fill=True,
                    fill_color='#3b82f6'
                ).add_to(m)
            m.save('map.html')
        """.trimIndent()

        return GeneratedVisual(title, html, pyCode)
    }

    private fun computeCorrelation(xs: List<Double>, ys: List<Double>): Double {
        val n = minOf(xs.size, ys.size)
        if (n < 2) return 0.0
        val meanX = xs.take(n).average()
        val meanY = ys.take(n).average()
        var num = 0.0
        var denX = 0.0
        var denY = 0.0
        for (i in 0 until n) {
            val dx = xs[i] - meanX
            val dy = ys[i] - meanY
            num += dx * dy
            denX += dx * dx
            denY += dy * dy
        }
        val den = Math.sqrt(denX * denY)
        return if (den == 0.0) 0.0 else (num / den).coerceIn(-1.0, 1.0)
    }
}
