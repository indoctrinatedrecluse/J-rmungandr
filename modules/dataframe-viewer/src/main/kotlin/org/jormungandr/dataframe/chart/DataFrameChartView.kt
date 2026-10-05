package org.jormungandr.dataframe.chart

import com.intellij.ui.components.JBLabel
import org.jormungandr.dataframe.model.DataFrame
import org.jormungandr.dataframe.model.DataTypeCategory
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Ellipse2D
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.*
import javax.swing.border.EmptyBorder

enum class ChartType(val displayName: String) {
    BAR("Bar Chart"),
    LINE("Line Chart"),
    SCATTER("Scatter Plot"),
    HISTOGRAM("Histogram")
}

/**
 * High-performance 2D vector charting component for Jörmungandr DataFrame Viewer.
 * Renders anti-aliased charts, gridlines, axis ticks, tooltips, and image exports.
 */
class DataFrameChartView(
    private var dataFrame: DataFrame = DataFrame.empty()
) : JPanel(BorderLayout()) {

    private val chartTypeCombo = JComboBox(ChartType.values())
    private val xAxisCombo = JComboBox<String>()
    private val yAxisCombo = JComboBox<String>()
    private val exportBtn = JButton("📸 Export PNG")

    private val canvas = ChartCanvas()
    private val hoverLabel = JBLabel("Hover over chart elements to inspect values").apply {
        font = font.deriveFont(Font.PLAIN, 11f)
        foreground = Color(120, 120, 120)
    }

    init {
        val controls = JPanel(FlowLayout(FlowLayout.LEFT, 8, 4)).apply {
            border = EmptyBorder(4, 6, 4, 6)
        }

        controls.add(JBLabel("Type:"))
        controls.add(chartTypeCombo)
        controls.add(JBLabel("X-Axis:"))
        controls.add(xAxisCombo)
        controls.add(JBLabel("Y-Axis:"))
        controls.add(yAxisCombo)
        controls.add(exportBtn)

        val bottomBar = JPanel(BorderLayout()).apply {
            border = EmptyBorder(4, 8, 4, 8)
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
                    val img = BufferedImage(canvas.width.coerceAtLeast(400), canvas.height.coerceAtLeast(300), BufferedImage.TYPE_INT_ARGB)
                    val g = img.createGraphics()
                    canvas.paint(g)
                    g.dispose()
                    ImageIO.write(img, "png", fileChooser.selectedFile)
                }
            }
        }

        refreshColumns()
    }

    fun setDataFrame(df: DataFrame) {
        this.dataFrame = df
        refreshColumns()
    }

    private fun refreshColumns() {
        val prevX = xAxisCombo.selectedItem as? String
        val prevY = yAxisCombo.selectedItem as? String

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
            }
        }

        canvas.setData(type, xCol, yCol, points)
        canvas.repaint()
    }

    private inner class ChartCanvas : JComponent() {
        private var chartType: ChartType = ChartType.BAR
        private var xName: String = ""
        private var yName: String = ""
        private var dataPoints: List<Pair<String, Double>> = emptyList()

        init {
            preferredSize = Dimension(600, 360)
            addMouseMotionListener(object : MouseAdapter() {
                override fun mouseMoved(e: MouseEvent) {
                    val hit = findHitPoint(e.point)
                    if (hit != null) {
                        hoverLabel.text = "Data point: ${hit.first} = ${"%.2f".format(hit.second)}"
                    }
                }
            })
        }

        fun setData(type: ChartType, x: String, y: String, points: List<Pair<String, Double>>) {
            this.chartType = type
            this.xName = x
            this.yName = y
            this.dataPoints = points
        }

        private fun findHitPoint(p: Point): Pair<String, Double>? {
            if (dataPoints.isEmpty()) return null
            val padding = 50
            val chartW = width - (padding * 2)
            if (chartW <= 0) return null

            val step = chartW.toDouble() / dataPoints.size.toDouble()
            val index = ((p.x - padding) / step).toInt()
            return dataPoints.getOrNull(index)
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

            val w = width
            val h = height
            g2.color = Color(250, 250, 250)
            g2.fillRect(0, 0, w, h)

            if (dataPoints.isEmpty()) {
                g2.color = Color(140, 140, 140)
                g2.font = g2.font.deriveFont(Font.PLAIN, 14f)
                g2.drawString("No numeric data available for selected columns", w / 3, h / 2)
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

            // Draw Grid Lines & Ticks
            g2.color = Color(225, 225, 225)
            val gridTicks = 5
            for (i in 0..gridTicks) {
                val yPos = plotY + plotH - (i * (plotH / gridTicks))
                g2.drawLine(plotX, yPos, plotX + plotW, yPos)

                val tickVal = minY + (i * (rangeY / gridTicks))
                g2.color = Color(120, 120, 120)
                g2.font = g2.font.deriveFont(Font.PLAIN, 10f)
                g2.drawString("%.1f".format(tickVal), plotX - 45, yPos + 4)
                g2.color = Color(225, 225, 225)
            }

            // Draw Axes
            g2.color = Color(80, 80, 80)
            g2.stroke = BasicStroke(1.5f)
            g2.drawLine(plotX, plotY, plotX, plotY + plotH)
            g2.drawLine(plotX, plotY + plotH, plotX + plotW, plotY + plotH)

            // Axis labels
            g2.font = g2.font.deriveFont(Font.BOLD, 11f)
            g2.drawString(xName, plotX + (plotW / 2) - 20, plotY + plotH + 35)
            g2.drawString(yName, plotX - 45, plotY - 15)

            // Render Plot Elements
            when (chartType) {
                ChartType.BAR, ChartType.HISTOGRAM -> {
                    val barWidth = ((plotW.toDouble() / dataPoints.size) * 0.7).coerceAtLeast(3.0)
                    val step = plotW.toDouble() / dataPoints.size

                    for ((idx, pt) in dataPoints.withIndex()) {
                        val barH = ((pt.second - minY) / rangeY * plotH).coerceAtLeast(1.0)
                        val xPos = plotX + (idx * step) + (step - barWidth) / 2
                        val yPos = plotY + plotH - barH

                        // Gradient bar
                        val gradient = GradientPaint(
                            xPos.toFloat(), yPos.toFloat(), Color(38, 139, 210),
                            xPos.toFloat(), (yPos + barH).toFloat(), Color(42, 161, 152)
                        )
                        g2.paint = gradient
                        g2.fill(Rectangle2D.Double(xPos, yPos, barWidth, barH))
                        g2.color = Color(24, 100, 150)
                        g2.draw(Rectangle2D.Double(xPos, yPos, barWidth, barH))
                    }
                }
                ChartType.LINE -> {
                    val step = plotW.toDouble() / dataPoints.size.coerceAtLeast(1)
                    var prevX = 0.0
                    var prevY = 0.0

                    g2.color = Color(38, 139, 210)
                    g2.stroke = BasicStroke(2.2f)

                    for ((idx, pt) in dataPoints.withIndex()) {
                        val xPos = plotX + (idx * step) + (step / 2)
                        val yPos = plotY + plotH - ((pt.second - minY) / rangeY * plotH)

                        if (idx > 0) {
                            g2.drawLine(prevX.toInt(), prevY.toInt(), xPos.toInt(), yPos.toInt())
                        }
                        prevX = xPos
                        prevY = yPos

                        // Node circle
                        g2.color = Color(42, 161, 152)
                        g2.fill(Ellipse2D.Double(xPos - 3, yPos - 3, 6.0, 6.0))
                        g2.color = Color(38, 139, 210)
                    }
                }
                ChartType.SCATTER -> {
                    val step = plotW.toDouble() / dataPoints.size.coerceAtLeast(1)
                    g2.color = Color(224, 33, 138)

                    for ((idx, pt) in dataPoints.withIndex()) {
                        val xPos = plotX + (idx * step) + (step / 2)
                        val yPos = plotY + plotH - ((pt.second - minY) / rangeY * plotH)
                        g2.fill(Ellipse2D.Double(xPos - 4, yPos - 4, 8.0, 8.0))
                        g2.color = Color(160, 20, 90)
                        g2.draw(Ellipse2D.Double(xPos - 4, yPos - 4, 8.0, 8.0))
                        g2.color = Color(224, 33, 138)
                    }
                }
            }
        }
    }
}
