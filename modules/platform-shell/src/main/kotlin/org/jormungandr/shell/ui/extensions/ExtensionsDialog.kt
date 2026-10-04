package org.jormungandr.shell.ui.extensions

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import org.jormungandr.core.extension.ExtensionManager
import org.jormungandr.core.extension.ExtensionState
import org.jormungandr.core.extension.JormungandrExtension
import org.jormungandr.shell.extension.CoreExtensionsRegistry
import org.jormungandr.shell.icon.JormungandrIcons
import java.awt.*
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder

/**
 * Extensions Modal Dialog displaying loaded and active modular extensions,
 * their metadata, supported languages, associated technology stacks, and resource quotas.
 */
class ExtensionsDialog(project: Project? = null) : DialogWrapper(project, true) {

    private val extensionManager: ExtensionManager? = runCatching {
        ApplicationManager.getApplication()?.getService(ExtensionManager::class.java)
    }.getOrNull()

    private val extensionListModel = DefaultListModel<JormungandrExtension>()
    private val extensionList: JBList<JormungandrExtension>
    private val detailPanel = JPanel()

    init {
        title = "Jörmungandr Modular Extensions"
        isResizable = true

        // Ensure default core extensions are registered if not yet bootstrapped
        extensionManager?.let { mgr ->
            if (mgr.loadedExtensions.value.isEmpty()) {
                CoreExtensionsRegistry.ensureCoreExtensionsRegistered(mgr)
            }
            val extensions = mgr.loadedExtensions.value.values.toList()
            for (ext in extensions) {
                extensionListModel.addElement(ext)
            }
        }

        extensionList = JBList(extensionListModel).apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            cellRenderer = ExtensionListCellRenderer()
            fixedCellHeight = 56
            addListSelectionListener {
                val selected = selectedValue
                updateDetailPanel(selected)
            }
        }

        init()

        if (!extensionListModel.isEmpty) {
            extensionList.selectedIndex = 0
        }
    }

    override fun createCenterPanel(): JComponent {
        val root = JPanel(BorderLayout(16, 12)).apply {
            preferredSize = Dimension(740, 460)
            border = EmptyBorder(12, 16, 12, 16)
        }

        // Header Banner
        val header = JPanel(BorderLayout(12, 0)).apply {
            isOpaque = false
            border = EmptyBorder(0, 0, 8, 0)
        }
        val iconLabel = JLabel(JormungandrIcons.LOGO_32)
        val titleText = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            val hLabel = JBLabel("Modular Extension Architecture").apply { font = font.deriveFont(Font.BOLD, 16f) }
            val subLabel = JBLabel("Governed extension lifecycles, memory quotas, and language runtimes.").apply {
                font = font.deriveFont(Font.PLAIN, 11f)
                foreground = Color(110, 110, 110)
            }
            add(hLabel)
            add(Box.createVerticalStrut(2))
            add(subLabel)
        }
        header.add(iconLabel, BorderLayout.WEST)
        header.add(titleText, BorderLayout.CENTER)
        root.add(header, BorderLayout.NORTH)

        // Split Pane (List on Left, Details on Right)
        val leftScroll = JBScrollPane(extensionList).apply {
            preferredSize = Dimension(270, 360)
            border = LineBorder(Color(215, 215, 215), 1)
        }

        detailPanel.apply {
            layout = BorderLayout()
            background = Color(253, 246, 227) // Solarized Base3 cream card
            border = BorderFactory.createCompoundBorder(
                LineBorder(Color(220, 215, 200), 1, true),
                EmptyBorder(16, 20, 16, 20)
            )
        }

        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftScroll, detailPanel).apply {
            dividerLocation = 270
            dividerSize = 6
            isContinuousLayout = true
            border = null
        }
        root.add(splitPane, BorderLayout.CENTER)

        return root
    }

    private fun updateDetailPanel(ext: JormungandrExtension?) {
        detailPanel.removeAll()
        if (ext == null) {
            val emptyLabel = JLabel("Select an extension to view details", SwingConstants.CENTER)
            detailPanel.add(emptyLabel, BorderLayout.CENTER)
            detailPanel.revalidate()
            detailPanel.repaint()
            return
        }

        val m = ext.metadata
        val content = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
        }

        // Title & Version
        val nameLabel = JBLabel(m.displayName).apply {
            font = font.deriveFont(Font.BOLD, 17f)
            foreground = Color(7, 54, 66)
            alignmentX = Component.LEFT_ALIGNMENT
        }
        val idLabel = JBLabel("${ext.id.value} · v${m.version}").apply {
            font = font.deriveFont(Font.PLAIN, 11f)
            foreground = Color(101, 123, 131)
            alignmentX = Component.LEFT_ALIGNMENT
        }
        content.add(nameLabel)
        content.add(Box.createVerticalStrut(2))
        content.add(idLabel)
        content.add(Box.createVerticalStrut(10))

        // State Badge
        val stateBadge = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            alignmentX = Component.LEFT_ALIGNMENT
            isOpaque = false
            val stateText = when (ext.state) {
                ExtensionState.ACTIVE -> "● ACTIVE"
                ExtensionState.INITIALIZED -> "◐ INITIALIZED"
                ExtensionState.PAUSED -> "⏸ PAUSED"
                else -> "○ ${ext.state.name}"
            }
            val stateColor = when (ext.state) {
                ExtensionState.ACTIVE -> Color(42, 161, 152) // Cyan
                ExtensionState.INITIALIZED -> Color(38, 139, 210) // Blue
                else -> Color(147, 161, 161)
            }
            val badge = JLabel(" $stateText ").apply {
                font = font.deriveFont(Font.BOLD, 10.5f)
                foreground = Color.WHITE
                isOpaque = true
                background = stateColor
                border = EmptyBorder(3, 8, 3, 8)
            }
            add(badge)
        }
        content.add(stateBadge)
        content.add(Box.createVerticalStrut(12))

        // Description
        val descLabel = JLabel("<html><div style='width: 380px;'>${m.description}</div></html>").apply {
            alignmentX = Component.LEFT_ALIGNMENT
            foreground = Color(7, 54, 66)
        }
        content.add(descLabel)
        content.add(Box.createVerticalStrut(14))

        // Supported Languages Section
        val langSectionLabel = JBLabel("Supported Languages:").apply {
            font = font.deriveFont(Font.BOLD, 12f)
            foreground = Color(38, 139, 210)
            alignmentX = Component.LEFT_ALIGNMENT
        }
        content.add(langSectionLabel)
        content.add(Box.createVerticalStrut(4))

        val langChips = JPanel(FlowLayout(FlowLayout.LEFT, 4, 4)).apply {
            alignmentX = Component.LEFT_ALIGNMENT
            isOpaque = false
            val languages = if (m.supportedLanguages.isNotEmpty()) m.supportedLanguages else listOf("JVM", "Polyglot")
            for (lang in languages) {
                add(createChip(lang, Color(238, 232, 213), Color(88, 110, 117)))
            }
        }
        content.add(langChips)
        content.add(Box.createVerticalStrut(10))

        // Associated Stacks Section
        val stackSectionLabel = JBLabel("Associated Technology Stacks:").apply {
            font = font.deriveFont(Font.BOLD, 12f)
            foreground = Color(181, 137, 0)
            alignmentX = Component.LEFT_ALIGNMENT
        }
        content.add(stackSectionLabel)
        content.add(Box.createVerticalStrut(4))

        val stackChips = JPanel(FlowLayout(FlowLayout.LEFT, 4, 4)).apply {
            alignmentX = Component.LEFT_ALIGNMENT
            isOpaque = false
            val stacks = if (m.associatedStacks.isNotEmpty()) m.associatedStacks else listOf("IntelliJ Platform")
            for (stack in stacks) {
                add(createChip(stack, Color(254, 237, 222), Color(203, 75, 22)))
            }
        }
        content.add(stackChips)
        content.add(Box.createVerticalStrut(14))

        // Quotas & Governance
        val quotaPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            alignmentX = Component.LEFT_ALIGNMENT
            isOpaque = false
            border = BorderFactory.createCompoundBorder(
                LineBorder(Color(220, 215, 200), 1),
                EmptyBorder(8, 10, 8, 10)
            )
            val heapMb = m.quota.maxHeapBytes / (1024 * 1024)
            val offHeapMb = m.quota.maxOffHeapBytes / (1024 * 1024)
            add(JLabel("<html><b>Heap Quota:</b> ${heapMb} MB &nbsp;|&nbsp; <b>Off-Heap Quota:</b> ${offHeapMb} MB &nbsp;|&nbsp; <b>Worker Threads:</b> ${m.quota.maxWorkerThreads}</html>").apply {
                font = font.deriveFont(10.5f)
                foreground = Color(101, 123, 131)
            })
        }
        content.add(quotaPanel)

        val scroll = JBScrollPane(content).apply {
            border = null
            isOpaque = false
            viewport.isOpaque = false
        }
        detailPanel.add(scroll, BorderLayout.CENTER)
        detailPanel.revalidate()
        detailPanel.repaint()
    }

    private fun createChip(text: String, bg: Color, fg: Color): JLabel {
        return JLabel(text).apply {
            font = font.deriveFont(Font.BOLD, 10.5f)
            isOpaque = true
            background = bg
            foreground = fg
            border = BorderFactory.createCompoundBorder(
                LineBorder(fg.brighter(), 1, true),
                EmptyBorder(3, 8, 3, 8)
            )
        }
    }

    override fun createActions(): Array<Action> {
        return arrayOf(okAction)
    }

    /** Custom list cell renderer for extension overview */
    private inner class ExtensionListCellRenderer : ListCellRenderer<JormungandrExtension> {
        private val panel = JPanel(BorderLayout(8, 0)).apply {
            border = EmptyBorder(6, 10, 6, 10)
        }
        private val titleLabel = JLabel().apply { font = font.deriveFont(Font.BOLD, 12f) }
        private val idLabel = JLabel().apply {
            font = font.deriveFont(Font.PLAIN, 10f)
            foreground = Color.GRAY
        }
        private val stateLabel = JLabel().apply {
            font = font.deriveFont(Font.BOLD, 9.5f)
        }

        init {
            val center = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                isOpaque = false
                add(titleLabel)
                add(Box.createVerticalStrut(2))
                add(idLabel)
            }
            panel.add(center, BorderLayout.CENTER)
            panel.add(stateLabel, BorderLayout.EAST)
        }

        override fun getListCellRendererComponent(
            list: JList<out JormungandrExtension>,
            value: JormungandrExtension?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            if (value != null) {
                titleLabel.text = value.metadata.displayName
                idLabel.text = value.id.value
                val isActive = value.state == ExtensionState.ACTIVE
                stateLabel.text = if (isActive) "ACTIVE" else value.state.name
                stateLabel.foreground = if (isActive) Color(42, 161, 152) else Color.GRAY
            }
            panel.background = if (isSelected) Color(238, 232, 213) else Color.WHITE
            return panel
        }
    }
}
