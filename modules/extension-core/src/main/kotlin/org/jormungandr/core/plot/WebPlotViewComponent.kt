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

import com.intellij.ide.browsers.BrowserLauncher
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.io.File
import javax.swing.*
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder

/**
 * High-performance viewer component for interactive Web and 3D visual artifacts
 * (Plotly 3D scatter/mesh, Altair / Vega-Lite, Folium / Leaflet maps, etc.).
 *
 * Automatically utilizes Chromium Embedded Framework ([JBCefBrowser]) when supported
 * for zero-latency in-IDE interactivity, while providing a graceful and polished
 * fallback system (with instant one-click browser launch and HTML exports) in headless
 * or non-CEF environments.
 */
class WebPlotViewComponent(
    private var webPlot: WebPlotItem? = null,
    private val parentDisposable: Disposable? = null,
    private val compactMode: Boolean = false
) : JPanel(BorderLayout()), Disposable {

    private var cefBrowser: JBCefBrowser? = null
    private val contentHolder = JPanel(BorderLayout())
    private val headerBar = JPanel(BorderLayout())
    private val titleLabel = JBLabel("No active interactive plot")
    private val badgeLabel = JBLabel("")

    private var activeHtmlFile: File? = null

    init {
        background = Color(248, 250, 252)
        border = if (compactMode) {
            CompoundBorder(
                LineBorder(Color(226, 232, 240), 1, true),
                EmptyBorder(2, 4, 4, 4)
            )
        } else {
            null
        }

        buildHeader()
        add(headerBar, BorderLayout.NORTH)
        add(contentHolder, BorderLayout.CENTER)

        if (parentDisposable != null) {
            Disposer.register(parentDisposable, this)
        }

        updatePlot(webPlot)
    }

    private fun buildHeader() {
        headerBar.isOpaque = true
        headerBar.background = Color(241, 245, 249)
        headerBar.border = EmptyBorder(4, 8, 4, 8)

        val left = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply { isOpaque = false }
        badgeLabel.font = badgeLabel.font.deriveFont(Font.BOLD, 11f)
        badgeLabel.foreground = Color(37, 99, 235)
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 12f)
        titleLabel.foreground = Color(30, 41, 59)

        left.add(badgeLabel)
        left.add(titleLabel)
        headerBar.add(left, BorderLayout.WEST)

        val right = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply { isOpaque = false }

        val openBrowserBtn = JButton("🌐 Open in Browser").apply {
            font = font.deriveFont(Font.PLAIN, 11f)
            isFocusable = false
            toolTipText = "Open interactive figure in your default web browser"
            addActionListener { openInExternalBrowser() }
        }

        val reloadBtn = JButton("🔄 Reload").apply {
            font = font.deriveFont(Font.PLAIN, 11f)
            isFocusable = false
            toolTipText = "Reload interactive view"
            addActionListener { reload() }
        }

        val copyHtmlBtn = JButton("📋 Copy HTML").apply {
            font = font.deriveFont(Font.PLAIN, 11f)
            isFocusable = false
            toolTipText = "Copy standalone HTML code to clipboard"
            addActionListener {
                val html = webPlot?.let { WebPlotItem.ensureCompleteHtml(it.htmlContent, it.title) } ?: return@addActionListener
                val sel = StringSelection(html)
                Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, sel)
            }
        }

        val saveBtn = JButton("💾 Save").apply {
            font = font.deriveFont(Font.PLAIN, 11f)
            isFocusable = false
            toolTipText = "Export HTML visualization to disk"
            addActionListener {
                val plot = webPlot ?: return@addActionListener
                val chooser = JFileChooser()
                chooser.selectedFile = File("plot_${plot.plotType.name.lowercase()}_${System.currentTimeMillis()}.html")
                if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                    runCatching {
                        plot.exportHtmlFile(chooser.selectedFile)
                        Messages.showInfoMessage(this, "Saved interactive plot to ${chooser.selectedFile.name}", "Plot Exported")
                    }
                }
            }
        }

        right.add(openBrowserBtn)
        if (!compactMode) right.add(reloadBtn)
        right.add(copyHtmlBtn)
        right.add(saveBtn)
        headerBar.add(right, BorderLayout.EAST)
    }

    fun updatePlot(plot: WebPlotItem?) {
        this.webPlot = plot
        contentHolder.removeAll()

        if (plot == null) {
            badgeLabel.text = ""
            titleLabel.text = "No active interactive plot"
            val placeholder = JPanel(GridBagLayout()).apply {
                background = Color(248, 250, 252)
                val label = JLabel("🌐 No interactive web or 3D plots loaded yet.").apply {
                    font = font.deriveFont(Font.PLAIN, 13f)
                    foreground = Color(148, 163, 184)
                }
                add(label)
            }
            contentHolder.add(placeholder, BorderLayout.CENTER)
            revalidate()
            repaint()
            return
        }

        badgeLabel.text = "${plot.plotType.badgeIcon} ${plot.plotType.displayName}"
        titleLabel.text = plot.title

        runCatching {
            activeHtmlFile = plot.exportHtmlFile()
        }

        val renderedInJcef = tryRenderJcef(plot)
        if (!renderedInJcef) {
            renderFallbackCard(plot)
        }

        revalidate()
        repaint()
    }

    private fun tryRenderJcef(plot: WebPlotItem): Boolean {
        if (GraphicsEnvironment.isHeadless()) return false

        return runCatching {
            if (!JBCefApp.isSupported()) return@runCatching false

            val browser = cefBrowser ?: JBCefBrowser().also {
                cefBrowser = it
                Disposer.register(this, it)
            }

            val file = activeHtmlFile
            if (file != null && file.exists()) {
                browser.loadURL(file.toURI().toString())
            } else {
                browser.loadHTML(WebPlotItem.ensureCompleteHtml(plot.htmlContent, plot.title))
            }

            contentHolder.add(browser.component, BorderLayout.CENTER)
            true
        }.getOrDefault(false)
    }

    private fun renderFallbackCard(plot: WebPlotItem) {
        val card = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            background = Color(255, 255, 255)
            border = CompoundBorder(
                LineBorder(Color(226, 232, 240), 1, true),
                EmptyBorder(24, 24, 24, 24)
            )
        }

        val topRow = JPanel(FlowLayout(FlowLayout.CENTER, 8, 4)).apply { isOpaque = false }
        val iconLabel = JLabel(plot.plotType.badgeIcon).apply {
            font = font.deriveFont(Font.PLAIN, 36f)
        }
        val headingLabel = JLabel(plot.title).apply {
            font = font.deriveFont(Font.BOLD, 16f)
            foreground = Color(15, 23, 42)
        }
        topRow.add(iconLabel)
        topRow.add(headingLabel)
        card.add(topRow)

        card.add(Box.createVerticalStrut(12))

        val infoBox = JPanel(GridLayout(3, 1, 4, 4)).apply {
            isOpaque = false
            maximumSize = Dimension(450, 80)
        }
        val typeInfo = JLabel("Type: ${plot.plotType.displayName} | Source: ${plot.source}").apply {
            horizontalAlignment = SwingConstants.CENTER
            foreground = Color(71, 85, 105)
            font = font.deriveFont(Font.PLAIN, 12f)
        }
        val sizeKb = (plot.htmlContent.length / 1024.0)
        val sizeInfo = JLabel("Payload size: ${String.format("%.1f", sizeKb)} KB (Standalone HTML5)").apply {
            horizontalAlignment = SwingConstants.CENTER
            foreground = Color(100, 116, 139)
            font = font.deriveFont(Font.PLAIN, 11f)
        }
        val noteInfo = JLabel("Click below to interact with 3D rotation, zooming, and full responsive maps:").apply {
            horizontalAlignment = SwingConstants.CENTER
            foreground = Color(30, 41, 59)
            font = font.deriveFont(Font.BOLD, 12f)
        }
        infoBox.add(typeInfo)
        infoBox.add(sizeInfo)
        infoBox.add(noteInfo)
        card.add(infoBox)

        card.add(Box.createVerticalStrut(16))

        val btnRow = JPanel(FlowLayout(FlowLayout.CENTER, 12, 0)).apply { isOpaque = false }
        val bigBrowserBtn = JButton("🌐 Open Interactive View in Browser").apply {
            font = font.deriveFont(Font.BOLD, 13f)
            foreground = Color(255, 255, 255)
            background = Color(37, 99, 235)
            isOpaque = true
            border = EmptyBorder(8, 16, 8, 16)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addActionListener { openInExternalBrowser() }
        }
        btnRow.add(bigBrowserBtn)
        card.add(btnRow)

        val wrapper = JPanel(GridBagLayout()).apply {
            background = Color(248, 250, 252)
            add(card)
        }
        contentHolder.add(wrapper, BorderLayout.CENTER)
    }

    fun openInExternalBrowser() {
        val plot = webPlot ?: return
        val file = activeHtmlFile ?: runCatching { plot.exportHtmlFile() }.getOrNull()
        if (file != null && file.exists()) {
            runCatching {
                BrowserLauncher.instance.browse(file.toURI())
            }.onFailure {
                runCatching {
                    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                        Desktop.getDesktop().browse(file.toURI())
                    }
                }
            }
        }
    }

    fun reload() {
        val plot = webPlot ?: return
        runCatching {
            activeHtmlFile = plot.exportHtmlFile()
            val file = activeHtmlFile
            if (cefBrowser != null && file != null && file.exists()) {
                cefBrowser?.loadURL(file.toURI().toString())
            } else {
                updatePlot(plot)
            }
        }
    }

    override fun dispose() {
        runCatching {
            cefBrowser?.let { Disposer.dispose(it) }
            cefBrowser = null
        }
        runCatching {
            activeHtmlFile?.delete()
            activeHtmlFile = null
        }
    }
}
