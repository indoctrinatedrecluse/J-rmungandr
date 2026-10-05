package org.jormungandr.jupyter.ui.plot

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.jormungandr.core.plot.PlotItem
import org.jormungandr.core.plot.PlotManagerService
import java.awt.*
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.event.*
import java.awt.image.BufferedImage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import javax.imageio.ImageIO
import javax.swing.*
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder

class ScientificPlotsPanel : JPanel(BorderLayout()) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val plotService = PlotManagerService.getInstance()

    private val titleLabel = JBLabel("No active plots")
    private val prevBtn = JButton("⮜").apply { toolTipText = "Previous Plot"; isFocusable = false }
    private val nextBtn = JButton("⮞").apply { toolTipText = "Next Plot"; isFocusable = false }
    private val zoomInBtn = JButton("+").apply { toolTipText = "Zoom In"; isFocusable = false }
    private val zoomOutBtn = JButton("-").apply { toolTipText = "Zoom Out"; isFocusable = false }
    private val zoom100Btn = JButton("1:1").apply { toolTipText = "Original Size (100%)"; isFocusable = false }
    private val zoomFitBtn = JButton("⤢").apply { toolTipText = "Fit to Viewport"; isFocusable = false }
    private val copyBtn = JButton("📋 Copy").apply { toolTipText = "Copy Image to Clipboard"; isFocusable = false }
    private val saveBtn = JButton("💾 Save").apply { toolTipText = "Save Plot Image"; isFocusable = false }
    private val clearBtn = JButton("🧹 Clear").apply { toolTipText = "Clear All Plots"; isFocusable = false }

    private val canvas = InteractivePlotCanvas()
    private val thumbnailsContainer = JPanel()

    private var allPlots: List<PlotItem> = emptyList()
    private var activePlot: PlotItem? = null

    init {
        background = Color(245, 247, 250)

        // Top Toolbar
        val toolbar = JPanel(BorderLayout()).apply {
            background = Color(248, 250, 252)
            border = CompoundBorder(
                LineBorder(Color(226, 232, 240), 1),
                EmptyBorder(4, 8, 4, 8)
            )
        }

        val leftTools = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply { isOpaque = false }
        leftTools.add(prevBtn)
        leftTools.add(nextBtn)
        leftTools.add(Box.createHorizontalStrut(8))
        leftTools.add(zoomInBtn)
        leftTools.add(zoomOutBtn)
        leftTools.add(zoom100Btn)
        leftTools.add(zoomFitBtn)
        leftTools.add(Box.createHorizontalStrut(8))
        leftTools.add(copyBtn)
        leftTools.add(saveBtn)
        leftTools.add(clearBtn)

        val rightTools = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply { isOpaque = false }
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 11f)
        rightTools.add(titleLabel)

        toolbar.add(leftTools, BorderLayout.WEST)
        toolbar.add(rightTools, BorderLayout.EAST)
        add(toolbar, BorderLayout.NORTH)

        // Left Thumbnails Gallery Strip
        thumbnailsContainer.layout = BoxLayout(thumbnailsContainer, BoxLayout.Y_AXIS)
        thumbnailsContainer.background = Color(241, 245, 249)
        thumbnailsContainer.border = EmptyBorder(6, 6, 6, 6)

        val thumbScroll = JBScrollPane(thumbnailsContainer).apply {
            preferredSize = Dimension(160, 400)
            border = LineBorder(Color(226, 232, 240), 1)
            verticalScrollBar.unitIncrement = 16
        }

        val split = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, thumbScroll, canvas).apply {
            resizeWeight = 0.2
            isContinuousLayout = true
            border = null
        }
        add(split, BorderLayout.CENTER)

        setupListeners()
        observePlots()
    }

    private fun setupListeners() {
        prevBtn.addActionListener {
            val idx = allPlots.indexOf(activePlot)
            if (idx > 0) plotService.selectPlot(allPlots[idx - 1])
        }
        nextBtn.addActionListener {
            val idx = allPlots.indexOf(activePlot)
            if (idx >= 0 && idx < allPlots.size - 1) plotService.selectPlot(allPlots[idx + 1])
        }
        zoomInBtn.addActionListener { canvas.zoom(1.25) }
        zoomOutBtn.addActionListener { canvas.zoom(0.8) }
        zoom100Btn.addActionListener { canvas.resetZoom() }
        zoomFitBtn.addActionListener { canvas.fitToViewport() }

        copyBtn.addActionListener {
            val img = activePlot?.image ?: return@addActionListener
            Toolkit.getDefaultToolkit().systemClipboard.setContents(ImageTransferable(img), null)
        }

        saveBtn.addActionListener {
            val img = activePlot?.image ?: return@addActionListener
            val chooser = JFileChooser()
            chooser.selectedFile = File("plot_${System.currentTimeMillis()}.png")
            if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                runCatching {
                    ImageIO.write(img, "png", chooser.selectedFile)
                }
            }
        }

        clearBtn.addActionListener {
            plotService.clearAll()
        }
    }

    private fun observePlots() {
        scope.launch {
            plotService.plots.collect { plots ->
                allPlots = plots
                rebuildThumbnails()
                updateNavigationState()
            }
        }

        scope.launch {
            plotService.activePlot.collect { plot ->
                activePlot = plot
                canvas.setImage(plot?.image)
                updateNavigationState()
            }
        }
    }

    private fun updateNavigationState() {
        val count = allPlots.size
        val idx = allPlots.indexOf(activePlot)

        prevBtn.isEnabled = idx > 0
        nextBtn.isEnabled = idx >= 0 && idx < count - 1
        copyBtn.isEnabled = activePlot != null
        saveBtn.isEnabled = activePlot != null
        clearBtn.isEnabled = count > 0

        if (activePlot != null) {
            val p = activePlot!!
            val time = SimpleDateFormat("HH:mm:ss").format(Date(p.timestamp))
            titleLabel.text = "Plot ${idx + 1} of $count | ${p.source} (${p.width}×${p.height}) | $time"
        } else {
            titleLabel.text = "No active plots"
        }
    }

    private fun rebuildThumbnails() {
        thumbnailsContainer.removeAll()
        for ((idx, plot) in allPlots.withIndex()) {
            val thumbCard = JPanel(BorderLayout(4, 4)).apply {
                maximumSize = Dimension(150, 110)
                preferredSize = Dimension(140, 100)
                background = if (plot == activePlot) Color(224, 231, 255) else Color(255, 255, 255)
                border = CompoundBorder(
                    LineBorder(if (plot == activePlot) Color(99, 102, 241) else Color(203, 213, 225), 1),
                    EmptyBorder(4, 4, 4, 4)
                )
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            }

            val scaledThumb = scaleThumbnail(plot.image, 130, 70)
            val thumbLabel = JLabel(ImageIcon(scaledThumb))
            thumbCard.add(thumbLabel, BorderLayout.CENTER)

            val caption = JLabel("#${idx + 1} ${plot.title.take(16)}").apply {
                font = font.deriveFont(Font.PLAIN, 10f)
                foreground = Color(51, 65, 85)
            }
            thumbCard.add(caption, BorderLayout.SOUTH)

            thumbCard.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent?) {
                    plotService.selectPlot(plot)
                }
            })

            thumbnailsContainer.add(thumbCard)
            thumbnailsContainer.add(Box.createVerticalStrut(6))
        }

        thumbnailsContainer.revalidate()
        thumbnailsContainer.repaint()
    }

    private fun scaleThumbnail(src: BufferedImage, maxW: Int, maxH: Int): BufferedImage {
        val ratio = minOf(maxW.toDouble() / src.width, maxH.toDouble() / src.height, 1.0)
        val w = (src.width * ratio).toInt().coerceAtLeast(1)
        val h = (src.height * ratio).toInt().coerceAtLeast(1)
        val thumb = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = thumb.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.drawImage(src, 0, 0, w, h, null)
        g.dispose()
        return thumb
    }

    private class InteractivePlotCanvas : JComponent() {
        private var image: BufferedImage? = null
        private var scale: Double = 1.0
        private var offsetX: Double = 0.0
        private var offsetY: Double = 0.0
        private var lastMousePoint: Point? = null

        init {
            background = Color(250, 250, 250)
            isOpaque = true

            addMouseWheelListener { e ->
                if (image != null) {
                    val factor = if (e.wheelRotation < 0) 1.15 else 0.85
                    zoom(factor)
                }
            }

            addMouseListener(object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    lastMousePoint = e.point
                }
                override fun mouseReleased(e: MouseEvent) {
                    lastMousePoint = null
                }
            })

            addMouseMotionListener(object : MouseMotionAdapter() {
                override fun mouseDragged(e: MouseEvent) {
                    val last = lastMousePoint
                    if (last != null && image != null) {
                        offsetX += (e.x - last.x)
                        offsetY += (e.y - last.y)
                        lastMousePoint = e.point
                        repaint()
                    }
                }
            })
        }

        fun setImage(img: BufferedImage?) {
            this.image = img
            fitToViewport()
        }

        fun zoom(factor: Double) {
            scale = (scale * factor).coerceIn(0.1, 10.0)
            repaint()
        }

        fun resetZoom() {
            scale = 1.0
            val img = image ?: return
            offsetX = ((width - img.width) / 2.0).coerceAtLeast(0.0)
            offsetY = ((height - img.height) / 2.0).coerceAtLeast(0.0)
            repaint()
        }

        fun fitToViewport() {
            val img = image
            if (img == null || width <= 0 || height <= 0) {
                scale = 1.0
                offsetX = 0.0
                offsetY = 0.0
                repaint()
                return
            }

            val scaleX = width.toDouble() / img.width.toDouble()
            val scaleY = height.toDouble() / img.height.toDouble()
            scale = minOf(scaleX, scaleY, 1.0) * 0.95

            val renderW = img.width * scale
            val renderH = img.height * scale
            offsetX = (width - renderW) / 2.0
            offsetY = (height - renderH) / 2.0
            repaint()
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2 = g as Graphics2D
            g2.color = Color(248, 250, 252)
            g2.fillRect(0, 0, width, height)

            val img = image
            if (img == null) {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = Color(148, 163, 184)
                g2.font = font.deriveFont(Font.PLAIN, 13f)
                val msg = "📊 No plots in session. Run Matplotlib (plt.show()), Seaborn, or send a chart from DataFrame Studio."
                val fm = g2.fontMetrics
                g2.drawString(msg, (width - fm.stringWidth(msg)) / 2, height / 2)
                return
            }

            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            val renderW = (img.width * scale).toInt()
            val renderH = (img.height * scale).toInt()

            // Subtle drop shadow
            g2.color = Color(226, 232, 240)
            g2.fillRect(offsetX.toInt() + 4, offsetY.toInt() + 4, renderW, renderH)

            // Draw image
            g2.drawImage(img, offsetX.toInt(), offsetY.toInt(), renderW, renderH, null)
            g2.color = Color(203, 213, 225)
            g2.drawRect(offsetX.toInt(), offsetY.toInt(), renderW, renderH)
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

class ScientificPlotsToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = ScientificPlotsPanel()
        val content = ContentFactory.getInstance().createContent(panel, "Scientific Plots", false)
        toolWindow.contentManager.addContent(content)
    }
}
