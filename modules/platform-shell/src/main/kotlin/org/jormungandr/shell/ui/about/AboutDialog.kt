package org.jormungandr.shell.ui.about

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import org.jormungandr.shell.icon.JormungandrIcons
import java.awt.*
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder

/**
 * About Dialog displaying application metadata, author attribution,
 * and a toggleable embedded read-only textbox containing the Apache 2.0 license.
 */
class AboutDialog(project: Project? = null) : DialogWrapper(project, true) {

    private val licenseScrollPane: JBScrollPane
    private val toggleLicenseButton: JButton
    private var isLicenseVisible = false

    init {
        title = "About Jörmungandr"
        isResizable = true

        // Read embedded license from classpath
        val licenseContent = loadLicenseText()

        val licenseTextArea = JTextArea(licenseContent).apply {
            isEditable = false
            font = Font(Font.MONOSPACED, Font.PLAIN, 11)
            lineWrap = true
            wrapStyleWord = true
            background = Color(245, 245, 245)
            foreground = Color(50, 50, 50)
            margin = Insets(8, 8, 8, 8)
            caretPosition = 0
        }

        licenseScrollPane = JBScrollPane(licenseTextArea).apply {
            preferredSize = Dimension(540, 200)
            border = LineBorder(Color(200, 200, 200), 1)
            isVisible = false
        }

        toggleLicenseButton = JButton("View Full Apache 2.0 License ▼").apply {
            isFocusPainted = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addActionListener {
                isLicenseVisible = !isLicenseVisible
                licenseScrollPane.isVisible = isLicenseVisible
                text = if (isLicenseVisible) "Hide License ▲" else "View Full Apache 2.0 License ▼"
                window?.pack()
            }
        }

        init()
    }

    override fun createCenterPanel(): JComponent {
        val rootPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = EmptyBorder(16, 20, 16, 20)
        }

        // Header Panel (Icon + Titles)
        val headerPanel = JPanel(BorderLayout(16, 0)).apply {
            alignmentX = Component.LEFT_ALIGNMENT
            isOpaque = false
        }

        val iconLabel = JLabel(JormungandrIcons.LOGO_64).apply {
            verticalAlignment = SwingConstants.TOP
        }
        headerPanel.add(iconLabel, BorderLayout.WEST)

        val textPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
        }

        val titleLabel = JBLabel("Jörmungandr").apply {
            font = font.deriveFont(Font.BOLD, 22f)
        }
        val taglineLabel = JBLabel("The Modular Python, Data Science & Analytics IDE").apply {
            font = font.deriveFont(Font.BOLD, 12f)
            foreground = Color(42, 161, 152) // Solarized Cyan
        }
        val versionLabel = JBLabel("Version 0.1.0-alpha · Built on IntelliJ Platform 2024.3").apply {
            font = font.deriveFont(Font.PLAIN, 11f)
            foreground = Color(120, 120, 120)
        }

        textPanel.add(titleLabel)
        textPanel.add(Box.createVerticalStrut(4))
        textPanel.add(taglineLabel)
        textPanel.add(Box.createVerticalStrut(2))
        textPanel.add(versionLabel)
        headerPanel.add(textPanel, BorderLayout.CENTER)

        rootPanel.add(headerPanel)
        rootPanel.add(Box.createVerticalStrut(14))

        // Brief Description
        val descLabel = JLabel(
            "<html><div style='width: 480px;'>" +
            "<b>Jörmungandr</b> is an open-source, modular IDE tailored for Python and modern Data Science. " +
            "It unites interactive Jupyter notebooks, zero-copy Apache Arrow dataframe exploration, " +
            "and multi-engine SQL/NoSQL databases with governed extension memory management." +
            "</div></html>"
        ).apply {
            alignmentX = Component.LEFT_ALIGNMENT
        }
        rootPanel.add(descLabel)
        rootPanel.add(Box.createVerticalStrut(12))

        // Author & Attribution Notice
        val authorPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            alignmentX = Component.LEFT_ALIGNMENT
            border = BorderFactory.createCompoundBorder(
                LineBorder(Color(230, 220, 200), 1, true),
                EmptyBorder(8, 10, 8, 10)
            )
            background = Color(253, 246, 227) // Solarized Base3 cream
        }

        val authorLabel = JLabel("<html><b>Author:</b> indoctrinatedrecluse (2025–2026)</html>").apply {
            foreground = Color(7, 54, 66)
        }
        val upstreamLabel = JLabel(
            "<html><b>Upstream Attribution:</b> Based on the open-source <i>IntelliJ Platform Community Edition</i> " +
            "and <i>Python Community Plugins</i> by JetBrains s.r.o. (Apache 2.0 License).</html>"
        ).apply {
            foreground = Color(101, 123, 131)
            font = font.deriveFont(10.5f)
        }

        authorPanel.add(authorLabel)
        authorPanel.add(Box.createVerticalStrut(4))
        authorPanel.add(upstreamLabel)
        rootPanel.add(authorPanel)
        rootPanel.add(Box.createVerticalStrut(14))

        // Toggleable License Section
        val licenseHeader = JPanel(BorderLayout()).apply {
            alignmentX = Component.LEFT_ALIGNMENT
            isOpaque = false
        }
        licenseHeader.add(toggleLicenseButton, BorderLayout.WEST)

        rootPanel.add(licenseHeader)
        rootPanel.add(Box.createVerticalStrut(8))
        rootPanel.add(licenseScrollPane.apply { alignmentX = Component.LEFT_ALIGNMENT })

        return rootPanel
    }

    override fun createActions(): Array<Action> {
        return arrayOf(okAction)
    }

    private fun loadLicenseText(): String {
        val stream = AboutDialog::class.java.getResourceAsStream("/LICENSE")
        return stream?.bufferedReader()?.use { it.readText() }
            ?: "Apache License 2.0\nCopyright 2025-2026 indoctrinatedrecluse\n\n(Full LICENSE file available in repository root)"
    }
}
