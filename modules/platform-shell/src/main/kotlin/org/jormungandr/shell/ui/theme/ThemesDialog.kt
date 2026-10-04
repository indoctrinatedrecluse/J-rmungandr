package org.jormungandr.shell.ui.theme

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import org.jormungandr.core.theme.JormungandrTheme
import org.jormungandr.core.theme.ThemeManager
import org.jormungandr.shell.icon.JormungandrIcons
import java.awt.*
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder

/**
 * Themes Modal Dialog allowing users to view available visual themes,
 * preview their color swatches, and switch active themes in real time.
 */
class ThemesDialog(project: Project? = null) : DialogWrapper(project, true) {

    private val themeManager: ThemeManager? = runCatching {
        ApplicationManager.getApplication()?.getService(ThemeManager::class.java)
    }.getOrNull()

    private val themeCardsPanel = JPanel()

    init {
        title = "Jörmungandr Visual Themes"
        isResizable = true
        init()
        refreshThemeCards()
    }

    override fun createCenterPanel(): JComponent {
        val root = JPanel(BorderLayout(12, 12)).apply {
            preferredSize = Dimension(620, 420)
            border = EmptyBorder(12, 16, 12, 16)
        }

        // Header
        val header = JPanel(BorderLayout(12, 0)).apply {
            isOpaque = false
            border = EmptyBorder(0, 0, 10, 0)
        }
        val iconLabel = JLabel(JormungandrIcons.LOGO_32)
        val titleText = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            val hLabel = JBLabel("Universal Visual Theme Engine").apply { font = font.deriveFont(Font.BOLD, 16f) }
            val subLabel = JBLabel("Switch themes to apply uniform styling across IDE windows and modular extensions.").apply {
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

        // Cards Container
        themeCardsPanel.apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
        }

        val scrollPane = JBScrollPane(themeCardsPanel).apply {
            border = LineBorder(Color(220, 220, 220), 1)
            verticalScrollBar.unitIncrement = 16
        }
        root.add(scrollPane, BorderLayout.CENTER)

        return root
    }

    private fun refreshThemeCards() {
        themeCardsPanel.removeAll()
        val themes = themeManager?.availableThemes?.value ?: listOf(
            JormungandrTheme.SOLARIZED_LIGHT,
            JormungandrTheme.BUBBLEGUM_BARBIE
        )
        val currentId = themeManager?.currentTheme?.value?.id ?: JormungandrTheme.SOLARIZED_LIGHT.id

        for (theme in themes) {
            val card = createThemeCard(theme, isCurrent = (theme.id == currentId))
            themeCardsPanel.add(card)
            themeCardsPanel.add(Box.createVerticalStrut(10))
        }

        themeCardsPanel.revalidate()
        themeCardsPanel.repaint()
    }

    private fun createThemeCard(theme: JormungandrTheme, isCurrent: Boolean): JPanel {
        val card = JPanel(BorderLayout(12, 8)).apply {
            border = BorderFactory.createCompoundBorder(
                LineBorder(if (isCurrent) Color(38, 139, 210) else Color(225, 220, 210), if (isCurrent) 2 else 1, true),
                EmptyBorder(12, 16, 12, 16)
            )
            background = parseHex(theme.colors.surface, Color.WHITE)
        }

        // Left info
        val infoPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false

            val titleRow = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply { isOpaque = false }
            val nameLabel = JBLabel(theme.name).apply {
                font = font.deriveFont(Font.BOLD, 15f)
                foreground = parseHex(theme.colors.foreground, Color.BLACK)
            }
            titleRow.add(nameLabel)

            if (isCurrent) {
                titleRow.add(Box.createHorizontalStrut(8))
                val activeBadge = JLabel(" CURRENTLY ACTIVE ").apply {
                    font = font.deriveFont(Font.BOLD, 9.5f)
                    foreground = Color.WHITE
                    background = Color(42, 161, 152) // Solarized Cyan
                    isOpaque = true
                    border = EmptyBorder(2, 6, 2, 6)
                }
                titleRow.add(activeBadge)
            }
            add(titleRow)
            add(Box.createVerticalStrut(4))

            val idLabel = JBLabel("ID: ${theme.id} · Mode: ${if (theme.isDark) "Dark" else "Light"}").apply {
                font = font.deriveFont(Font.PLAIN, 10.5f)
                foreground = parseHex(theme.colors.mutedForeground, Color.GRAY)
            }
            add(idLabel)
            add(Box.createVerticalStrut(8))

            // Palette Swatches Row
            val swatches = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply { isOpaque = false }
            swatches.add(createSwatch("BG", theme.colors.background))
            swatches.add(createSwatch("Surface", theme.colors.surface))
            swatches.add(createSwatch("Accent", theme.colors.accent))
            swatches.add(createSwatch("Border", theme.colors.border))
            swatches.add(createSwatch("Grid", theme.dataGrid.headerBackground))
            swatches.add(createSwatch("Text", theme.colors.foreground))
            add(swatches)
        }
        card.add(infoPanel, BorderLayout.CENTER)

        // Right Action Button
        val buttonPanel = JPanel(GridBagLayout()).apply { isOpaque = false }
        val applyBtn = JButton(if (isCurrent) "Active" else "Apply Theme").apply {
            isEnabled = !isCurrent
            isFocusPainted = false
            cursor = if (!isCurrent) Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) else Cursor.getDefaultCursor()
            addActionListener {
                themeManager?.applyTheme(theme.id)
                refreshThemeCards()
            }
        }
        buttonPanel.add(applyBtn)
        card.add(buttonPanel, BorderLayout.EAST)

        return card
    }

    private fun createSwatch(label: String, hex: String): JPanel {
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
        }
        val colorBox = JPanel().apply {
            preferredSize = Dimension(32, 20)
            maximumSize = Dimension(32, 20)
            background = parseHex(hex, Color.GRAY)
            border = LineBorder(Color(180, 180, 180), 1)
        }
        val textLabel = JLabel(label, SwingConstants.CENTER).apply {
            font = font.deriveFont(Font.PLAIN, 9f)
            foreground = Color.GRAY
            alignmentX = Component.CENTER_ALIGNMENT
        }
        panel.add(colorBox)
        panel.add(Box.createVerticalStrut(2))
        panel.add(textLabel)
        return panel
    }

    private fun parseHex(hex: String, fallback: Color): Color {
        return try {
            Color.decode(hex)
        } catch (e: Exception) {
            fallback
        }
    }

    override fun createActions(): Array<Action> {
        return arrayOf(okAction)
    }
}
