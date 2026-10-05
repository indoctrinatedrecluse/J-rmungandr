package org.jormungandr.dataframe.chart

import com.intellij.ui.components.JBLabel
import org.jormungandr.dataframe.model.DataFrame
import java.awt.*
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Arc2D
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import javax.swing.*
import javax.swing.border.EmptyBorder

enum class ChartType(val displayName: String) {
    BAR("Bar Chart"),
    LINE("Line Chart"),
    AREA("Area Chart"),
    SCATTER("Scatter Plot"),
    HISTOGRAM("Histogram"),
    BOX("Box Plot"),
    DONUT("Donut / Pie Chart"),
    CORRELATION("Correlation Matrix Heatmap")
}

/**
 * High-performance 2D vector charting component for Jörmungandr DataFrame Viewer.
 * Supports Bar, Line, Area, Scatter, Histogram, Box Plot, Donut, and Correlation Heatmap charts
 * with interactive hover inspection, image exports, clipboard copy, and Scientific Plots integration.
 */
class DataFrameChartView(
    private var dataFrame: DataFrame = DataFrame.empty()
) : JPanel(BorderLayout()) {

    private val chartTypeCombo = JComboBox(ChartType.values())
    private val xAxisCombo = JComboBox<String>()
    private val yAxisCombo = JComboBox<String>()
    private val exportBtn = JButton("📸 Export PNG").apply { isFocusable = false }
    private val copyBtn = JButton("📋 Copy Chart").apply { isFocusable = false }
    private val sendPlotsBtn = JButton("🖼️ Send to Plots").apply { isFocusable = false }

    private val canvas = ChartCanvas()
    private val hoverLabel = JBLabel("Hover over chart elements to inspect values").apply {
        font = font.deriveFont(Font.PLAIN, 11f)
        foreground = Color(100, 116, 139)
    }

    init {
        val controls = JPanel(FlowLayout(FlowLayout.LEFT, 8, 4)).apply {
            border = EmptyBorder(4, 6, 4, 6)
            background = Color(248, 250, 252)
        }

        controls.add(JBLabel("Type:"))
        controls.add(chartTypeCombo)
        controls.add(JBLabel("X-Axis:"))
        controls.add(xAxisCombo)
        controls.add(JBLabel("Y-Axis:"))
        controls.add(yAxisCombo)
        controls.add(Box.createHorizontalStrut(6))
        controls.add(exportBtn)
        controls.add(copyBtn)
        controls.add(sendPlotsBtn)

        val bottomBar = JPanel(BorderLayout()).apply {
            border = EmptyBorder(4, 8, 4, 8)
            background = Color(248, 250, 252)
            add(hoverLabel, BorderLayout.WEST)
        }

        add(controls, BorderLayout.NORTH)
        add(canvas, BorderLayout.CENTER)
        add(bottomBar, BorderLayout.SOUTH)

        chartTypeCombo.addActionListener { updateChart() }
        xAxisCombo.addActionListener { updateChart() }
        yAxisCombo.addActionListener { updateChart() }

        exportBtn.addActionListener {
            val fileChooser = JFileChooser()
            fileChooser.selectedFile = File("dataframe_chart.png")
            if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                runCatching {
                    val img = renderChartImage()
                    ImageIO.write(img, "png", fileChooser.selectedFile)
                    hoverLabel.text = "✓ Exported chart to ${fileChooser.selectedFile.name}"
                }
            }
        }

        copyBtn.addActionListener {
            val img = renderChartImage()
            Toolkit.getDefaultToolkit().systemClipboard.setContents(ImageTransferable(img), null)
            hoverLabel.text = "✓ Copied chart image to clipboard"
        }

        sendPlotsBtn.addActionListener {
            val img = renderChartImage()
            val xCol = xAxisCombo.selectedItem as? String ?: ""
            val yCol = yAxisCombo.selectedItem as? String ?: ""
            val type = chartTypeCombo.selectedItem as? ChartType ?: ChartType.BAR
            runCatching {
                val plotItem = org.jormungandr.core.plot.PlotItem(
                    title = "$yCol vs $xCol (${type.displayName})",
                    source = "DataFrame Studio",
                    image = img
                )
                org.jormungandr.core.plot.PlotManagerService.getInstance().addPlot(plotItem)
                hoverLabel.text = "✓ Sent chart to Scientific Plots tool window"
            }
        }

        refreshColumns()
    }

    fun setDataFrame(df: DataFrame) {
        this.dataFrame = df
        refreshColumns()
    }

    fun renderChartImage(): BufferedImage {
        val w = canvas.width.coerceAtLeast(600)
        val h = canvas.height.coerceAtLeast(400)
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        canvas.paint(g)
        g.dispose()
        return img
    }

    private fun refreshColumns() {
        xAxisCombo.removeAllItems()
        yAxisCombo.removeAllItems()

        for (col in dataFrame.columns) {
            xAxisCombo.addItem(col.name)
            yAxisCombo.addItem(col.name)
        }

        val numericCols = dataFrame.columns.filter { it.isNumeric }
        if (numericCols.isNotEmpty()) {
            yAxisCombo.selectedItem = numericCols.first().name
        }
        if (dataFrame.columns.isNotEmpty()) {
            xAxisCombo.selectedIndex = 0
        }

        updateChart()
    }

    private fun updateChart() {
        val xCol = xAxisCombo.selectedItem as? String ?: return
        val yCol = yAxisCombo.selectedItem as? String ?: return
        val type = chartTypeCombo.selectedItem as? ChartType ?: ChartType.BAR

        val xIdx = dataFrame.getColumnIndex(xCol)
        val yIdx = dataFrame.getColumnIndex(yCol)
        if (xIdx < 0 || yIdx < 0) return

        val points = mutableListOf<Pair<String, Double>>()
        val rawYValues = mutableListOf<Double>()
        for (row in dataFrame.rows) {
            val xVal = row.getOrNull(xIdx)?.toString() ?: ""
            val yRaw = row.getOrNull(yIdx)
            val yVal = when (yRaw) {
                is Number -> yRaw.toDouble()
                is String -> yRaw.toDoubleOrNull()
                else -> null
            }
            if (yVal != null) {
                points.add(Pair(xVal, yVal))
                rawYValues.add(yVal)
            }
        }

        canvas.setData(type, xCol, yCol, points, rawYValues, dataFrame)
        canvas.repaint()
    }

    private inner class ChartCanvas : JComponent() {
        private var chartType: ChartType = ChartType.BAR
        private var xName: String = ""
        private var yName: String = ""
        private var dataPoints: List<Pair<String, Double>> = emptyList()
        private var rawYValues: List<Double> = emptyList()
        private var currentDf: DataFrame = DataFrame.empty()

        init {
            preferredSize = Dimension(650, 400)
            addMouseMotionListener(object : MouseAdapter() {
                override fun mouseMoved(e: MouseEvent) {
                    val hit = findHitPoint(e.point)
                    if (hit != null) {
                        hoverLabel.text = hit
                    }
                }
            })
        }

        fun setData(
            type: ChartType,
            x: String,
            y: String,
            points: List<Pair<String, Double>>,
            rawY: List<Double>,
            df: DataFrame
        ) {
            this.chartType = type
            this.xName = x
            this.yName = y
            this.dataPoints = points
            this.rawYValues = rawY
            this.currentDf = df
        }

        private fun findHitPoint(p: Point): String? {
            if (dataPoints.isEmpty() && chartType != ChartType.CORRELATION) return null
            val padding = 60
            val plotW = width - (padding * 2)
            if (plotW <= 0) return null

            return when (chartType) {
                ChartType.BAR, ChartType.LINE, ChartType.AREA, ChartType.SCATTER -> {
                    val step = plotW.toDouble() / dataPoints.size.coerceAtLeast(1)
                    val index = ((p.x - padding) / step).toInt()
                    val pt = dataPoints.getOrNull(index)
                    if (pt != null) "Data point: ${pt.first} = ${String.format(Locale.US, "%.2f", pt.second)}" else null
                }
                ChartType.HISTOGRAM -> {
                    "Histogram Distribution: ${rawYValues.size} samples"
                }
                ChartType.BOX -> {
                    if (rawYValues.isNotEmpty()) {
                        val sorted = rawYValues.sorted()
                        val n = sorted.size
                        val q1 = sorted[n / 4]
                        val med = sorted[n / 2]
                        val q3 = sorted[(3 * n) / 4]
                        "Box Plot: Min=${sorted.first()}, Q1=$q1, Median=$med, Q3=$q3, Max=${sorted.last()}"
                    } else null
                }
                ChartType.DONUT -> {
                    "Donut Slice: Category distribution"
                }
                ChartType.CORRELATION -> {
                    "Correlation Matrix: Pairwise Pearson coefficients"
                }
            }
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

            val w = width
            val h = height
            g2.color = Color(250, 250, 252)
            g2.fillRect(0, 0, w, h)

            if (chartType == ChartType.CORRELATION) {
                paintCorrelationMatrix(g2, w, h)
                return
            }

            if (chartType == ChartType.DONUT) {
                paintDonutChart(g2, w, h)
                return
            }

            if (chartType == ChartType.BOX) {
                paintBoxPlot(g2, w, h)
                return
            }

            if (chartType == ChartType.HISTOGRAM) {
                paintHistogram(g2, w, h)
                return
            }

            if (dataPoints.isEmpty()) {
                g2.color = Color(148, 163, 184)
                g2.font = g2.font.deriveFont(Font.PLAIN, 14f)
                val msg = "No numeric data available for selected columns"
                val fm = g2.fontMetrics
                g2.drawString(msg, (w - fm.stringWidth(msg)) / 2, h / 2)
                return
            }

            val padding = 60
            val plotX = padding
            val plotY = padding
            val plotW = w - (padding * 2)
            val plotH = h - (padding * 2)

            val maxY = dataPoints.maxOfOrNull { it.second } ?: 1.0
            val minY = (dataPoints.minOfOrNull { it.second } ?: 0.0).coerceAtMost(0.0)
            val rangeY = (maxY - minY).coerceAtLeast(0.0001)

            // Grid Lines & Ticks
            g2.color = Color(226, 232, 240)
            val gridTicks = 5
            for (i in 0..gridTicks) {
                val yPos = plotY + plotH - (i * (plotH / gridTicks))
                g2.drawLine(plotX, yPos, plotX + plotW, yPos)

                val tickVal = minY + (i * (rangeY / gridTicks))
                g2.color = Color(100, 116, 139)
                g2.font = g2.font.deriveFont(Font.PLAIN, 10f)
                g2.drawString(String.format(Locale.US, "%.1f", tickVal), plotX - 45, yPos + 4)
                g2.color = Color(226, 232, 240)
            }

            // Axes
            g2.color = Color(71, 85, 105)
            g2.stroke = BasicStroke(1.5f)
            g2.drawLine(plotX, plotY, plotX, plotY + plotH)
            g2.drawLine(plotX, plotY + plotH, plotX + plotW, plotY + plotH)

            // Axis labels
            g2.font = g2.font.deriveFont(Font.BOLD, 11f)
            g2.drawString(xName, plotX + (plotW / 2) - 20, plotY + plotH + 35)
            g2.drawString(yName, plotX - 45, plotY - 15)

            when (chartType) {
                ChartType.BAR -> {
                    val barWidth = ((plotW.toDouble() / dataPoints.size) * 0.7).coerceAtLeast(3.0)
                    val step = plotW.toDouble() / dataPoints.size

                    for ((idx, pt) in dataPoints.withIndex()) {
                        val barH = ((pt.second - minY) / rangeY * plotH).coerceAtLeast(1.0)
                        val xPos = plotX + (idx * step) + (step - barWidth) / 2
                        val yPos = plotY + plotH - barH

                        val gradient = GradientPaint(
                            xPos.toFloat(), yPos.toFloat(), Color(37, 99, 235),
                            xPos.toFloat(), (yPos + barH).toFloat(), Color(14, 165, 233)
                        )
                        g2.paint = gradient
                        g2.fill(Rectangle2D.Double(xPos, yPos, barWidth, barH))
                        g2.color = Color(29, 78, 216)
                        g2.draw(Rectangle2D.Double(xPos, yPos, barWidth, barH))
                    }
                }
                ChartType.LINE -> {
                    val step = plotW.toDouble() / dataPoints.size.coerceAtLeast(1)
                    var prevX = 0.0
                    var prevY = 0.0

                    g2.color = Color(37, 99, 235)
                    g2.stroke = BasicStroke(2.2f)

                    for ((idx, pt) in dataPoints.withIndex()) {
                        val xPos = plotX + (idx * step) + (step / 2)
                        val yPos = plotY + plotH - ((pt.second - minY) / rangeY * plotH)

                        if (idx > 0) {
                            g2.drawLine(prevX.toInt(), prevY.toInt(), xPos.toInt(), yPos.toInt())
                        }
                        prevX = xPos
                        prevY = yPos

                        g2.color = Color(14, 165, 233)
                        g2.fill(Ellipse2D.Double(xPos - 3, yPos - 3, 6.0, 6.0))
                        g2.color = Color(37, 99, 235)
                    }
                }
                ChartType.AREA -> {
                    val step = plotW.toDouble() / dataPoints.size.coerceAtLeast(1)
                    val path = Path2D.Double()
                    path.moveTo(plotX.toDouble(), (plotY + plotH).toDouble())

                    for ((idx, pt) in dataPoints.withIndex()) {
                        val xPos = plotX + (idx * step) + (step / 2)
                        val yPos = plotY + plotH - ((pt.second - minY) / rangeY * plotH)
                        path.lineTo(xPos, yPos)
                    }
                    path.lineTo((plotX + plotW).toDouble(), (plotY + plotH).toDouble())
                    path.closePath()

                    val areaGradient = GradientPaint(
                        plotX.toFloat(), plotY.toFloat(), Color(37, 99, 235, 140),
                        plotX.toFloat(), (plotY + plotH).toFloat(), Color(14, 165, 233, 20)
                    )
                    g2.paint = areaGradient
                    g2.fill(path)

                    g2.color = Color(29, 78, 216)
                    g2.stroke = BasicStroke(2f)
                    g2.draw(path)
                }
                ChartType.SCATTER -> {
                    val step = plotW.toDouble() / dataPoints.size.coerceAtLeast(1)
                    g2.color = Color(225, 29, 72)

                    for ((idx, pt) in dataPoints.withIndex()) {
                        val xPos = plotX + (idx * step) + (step / 2)
                        val yPos = plotY + plotH - ((pt.second - minY) / rangeY * plotH)
                        g2.fill(Ellipse2D.Double(xPos - 4, yPos - 4, 8.0, 8.0))
                        g2.color = Color(159, 18, 57)
                        g2.draw(Ellipse2D.Double(xPos - 4, yPos - 4, 8.0, 8.0))
                        g2.color = Color(225, 29, 72)
                    }
                }
                else -> {}
            }
        }

        private fun paintHistogram(g2: Graphics2D, w: Int, h: Int) {
            if (rawYValues.isEmpty()) return
            val padding = 60
            val plotX = padding
            val plotY = padding
            val plotW = w - (padding * 2)
            val plotH = h - (padding * 2)

            val minVal = rawYValues.minOrNull() ?: 0.0
            val maxVal = rawYValues.maxOrNull() ?: 1.0
            val binCount = 12
            val binWidth = (maxVal - minVal) / binCount.coerceAtLeast(1)

            val bins = IntArray(binCount)
            for (v in rawYValues) {
                val idx = if (binWidth > 0) {
                    ((v - minVal) / binWidth).toInt().coerceIn(0, binCount - 1)
                } else 0
                bins[idx]++
            }

            val maxFreq = (bins.maxOrNull() ?: 1).coerceAtLeast(1)

            // Axes
            g2.color = Color(71, 85, 105)
            g2.stroke = BasicStroke(1.5f)
            g2.drawLine(plotX, plotY, plotX, plotY + plotH)
            g2.drawLine(plotX, plotY + plotH, plotX + plotW, plotY + plotH)

            val barW = plotW.toDouble() / binCount
            for (i in 0 until binCount) {
                val freq = bins[i]
                val barH = (freq.toDouble() / maxFreq) * plotH
                val xPos = plotX + (i * barW)
                val yPos = plotY + plotH - barH

                val gp = GradientPaint(
                    xPos.toFloat(), yPos.toFloat(), Color(16, 185, 129),
                    xPos.toFloat(), (yPos + barH).toFloat(), Color(5, 150, 105)
                )
                g2.paint = gp
                g2.fill(Rectangle2D.Double(xPos + 2, yPos, barW - 4, barH))
                g2.color = Color(4, 120, 87)
                g2.draw(Rectangle2D.Double(xPos + 2, yPos, barW - 4, barH))

                if (freq > 0) {
                    g2.font = g2.font.deriveFont(Font.PLAIN, 10f)
                    g2.drawString("$freq", (xPos + (barW / 2) - 4).toInt(), (yPos - 4).toInt())
                }
            }

            g2.color = Color(51, 65, 85)
            g2.font = g2.font.deriveFont(Font.BOLD, 11f)
            g2.drawString("Frequency Distribution ($yName)", plotX + (plotW / 2) - 80, plotY - 15)
        }

        private fun paintBoxPlot(g2: Graphics2D, w: Int, h: Int) {
            if (rawYValues.isEmpty()) return
            val sorted = rawYValues.sorted()
            val n = sorted.size
            val minVal = sorted.first()
            val maxVal = sorted.last()
            val q1 = sorted[n / 4]
            val med = sorted[n / 2]
            val q3 = sorted[(3 * n) / 4]
            val range = (maxVal - minVal).coerceAtLeast(0.0001)

            val padding = 80
            val plotX = padding
            val plotY = padding
            val plotH = h - (padding * 2)

            fun toY(v: Double): Int = (plotY + plotH - ((v - minVal) / range * plotH)).toInt()

            val boxCenterX = w / 2
            val boxWidth = 100

            val yMin = toY(minVal)
            val yQ1 = toY(q1)
            val yMed = toY(med)
            val yQ3 = toY(q3)
            val yMax = toY(maxVal)

            // Whiskers (vertical line)
            g2.color = Color(71, 85, 105)
            g2.stroke = BasicStroke(2f)
            g2.drawLine(boxCenterX, yMax, boxCenterX, yQ3)
            g2.drawLine(boxCenterX, yQ1, boxCenterX, yMin)

            // Whisker caps
            g2.drawLine(boxCenterX - 30, yMax, boxCenterX + 30, yMax)
            g2.drawLine(boxCenterX - 30, yMin, boxCenterX + 30, yMin)

            // Box (Q1 to Q3)
            val boxH = (yQ1 - yQ3).coerceAtLeast(4)
            val boxRect = Rectangle2D.Double((boxCenterX - (boxWidth / 2)).toDouble(), yQ3.toDouble(), boxWidth.toDouble(), boxH.toDouble())
            g2.paint = GradientPaint(boxCenterX.toFloat(), yQ3.toFloat(), Color(199, 210, 254), boxCenterX.toFloat(), yQ1.toFloat(), Color(224, 231, 255))
            g2.fill(boxRect)
            g2.color = Color(79, 70, 229)
            g2.draw(boxRect)

            // Median line
            g2.color = Color(185, 28, 28)
            g2.stroke = BasicStroke(3f)
            g2.drawLine(boxCenterX - (boxWidth / 2), yMed, boxCenterX + (boxWidth / 2), yMed)

            // Labels
            g2.color = Color(30, 41, 59)
            g2.font = g2.font.deriveFont(Font.BOLD, 11f)
            val labelX = boxCenterX + (boxWidth / 2) + 20
            g2.drawString("Max: ${String.format(Locale.US, "%.2f", maxVal)}", labelX, yMax + 4)
            g2.drawString("Q3: ${String.format(Locale.US, "%.2f", q3)}", labelX, yQ3 + 4)
            g2.drawString("Median: ${String.format(Locale.US, "%.2f", med)}", labelX, yMed + 4)
            g2.drawString("Q1: ${String.format(Locale.US, "%.2f", q1)}", labelX, yQ1 + 4)
            g2.drawString("Min: ${String.format(Locale.US, "%.2f", minVal)}", labelX, yMin + 4)
        }

        private fun paintDonutChart(g2: Graphics2D, w: Int, h: Int) {
            val counts = mutableMapOf<String, Int>()
            for (pt in dataPoints) {
                counts[pt.first] = (counts[pt.first] ?: 0) + 1
            }
            if (counts.isEmpty()) return

            val topCategories = counts.entries.sortedByDescending { it.value }.take(6)
            val total = topCategories.sumOf { it.value }.coerceAtLeast(1)

            val centerX = w / 3
            val centerY = h / 2
            val outerRadius = minOf(w / 3, h / 3).coerceAtLeast(60)
            val innerRadius = (outerRadius * 0.55).toInt()

            val colors = arrayOf(
                Color(37, 99, 235), Color(16, 185, 129), Color(245, 158, 11),
                Color(239, 68, 68), Color(139, 92, 246), Color(236, 72, 153)
            )

            var curAngle = 0.0
            for ((idx, entry) in topCategories.withIndex()) {
                val sliceAngle = (entry.value.toDouble() / total) * 360.0
                g2.color = colors[idx % colors.size]
                val arc = Arc2D.Double(
                    (centerX - outerRadius).toDouble(), (centerY - outerRadius).toDouble(),
                    (outerRadius * 2).toDouble(), (outerRadius * 2).toDouble(),
                    curAngle, sliceAngle, Arc2D.PIE
                )
                g2.fill(arc)
                curAngle += sliceAngle
            }

            // Cutout donut center
            g2.color = Color(250, 250, 252)
            g2.fill(Ellipse2D.Double(
                (centerX - innerRadius).toDouble(), (centerY - innerRadius).toDouble(),
                (innerRadius * 2).toDouble(), (innerRadius * 2).toDouble()
            ))

            // Legend on right
            val legendX = (w * 0.6).toInt()
            var legendY = (h / 2) - (topCategories.size * 12)
            g2.font = g2.font.deriveFont(Font.PLAIN, 11f)

            for ((idx, entry) in topCategories.withIndex()) {
                val pct = String.format(Locale.US, "%.1f%%", (entry.value.toDouble() / total) * 100.0)
                g2.color = colors[idx % colors.size]
                g2.fillRect(legendX, legendY, 12, 12)
                g2.color = Color(30, 41, 59)
                g2.drawString("${entry.key}: $pct (${entry.value})", legendX + 20, legendY + 11)
                legendY += 24
            }
        }

        private fun paintCorrelationMatrix(g2: Graphics2D, w: Int, h: Int) {
            val numCols = currentDf.columns.filter { it.isNumeric }.take(8)
            if (numCols.size < 2) {
                g2.color = Color(148, 163, 184)
                g2.font = g2.font.deriveFont(Font.PLAIN, 13f)
                g2.drawString("At least 2 numeric columns required for correlation matrix", w / 4, h / 2)
                return
            }

            val n = numCols.size
            val padding = 90
            val cellSize = minOf((w - padding * 2) / n, (h - padding * 2) / n).coerceIn(28, 64)

            // Column vectors
            val vectors = numCols.map { col ->
                val idx = currentDf.getColumnIndex(col.name)
                currentDf.rows.mapNotNull { r ->
                    val v = r.getOrNull(idx)
                    when (v) {
                        is Number -> v.toDouble()
                        is String -> v.toDoubleOrNull()
                        else -> null
                    }
                }
            }

            // Compute correlations
            for (i in 0 until n) {
                for (j in 0 until n) {
                    val r = computePearson(vectors[i], vectors[j])
                    val cellX = padding + (j * cellSize)
                    val cellY = padding + (i * cellSize)

                    // Color: Red (-1.0) -> White (0.0) -> Blue (+1.0)
                    val color = when {
                        r > 0 -> blendColor(Color(255, 255, 255), Color(37, 99, 235), r.toFloat())
                        r < 0 -> blendColor(Color(255, 255, 255), Color(225, 29, 72), (-r).toFloat())
                        else -> Color(255, 255, 255)
                    }

                    g2.color = color
                    g2.fillRect(cellX, cellY, cellSize, cellSize)
                    g2.color = Color(226, 232, 240)
                    g2.drawRect(cellX, cellY, cellSize, cellSize)

                    g2.color = if (Math.abs(r) > 0.5) Color(255, 255, 255) else Color(30, 41, 59)
                    g2.font = g2.font.deriveFont(Font.BOLD, 10f)
                    val str = String.format(Locale.US, "%+.2f", r)
                    val fm = g2.fontMetrics
                    g2.drawString(str, cellX + (cellSize - fm.stringWidth(str)) / 2, cellY + (cellSize / 2) + 4)
                }

                // Row label
                g2.color = Color(51, 65, 85)
                g2.font = g2.font.deriveFont(Font.BOLD, 10f)
                val rowName = numCols[i].name.take(10)
                g2.drawString(rowName, padding - 70, padding + (i * cellSize) + (cellSize / 2) + 4)

                // Col label
                val colName = numCols[i].name.take(10)
                g2.drawString(colName, padding + (i * cellSize) + 4, padding - 10)
            }
        }

        private fun computePearson(x: List<Double>, y: List<Double>): Double {
            val len = minOf(x.size, y.size)
            if (len < 2) return 0.0
            val meanX = x.take(len).average()
            val meanY = y.take(len).average()

            var num = 0.0
            var denX = 0.0
            var denY = 0.0

            for (i in 0 until len) {
                val dx = x[i] - meanX
                val dy = y[i] - meanY
                num += dx * dy
                denX += dx * dx
                denY += dy * dy
            }

            val denom = Math.sqrt(denX * denY)
            return if (denom > 1e-9) (num / denom).coerceIn(-1.0, 1.0) else 0.0
        }

        private fun blendColor(c1: Color, c2: Color, ratio: Float): Color {
            val r = (c1.red + (c2.red - c1.red) * ratio).toInt().coerceIn(0, 255)
            val g = (c1.green + (c2.green - c1.green) * ratio).toInt().coerceIn(0, 255)
            val b = (c1.blue + (c2.blue - c1.blue) * ratio).toInt().coerceIn(0, 255)
            return Color(r, g, b)
        }
    }

    private class ImageTransferable(private val image: Image) : Transferable {
        override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.imageFlavor)
        override fun isDataFlavorSupported(flavor: DataFlavor?): Boolean = flavor == DataFlavor.imageFlavor
        override fun getTransferData(flavor: DataFlavor?): Any {
            if (flavor != DataFlavor.imageFlavor) throw UnsupportedFlavorException(flavor)
            return image
        }
    }
}
