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

package org.jormungandr.dataframe.webapp

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import org.jormungandr.dataframe.model.DataFrame
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.io.File
import javax.swing.*
import javax.swing.border.EmptyBorder

/**
 * Interactive Web App & Dashboard Generator Dialog.
 * Allows users to preview, customize, copy, and save turnkey Streamlit & Gradio web apps.
 */
class WebAppGeneratorDialog(
    private val project: Project?,
    private val dataFrame: DataFrame,
    private val modelName: String? = null,
    private val targetCol: String? = null,
    private val featureCols: List<String> = emptyList()
) : DialogWrapper(project) {

    private val frameworkCombo = JComboBox(WebAppFramework.values())
    private val codeArea = JBTextArea().apply {
        font = Font("Monospaced", Font.PLAIN, 12)
        margin = Insets(8, 8, 8, 8)
    }

    init {
        title = "📱 1-Click Interactive Web App & Dashboard Generator"
        init()
        updateCode()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(8, 8)).apply {
            preferredSize = Dimension(750, 520)
            border = EmptyBorder(8, 8, 8, 8)
        }

        val topControls = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0))
        topControls.add(JBLabel("Target Web Framework:").apply { font = font.deriveFont(Font.BOLD, 12f) })
        topControls.add(frameworkCombo)

        frameworkCombo.addActionListener { updateCode() }

        val copyBtn = JButton("📋 Copy Code").apply {
            isFocusable = false
            addActionListener {
                val sel = StringSelection(codeArea.text)
                Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, sel)
                Messages.showInfoMessage(project, "Web application code copied to clipboard!", "Copied")
            }
        }
        val saveBtn = JButton("💾 Save to File...").apply {
            isFocusable = false
            font = font.deriveFont(Font.BOLD, 11f)
            addActionListener { saveToFile() }
        }

        topControls.add(Box.createHorizontalStrut(10))
        topControls.add(copyBtn)
        topControls.add(saveBtn)

        panel.add(topControls, BorderLayout.NORTH)
        panel.add(JBScrollPane(codeArea), BorderLayout.CENTER)

        val bottomInfo = JBLabel("💡 Run in your terminal via: 'streamlit run streamlit_app.py' or 'python gradio_app.py'").apply {
            font = font.deriveFont(Font.ITALIC, 11f)
            foreground = Color(100, 116, 139)
        }
        panel.add(bottomInfo, BorderLayout.SOUTH)

        return panel
    }

    private fun updateCode() {
        val framework = frameworkCombo.selectedItem as? WebAppFramework ?: WebAppFramework.STREAMLIT
        val code = when (framework) {
            WebAppFramework.STREAMLIT -> InteractiveWebAppGeneratorService.generateStreamlitDashboard(
                df = dataFrame,
                appName = "Data Intelligence Dashboard",
                modelName = modelName,
                targetCol = targetCol,
                featureCols = featureCols
            )
            WebAppFramework.GRADIO -> InteractiveWebAppGeneratorService.generateGradioPlayground(
                df = dataFrame,
                appName = "Interactive ML Playground",
                modelName = modelName ?: "Predictive Model",
                targetCol = targetCol,
                featureCols = featureCols
            )
        }
        codeArea.text = code
        codeArea.caretPosition = 0
    }

    private fun saveToFile() {
        val framework = frameworkCombo.selectedItem as? WebAppFramework ?: WebAppFramework.STREAMLIT
        val chooser = JFileChooser().apply {
            dialogTitle = "Save ${framework.displayName}"
            selectedFile = File(framework.defaultFileName)
        }
        if (chooser.showSaveDialog(contentPane) == JFileChooser.APPROVE_OPTION) {
            val file = chooser.selectedFile
            file.writeText(codeArea.text, Charsets.UTF_8)
            Messages.showInfoMessage(project, "Web app script saved to:\n${file.absolutePath}", "Saved Successfully")
        }
    }
}
