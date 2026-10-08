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
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.jormungandr.core.license.LicenseInfo
import org.jormungandr.core.license.LicenseService
import org.jormungandr.core.license.LicenseType
import org.jormungandr.shell.icon.JormungandrIcons
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.*
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder

/**
 * Interactive Licensing Modal displaying active tier, hardware fingerprint (HWID),
 * key masking with reveal/hide toggle, activation input, and license removal.
 */
class LicenseDialog(project: Project? = null) : DialogWrapper(project, true) {

    private val licenseService = LicenseService.getInstance()
    private var isKeyRevealed = false

    // Active License Card Components
    private val badgeHolder = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply { isOpaque = false }
    private val keyField = JBTextField(24).apply {
        isEditable = false
        font = Font("Consolas", Font.BOLD, 12)
    }
    private val revealBtn = JButton("👁️ Reveal")
    private val statusValueLabel = JBLabel("")
    private val hwidLabel = JBLabel("").apply { font = Font("Consolas", Font.PLAIN, 11) }
    private val userLabel = JBLabel("")
    private val expiryLabel = JBLabel("")

    // Activation Form Components
    private val inputKeyField = JBTextField(24)
    private val inputUsernameField = JBTextField(16)
    private val inputPasswordField = JPasswordField(16)
    private val adminNoteLabel = JBLabel("* Note: Admin licenses (ADM-) do not require username or password.").apply {
        font = font.deriveFont(Font.ITALIC, 10.5f)
        foreground = JBColor(Color(100, 116, 139), Color(148, 163, 184))
    }

    private val activateBtn = JButton("🔑 Activate License Online")
    private val refreshBtn = JButton("🔄 Check & Validate")
    private val removeBtn = JButton("🗑️ Remove License (Revert to Trial)")
    private val copyHwidBtn = JButton("📋 Copy HWID")

    private val statusMessageLabel = JBLabel("Ready").apply {
        font = font.deriveFont(Font.PLAIN, 11f)
    }

    init {
        title = "Jörmungandr Commercial Licensing"
        isResizable = false
        init()
        updateUI(licenseService.currentLicense.value)
    }

    override fun createCenterPanel(): JComponent {
        val root = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            preferredSize = Dimension(620, 560)
            border = EmptyBorder(16, 20, 16, 20)
        }

        // 1. Header Banner
        val header = JPanel(BorderLayout(12, 0)).apply {
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
        }
        val iconLabel = JLabel(JormungandrIcons.LOGO_32)
        val titleText = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            val hLabel = JBLabel("Jörmungandr Licensing Manager").apply { font = font.deriveFont(Font.BOLD, 16f) }
            val subLabel = JBLabel("Manage enterprise tiers, hardware node-locks, and extension access.").apply {
                font = font.deriveFont(Font.PLAIN, 11f)
                foreground = JBColor(Color(100, 116, 139), Color(148, 163, 184))
            }
            add(hLabel)
            add(Box.createVerticalStrut(2))
            add(subLabel)
        }
        header.add(iconLabel, BorderLayout.WEST)
        header.add(titleText, BorderLayout.CENTER)
        root.add(header)
        root.add(Box.createVerticalStrut(14))

        // 2. Active License Information Card
        val card = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            alignmentX = Component.LEFT_ALIGNMENT
            background = JBColor(Color(248, 250, 252), Color(24, 33, 47))
            border = CompoundBorder(
                LineBorder(JBColor(Color(226, 232, 240), Color(51, 65, 85)), 1, true),
                EmptyBorder(12, 14, 12, 14)
            )
        }

        val badgeRow = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(badgeHolder, BorderLayout.WEST)
            val actionsRow = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply { isOpaque = false }
            actionsRow.add(refreshBtn)
            actionsRow.add(removeBtn)
            add(actionsRow, BorderLayout.EAST)
        }
        card.add(badgeRow)
        card.add(Box.createVerticalStrut(10))

        // Key Row with Reveal Button
        val keyRow = JPanel(BorderLayout(8, 0)).apply { isOpaque = false }
        keyRow.add(JBLabel("License Key:").apply { font = font.deriveFont(Font.BOLD, 11.5f) }, BorderLayout.WEST)
        val keyBox = JPanel(BorderLayout(6, 0)).apply { isOpaque = false }
        keyBox.add(keyField, BorderLayout.CENTER)
        revealBtn.addActionListener {
            isKeyRevealed = !isKeyRevealed
            revealBtn.text = if (isKeyRevealed) "🔒 Hide" else "👁️ Reveal"
            updateUI(licenseService.currentLicense.value)
        }
        keyBox.add(revealBtn, BorderLayout.EAST)
        keyRow.add(keyBox, BorderLayout.CENTER)
        card.add(keyRow)
        card.add(Box.createVerticalStrut(8))

        // Details Grid
        val detailsGrid = JPanel(GridLayout(3, 2, 8, 4)).apply { isOpaque = false }
        detailsGrid.add(statusValueLabel)
        detailsGrid.add(userLabel)
        detailsGrid.add(expiryLabel)

        val hwidBox = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            isOpaque = false
            add(JBLabel("HWID:").apply { font = font.deriveFont(Font.BOLD, 11f) })
            add(hwidLabel)
            copyHwidBtn.font = font.deriveFont(Font.PLAIN, 10f)
            copyHwidBtn.addActionListener {
                Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(licenseService.hwid), null)
                statusMessageLabel.text = "Copied HWID to clipboard!"
            }
            add(copyHwidBtn)
        }
        detailsGrid.add(hwidBox)
        card.add(detailsGrid)
        root.add(card)
        root.add(Box.createVerticalStrut(16))

        // 3. New License Activation Form
        val activationPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            alignmentX = Component.LEFT_ALIGNMENT
            background = JBColor(Color(255, 255, 255), Color(30, 41, 59))
            border = CompoundBorder(
                LineBorder(JBColor(Color(226, 232, 240), Color(51, 65, 85)), 1, true),
                EmptyBorder(12, 14, 12, 14)
            )
        }

        activationPanel.add(JBLabel("Activate New Commercial License").apply {
            font = font.deriveFont(Font.BOLD, 13f)
        })
        activationPanel.add(Box.createVerticalStrut(8))

        val formGrid = JPanel(GridLayout(3, 2, 8, 8)).apply { isOpaque = false }
        formGrid.add(JBLabel("License Key (USER-, DEV-, ADM-):").apply { font = font.deriveFont(Font.BOLD, 11f) })
        formGrid.add(inputKeyField)

        formGrid.add(JBLabel("Username (Account Owner):").apply { font = font.deriveFont(Font.BOLD, 11f) })
        formGrid.add(inputUsernameField)

        formGrid.add(JBLabel("Password (Optional for ADM-):").apply { font = font.deriveFont(Font.BOLD, 11f) })
        formGrid.add(inputPasswordField)
        activationPanel.add(formGrid)
        activationPanel.add(Box.createVerticalStrut(4))
        activationPanel.add(adminNoteLabel)
        activationPanel.add(Box.createVerticalStrut(10))

        val btnRow = JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply { isOpaque = false }
        btnRow.add(activateBtn)
        activationPanel.add(btnRow)

        root.add(activationPanel)
        root.add(Box.createVerticalStrut(12))

        // Status bar
        root.add(statusMessageLabel)

        setupListeners()
        return root
    }

    private fun setupListeners() {
        activateBtn.addActionListener { doActivate() }
        refreshBtn.addActionListener { performOnlineValidation() }
        removeBtn.addActionListener { doRemove() }
    }

    private fun updateUI(info: LicenseInfo) {
        badgeHolder.removeAll()
        badgeHolder.add(LicenseBadgeFactory.createBadgeComponent(info, large = true))
        badgeHolder.revalidate()
        badgeHolder.repaint()

        if (info.licenseType == LicenseType.TRIAL || info.licenseKey.isBlank()) {
            keyField.text = "NO-ACTIVE-LICENSE (TRIAL MODE)"
            removeBtn.isEnabled = false
            revealBtn.isEnabled = false
        } else {
            keyField.text = if (isKeyRevealed) info.licenseKey else info.getMaskedKey()
            removeBtn.isEnabled = true
            revealBtn.isEnabled = true
        }

        statusValueLabel.text = "Status: ${info.status.displayName}"
        userLabel.text = "Owner: ${info.username.ifBlank { "N/A" }}"
        hwidLabel.text = info.hwid

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val exp = info.expiresAt
        expiryLabel.text = when {
            info.licenseType == LicenseType.TRIAL -> "Trial Remaining: ${info.trialDaysRemaining} days"
            exp != null -> "Expires: ${sdf.format(Date(exp))}"
            else -> "Subscription: Perpetual / Active"
        }

        statusMessageLabel.text = info.message
    }

    private fun doActivate() {
        val key = inputKeyField.text.trim().uppercase()
        val username = inputUsernameField.text.trim()
        val password = String(inputPasswordField.password)

        if (key.isBlank()) {
            JOptionPane.showMessageDialog(rootPane, "Please enter a valid License Key.", "Validation Error", JOptionPane.WARNING_MESSAGE)
            return
        }

        activateBtn.isEnabled = false
        statusMessageLabel.text = "Contacting licensing server at https://licensor-h5zdysrkqa-uc.a.run.app..."

        GlobalScope.launch(Dispatchers.IO) {
            val result = licenseService.activateLicense(key, username, password)
            SwingUtilities.invokeLater {
                activateBtn.isEnabled = true
                if (result.isSuccess) {
                    val info = result.getOrThrow()
                    updateUI(info)
                    inputKeyField.text = ""
                    inputUsernameField.text = ""
                    inputPasswordField.text = ""
                    JOptionPane.showMessageDialog(rootPane, "License activated successfully!\nTier: ${info.licenseType.displayName}", "License Activated", JOptionPane.INFORMATION_MESSAGE)
                } else {
                    val err = result.exceptionOrNull()?.message ?: "Unknown error"
                    statusMessageLabel.text = "Activation failed: $err"
                    JOptionPane.showMessageDialog(rootPane, "Activation Failed:\n$err", "Activation Error", JOptionPane.ERROR_MESSAGE)
                }
            }
        }
    }

    private fun performOnlineValidation() {
        refreshBtn.isEnabled = false
        statusMessageLabel.text = "Validating machine license with server..."

        GlobalScope.launch(Dispatchers.IO) {
            val result = licenseService.validateCurrentLicense()
            SwingUtilities.invokeLater {
                refreshBtn.isEnabled = true
                if (result.isSuccess) {
                    val info = result.getOrThrow()
                    updateUI(info)
                    statusMessageLabel.text = "Validation completed successfully: ${info.status.displayName}"
                } else {
                    val err = result.exceptionOrNull()?.message ?: "Unknown error"
                    statusMessageLabel.text = "Validation failed: $err"
                }
            }
        }
    }

    private fun doRemove() {
        val confirm = JOptionPane.showConfirmDialog(
            rootPane,
            "Are you sure you want to remove this license from this machine?\nYour installation will revert to the 60-day trial mode.",
            "Confirm Remove License",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        )
        if (confirm == JOptionPane.YES_OPTION) {
            val trialInfo = licenseService.removeLicense()
            updateUI(trialInfo)
            JOptionPane.showMessageDialog(rootPane, "License removed. Switched to evaluation trial mode.", "License Removed", JOptionPane.INFORMATION_MESSAGE)
        }
    }

    override fun createActions(): Array<Action> {
        return arrayOf(okAction)
    }
}
