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
import com.intellij.ui.components.*
import com.intellij.ui.table.JBTable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.*
import javax.swing.*
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.table.DefaultTableModel

/**
 * Interactive Studio Panel for discovering, launching, and managing remote Jupyter Gateway instances.
 */
class RemoteJupyterGatewayPanel(
    private val project: Project? = null
) : JPanel(BorderLayout()) {

    private val scope = CoroutineScope(Dispatchers.Main)

    private var activeConfig = RemoteGatewayConfig()
    private var client = RemoteJupyterGatewayClient(activeConfig)

    private val statusLabel = JBLabel("Gateway: Standby (Not Connected)")
    private val serverUrlLabel = JBLabel("Server: ${activeConfig.baseUrl}")

    private val tableModel = DefaultTableModel(arrayOf("Kernel ID", "Spec Name", "State", "Connections", "Action"), 0)
    private val table = JBTable(tableModel)

    init {
        buildUi()
    }

    private fun buildUi() {
        val toolbar = JPanel(BorderLayout(8, 0)).apply {
            border = CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Color(220, 220, 220)),
                EmptyBorder(6, 10, 6, 10)
            )
            background = Color(248, 249, 250)
        }

        val leftTools = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply { isOpaque = false }
        val configureBtn = JButton("⚙️ Gateway Settings...").apply {
            isFocusable = false
            addActionListener { openConfigureDialog() }
        }
        val refreshBtn = JButton("🔄 Refresh Kernels").apply {
            isFocusable = false
            addActionListener { refreshRemoteKernels() }
        }
        val spawnBtn = JButton("➕ Launch Remote Kernel").apply {
            isFocusable = false
            addActionListener { spawnNewKernel() }
        }

        leftTools.add(configureBtn)
        leftTools.add(refreshBtn)
        leftTools.add(spawnBtn)

        val rightTools = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply { isOpaque = false }
        rightTools.add(serverUrlLabel)

        toolbar.add(leftTools, BorderLayout.WEST)
        toolbar.add(rightTools, BorderLayout.EAST)
        add(toolbar, BorderLayout.NORTH)

        table.rowHeight = 24
        table.autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS
        add(JBScrollPane(table), BorderLayout.CENTER)

        val statusBar = JPanel(BorderLayout()).apply {
            border = EmptyBorder(4, 10, 4, 10)
            background = Color(248, 249, 250)
        }
        statusBar.add(statusLabel, BorderLayout.WEST)
        add(statusBar, BorderLayout.SOUTH)
    }

    private fun openConfigureDialog() {
        val dialog = RemoteKernelConnectionDialog(project, activeConfig)
        if (dialog.showAndGet()) {
            activeConfig = dialog.getConfiguration()
            client = RemoteJupyterGatewayClient(activeConfig)
            serverUrlLabel.text = "Server: ${activeConfig.baseUrl}"
            refreshRemoteKernels()
        }
    }

    private fun refreshRemoteKernels() {
        statusLabel.text = "Polling remote gateway ${activeConfig.baseUrl}..."
        scope.launch {
            val kernels = client.listRunningKernels()
            withContext(Dispatchers.Main) {
                tableModel.setRowCount(0)
                if (kernels.isEmpty()) {
                    statusLabel.text = "Connected to ${activeConfig.baseUrl} | No running kernels."
                } else {
                    for (k in kernels) {
                        tableModel.addRow(
                            arrayOf<Any>(
                                k.id.take(12),
                                k.name,
                                k.executionState,
                                k.connections,
                                "Active"
                            )
                        )
                    }
                    statusLabel.text = "Connected: ${kernels.size} active remote kernel(s)."
                }
            }
        }
    }

    private fun spawnNewKernel() {
        statusLabel.text = "Spawning new remote kernel on gateway..."
        scope.launch {
            val res = client.startRemoteKernel("python3")
            withContext(Dispatchers.Main) {
                res.fold(
                    onSuccess = { info ->
                        statusLabel.text = "✅ Spawned remote kernel: ${info.id.take(8)}"
                        refreshRemoteKernels()
                    },
                    onFailure = { err ->
                        statusLabel.text = "❌ Failed to spawn: ${err.message}"
                    }
                )
            }
        }
    }
}
