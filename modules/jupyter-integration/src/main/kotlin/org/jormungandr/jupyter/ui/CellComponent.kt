package org.jormungandr.jupyter.ui

import com.google.gson.Gson
import com.intellij.ide.browsers.BrowserLauncher
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import org.jormungandr.core.plot.WebPlotItem
import org.jormungandr.core.plot.WebPlotType
import org.jormungandr.core.theme.ThemeManager
import org.jormungandr.jupyter.model.*
import org.jormungandr.jupyter.ui.plot.WebPlotViewComponent
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.io.ByteArrayInputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.imageio.ImageIO
import javax.swing.*
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder
import javax.swing.border.MatteBorder

/**
 * UI Component rendering an individual Jupyter notebook cell:
 * - Code/Markdown editor
 * - Gutter execution count `In [1]:` / `In [*]:`
 * - Live streaming output container (stdout, stderr, rich PNG/HTML, tracebacks)
 * - Threaded cell comments and reply system
 */
class CellComponent(
    val cell: NotebookCell,
    private val project: Project,
    private val onRunRequested: (CellComponent) -> Unit,
    private val onDeleteRequested: (CellComponent) -> Unit,
    private val onMoveUpRequested: (CellComponent) -> Unit,
    private val onMoveDownRequested: (CellComponent) -> Unit,
    private val onModified: () -> Unit,
    private val onRunAllAboveRequested: ((CellComponent) -> Unit)? = null,
    private val onRunAllBelowRequested: ((CellComponent) -> Unit)? = null,
    private val onClearOutputsRequested: ((CellComponent) -> Unit)? = null
) : JPanel(BorderLayout()) {

    private val themeManager: ThemeManager? = runCatching {
        ApplicationManager.getApplication()?.getService(ThemeManager::class.java)
    }.getOrNull()

    private val headerPanel = JPanel(BorderLayout())
    private val execLabel = JLabel()
    private val typeCombo = JComboBox(arrayOf("Code", "Markdown", "Raw"))
    private val commentsToggleBtn = JButton()

    private var editor: Editor? = null
    private var fallbackTextArea: JTextArea? = null
    private val editorContainer = JPanel(BorderLayout())

    private val outputPanel = JPanel()
    private val commentsPanel = JPanel()
    private var commentsVisible = cell.comments.isNotEmpty()

    init {
        border = CompoundBorder(
            EmptyBorder(4, 8, 8, 8),
            LineBorder(getBorderColor(), 1, true)
        )
        background = getSurfaceColor()

        buildHeader()
        buildEditor()
        buildOutputPanel()
        buildCommentsPanel()

        val centerPanel = JPanel()
        centerPanel.layout = BoxLayout(centerPanel, BoxLayout.Y_AXIS)
        centerPanel.isOpaque = false
        centerPanel.add(editorContainer)
        centerPanel.add(outputPanel)
        centerPanel.add(commentsPanel)

        add(headerPanel, BorderLayout.NORTH)
        add(centerPanel, BorderLayout.CENTER)

        updateExecutionDisplay()
        renderOutputs()
        renderComments()
    }

    private fun buildHeader() {
        headerPanel.isOpaque = true
        headerPanel.background = getSecondaryBgColor()
        headerPanel.border = EmptyBorder(4, 8, 4, 8)

        // Left Header: Execution Count & Type
        val left = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply { isOpaque = false }
        execLabel.font = Font("Monospaced", Font.BOLD, 12)
        execLabel.foreground = getAccentColor()
        left.add(execLabel)

        typeCombo.selectedItem = when (cell.cellType) {
            CellType.CODE -> "Code"
            CellType.MARKDOWN -> "Markdown"
            CellType.RAW -> "Raw"
        }
        typeCombo.isFocusable = false
        typeCombo.addActionListener {
            val newType = when (typeCombo.selectedItem as String) {
                "Markdown" -> CellType.MARKDOWN
                "Raw" -> CellType.RAW
                else -> CellType.CODE
            }
            if (cell.cellType != newType) {
                cell.cellType = newType
                updateExecutionDisplay()
                onModified()
            }
        }
        left.add(typeCombo)
        headerPanel.add(left, BorderLayout.WEST)

        // Right Header: Run, Comments Badge, Move, Delete
        val right = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply { isOpaque = false }

        val runBtn = JButton("▶ Run").apply {
            isFocusPainted = false
            font = font.deriveFont(Font.BOLD, 11f)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            toolTipText = "Execute cell (Shift+Enter)"
            addActionListener { onRunRequested(this@CellComponent) }
        }
        right.add(runBtn)

        updateCommentButtonText()
        commentsToggleBtn.apply {
            isFocusPainted = false
            font = font.deriveFont(Font.PLAIN, 11f)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            toolTipText = "Toggle cell review comments"
            addActionListener {
                commentsVisible = !commentsVisible
                commentsPanel.isVisible = commentsVisible
                revalidate()
                repaint()
            }
        }
        right.add(commentsToggleBtn)

        val upBtn = JButton("▲").apply {
            isFocusPainted = false
            toolTipText = "Move cell up"
            addActionListener { onMoveUpRequested(this@CellComponent) }
        }
        val downBtn = JButton("▼").apply {
            isFocusPainted = false
            toolTipText = "Move cell down"
            addActionListener { onMoveDownRequested(this@CellComponent) }
        }
        val delBtn = JButton("✕").apply {
            isFocusPainted = false
            toolTipText = "Delete cell"
            addActionListener { onDeleteRequested(this@CellComponent) }
        }

        val moreBtn = JButton("⋮").apply {
            isFocusPainted = false
            font = font.deriveFont(Font.BOLD, 12f)
            toolTipText = "More cell actions"
            addActionListener {
                val menu = JPopupMenu()
                val runItem = JMenuItem("▶ Run Cell").apply {
                    addActionListener { onRunRequested(this@CellComponent) }
                }
                val runAboveItem = JMenuItem("⏩ Run All Above").apply {
                    addActionListener { onRunAllAboveRequested?.invoke(this@CellComponent) }
                }
                val runBelowItem = JMenuItem("⏩ Run All Below").apply {
                    addActionListener { onRunAllBelowRequested?.invoke(this@CellComponent) }
                }
                val clearOutItem = JMenuItem("🧹 Clear Output").apply {
                    addActionListener {
                        if (onClearOutputsRequested != null) {
                            onClearOutputsRequested.invoke(this@CellComponent)
                        } else {
                            cell.clearOutputs()
                            renderOutputs()
                            onModified()
                        }
                    }
                }
                val copyCodeItem = JMenuItem("📋 Copy Code").apply {
                    addActionListener {
                        val sel = StringSelection(cell.source)
                        Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, sel)
                    }
                }
                val delItem = JMenuItem("✕ Delete Cell").apply {
                    addActionListener { onDeleteRequested(this@CellComponent) }
                }

                menu.add(runItem)
                menu.add(runAboveItem)
                menu.add(runBelowItem)
                menu.addSeparator()
                menu.add(clearOutItem)
                menu.add(copyCodeItem)
                menu.addSeparator()
                menu.add(delItem)

                menu.show(this, 0, height)
            }
        }

        right.add(upBtn)
        right.add(downBtn)
        right.add(moreBtn)
        right.add(delBtn)

        headerPanel.add(right, BorderLayout.EAST)
    }

    private fun buildEditor() {
        editorContainer.border = EmptyBorder(4, 4, 4, 4)
        editorContainer.isOpaque = false

        runCatching {
            val editorFactory = EditorFactory.getInstance()
            val document = editorFactory.createDocument(cell.source)
            document.addDocumentListener(object : DocumentListener {
                override fun documentChanged(event: DocumentEvent) {
                    cell.source = document.text
                    onModified()
                }
            })

            val pyFileType = FileTypeManager.getInstance().getFileTypeByExtension("py")
            val newEditor = editorFactory.createEditor(document, project, pyFileType, false)
            newEditor.settings.apply {
                isLineNumbersShown = true
                isAutoCodeFoldingEnabled = false
                isLineMarkerAreaShown = false
                isIndentGuidesShown = true
            }

            // Keyboard shortcut for Shift+Enter (Run) & Ctrl+Enter (Run in place)
            newEditor.contentComponent.addKeyListener(object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent) {
                    if (e.keyCode == KeyEvent.VK_ENTER && e.isShiftDown) {
                        e.consume()
                        onRunRequested(this@CellComponent)
                    } else if (e.keyCode == KeyEvent.VK_ENTER && (e.isControlDown || e.isMetaDown)) {
                        e.consume()
                        onRunRequested(this@CellComponent)
                    }
                }
            })

            editor = newEditor
            editorContainer.add(newEditor.component, BorderLayout.CENTER)
        }.onFailure {
            // Fallback lightweight Swing text area
            val textArea = JBTextArea(cell.source).apply {
                font = Font("Consolas", Font.PLAIN, 13)
                rows = (cell.source.lines().size.coerceAtLeast(2))
                lineWrap = false
                addKeyListener(object : KeyAdapter() {
                    override fun keyReleased(e: KeyEvent?) {
                        cell.source = text
                        onModified()
                    }
                    override fun keyPressed(e: KeyEvent) {
                        if (e.keyCode == KeyEvent.VK_ENTER && e.isShiftDown) {
                            e.consume()
                            onRunRequested(this@CellComponent)
                        }
                    }
                })
            }
            fallbackTextArea = textArea
            val scroll = JBScrollPane(textArea)
            editorContainer.add(scroll, BorderLayout.CENTER)
        }
    }

    private fun buildOutputPanel() {
        outputPanel.layout = BoxLayout(outputPanel, BoxLayout.Y_AXIS)
        outputPanel.isOpaque = false
        outputPanel.border = EmptyBorder(2, 8, 4, 8)
    }

    private fun buildCommentsPanel() {
        commentsPanel.layout = BoxLayout(commentsPanel, BoxLayout.Y_AXIS)
        commentsPanel.isOpaque = true
        commentsPanel.background = getSecondaryBgColor()
        commentsPanel.border = MatteBorder(1, 0, 0, 0, getBorderColor())
        commentsPanel.isVisible = commentsVisible
    }

    fun updateExecutionDisplay() {
        SwingUtilities.invokeLater {
            if (cell.cellType != CellType.CODE) {
                execLabel.text = "        "
                return@invokeLater
            }

            if (cell.isExecuting) {
                execLabel.text = "In [*]: "
                execLabel.foreground = Color(203, 75, 22) // Orange
            } else if (cell.executionCount != null) {
                execLabel.text = "In [${cell.executionCount}]: "
                execLabel.foreground = getAccentColor()
            } else {
                execLabel.text = "In [ ]: "
                execLabel.foreground = Color.GRAY
            }
        }
    }

    fun appendStreamOutput(stream: CellOutput.StreamOutput) {
        SwingUtilities.invokeLater {
            cell.outputs.add(stream)
            val lineLabel = JBLabel(stripAnsi(stream.text)).apply {
                font = Font("Consolas", Font.PLAIN, 12)
                foreground = if (stream.name == "stderr") Color(220, 50, 47) else getForegroundColor()
            }
            outputPanel.add(lineLabel)
            outputPanel.revalidate()
            outputPanel.repaint()
        }
    }

    fun renderOutputs() {
        SwingUtilities.invokeLater {
            outputPanel.removeAll()
            if (cell.outputs.isEmpty()) {
                outputPanel.revalidate()
                outputPanel.repaint()
                return@invokeLater
            }

            for (output in cell.outputs) {
                when (output) {
                    is CellOutput.StreamOutput -> {
                        val text = stripAnsi(output.text)
                        val area = JTextArea(text).apply {
                            isEditable = false
                            font = Font("Consolas", Font.PLAIN, 12)
                            foreground = if (output.name == "stderr") Color(220, 50, 47) else getForegroundColor()
                            background = getSurfaceColor()
                            border = EmptyBorder(2, 4, 2, 4)
                        }
                        outputPanel.add(area)
                    }
                    is CellOutput.ExecuteResultOutput -> {
                        renderRichMime(output.data, output.executionCount)
                    }
                    is CellOutput.DisplayDataOutput -> {
                        renderRichMime(output.data, null)
                    }
                    is CellOutput.ErrorOutput -> {
                        val errorBox = JPanel().apply {
                            layout = BoxLayout(this, BoxLayout.Y_AXIS)
                            background = Color(255, 235, 238)
                            border = CompoundBorder(LineBorder(Color(239, 154, 154), 1), EmptyBorder(6, 8, 6, 8))
                            val title = JLabel("${output.ename}: ${output.evalue}").apply {
                                font = font.deriveFont(Font.BOLD, 12f)
                                foreground = Color(183, 28, 28)
                            }
                            add(title)

                            if (output.traceback.isNotEmpty()) {
                                add(Box.createVerticalStrut(4))
                                val tb = JTextArea(output.traceback.joinToString("\n") { stripAnsi(it) }).apply {
                                    isEditable = false
                                    font = Font("Consolas", Font.PLAIN, 11)
                                    foreground = Color(136, 14, 79)
                                    background = Color(255, 235, 238)
                                }
                                add(tb)
                            }
                        }
                        outputPanel.add(errorBox)
                    }
                }
                outputPanel.add(Box.createVerticalStrut(4))
            }

            outputPanel.revalidate()
            outputPanel.repaint()
        }
    }

    private fun renderRichMime(data: Map<String, Any>, execCount: Int?) {
        // 1. Check for Images (PNG, JPEG)
        val imageBase64 = data["image/png"]?.toString() ?: data["image/jpeg"]?.toString()
        if (imageBase64 != null) {
            runCatching {
                val cleanBase64 = imageBase64.replace("\n", "").replace("\r", "").trim()
                val bytes = Base64.getDecoder().decode(cleanBase64)
                val img = ImageIO.read(ByteArrayInputStream(bytes))
                if (img != null) {
                    val imgContainer = JPanel(BorderLayout()).apply {
                        isOpaque = false
                        border = EmptyBorder(4, 0, 4, 0)
                    }

                    val imgLabel = JLabel(ImageIcon(img)).apply {
                        border = CompoundBorder(
                            LineBorder(Color(229, 231, 235), 1),
                            EmptyBorder(4, 4, 4, 4)
                        )
                    }

                    runCatching {
                        val plotItem = org.jormungandr.core.plot.PlotItem(
                            title = "Cell Output [${img.width}×${img.height}]",
                            source = "Matplotlib / Jupyter",
                            image = img
                        )
                        org.jormungandr.core.plot.PlotManagerService.getInstance().addPlot(plotItem)
                    }

                    val toolbar = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply { isOpaque = false }
                    val sendToPlotsBtn = JButton("🖼️ Open in Plots").apply {
                        font = font.deriveFont(Font.PLAIN, 10f)
                        toolTipText = "Inspect this figure in the Scientific Plots tool window"
                        addActionListener {
                            runCatching {
                                val tw = com.intellij.openapi.wm.ToolWindowManager.getInstance(project).getToolWindow("Scientific Plots")
                                tw?.show()
                            }
                        }
                    }
                    val copyImgBtn = JButton("📋 Copy Image").apply {
                        font = font.deriveFont(Font.PLAIN, 10f)
                        addActionListener {
                            Toolkit.getDefaultToolkit().systemClipboard.setContents(
                                ImageTransferable(img),
                                null
                            )
                        }
                    }
                    toolbar.add(sendToPlotsBtn)
                    toolbar.add(copyImgBtn)

                    imgContainer.add(toolbar, BorderLayout.NORTH)
                    imgContainer.add(imgLabel, BorderLayout.CENTER)
                    outputPanel.add(imgContainer)
                    return
                }
            }
        }

        // 2. Check for Plotly JSON MIME
        val plotlyJson = data["application/vnd.plotly.v1+json"]
        if (plotlyJson != null) {
            val jsonString = if (plotlyJson is String) plotlyJson else Gson().toJson(plotlyJson)
            val html = buildPlotlyHtmlFromJson(jsonString)
            renderInteractiveWebCard(html, WebPlotType.PLOTLY, execCount)
            return
        }

        // 3. Check for Vega-Lite MIME
        val vegaJson = data["application/vnd.vegalite.v5+json"]
            ?: data["application/vnd.vegalite.v4+json"]
            ?: data["application/vnd.vega.v5+json"]
        if (vegaJson != null) {
            val jsonString = if (vegaJson is String) vegaJson else Gson().toJson(vegaJson)
            val html = buildVegaHtmlFromJson(jsonString)
            renderInteractiveWebCard(html, WebPlotType.VEGA_LITE, execCount)
            return
        }

        // 4. Check for HTML
        val htmlContent = data["text/html"]?.toString()
        if (htmlContent != null) {
            val parsedTable = HtmlTableParser.parse(htmlContent)
            if (parsedTable != null) {
                // Rich DataFrame Card
                val tableCard = JPanel(BorderLayout()).apply {
                    isOpaque = true
                    background = Color(255, 255, 255)
                    border = CompoundBorder(
                        LineBorder(Color(203, 213, 225), 1, true),
                        EmptyBorder(4, 8, 8, 8)
                    )
                }

                // Table Card Header Bar
                val headerBar = JPanel(BorderLayout()).apply {
                    isOpaque = false
                    border = EmptyBorder(4, 4, 6, 4)
                }
                val infoLabel = JLabel("📊 DataFrame Output [${parsedTable.rows.size} rows × ${parsedTable.headers.size} cols]").apply {
                    font = font.deriveFont(Font.BOLD, 11f)
                    foreground = Color(30, 41, 59)
                }
                headerBar.add(infoLabel, BorderLayout.WEST)

                val actions = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply { isOpaque = false }
                val openDfBtn = JButton("📊 Open in DataFrame Studio").apply {
                    font = font.deriveFont(Font.BOLD, 10f)
                    foreground = Color(30, 64, 175)
                    toolTipText = "Open this table directly in Jörmungandr DataFrame Studio"
                    addActionListener {
                        runCatching {
                            val tempFile = File.createTempFile("jupyter_table_", ".csv")
                            tempFile.writeText(parsedTable.toCsv(), Charsets.UTF_8)
                            tempFile.deleteOnExit()
                            val vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempFile)
                            if (vFile != null) {
                                FileEditorManager.getInstance(project).openFile(vFile, true)
                            }
                        }
                    }
                }
                val copyCsvBtn = JButton("📋 Copy CSV").apply {
                    font = font.deriveFont(Font.PLAIN, 10f)
                    addActionListener {
                        val sel = StringSelection(parsedTable.toCsv())
                        Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, sel)
                    }
                }
                actions.add(openDfBtn)
                actions.add(copyCsvBtn)
                headerBar.add(actions, BorderLayout.EAST)

                tableCard.add(headerBar, BorderLayout.NORTH)

                // HTML Table Viewer
                val editorPane = JEditorPane("text/html", formatTableHtml(htmlContent)).apply {
                    isEditable = false
                    background = Color(255, 255, 255)
                    border = null
                }
                val scroll = JBScrollPane(editorPane).apply {
                    preferredSize = Dimension(editorPane.preferredSize.width, (editorPane.preferredSize.height + 20).coerceIn(60, 320))
                    border = null
                }
                tableCard.add(scroll, BorderLayout.CENTER)

                outputPanel.add(tableCard)
                return
            } else if (isInteractiveWebVisual(htmlContent)) {
                val detectedType = WebPlotItem.detectType(htmlContent)
                renderInteractiveWebCard(htmlContent, detectedType, execCount)
                return
            } else {
                // General HTML
                val editorPane = JEditorPane("text/html", htmlContent).apply {
                    isEditable = false
                    background = getSurfaceColor()
                    border = null
                }
                outputPanel.add(editorPane)
                return
            }
        }

        // 3. Fallback to Plain Text
        val plainText = data["text/plain"]?.toString() ?: data.values.firstOrNull()?.toString() ?: ""
        val resultRow = JPanel(BorderLayout(6, 0)).apply { isOpaque = false }
        if (execCount != null) {
            val outLabel = JLabel("Out [$execCount]: ").apply {
                font = Font("Monospaced", Font.BOLD, 11)
                foreground = Color(203, 75, 22) // Orange accent
            }
            resultRow.add(outLabel, BorderLayout.WEST)
        }
        val textComp = JTextArea(plainText).apply {
            isEditable = false
            font = Font("Consolas", Font.PLAIN, 12)
            foreground = getForegroundColor()
            background = getSurfaceColor()
            border = EmptyBorder(2, 0, 2, 0)
        }
        resultRow.add(textComp, BorderLayout.CENTER)
        outputPanel.add(resultRow)
    }

    private fun isInteractiveWebVisual(html: String): Boolean {
        val lower = html.lowercase()
        return lower.contains("plotly") ||
               lower.contains("plot.ly") ||
               lower.contains("leaflet") ||
               lower.contains("folium") ||
               lower.contains("vega-lite") ||
               lower.contains("vegaembed") ||
               lower.contains("echarts") ||
               (lower.contains("<script") && (lower.contains("canvas") || lower.contains("chart") || lower.contains("plot") || lower.contains("webgl")))
    }

    private fun buildPlotlyHtmlFromJson(jsonString: String): String {
        return """
            <div id="plotly-div" style="width:100%;height:100%;min-height:360px;"></div>
            <script src="https://cdn.plot.ly/plotly-2.35.2.min.js"></script>
            <script>
            (function() {
                var fig = $jsonString;
                Plotly.newPlot('plotly-div', fig.data || [], fig.layout || {}, fig.config || {responsive: true});
            })();
            </script>
        """.trimIndent()
    }

    private fun buildVegaHtmlFromJson(jsonString: String): String {
        return """
            <div id="vis" style="width:100%;height:100%;min-height:360px;"></div>
            <script src="https://cdn.jsdelivr.net/npm/vega@5"></script>
            <script src="https://cdn.jsdelivr.net/npm/vega-lite@5"></script>
            <script src="https://cdn.jsdelivr.net/npm/vega-embed@6"></script>
            <script>
            (function() {
                var spec = $jsonString;
                vegaEmbed('#vis', spec, {mode: "vega-lite"}).catch(console.warn);
            })();
            </script>
        """.trimIndent()
    }

    private fun renderInteractiveWebCard(html: String, type: WebPlotType, execCount: Int?) {
        val plotItem = WebPlotItem(
            title = "Notebook Output [${type.displayName}]",
            plotType = type,
            htmlContent = html,
            source = "Jupyter Cell"
        )
        runCatching {
            org.jormungandr.core.plot.PlotManagerService.getInstance().addWebPlot(plotItem)
        }

        val card = JPanel(BorderLayout()).apply {
            isOpaque = true
            background = Color(255, 255, 255)
            border = CompoundBorder(
                LineBorder(Color(203, 213, 225), 1, true),
                EmptyBorder(4, 6, 6, 6)
            )
        }

        val header = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = EmptyBorder(4, 4, 6, 4)
        }

        val left = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply { isOpaque = false }
        if (execCount != null) {
            val outLabel = JLabel("Out [$execCount]: ").apply {
                font = Font("Monospaced", Font.BOLD, 11)
                foreground = Color(203, 75, 22)
            }
            left.add(outLabel)
        }
        val badge = JLabel("${type.badgeIcon} ${type.displayName}").apply {
            font = font.deriveFont(Font.BOLD, 11f)
            foreground = Color(30, 64, 175)
        }
        left.add(badge)
        header.add(left, BorderLayout.WEST)

        val right = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply { isOpaque = false }
        val openBrowserBtn = JButton("🌐 Open in Browser").apply {
            font = font.deriveFont(Font.PLAIN, 10f)
            isFocusable = false
            toolTipText = "Open interactive figure in your default web browser"
            addActionListener {
                runCatching {
                    val f = plotItem.exportHtmlFile()
                    BrowserLauncher.instance.browse(f.toURI())
                }
            }
        }
        val openPlotsBtn = JButton("🖼️ Open in Scientific Plots").apply {
            font = font.deriveFont(Font.PLAIN, 10f)
            isFocusable = false
            toolTipText = "Inspect in the Scientific Plots tool window"
            addActionListener {
                runCatching {
                    val tw = ToolWindowManager.getInstance(project).getToolWindow("Scientific Plots")
                    tw?.show()
                }
            }
        }
        val copyHtmlBtn = JButton("📋 Copy HTML").apply {
            font = font.deriveFont(Font.PLAIN, 10f)
            isFocusable = false
            addActionListener {
                val fullHtml = WebPlotItem.ensureCompleteHtml(html, plotItem.title)
                val sel = StringSelection(fullHtml)
                Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, sel)
            }
        }
        right.add(openBrowserBtn)
        right.add(openPlotsBtn)
        right.add(copyHtmlBtn)
        header.add(right, BorderLayout.EAST)

        card.add(header, BorderLayout.NORTH)

        val webView = WebPlotViewComponent(webPlot = plotItem, parentDisposable = null, compactMode = true).apply {
            preferredSize = Dimension(preferredSize.width, 380)
        }
        card.add(webView, BorderLayout.CENTER)

        outputPanel.add(card)
    }

    private fun formatTableHtml(tableHtml: String): String {
        return """
            <html>
            <head>
            <style>
                body { font-family: sans-serif; font-size: 11px; margin: 0; padding: 4px; }
                table { border-collapse: collapse; width: 100%; }
                th { background-color: #f1f5f9; color: #1e293b; padding: 4px 8px; border: 1px solid #cbd5e1; font-weight: bold; text-align: left; }
                td { padding: 4px 8px; border: 1px solid #e2e8f0; color: #334155; }
                tr:nth-child(even) { background-color: #f8fafc; }
            </style>
            </head>
            <body>
            $tableHtml
            </body>
            </html>
        """.trimIndent()
    }

    private class ImageTransferable(private val image: Image) : java.awt.datatransfer.Transferable {
        override fun getTransferDataFlavors(): Array<java.awt.datatransfer.DataFlavor> =
            arrayOf(java.awt.datatransfer.DataFlavor.imageFlavor)
        override fun isDataFlavorSupported(flavor: java.awt.datatransfer.DataFlavor): Boolean =
            java.awt.datatransfer.DataFlavor.imageFlavor.equals(flavor)
        override fun getTransferData(flavor: java.awt.datatransfer.DataFlavor): Any = image
    }

    /**
     * Renders the interactive Threaded Comments Section for this cell.
     */
    fun renderComments() {
        commentsPanel.removeAll()
        updateCommentButtonText()

        val header = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = EmptyBorder(6, 12, 6, 12)
            val lbl = JLabel("💬 Cell Review & Threaded Discussions (${cell.comments.size})").apply {
                font = font.deriveFont(Font.BOLD, 12f)
                foreground = getAccentColor()
            }
            add(lbl, BorderLayout.WEST)
        }
        commentsPanel.add(header)

        // List each comment thread
        val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        for (comment in cell.comments) {
            val card = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                isOpaque = true
                background = if (comment.resolved) Color(245, 245, 245) else getSurfaceColor()
                border = CompoundBorder(
                    LineBorder(if (comment.resolved) Color.LIGHT_GRAY else getBorderColor(), 1, true),
                    EmptyBorder(8, 10, 8, 10)
                )
            }

            // Top row: Author, Date, Resolve Checkbox
            val metaRow = JPanel(BorderLayout()).apply { isOpaque = false }
            val authorLabel = JLabel("${comment.author} · ${sdf.format(Date(comment.timestamp))}").apply {
                font = font.deriveFont(Font.BOLD, 11f)
                foreground = if (comment.resolved) Color.GRAY else getForegroundColor()
            }
            metaRow.add(authorLabel, BorderLayout.WEST)

            val resolveBtn = JButton(if (comment.resolved) "✓ Resolved" else "Resolve").apply {
                font = font.deriveFont(Font.PLAIN, 10f)
                isFocusPainted = false
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                addActionListener {
                    comment.resolved = !comment.resolved
                    renderComments()
                    onModified()
                }
            }
            metaRow.add(resolveBtn, BorderLayout.EAST)
            card.add(metaRow)
            card.add(Box.createVerticalStrut(4))

            // Comment text
            val commentBody = JTextArea(comment.text).apply {
                isEditable = false
                lineWrap = true
                wrapStyleWord = true
                font = font.deriveFont(Font.PLAIN, 12f)
                foreground = if (comment.resolved) Color.GRAY else getForegroundColor()
                background = card.background
                border = EmptyBorder(2, 0, 4, 0)
            }
            card.add(commentBody)

            // Replies
            if (comment.replies.isNotEmpty()) {
                val repliesBox = JPanel().apply {
                    layout = BoxLayout(this, BoxLayout.Y_AXIS)
                    isOpaque = false
                    border = MatteBorder(0, 2, 0, 0, getAccentColor())
                }
                for (reply in comment.replies) {
                    val repPanel = JPanel(BorderLayout()).apply {
                        isOpaque = false
                        border = EmptyBorder(3, 8, 3, 0)
                        val repMeta = JLabel("${reply.author} (${sdf.format(Date(reply.timestamp))}):").apply {
                            font = font.deriveFont(Font.BOLD, 10.5f)
                            foreground = getAccentColor()
                        }
                        val repText = JLabel(reply.text).apply { font = font.deriveFont(Font.PLAIN, 11f) }
                        add(repMeta, BorderLayout.NORTH)
                        add(repText, BorderLayout.CENTER)
                    }
                    repliesBox.add(repPanel)
                }
                card.add(repliesBox)
            }

            // Reply input field
            val replyRow = JPanel(BorderLayout(6, 0)).apply {
                isOpaque = false
                border = EmptyBorder(6, 0, 0, 0)
            }
            val replyField = JTextField().apply {
                toolTipText = "Reply to this discussion..."
            }
            val replyBtn = JButton("Reply").apply {
                font = font.deriveFont(Font.PLAIN, 11f)
                addActionListener {
                    val txt = replyField.text.trim()
                    if (txt.isNotBlank()) {
                        comment.replies.add(CellCommentReply(author = "indoctrinatedrecluse", text = txt))
                        replyField.text = ""
                        renderComments()
                        onModified()
                    }
                }
            }
            replyRow.add(replyField, BorderLayout.CENTER)
            replyRow.add(replyBtn, BorderLayout.EAST)
            card.add(replyRow)

            commentsPanel.add(card)
            commentsPanel.add(Box.createVerticalStrut(6))
        }

        // New Comment Input Section at bottom of thread
        val newCommentBox = JPanel(BorderLayout(6, 0)).apply {
            isOpaque = false
            border = EmptyBorder(6, 12, 10, 12)
        }
        val newCommentInput = JTextField().apply {
            toolTipText = "Add new review comment on this cell..."
        }
        val postBtn = JButton("Post Comment").apply {
            font = font.deriveFont(Font.BOLD, 11f)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addActionListener {
                val txt = newCommentInput.text.trim()
                if (txt.isNotBlank()) {
                    cell.addComment(author = "indoctrinatedrecluse", text = txt)
                    newCommentInput.text = ""
                    renderComments()
                    onModified()
                }
            }
        }
        newCommentBox.add(newCommentInput, BorderLayout.CENTER)
        newCommentBox.add(postBtn, BorderLayout.EAST)
        commentsPanel.add(newCommentBox)

        commentsPanel.revalidate()
        commentsPanel.repaint()
    }

    private fun updateCommentButtonText() {
        val count = cell.comments.size
        commentsToggleBtn.text = if (count > 0) "💬 Comments ($count)" else "💬 Comment"
    }

    private fun stripAnsi(text: String): String {
        return text.replace(Regex("\\u001B\\[[;\\d]*[ -/]*[@-~]"), "")
    }

    private fun getSurfaceColor(): Color = parseHex(themeManager?.currentTheme?.value?.colors?.surface, Color(250, 242, 220))
    private fun getSecondaryBgColor(): Color = parseHex(themeManager?.currentTheme?.value?.colors?.secondaryBackground, Color(238, 232, 213))
    private fun getBorderColor(): Color = parseHex(themeManager?.currentTheme?.value?.colors?.border, Color(224, 216, 195))
    private fun getAccentColor(): Color = parseHex(themeManager?.currentTheme?.value?.colors?.accent, Color(38, 139, 210))
    private fun getForegroundColor(): Color = parseHex(themeManager?.currentTheme?.value?.colors?.foreground, Color(101, 123, 131))

    private fun parseHex(hex: String?, fallback: Color): Color {
        if (hex.isNullOrBlank()) return fallback
        return try { Color.decode(hex) } catch (e: Exception) { fallback }
    }

    fun dispose() {
        editor?.let {
            EditorFactory.getInstance().releaseEditor(it)
            editor = null
        }
    }
}
