/*
 * Copyright (c) 2025-2026 indoctrinatedrecluse
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

package org.jormungandr.shell.license

import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import org.jormungandr.core.license.LicenseService
import java.awt.*
import java.awt.geom.RoundRectangle2D
import javax.swing.*
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder

/**
 * Reusable visual lock overlay displayed over proprietary extensions
 * when running in unlicensed trial or expired mode.
 */
class LicenseLockOverlay(
    private val extensionName: String,
    private val project: Project? = null,
    private val onUnlocked: (() -> Unit)? = null
) : JPanel(GridBagLayout()) {

    private val licenseService = LicenseService.getInstance()
    private val badgeContainer = JPanel(FlowLayout(FlowLayout.CENTER, 0, 0)).apply { isOpaque = false }

    init {
        isOpaque = true
        background = JBColor(Color(248, 250, 252, 245), Color(15, 23, 42, 245))

        val card = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            background = JBColor(Color(255, 255, 255), Color(30, 41, 59))
            border = CompoundBorder(
                LineBorder(JBColor(Color(226, 232, 240), Color(51, 65, 85)), 1, true),
                EmptyBorder(28, 36, 28, 36)
            )
            maximumSize = Dimension(460, 320)
        }

        val iconLabel = JLabel("🔒").apply {
            font = Font("Segoe UI Emoji", Font.PLAIN, 42)
            alignmentX = Component.CENTER_ALIGNMENT
        }
        card.add(iconLabel)
        card.add(Box.createVerticalStrut(12))

        val titleLabel = JBLabel("Commercial License Required").apply {
            font = font.deriveFont(Font.BOLD, 17f)
            alignmentX = Component.CENTER_ALIGNMENT
        }
        card.add(titleLabel)
        card.add(Box.createVerticalStrut(6))

        val subLabel = JLabel("<html><center style='width: 320px; color: #64748b; font-size: 11px;'>" +
                "<b>$extensionName</b> is a proprietary component of the Jörmungandr Professional Data Suite. " +
                "Activate a valid User, Developer, or Admin license to unlock complete functionality." +
                "</center></html>").apply {
            alignmentX = Component.CENTER_ALIGNMENT
        }
        card.add(subLabel)
        card.add(Box.createVerticalStrut(14))

        badgeContainer.alignmentX = Component.CENTER_ALIGNMENT
        card.add(badgeContainer)
        card.add(Box.createVerticalStrut(16))

        val activateBtn = JButton("🔑 Register & Activate License...").apply {
            font = font.deriveFont(Font.BOLD, 12f)
            alignmentX = Component.CENTER_ALIGNMENT
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addActionListener {
                val dialog = LicenseDialog(project)
                if (dialog.showAndGet() || licenseService.isLicensed()) {
                    if (licenseService.isLicensed()) {
                        onUnlocked?.invoke()
                    }
                }
            }
        }
        card.add(activateBtn)

        add(card)
        updateBadge()

        licenseService.addListener { info ->
            SwingUtilities.invokeLater {
                updateBadge()
                if (info.isFullyLicensed) {
                    onUnlocked?.invoke()
                }
            }
        }
    }

    private fun updateBadge() {
        badgeContainer.removeAll()
        badgeContainer.add(LicenseBadgeFactory.createBadgeComponent(licenseService.currentLicense.value, large = true))
        badgeContainer.revalidate()
        badgeContainer.repaint()
    }
}
