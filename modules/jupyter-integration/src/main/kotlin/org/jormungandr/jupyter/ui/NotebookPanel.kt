package org.jormungandr.jupyter.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import kotlinx.coroutines.*
import org.jormungandr.core.theme.ThemeManager
import org.jormungandr.jupyter.diff.NotebookDiffDialog
import org.jormungandr.jupyter.export.NotebookExporter
import org.jormungandr.jupyter.format.NotebookFormat
import org.jormungandr.jupyter.kernel.*
import org.jormungandr.jupyter.model.CellOutput
import org.jormungandr.jupyter.model.CellType
import org.jormungandr.jupyter.model.JupyterKernelSpec
import org.jormungandr.jupyter.model.NotebookModel
import org.jormungandr.jupyter.ui.outline.NotebookOutlinePanel
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.io.File
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.border.MatteBorder

private val LOG = logger<NotebookPanel>()

/**
 * Main Interactive Jupyter Notebook UI Panel:
 * - Action toolbar with Run, Run All, Interrupt, Restart, Cell Types, Comments, Kernel status.
 * - Interactive Outline / TOC panel for quick document navigation.
 * - Multi-format export engine (HTML, Python, Markdown, LaTeX).
 * - Visual cell-by-cell notebook diff comparison.
 * - Dynamic scrollable cells canvas.
 * - Real-time execution loop with live streaming outputs.
 */
class NotebookPanel(
    val project: Project,
    val virtualFile: VirtualFile
) : JPanel(BorderLayout()) {

    private val themeManager: ThemeManager? = runCatching {
        ApplicationManager.getApplication()?.getService(ThemeManager::class.java)
    }.getOrNull()

    private val kernelService = ApplicationManager.getApplication().getService(JupyterKernelService::class.java)

    private val panelScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var notebookModel: NotebookModel = NotebookModel.createDefaultPythonNotebook()
    private val cellComponents = mutableListOf<CellComponent>()

    private val cellsContainer = JPanel()
    private val scrollPane = JBScrollPane(cellsContainer)
    private val outlinePanel = NotebookOutlinePanel { cellIndex -> scrollToCell(cellIndex) }
    private lateinit var mainSplitPane: JSplitPane

    private val kernelStatusLabel = JLabel("⚪ Initializing...")
    private val kernelCombo = JComboBox<String>()
    private var availableKernelSpecs = listOf<JupyterKernelSpec>()
    private var activeSession: KernelSession? = null

    init {
        background = getBgColor()
        buildToolbar()
        buildCellsContainer()

        outlinePanel.isVisible = false
        mainSplitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scrollPane, outlinePanel).apply {
            isContinuousLayout = true
            resizeWeight = 1.0
            dividerSize = 4
            border = null
        }

        add(mainSplitPane, BorderLayout.CENTER)

        loadNotebookContent()
        initKernel()
    }

    private fun buildToolbar() {
        val toolbar = JPanel(BorderLayout()).apply {
            isOpaque = true
            background = getSecondaryBgColor()
            border = MatteBorder(0, 0, 1, 0, getBorderColor())
        }

        val leftTools = JPanel(FlowLayout(FlowLayout.LEFT, 4, 4)).apply { isOpaque = false }

        val runBtn = JButton("▶ Run").apply {
            isFocusPainted = false
            font = font.deriveFont(Font.BOLD, 11f)
            toolTipText = "Execute selected cell (Shift+Enter)"
            addActionListener { runFocusedOrFirstCell() }
        }
        val runAllBtn = JButton("⏩ Run All").apply {
            isFocusPainted = false
            font = font.deriveFont(Font.PLAIN, 11f)
            toolTipText = "Execute all cells sequentially"
            addActionListener { runAllCells() }
        }
        val interruptBtn = JButton("⏹ Interrupt").apply {
            isFocusPainted = false
            font = font.deriveFont(Font.PLAIN, 11f)
            toolTipText = "Interrupt current execution"
            addActionListener { interruptKernel() }
        }
        val restartBtn = JButton("🔄 Restart").apply {
            isFocusPainted = false
            font = font.deriveFont(Font.PLAIN, 11f)
            toolTipText = "Restart kernel session"
            addActionListener { restartKernel() }
        }
        val addCodeBtn = JButton("+ Code").apply {
            isFocusPainted = false
            font = font.deriveFont(Font.PLAIN, 11f)
            toolTipText = "Insert new Code cell"
            addActionListener { addCell(CellType.CODE) }
        }
        val addMdBtn = JButton("+ Markdown").apply {
            isFocusPainted = false
            font = font.deriveFont(Font.PLAIN, 11f)
            toolTipText = "Insert new Markdown cell"
            addActionListener { addCell(CellType.MARKDOWN) }
        }
        val clearOutputsBtn = JButton("🧹 Clear").apply {
            isFocusPainted = false
            font = font.deriveFont(Font.PLAIN, 11f)
            toolTipText = "Clear all cell outputs"
            addActionListener { clearAllOutputs() }
        }

        val outlineBtn = JToggleButton("📑 Outline").apply {
            isFocusPainted = false
            font = font.deriveFont(Font.PLAIN, 11f)
            toolTipText = "Toggle Table of Contents Outline panel"
            addActionListener {
                outlinePanel.isVisible = isSelected
                if (isSelected) {
                    outlinePanel.updateOutline(notebookModel)
                    mainSplitPane.dividerLocation = (width - 260).coerceAtLeast(100)
                }
                revalidate()
                repaint()
            }
        }

        val exportBtn = JButton("💾 Export ▾").apply {
            isFocusPainted = false
            font = font.deriveFont(Font.PLAIN, 11f)
            toolTipText = "Export notebook to HTML, Python, Markdown, or LaTeX"
            addActionListener { showExportMenu(this) }
        }

        val diffBtn = JButton("🔀 Diff").apply {
            isFocusPainted = false
            font = font.deriveFont(Font.PLAIN, 11f)
            toolTipText = "Compare modified notebook with disk version"
            addActionListener { showNotebookDiff() }
        }

        leftTools.add(runBtn)
        leftTools.add(runAllBtn)
        leftTools.add(interruptBtn)
        leftTools.add(restartBtn)
        leftTools.add(Box.createHorizontalStrut(6))
        leftTools.add(addCodeBtn)
        leftTools.add(addMdBtn)
        leftTools.add(clearOutputsBtn)
        leftTools.add(Box.createHorizontalStrut(6))
        leftTools.add(outlineBtn)
        leftTools.add(exportBtn)
        leftTools.add(diffBtn)

        toolbar.add(leftTools, BorderLayout.WEST)

        // Right Tools: Kernel status and selector
        val rightTools = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 4)).apply { isOpaque = false }

        kernelStatusLabel.font = font.deriveFont(Font.BOLD, 11f)
        rightTools.add(kernelStatusLabel)

        kernelCombo.isFocusable = false
        kernelCombo.addActionListener {
            val selected = kernelCombo.selectedIndex
            if (selected in availableKernelSpecs.indices) {
                val spec = availableKernelSpecs[selected]
                notebookModel.metadata.kernelspec.name = spec.id
                notebookModel.metadata.kernelspec.displayName = spec.displayName
                switchKernel(spec)
            }
        }
        rightTools.add(kernelCombo)

        toolbar.add(rightTools, BorderLayout.EAST)
        add(toolbar, BorderLayout.NORTH)
    }

    private fun buildCellsContainer() {
        cellsContainer.layout = BoxLayout(cellsContainer, BoxLayout.Y_AXIS)
        cellsContainer.isOpaque = true
        cellsContainer.background = getBgColor()
        cellsContainer.border = EmptyBorder(8, 8, 8, 8)

        scrollPane.verticalScrollBar.unitIncrement = 24
        scrollPane.border = null
    }

    private fun loadNotebookContent() {
        runCatching {
            val text = String(virtualFile.contentsToByteArray(), Charsets.UTF_8)
            notebookModel = NotebookFormat.readNotebook(text)
        }.onFailure { err ->
            LOG.warn("Could not read notebook file, creating default: ${err.message}")
            notebookModel = NotebookModel.createDefaultPythonNotebook()
        }

        rebuildCellComponents()
    }

    private fun rebuildCellComponents() {
        cellsContainer.removeAll()
        cellComponents.forEach { it.dispose() }
        cellComponents.clear()

        for (cell in notebookModel.cells) {
            val comp = CellComponent(
                cell = cell,
                project = project,
                onRunRequested = { runCell(it) },
                onDeleteRequested = { deleteCell(it) },
                onMoveUpRequested = { moveCellUp(it) },
                onMoveDownRequested = { moveCellDown(it) },
                onModified = { saveNotebook() },
                onRunAllAboveRequested = { runAllAbove(it) },
                onRunAllBelowRequested = { runAllBelow(it) },
                onClearOutputsRequested = {
                    it.cell.clearOutputs()
                    it.renderOutputs()
                    saveNotebook()
                }
            )
            cellComponents.add(comp)
            cellsContainer.add(comp)
            cellsContainer.add(Box.createVerticalStrut(8))
        }

        cellsContainer.revalidate()
        cellsContainer.repaint()
        if (outlinePanel.isVisible) {
            outlinePanel.updateOutline(notebookModel)
        }
    }

    private fun initKernel() {
        panelScope.launch(Dispatchers.IO) {
            availableKernelSpecs = KernelDiscovery.discoverKernels()
            withContext(Dispatchers.Main) {
                kernelCombo.removeAllItems()
                for (spec in availableKernelSpecs) {
                    kernelCombo.addItem(spec.displayName)
                }

                // Match with model kernelspec if possible
                val matchIdx = availableKernelSpecs.indexOfFirst { it.id == notebookModel.metadata.kernelspec.name }
                if (matchIdx >= 0) {
                    kernelCombo.selectedIndex = matchIdx
                }
            }

            val chosenSpec = if (kernelCombo.selectedIndex >= 0 && kernelCombo.selectedIndex < availableKernelSpecs.size) {
                availableKernelSpecs[kernelCombo.selectedIndex]
            } else {
                availableKernelSpecs.firstOrNull() ?: JupyterKernelSpec("python3", "Python 3", "python", listOf("python", "-m", "ipykernel_launcher", "-f", "{connection_file}"))
            }

            val session = kernelService.getOrCreateSession(virtualFile.path, chosenSpec)
            activeSession = session

            // Observe status flow
            launch {
                session.status.collect { status ->
                    withContext(Dispatchers.Main) {
                        updateKernelStatusDisplay(status)
                    }
                }
            }
        }
    }

    private fun updateKernelStatusDisplay(status: KernelStatus) {
        when (status) {
            KernelStatus.IDLE -> {
                kernelStatusLabel.text = "🟢 Idle"
                kernelStatusLabel.foreground = Color(133, 153, 0) // Green
            }
            KernelStatus.BUSY -> {
                kernelStatusLabel.text = "🟠 Busy"
                kernelStatusLabel.foreground = Color(203, 75, 22) // Orange
            }
            KernelStatus.STARTING -> {
                kernelStatusLabel.text = "🟡 Starting..."
                kernelStatusLabel.foreground = Color(181, 137, 0) // Yellow
            }
            KernelStatus.RESTARTING -> {
                kernelStatusLabel.text = "🟡 Restarting..."
                kernelStatusLabel.foreground = Color(181, 137, 0)
            }
            KernelStatus.DEAD, KernelStatus.DISCONNECTED -> {
                kernelStatusLabel.text = "🔴 Stopped"
                kernelStatusLabel.foreground = Color(220, 50, 47) // Red
            }
        }
    }

    fun runCell(comp: CellComponent) {
        val cell = comp.cell
        if (cell.cellType != CellType.CODE) return

        cell.isExecuting = true
        comp.updateExecutionDisplay()
        cell.clearOutputs()
        comp.renderOutputs()

        panelScope.launch(Dispatchers.IO) {
            val session = activeSession ?: kernelService.getOrCreateSession(virtualFile.path)
            activeSession = session

            val result = session.execute(cell.source) { output ->
                if (output is CellOutput.StreamOutput) {
                    comp.appendStreamOutput(output)
                } else {
                    SwingUtilities.invokeLater {
                        cell.outputs.add(output)
                        comp.renderOutputs()
                    }
                }
            }

            withContext(Dispatchers.Main) {
                cell.isExecuting = false
                cell.executionCount = result.executionCount
                comp.updateExecutionDisplay()
                comp.renderOutputs()
                saveNotebook()
            }

            // Asynchronously refresh Variable Inspector
            runCatching {
                val varService = org.jormungandr.jupyter.variable.VariableInspectorService.getInstance(project)
                varService.refresh(session)
            }
        }
    }

    fun runAllCells() {
        panelScope.launch(Dispatchers.IO) {
            for (comp in cellComponents.toList()) {
                if (comp.cell.cellType == CellType.CODE) {
                    runCell(comp)
                    // Wait until cell completes execution
                    while (comp.cell.isExecuting) {
                        delay(50)
                    }
                }
            }
        }
    }

    fun runAllAbove(comp: CellComponent) {
        val targetIdx = cellComponents.indexOf(comp)
        if (targetIdx <= 0) return
        panelScope.launch(Dispatchers.IO) {
            for (i in 0 until targetIdx) {
                val c = cellComponents.getOrNull(i) ?: continue
                if (c.cell.cellType == CellType.CODE) {
                    runCell(c)
                    while (c.cell.isExecuting) delay(50)
                }
            }
        }
    }

    fun runAllBelow(comp: CellComponent) {
        val targetIdx = cellComponents.indexOf(comp)
        if (targetIdx < 0 || targetIdx >= cellComponents.size - 1) return
        panelScope.launch(Dispatchers.IO) {
            for (i in targetIdx + 1 until cellComponents.size) {
                val c = cellComponents.getOrNull(i) ?: continue
                if (c.cell.cellType == CellType.CODE) {
                    runCell(c)
                    while (c.cell.isExecuting) delay(50)
                }
            }
        }
    }

    fun runFocusedOrFirstCell() {
        val target = cellComponents.firstOrNull { it.cell.cellType == CellType.CODE } ?: return
        runCell(target)
    }

    fun interruptKernel() {
        panelScope.launch(Dispatchers.IO) {
            activeSession?.interrupt()
        }
    }

    fun restartKernel() {
        panelScope.launch(Dispatchers.IO) {
            activeSession?.restart()
            withContext(Dispatchers.Main) {
                for (comp in cellComponents) {
                    if (comp.cell.cellType == CellType.CODE) {
                        comp.cell.executionCount = null
                        comp.updateExecutionDisplay()
                    }
                }
            }
        }
    }

    fun switchKernel(spec: JupyterKernelSpec) {
        panelScope.launch(Dispatchers.IO) {
            kernelService.shutdownSession(virtualFile.path)
            val newSession = kernelService.getOrCreateSession(virtualFile.path, spec)
            activeSession = newSession
        }
    }

    fun addCell(type: CellType) {
        val newCell = notebookModel.addCell(type = type, source = "")
        val comp = CellComponent(
            cell = newCell,
            project = project,
            onRunRequested = { runCell(it) },
            onDeleteRequested = { deleteCell(it) },
            onMoveUpRequested = { moveCellUp(it) },
            onMoveDownRequested = { moveCellDown(it) },
            onModified = { saveNotebook() }
        )
        cellComponents.add(comp)
        cellsContainer.add(comp)
        cellsContainer.add(Box.createVerticalStrut(8))

        cellsContainer.revalidate()
        cellsContainer.repaint()
        saveNotebook()

        // Scroll to new cell
        SwingUtilities.invokeLater {
            scrollPane.verticalScrollBar.value = scrollPane.verticalScrollBar.maximum
        }
    }

    fun deleteCell(comp: CellComponent) {
        if (cellComponents.size <= 1) return // Keep at least one cell
        val idx = cellComponents.indexOf(comp)
        if (idx >= 0) {
            cellComponents.removeAt(idx)
            notebookModel.cells.remove(comp.cell)
            comp.dispose()
            rebuildCellComponents()
            saveNotebook()
        }
    }

    fun moveCellUp(comp: CellComponent) {
        val idx = cellComponents.indexOf(comp)
        if (idx > 0) {
            notebookModel.moveCell(idx, idx - 1)
            rebuildCellComponents()
            saveNotebook()
        }
    }

    fun moveCellDown(comp: CellComponent) {
        val idx = cellComponents.indexOf(comp)
        if (idx >= 0 && idx < cellComponents.size - 1) {
            notebookModel.moveCell(idx, idx + 1)
            rebuildCellComponents()
            saveNotebook()
        }
    }

    fun clearAllOutputs() {
        notebookModel.clearAllOutputs()
        cellComponents.forEach {
            it.updateExecutionDisplay()
            it.renderOutputs()
        }
        saveNotebook()
    }

    fun saveNotebook() {
        panelScope.launch(Dispatchers.IO) {
            runCatching {
                val json = NotebookFormat.writeNotebook(notebookModel)
                ApplicationManager.getApplication().runWriteAction {
                    virtualFile.setBinaryContent(json.toByteArray(Charsets.UTF_8))
                }
            }.onFailure { err ->
                LOG.warn("Could not save notebook to ${virtualFile.name}: ${err.message}")
            }
        }
    }

    private fun getBgColor(): Color = parseHex(themeManager?.currentTheme?.value?.colors?.background, Color(253, 246, 227))
    private fun getSecondaryBgColor(): Color = parseHex(themeManager?.currentTheme?.value?.colors?.secondaryBackground, Color(238, 232, 213))
    private fun getBorderColor(): Color = parseHex(themeManager?.currentTheme?.value?.colors?.border, Color(224, 216, 195))

    private fun parseHex(hex: String?, fallback: Color): Color {
        if (hex.isNullOrBlank()) return fallback
        return try { Color.decode(hex) } catch (e: Exception) { fallback }
    }

    private fun scrollToCell(cellIndex: Int) {
        if (cellIndex in cellComponents.indices) {
            val comp = cellComponents[cellIndex]
            comp.scrollRectToVisible(Rectangle(0, 0, comp.width, comp.height))
            comp.requestFocusInWindow()
        }
    }

    private fun showExportMenu(anchor: Component) {
        val popup = JPopupMenu()

        val exportHtmlItem = JMenuItem("🌐 Standalone HTML Document (.html)").apply {
            addActionListener {
                val chooser = JFileChooser().apply {
                    dialogTitle = "Export Notebook to HTML"
                    selectedFile = File("${virtualFile.nameWithoutExtension}.html")
                }
                if (chooser.showSaveDialog(this@NotebookPanel) == JFileChooser.APPROVE_OPTION) {
                    val file = chooser.selectedFile
                    val html = NotebookExporter.exportToHtml(notebookModel, virtualFile.nameWithoutExtension)
                    file.writeText(html, Charsets.UTF_8)
                    Messages.showInfoMessage(project, "Exported successfully to:\n${file.absolutePath}", "HTML Export Complete")
                }
            }
        }

        val exportPyItem = JMenuItem("🐍 Python Script (.py)").apply {
            addActionListener {
                val chooser = JFileChooser().apply {
                    dialogTitle = "Export Notebook to Python Script"
                    selectedFile = File("${virtualFile.nameWithoutExtension}.py")
                }
                if (chooser.showSaveDialog(this@NotebookPanel) == JFileChooser.APPROVE_OPTION) {
                    val file = chooser.selectedFile
                    val code = NotebookExporter.exportToPython(notebookModel)
                    file.writeText(code, Charsets.UTF_8)
                    Messages.showInfoMessage(project, "Exported successfully to:\n${file.absolutePath}", "Python Export Complete")
                }
            }
        }

        val exportMdItem = JMenuItem("📄 Markdown Document (.md)").apply {
            addActionListener {
                val chooser = JFileChooser().apply {
                    dialogTitle = "Export Notebook to Markdown"
                    selectedFile = File("${virtualFile.nameWithoutExtension}.md")
                }
                if (chooser.showSaveDialog(this@NotebookPanel) == JFileChooser.APPROVE_OPTION) {
                    val file = chooser.selectedFile
                    val md = NotebookExporter.exportToMarkdown(notebookModel)
                    file.writeText(md, Charsets.UTF_8)
                    Messages.showInfoMessage(project, "Exported successfully to:\n${file.absolutePath}", "Markdown Export Complete")
                }
            }
        }

        val exportTexItem = JMenuItem("📑 LaTeX Document (.tex)").apply {
            addActionListener {
                val chooser = JFileChooser().apply {
                    dialogTitle = "Export Notebook to LaTeX"
                    selectedFile = File("${virtualFile.nameWithoutExtension}.tex")
                }
                if (chooser.showSaveDialog(this@NotebookPanel) == JFileChooser.APPROVE_OPTION) {
                    val file = chooser.selectedFile
                    val tex = NotebookExporter.exportToLatex(notebookModel, virtualFile.nameWithoutExtension)
                    file.writeText(tex, Charsets.UTF_8)
                    Messages.showInfoMessage(project, "Exported successfully to:\n${file.absolutePath}", "LaTeX Export Complete")
                }
            }
        }

        val copyPyItem = JMenuItem("📋 Copy Python Code to Clipboard").apply {
            addActionListener {
                val code = NotebookExporter.exportToPython(notebookModel)
                val selection = StringSelection(code)
                Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
                Messages.showInfoMessage(project, "Python code copied to system clipboard!", "Copied")
            }
        }

        popup.add(exportHtmlItem)
        popup.add(exportPyItem)
        popup.add(exportMdItem)
        popup.add(exportTexItem)
        popup.addSeparator()
        popup.add(copyPyItem)

        popup.show(anchor, 0, anchor.height)
    }

    private fun showNotebookDiff() {
        panelScope.launch(Dispatchers.IO) {
            val diskText = runCatching {
                String(virtualFile.contentsToByteArray(), Charsets.UTF_8)
            }.getOrDefault("")

            val diskModel = NotebookFormat.readNotebook(diskText)

            withContext(Dispatchers.Main) {
                val dialog = NotebookDiffDialog(
                    project = project,
                    oldModel = diskModel,
                    newModel = notebookModel,
                    oldTitle = "${virtualFile.name} (Disk)",
                    newTitle = "${virtualFile.name} (Active Buffer)"
                )
                dialog.show()
            }
        }
    }

    fun dispose() {
        panelScope.cancel()
        cellComponents.forEach { it.dispose() }
        cellComponents.clear()
    }
}
