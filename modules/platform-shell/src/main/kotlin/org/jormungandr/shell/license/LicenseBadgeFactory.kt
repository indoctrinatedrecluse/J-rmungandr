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

import org.jormungandr.core.license.LicenseInfo
import org.jormungandr.core.license.LicenseType
import java.awt.*
import java.awt.geom.RoundRectangle2D
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.border.EmptyBorder

/**
 * Creates bespoke, highly stylized badges for USER, DEV, ADMIN, and TRIAL license tiers
 * with progressively increasing visual style, glowing gradients, and metallic borders.
 */
object LicenseBadgeFactory {

    /**
     * Creates a custom painted Swing badge component.
     */
    fun createBadgeComponent(info: LicenseInfo, large: Boolean = false): JComponent {
        return object : JComponent() {
            private val text = when (info.licenseType) {
                LicenseType.ADMIN -> if (large) "👑 ADMIN PRIVILEGED" else "👑 ADMIN"
                LicenseType.DEVELOPER -> if (large) "⚡ DEV EDITION" else "⚡ DEV"
                LicenseType.USER -> if (large) "👤 USER LICENSE" else "👤 USER"
                LicenseType.TRIAL -> if (large) "⏳ TRIAL (${info.trialDaysRemaining}d LEFT)" else "⏳ TRIAL"
            }

            init {
                isOpaque = false
                font = Font("Segoe UI", Font.BOLD, if (large) 12 else 11)
                val fm = getFontMetrics(font)
                val textWidth = fm.stringWidth(text)
                val paddingX = if (large) 24 else 16
                val height = if (large) 28 else 22
                preferredSize = Dimension(textWidth + paddingX, height)
                maximumSize = Dimension(textWidth + paddingX, height)
                minimumSize = Dimension(textWidth + paddingX, height)
                toolTipText = "${info.licenseType.displayName} - ${info.message}"
            }

            override fun paintComponent(g: Graphics) {
                val g2 = g as? Graphics2D ?: return
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

                val w = width.toDouble()
                val h = height.toDouble()
                val arc = h

                val rect = RoundRectangle2D.Double(1.0, 1.0, w - 2.0, h - 2.0, arc, arc)

                when (info.licenseType) {
                    // TIER 3: ADMIN (Maximum Style & Opulence: Royal gold holographic glow, double metallic border)
                    LicenseType.ADMIN -> {
                        // Outer glow bloom
                        g2.color = Color(251, 191, 36, 60)
                        g2.fill(RoundRectangle2D.Double(0.0, 0.0, w, h, arc, arc))

                        // Multi-stop gold metallic gradient
                        val gradient = GradientPaint(
                            0f, 0f, Color(245, 158, 11), // Amber Gold
                            w.toFloat(), h.toFloat(), Color(180, 83, 9) // Deep Bronze
                        )
                        g2.paint = gradient
                        g2.fill(rect)

                        // Highlight sheen on top
                        val sheen = GradientPaint(0f, 0f, Color(254, 240, 138, 150), 0f, (h / 2).toFloat(), Color(254, 240, 138, 0))
                        g2.paint = sheen
                        g2.fill(rect)

                        // Gold radiant border
                        g2.stroke = BasicStroke(1.8f)
                        g2.color = Color(254, 240, 138)
                        g2.draw(rect)

                        // Inner subtle border
                        g2.stroke = BasicStroke(0.8f)
                        g2.color = Color(251, 191, 36)
                        g2.draw(RoundRectangle2D.Double(2.5, 2.5, w - 5.0, h - 5.0, arc - 2, arc - 2))

                        // Text with shadow
                        g2.font = font
                        val fm = g2.fontMetrics
                        val textX = ((w - fm.stringWidth(text)) / 2).toInt()
                        val textY = (((h - fm.height) / 2) + fm.ascent).toInt()

                        g2.color = Color(69, 26, 3, 200) // Deep text shadow
                        g2.drawString(text, textX + 1, textY + 1)
                        g2.color = Color(255, 255, 255)
                        g2.drawString(text, textX, textY)
                    }

                    // TIER 2: DEVELOPER (Cybernetic neon purple & electric magenta gradient, tech aesthetic)
                    LicenseType.DEVELOPER -> {
                        val gradient = GradientPaint(
                            0f, 0f, Color(124, 58, 237), // Electric Violet
                            w.toFloat(), h.toFloat(), Color(217, 70, 239) // Neon Fuchsia
                        )
                        g2.paint = gradient
                        g2.fill(rect)

                        // Cyber glow border
                        g2.stroke = BasicStroke(1.5f)
                        g2.color = Color(216, 180, 254)
                        g2.draw(rect)

                        // Monospace tech accent dot
                        g2.color = Color(52, 211, 153)
                        g2.fillOval(7, (h / 2 - 3).toInt(), 6, 6)

                        g2.font = font
                        val fm = g2.fontMetrics
                        val textX = ((w - fm.stringWidth(text)) / 2).toInt()
                        val textY = (((h - fm.height) / 2) + fm.ascent).toInt()

                        g2.color = Color(59, 7, 100, 180)
                        g2.drawString(text, textX + 1, textY + 1)
                        g2.color = Color(255, 255, 255)
                        g2.drawString(text, textX, textY)
                    }

                    // TIER 1: USER (Clean, stylish emerald & cyan commercial pro pill)
                    LicenseType.USER -> {
                        val gradient = GradientPaint(
                            0f, 0f, Color(6, 182, 212), // Cyan
                            w.toFloat(), h.toFloat(), Color(16, 185, 129) // Emerald
                        )
                        g2.paint = gradient
                        g2.fill(rect)

                        g2.stroke = BasicStroke(1.2f)
                        g2.color = Color(167, 243, 208)
                        g2.draw(rect)

                        g2.font = font
                        val fm = g2.fontMetrics
                        val textX = ((w - fm.stringWidth(text)) / 2).toInt()
                        val textY = (((h - fm.height) / 2) + fm.ascent).toInt()

                        g2.color = Color(4, 47, 46, 160)
                        g2.drawString(text, textX + 1, textY + 1)
                        g2.color = Color(255, 255, 255)
                        g2.drawString(text, textX, textY)
                    }

                    // TIER 0: TRIAL (Amber & Slate evaluation badge)
                    LicenseType.TRIAL -> {
                        val gradient = GradientPaint(
                            0f, 0f, Color(71, 85, 105), // Slate
                            w.toFloat(), h.toFloat(), Color(100, 116, 139) // Slate Light
                        )
                        g2.paint = gradient
                        g2.fill(rect)

                        g2.stroke = BasicStroke(1.0f)
                        g2.color = Color(203, 213, 225)
                        g2.draw(rect)

                        g2.font = font
                        val fm = g2.fontMetrics
                        val textX = ((w - fm.stringWidth(text)) / 2).toInt()
                        val textY = (((h - fm.height) / 2) + fm.ascent).toInt()

                        g2.color = Color(254, 215, 170) // Soft amber text
                        g2.drawString(text, textX, textY)
                    }
                }
            }
        }
    }

    /**
     * Generates an HTML styled badge representation for inline labels and descriptions.
     */
    fun createHtmlBadge(info: LicenseInfo): String {
        return when (info.licenseType) {
            LicenseType.ADMIN -> {
                "<span style='background: linear-gradient(135deg, #f59e0b, #b45309); color: #ffffff; padding: 3px 8px; border-radius: 12px; font-weight: bold; border: 1px solid #fef08a;'>👑 ADMIN PRIVILEGED</span>"
            }
            LicenseType.DEVELOPER -> {
                "<span style='background: linear-gradient(135deg, #7c3aed, #d946ef); color: #ffffff; padding: 3px 8px; border-radius: 12px; font-weight: bold; border: 1px solid #d8b4fe;'>⚡ DEV EDITION</span>"
            }
            LicenseType.USER -> {
                "<span style='background: linear-gradient(135deg, #06b6d4, #10b981); color: #ffffff; padding: 3px 8px; border-radius: 12px; font-weight: bold; border: 1px solid #a7f3d0;'>👤 USER LICENSE</span>"
            }
            LicenseType.TRIAL -> {
                "<span style='background: #475569; color: #fed7aa; padding: 3px 8px; border-radius: 12px; font-weight: bold;'>⏳ TRIAL (${info.trialDaysRemaining}d LEFT)</span>"
            }
        }
    }
}
