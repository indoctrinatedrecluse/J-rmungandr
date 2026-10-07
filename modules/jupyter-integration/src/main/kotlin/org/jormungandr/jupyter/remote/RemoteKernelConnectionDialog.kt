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

package org.jormungandr.jupyter.remote

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.*
import java.awt.datatransfer.StringSelection
import javax.swing.*
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder

/**
 * Interactive Dialog for connecting to Remote Jupyter Servers, Cloud GPU Clusters,
 * JupyterHub instances, and configuring SSH tunnels.
 */
class RemoteKernelConnectionDialog(
    project: Project? = null,
    initialConfig: RemoteGatewayConfig = RemoteGatewayConfig()
) : DialogWrapper(project) {

    private val scope = CoroutineScope(Dispatchers.Main)

    private val nameField = JBTextField(initialConfig.name)
    private val urlField = JBTextField(initialConfig.baseUrl)
    private val tokenField = JPasswordField(initialConfig.token ?: "")

    private val sshCheckbox = JCheckBox("Enable SSH Port Forwarding Tunnel", initialConfig.useSshTunnel)
    private val sshHostField = JBTextField(initialConfig.sshHost)
    private val sshPortField = JBTextField(initialConfig.sshPort.toString())
    private val sshUserField = JBTextField(initialConfig.sshUser)
    private val sshKeyField = JBTextField(initialConfig.sshKeyPath ?: "")

    private val statusLabel = JBLabel("Ready to connect")
    private val copySshBtn = JButton("📋 Copy SSH Command")
    private val testBtn = JButton("⚡ Test Connection")

    private val presetCombo = JComboBox(
        arrayOf(
            "Custom Configuration...",
            "Localhost Jupyter Server (http://localhost:8888)",
            "Remote Linux GPU Server (SSH Tunnel)",
            "AWS EC2 / GCP Compute Engine Instance",
            "Google Colab / Kaggle SSH Tunnel"
        )
    )

    init {
        title = "🌐 Remote Jupyter Gateway & Cloud/SSH Kernel Client"
        init()
        updateSshFieldsVisibility()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, 10)).apply {
            preferredSize = Dimension(580, 420)
            border = EmptyBorder(8, 8, 8, 8)
        }

        // Presets
        val presetPanel = JPanel(BorderLayout(8, 0)).apply {
            add(JBLabel("Connection Preset:"), BorderLayout.WEST)
            add(presetCombo, BorderLayout.CENTER)
        }
        presetCombo.addActionListener { applyPreset(presetCombo.selectedIndex) }

        // Form
        val formPanel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            insets = Insets(4, 4, 4, 4)
        }

        var row = 0

        // Server Name
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.25
        formPanel.add(JBLabel("Connection Name:"), gbc)
        gbc.gridx = 1; gbc.gridy = row; gbc.weightx = 0.75
        formPanel.add(nameField, gbc)
        row++

        // Base URL
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.25
        formPanel.add(JBLabel("Jupyter URL:"), gbc)
        gbc.gridx = 1; gbc.gridy = row; gbc.weightx = 0.75
        formPanel.add(urlField, gbc)
        row++

        // Token
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.25
        formPanel.add(JBLabel("Token / Password:"), gbc)
        gbc.gridx = 1; gbc.gridy = row; gbc.weightx = 0.75
        formPanel.add(tokenField, gbc)
        row++

        // SSH Tunnel Section
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2
        formPanel.add(sshCheckbox, gbc)
        gbc.gridwidth = 1
        row++

        sshCheckbox.addActionListener { updateSshFieldsVisibility() }

        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.25
        formPanel.add(JBLabel("SSH Host:Port:"), gbc)
        val hostPortPanel = JPanel(BorderLayout(4, 0)).apply {
            add(sshHostField, BorderLayout.CENTER)
            sshPortField.columns = 5
            add(sshPortField, BorderLayout.EAST)
        }
        gbc.gridx = 1; gbc.gridy = row; gbc.weightx = 0.75
        formPanel.add(hostPortPanel, gbc)
        row++

        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.25
        formPanel.add(JBLabel("SSH Username:"), gbc)
        gbc.gridx = 1; gbc.gridy = row; gbc.weightx = 0.75
        formPanel.add(sshUserField, gbc)
        row++

        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.25
        formPanel.add(JBLabel("Identity Key (.pem/.id_rsa):"), gbc)
        gbc.gridx = 1; gbc.gridy = row; gbc.weightx = 0.75
        formPanel.add(sshKeyField, gbc)
        row++

        // Status & Actions
        val actionPanel = JPanel(BorderLayout(8, 0)).apply {
            border = CompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Color(220, 220, 220)),
                EmptyBorder(8, 4, 4, 4)
            )
        }

        statusLabel.font = statusLabel.font.deriveFont(Font.BOLD, 11f)
        actionPanel.add(statusLabel, BorderLayout.WEST)

        val btnBox = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0))
        testBtn.addActionListener { testConnection() }
        copySshBtn.addActionListener { copySshCommand() }
        btnBox.add(copySshBtn)
        btnBox.add(testBtn)
        actionPanel.add(btnBox, BorderLayout.EAST)

        val contentBox = JPanel(BorderLayout(0, 8)).apply {
            add(presetPanel, BorderLayout.NORTH)
            add(formPanel, BorderLayout.CENTER)
        }

        panel.add(contentBox, BorderLayout.CENTER)
        panel.add(actionPanel, BorderLayout.SOUTH)

        return panel
    }

    private fun updateSshFieldsVisibility() {
        val enabled = sshCheckbox.isSelected
        sshHostField.isEnabled = enabled
        sshPortField.isEnabled = enabled
        sshUserField.isEnabled = enabled
        sshKeyField.isEnabled = enabled
        copySshBtn.isEnabled = enabled
    }

    private fun applyPreset(idx: Int) {
        when (idx) {
            1 -> {
                nameField.text = "Localhost Jupyter Server"
                urlField.text = "http://localhost:8888"
                sshCheckbox.isSelected = false
            }
            2 -> {
                nameField.text = "Remote GPU Server"
                urlField.text = "http://localhost:8888"
                sshCheckbox.isSelected = true
                sshHostField.text = "gpu-box.internal"
                sshUserField.text = "ubuntu"
            }
            3 -> {
                nameField.text = "AWS EC2 GPU Instance"
                urlField.text = "http://localhost:8888"
                sshCheckbox.isSelected = true
                sshHostField.text = "ec2-user@ec2-instance.compute.amazonaws.com"
                sshUserField.text = "ec2-user"
                sshKeyField.text = "~/.ssh/my-key.pem"
            }
            4 -> {
                nameField.text = "Google Colab Tunnel"
                urlField.text = "http://localhost:8888"
                sshCheckbox.isSelected = true
                sshHostField.text = "colab.google.com"
                sshUserField.text = "root"
            }
        }
        updateSshFieldsVisibility()
    }

    private fun testConnection() {
        statusLabel.text = "Connecting..."
        statusLabel.foreground = Color.DARK_GRAY

        val config = getConfiguration()
        val client = RemoteJupyterGatewayClient(config)

        scope.launch {
            val res = client.testConnection()
            withContext(Dispatchers.Main) {
                res.fold(
                    onSuccess = { msg ->
                        statusLabel.text = "🟢 Connected: HTTP 200 OK"
                        statusLabel.foreground = Color(40, 167, 69)
                    },
                    onFailure = { err ->
                        statusLabel.text = "🔴 Connection Failed: ${err.message?.take(40)}..."
                        statusLabel.foreground = Color(220, 53, 69)
                    }
                )
            }
        }
    }

    private fun copySshCommand() {
        val config = getConfiguration()
        val client = RemoteJupyterGatewayClient(config)
        val cmd = client.generateSshTunnelCommand()
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(cmd), null)
        statusLabel.text = "Copied SSH tunnel command to clipboard!"
    }

    fun getConfiguration(): RemoteGatewayConfig {
        return RemoteGatewayConfig(
            name = nameField.text.trim(),
            baseUrl = urlField.text.trim(),
            token = String(tokenField.password).trim().takeIf { it.isNotBlank() },
            useSshTunnel = sshCheckbox.isSelected,
            sshHost = sshHostField.text.trim(),
            sshPort = sshPortField.text.trim().toIntOrNull() ?: 22,
            sshUser = sshUserField.text.trim(),
            sshKeyPath = sshKeyField.text.trim().takeIf { it.isNotBlank() }
        )
    }
}
