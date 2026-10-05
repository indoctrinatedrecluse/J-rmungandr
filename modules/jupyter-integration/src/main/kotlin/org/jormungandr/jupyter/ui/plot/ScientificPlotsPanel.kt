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

package org.jormungandr.jupyter.ui.plot

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.content.ContentFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.jormungandr.core.plot.PlotItem
import org.jormungandr.core.plot.PlotManagerService
import org.jormungandr.core.plot.WebPlotItem
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

/**
 * Unified Scientific Visualizations Panel coordinating both:
 * 1. Static raster figures (Matplotlib, Seaborn, DataFrame 2D charts) with zoom/pan and clipboard copy.
 * 2. Interactive Web & 3D visualizations (Plotly 3D scatter/mesh, Altair / Vega-Lite, Folium / Leaflet maps)
 *    rendered natively via Chromium ([WebPlotViewComponent]) with external browser preview.
 */
class ScientificPlotsPanel : JPanel(BorderLayout()) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val plotService = PlotManagerService.getInstance()

    private val tabbedPane = JBTabbedPane()

    // --- Tab 1: Static Figures State & UI ---
    private val staticTitleLabel = JBLabel("No active plots")
    private val staticPrevBtn = JButton("⮜").apply { toolTipText = "Previous Plot"; isFocusable = false }
    private val staticNextBtn = JButton("⮞").apply { toolTipText = "Next Plot"; isFocusable = false }
    private val zoomInBtn = JButton("+").apply { toolTipText = "Zoom In"; isFocusable = false }
    private val zoomOutBtn = JButton("-").apply { toolTipText = "Zoom Out"; isFocusable = false }
    private val zoom100Btn = JButton("1:1").apply { toolTipText = "Original Size (100%)"; isFocusable = false }
    private val zoomFitBtn = JButton("⤢").apply { toolTipText = "Fit to Viewport"; isFocusable = false }
    private val staticCopyBtn = JButton("📋 Copy").apply { toolTipText = "Copy Image to Clipboard"; isFocusable = false }
    private val staticSaveBtn = JButton("💾 Save").apply { toolTipText = "Save Plot Image"; isFocusable = false }
    private val staticClearBtn = JButton("🧹 Clear").apply { toolTipText = "Clear Static Plots"; isFocusable = false }

    private val staticCanvas = InteractivePlotCanvas()
    private val staticThumbnailsContainer = JPanel()
    private var allStaticPlots: List<PlotItem> = emptyList()
    private var activeStaticPlot: PlotItem? = null

    // --- Tab 2: Interactive 3D & Web State & UI ---
    private val webPlotView = WebPlotViewComponent()
    private val webThumbnailsContainer = JPanel()
    private val webClearBtn = JButton("🧹 Clear All").apply { toolTipText = "Clear All Interactive Plots"; isFocusable = false }
    private var allWebPlots: List<WebPlotItem> = emptyList()
    private var activeWebPlot: WebPlotItem? = null

    init {
        val staticPanel = createStaticFiguresPanel()
        val webPanel = createWebFiguresPanel()
        val mlStudioPanel = org.jormungandr.core.ml.ui.MlExperimentStudioPanel()

        tabbedPane.addTab("🖼️ Static Figures", staticPanel)
        tabbedPane.addTab("🌐 Interactive 3D & Web", webPanel)
        tabbedPane.addTab("🧪 ML Experiment Studio", mlStudioPanel)

        add(tabbedPane, BorderLayout.CENTER)

        setupStaticListeners()
        setupWebListeners()
        observePlots()
    }

    private fun createStaticFiguresPanel(): JComponent {
        val panel = JPanel(BorderLayout()).apply { background = Color(245, 247, 250) }

        val toolbar = JPanel(BorderLayout()).apply {
            background = Color(248, 250, 252)
            border = CompoundBorder(LineBorder(Color(226, 232, 240), 1), EmptyBorder(4, 8, 4, 8))
        }

        val leftTools = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply { isOpaque = false }
        leftTools.add(staticPrevBtn)
        leftTools.add(staticNextBtn)
        leftTools.add(Box.createHorizontalStrut(8))
        leftTools.add(zoomInBtn)
        leftTools.add(zoomOutBtn)
        leftTools.add(zoom100Btn)
        leftTools.add(zoomFitBtn)
        leftTools.add(Box.createHorizontalStrut(8))
        leftTools.add(staticCopyBtn)
        leftTools.add(staticSaveBtn)
        leftTools.add(staticClearBtn)

        val rightTools = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply { isOpaque = false }
        staticTitleLabel.font = staticTitleLabel.font.deriveFont(Font.BOLD, 11f)
        rightTools.add(staticTitleLabel)

        toolbar.add(leftTools, BorderLayout.WEST)
        toolbar.add(rightTools, BorderLayout.EAST)
        panel.add(toolbar, BorderLayout.NORTH)

        staticThumbnailsContainer.layout = BoxLayout(staticThumbnailsContainer, BoxLayout.Y_AXIS)
        staticThumbnailsContainer.background = Color(241, 245, 249)
        staticThumbnailsContainer.border = EmptyBorder(6, 6, 6, 6)

        val thumbScroll = JBScrollPane(staticThumbnailsContainer).apply {
            preferredSize = Dimension(160, 400)
            border = LineBorder(Color(226, 232, 240), 1)
            verticalScrollBar.unitIncrement = 16
        }

        val split = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, thumbScroll, staticCanvas).apply {
            resizeWeight = 0.2
            isContinuousLayout = true
            border = null
        }
        panel.add(split, BorderLayout.CENTER)

        return panel
    }

    private fun createWebFiguresPanel(): JComponent {
        val panel = JPanel(BorderLayout()).apply { background = Color(245, 247, 250) }

        val toolbar = JPanel(BorderLayout()).apply {
            background = Color(248, 250, 252)
            border = CompoundBorder(LineBorder(Color(226, 232, 240), 1), EmptyBorder(4, 8, 4, 8))
        }

        val leftTools = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply { isOpaque = false }
        val hintLabel = JBLabel("Plotly 3D · Altair / Vega · Folium Maps · Standalone HTML5").apply {
            font = font.deriveFont(Font.BOLD, 11f)
            foreground = Color(71, 85, 105)
        }
        leftTools.add(hintLabel)

        val rightTools = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply { isOpaque = false }
        rightTools.add(webClearBtn)

        toolbar.add(leftTools, BorderLayout.WEST)
        toolbar.add(rightTools, BorderLayout.EAST)
        panel.add(toolbar, BorderLayout.NORTH)

        webThumbnailsContainer.layout = BoxLayout(webThumbnailsContainer, BoxLayout.Y_AXIS)
        webThumbnailsContainer.background = Color(241, 245, 249)
        webThumbnailsContainer.border = EmptyBorder(6, 6, 6, 6)

        val thumbScroll = JBScrollPane(webThumbnailsContainer).apply {
            preferredSize = Dimension(180, 400)
            border = LineBorder(Color(226, 232, 240), 1)
            verticalScrollBar.unitIncrement = 16
        }

        val split = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, thumbScroll, webPlotView).apply {
            resizeWeight = 0.22
            isContinuousLayout = true
            border = null
        }
        panel.add(split, BorderLayout.CENTER)

        return panel
    }

    private fun setupStaticListeners() {
        staticPrevBtn.addActionListener {
            val idx = allStaticPlots.indexOf(activeStaticPlot)
            if (idx > 0) plotService.selectPlot(allStaticPlots[idx - 1])
        }
        staticNextBtn.addActionListener {
            val idx = allStaticPlots.indexOf(activeStaticPlot)
            if (idx >= 0 && idx < allStaticPlots.size - 1) plotService.selectPlot(allStaticPlots[idx + 1])
        }
        zoomInBtn.addActionListener { staticCanvas.zoom(1.25) }
        zoomOutBtn.addActionListener { staticCanvas.zoom(0.8) }
        zoom100Btn.addActionListener { staticCanvas.resetZoom() }
        zoomFitBtn.addActionListener { staticCanvas.fitToViewport() }

        staticCopyBtn.addActionListener {
            val img = activeStaticPlot?.image ?: return@addActionListener
            Toolkit.getDefaultToolkit().systemClipboard.setContents(ImageTransferable(img), null)
        }

        staticSaveBtn.addActionListener {
            val img = activeStaticPlot?.image ?: return@addActionListener
            val chooser = JFileChooser()
            chooser.selectedFile = File("plot_${System.currentTimeMillis()}.png")
            if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                runCatching {
                    ImageIO.write(img, "png", chooser.selectedFile)
                }
            }
        }

        staticClearBtn.addActionListener {
            plotService.clearAll()
        }
    }

    private fun setupWebListeners() {
        webClearBtn.addActionListener {
            plotService.clearAllWebPlots()
        }
    }

    private fun observePlots() {
        // Observe static raster plots
        scope.launch {
            plotService.plots.collect { plots ->
                val prevCount = allStaticPlots.size
                allStaticPlots = plots
                rebuildStaticThumbnails()
                updateStaticNavigationState()
                tabbedPane.setTitleAt(0, "🖼️ Static Figures (${plots.size})")
                if (plots.size > prevCount && plots.isNotEmpty()) {
                    tabbedPane.selectedIndex = 0
                }
            }
        }

        scope.launch {
            plotService.activePlot.collect { plot ->
                activeStaticPlot = plot
                staticCanvas.setImage(plot?.image)
                updateStaticNavigationState()
            }
        }

        // Observe interactive web & 3D plots
        scope.launch {
            plotService.webPlots.collect { webPlots ->
                val prevCount = allWebPlots.size
                allWebPlots = webPlots
                rebuildWebThumbnails()
                tabbedPane.setTitleAt(1, "🌐 Interactive 3D & Web (${webPlots.size})")
                if (webPlots.size > prevCount && webPlots.isNotEmpty()) {
                    tabbedPane.selectedIndex = 1
                }
            }
        }

        scope.launch {
            plotService.activeWebPlot.collect { plot ->
                activeWebPlot = plot
                webPlotView.updatePlot(plot)
                rebuildWebThumbnails()
            }
        }
    }

    private fun updateStaticNavigationState() {
        val count = allStaticPlots.size
        val idx = allStaticPlots.indexOf(activeStaticPlot)

        staticPrevBtn.isEnabled = idx > 0
        staticNextBtn.isEnabled = idx >= 0 && idx < count - 1
        staticCopyBtn.isEnabled = activeStaticPlot != null
        staticSaveBtn.isEnabled = activeStaticPlot != null
        staticClearBtn.isEnabled = count > 0

        if (activeStaticPlot != null) {
            val p = activeStaticPlot!!
            val time = SimpleDateFormat("HH:mm:ss").format(Date(p.timestamp))
            staticTitleLabel.text = "Plot ${idx + 1} of $count | ${p.source} (${p.width}×${p.height}) | $time"
        } else {
            staticTitleLabel.text = "No active plots"
        }
    }

    private fun rebuildStaticThumbnails() {
        staticThumbnailsContainer.removeAll()
        for ((idx, plot) in allStaticPlots.withIndex()) {
            val thumbCard = JPanel(BorderLayout(4, 4)).apply {
                maximumSize = Dimension(150, 110)
                preferredSize = Dimension(140, 100)
                background = if (plot == activeStaticPlot) Color(224, 231, 255) else Color(255, 255, 255)
                border = CompoundBorder(
                    LineBorder(if (plot == activeStaticPlot) Color(99, 102, 241) else Color(203, 213, 225), 1),
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

            staticThumbnailsContainer.add(thumbCard)
            staticThumbnailsContainer.add(Box.createVerticalStrut(6))
        }

        staticThumbnailsContainer.revalidate()
        staticThumbnailsContainer.repaint()
    }

    private fun rebuildWebThumbnails() {
        webThumbnailsContainer.removeAll()
        for ((idx, plot) in allWebPlots.withIndex()) {
            val thumbCard = JPanel(BorderLayout(4, 4)).apply {
                maximumSize = Dimension(170, 70)
                preferredSize = Dimension(160, 65)
                background = if (plot == activeWebPlot) Color(224, 231, 255) else Color(255, 255, 255)
                border = CompoundBorder(
                    LineBorder(if (plot == activeWebPlot) Color(99, 102, 241) else Color(203, 213, 225), 1),
                    EmptyBorder(4, 6, 4, 6)
                )
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            }

            val badge = JLabel("${plot.plotType.badgeIcon} ${plot.plotType.displayName}").apply {
                font = font.deriveFont(Font.BOLD, 10f)
                foreground = Color(30, 64, 175)
            }
            val title = JLabel("#${idx + 1} ${plot.title.take(18)}").apply {
                font = font.deriveFont(Font.PLAIN, 11f)
                foreground = Color(15, 23, 42)
            }
            val time = SimpleDateFormat("HH:mm:ss").format(Date(plot.timestamp))
            val meta = JLabel("$time · ${plot.source}").apply {
                font = font.deriveFont(Font.PLAIN, 9f)
                foreground = Color(100, 116, 139)
            }

            val content = JPanel(GridLayout(3, 1, 2, 2)).apply {
                isOpaque = false
                add(badge)
                add(title)
                add(meta)
            }

            thumbCard.add(content, BorderLayout.CENTER)

            thumbCard.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent?) {
                    plotService.selectWebPlot(plot)
                }
            })

            webThumbnailsContainer.add(thumbCard)
            webThumbnailsContainer.add(Box.createVerticalStrut(6))
        }

        webThumbnailsContainer.revalidate()
        webThumbnailsContainer.repaint()
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

            g2.color = Color(226, 232, 240)
            g2.fillRect(offsetX.toInt() + 4, offsetY.toInt() + 4, renderW, renderH)

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
