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

package org.jormungandr.dataframe.ui

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import org.jormungandr.core.ai.*
import org.jormungandr.dataframe.model.DataFrame
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.*
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder

/**
 * Interactive In-IDE Data Science Copilot Panel powered by Google Gemini AI.
 * Offers Natural Language querying (NL-to-SQL, NL-to-Pandas), dataset explanations,
 * outlier and pattern detection, and direct one-click code execution.
 */
class DataScienceCopilotPanel(
    private var dataFrame: DataFrame,
    private val project: Project? = null,
    private val onExecuteSql: ((String) -> Unit)? = null,
    private val onInsertNotebook: ((String) -> Unit)? = null
) : JPanel(BorderLayout()) {

    private val conversationHistory = mutableListOf<CopilotMessage>()

    // Header Controls
    private val modelCombo = JComboBox(arrayOf("gemini-2.5-flash", "gemini-2.5-pro")).apply {
        selectedItem = GeminiCopilotSettings.selectedModel
        addActionListener {
            GeminiCopilotSettings.selectedModel = selectedItem?.toString() ?: "gemini-2.5-flash"
        }
    }
    private val contextBadge = JBLabel("Dataset: ${dataFrame.name} (${dataFrame.columnCount} cols, ${dataFrame.rowCount} rows)").apply {
        font = font.deriveFont(Font.BOLD, 11f)
        foreground = Color(70, 130, 180)
    }
    private val apiKeyStatusLabel = JBLabel("").apply {
        font = font.deriveFont(Font.PLAIN, 11f)
    }

    // Chat Message Container
    private val messagesBox = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        background = Color(248, 249, 250)
        border = EmptyBorder(8, 8, 8, 8)
    }
    private val scrollPane = JBScrollPane(messagesBox).apply {
        verticalScrollBar.unitIncrement = 16
        border = null
    }

    // Input Controls
    private val inputArea = JBTextArea(3, 30).apply {
        lineWrap = true
        wrapStyleWord = true
        font = Font("SansSerif", Font.PLAIN, 12)
        margin = Insets(6, 6, 6, 6)
    }
    private val sendBtn = JButton("🚀 Ask Copilot").apply {
        isFocusable = false
        font = font.deriveFont(Font.BOLD, 11f)
        background = Color(66, 133, 244)
        foreground = Color.WHITE
        addActionListener { sendMessage() }
    }
    private val loadingLabel = JBLabel("").apply {
        foreground = Color(100, 100, 100)
    }

    init {
        updateApiKeyStatus()

        val topPanel = JPanel(BorderLayout()).apply {
            border = EmptyBorder(6, 8, 6, 8)
        }
        val headerLeft = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0))
        val headerRight = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0))

        val apiKeyBtn = JButton("🔑 API Key").apply {
            isFocusable = false
            font = font.deriveFont(Font.PLAIN, 11f)
            toolTipText = "Configure Gemini API Key"
            addActionListener { promptForApiKey() }
        }

        val clearBtn = JButton("🧹 Clear").apply {
            isFocusable = false
            font = font.deriveFont(Font.PLAIN, 11f)
            addActionListener { clearChat() }
        }

        headerLeft.add(JBLabel("Model:"))
        headerLeft.add(modelCombo)
        headerLeft.add(contextBadge)
        headerLeft.add(apiKeyStatusLabel)

        headerRight.add(apiKeyBtn)
        headerRight.add(clearBtn)

        topPanel.add(headerLeft, BorderLayout.WEST)
        topPanel.add(headerRight, BorderLayout.EAST)

        // Quick Prompt Pills
        val pillsPanel = createPromptPillsPanel()

        val northContainer = JPanel(BorderLayout())
        northContainer.add(topPanel, BorderLayout.NORTH)
        northContainer.add(pillsPanel, BorderLayout.SOUTH)

        // Bottom Input Container
        val bottomPanel = JPanel(BorderLayout()).apply {
            border = EmptyBorder(6, 8, 6, 8)
        }
        val inputWrap = JPanel(BorderLayout()).apply {
            border = CompoundBorder(LineBorder(Color(200, 200, 200), 1, true), EmptyBorder(2, 2, 2, 2))
            add(inputArea, BorderLayout.CENTER)
        }

        val sendWrap = JPanel(BorderLayout()).apply {
            border = EmptyBorder(0, 6, 0, 0)
            add(sendBtn, BorderLayout.CENTER)
            add(loadingLabel, BorderLayout.SOUTH)
        }

        bottomPanel.add(inputWrap, BorderLayout.CENTER)
        bottomPanel.add(sendWrap, BorderLayout.EAST)

        // Keyboard handler (Enter sends, Shift+Enter adds newline)
        inputArea.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER && !e.isShiftDown) {
                    e.consume()
                    sendMessage()
                }
            }
        })

        add(northContainer, BorderLayout.NORTH)
        add(scrollPane, BorderLayout.CENTER)
        add(bottomPanel, BorderLayout.SOUTH)

        // Initial welcome message
        addCopilotMessageBubble(
            CopilotMessage(
                role = "model",
                content = "👋 **Welcome to Jörmungandr Data Science Copilot!**\n\n" +
                "I am integrated with your active DataFrame `${dataFrame.name}` (${dataFrame.rowCount} rows, ${dataFrame.columnCount} columns).\n\n" +
                "You can ask me questions in plain English, for example:\n" +
                "- *\"Show me top 10 records by ${dataFrame.columns.firstOrNull { it.isNumeric }?.name ?: "value"}\"*\n" +
                "- *\"Calculate category breakdown and average\"*\n" +
                "- *\"Suggest 3D visualizations and outlier cleaning steps\"*\n" +
                "- *\"Generate a Python Pandas pipeline for feature engineering\"*"
            )
        )
    }

    private fun createPromptPillsPanel(): JPanel {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
            border = EmptyBorder(2, 6, 4, 6)
        }

        fun addPill(title: String, promptTemplate: String) {
            val pill = JButton(title).apply {
                isFocusable = false
                font = font.deriveFont(Font.PLAIN, 10.5f)
                margin = Insets(1, 6, 1, 6)
                addActionListener {
                    inputArea.text = promptTemplate
                    sendMessage()
                }
            }
            panel.add(pill)
        }

        val dictBtn = JButton("📚 Data Dictionary").apply {
            isFocusable = false
            font = font.deriveFont(Font.BOLD, 10.5f)
            margin = Insets(1, 6, 1, 6)
            addActionListener {
                val dict = org.jormungandr.dataframe.agent.AgenticDataStudioService.generateDataDictionary(dataFrame)
                val md = org.jormungandr.dataframe.agent.AgenticDataStudioService.formatDictionaryAsMarkdown(dict)
                val msg = CopilotMessage("model", md)
                conversationHistory.add(msg)
                addCopilotMessageBubble(msg)
            }
        }
        panel.add(dictBtn)

        val numCol = dataFrame.columns.firstOrNull { it.isNumeric }?.name ?: "value"
        val catCol = dataFrame.columns.firstOrNull { !it.isNumeric }?.name ?: "category"

        addPill("🔍 NL to SQL", "Write a SQL query to find top 10 rows ordered by $numCol descending.")
        addPill("🐼 NL to Pandas", "Create a Python Pandas data transformation pipeline grouping by $catCol and computing summary stats.")
        addPill("📊 Suggest Visualizations", "What are the most insightful 2D and 3D charts for this dataset?")
        addPill("🧹 Data Cleaning Strategy", "Analyze missing values, outliers, and suggest an optimal cleaning pipeline.")
        addPill("🧠 Explain Patterns", "Explain the distributions, correlations, and potential anomalies in this data.")

        return panel
    }

    fun updateDataFrame(newDf: DataFrame) {
        this.dataFrame = newDf
        contextBadge.text = "Dataset: ${newDf.name} (${newDf.columnCount} cols, ${newDf.rowCount} rows)"
    }

    private fun updateApiKeyStatus() {
        if (GeminiCopilotSettings.hasApiKey) {
            apiKeyStatusLabel.text = "🟢 Gemini Connected"
            apiKeyStatusLabel.foreground = Color(30, 140, 30)
        } else {
            apiKeyStatusLabel.text = "⚡ Agentic Intelligence"
            apiKeyStatusLabel.foreground = Color(200, 120, 20)
        }
    }

    private fun promptForApiKey() {
        val current = GeminiCopilotSettings.customApiKey ?: ""
        val key = JOptionPane.showInputDialog(
            this,
            "Enter Google Gemini API Key (or set GEMINI_API_KEY environment variable):\n" +
            "Get a free key from Google AI Studio: https://aistudio.google.com/",
            current
        )
        if (key != null) {
            GeminiCopilotSettings.customApiKey = key.trim()
            updateApiKeyStatus()
        }
    }

    private fun clearChat() {
        conversationHistory.clear()
        messagesBox.removeAll()
        messagesBox.revalidate()
        messagesBox.repaint()
    }

    private fun sendMessage() {
        val text = inputArea.text.trim()
        if (text.isBlank()) return

        inputArea.text = ""
        val userMsg = CopilotMessage("user", text)
        conversationHistory.add(userMsg)
        addUserMessageBubble(userMsg)

        loadingLabel.text = "✨ Thinking..."
        sendBtn.isEnabled = false

        val context = buildDataContext()

        Thread {
            val response = if (GeminiCopilotSettings.hasApiKey) {
                GeminiCopilotService.askCopilot(
                    userPrompt = text,
                    context = context,
                    conversationHistory = conversationHistory
                )
            } else {
                val agentic = org.jormungandr.dataframe.agent.AgenticDataStudioService.translateNaturalLanguageQuery(dataFrame, text)
                val body = StringBuilder()
                body.append("🤖 **Agentic Data Intelligence:**\n\n")
                body.append("${agentic.explanation}\n\n")
                body.append("```sql\n${agentic.duckDbSql}\n```\n\n")
                body.append("```python\n${agentic.pandasCode}\n```\n")
                if (agentic.suggestedChartType != null) {
                    body.append("\n📈 *Suggested Chart:* `${agentic.suggestedChartType}` along X: `${agentic.suggestedXCol}`, Y: `${agentic.suggestedYCol}`")
                }
                CopilotMessage(
                    role = "model",
                    content = body.toString(),
                    extractedCode = agentic.duckDbSql,
                    codeLanguage = "sql"
                )
            }
            conversationHistory.add(response)

            SwingUtilities.invokeLater {
                loadingLabel.text = ""
                sendBtn.isEnabled = true
                addCopilotMessageBubble(response)
            }
        }.start()
    }

    private fun buildDataContext(): CopilotDataContext {
        val colInfos = dataFrame.columns.map { col ->
            CopilotColumnInfo(
                name = col.name,
                type = col.category.displayName,
                isNumeric = col.isNumeric,
                nullCount = col.nullCount,
                minVal = col.minVal,
                maxVal = col.maxVal,
                sampleValues = dataFrame.rows.take(5).mapNotNull { row ->
                    val idx = dataFrame.getColumnIndex(col.name)
                    if (idx in row.indices) row[idx]?.toString() else null
                }.distinct()
            )
        }

        return CopilotDataContext(
            datasetName = dataFrame.name,
            columns = colInfos,
            rowCount = dataFrame.rowCount.toLong(),
            targetEngine = "SQL",
            dialect = "SQLite"
        )
    }

    private fun addUserMessageBubble(msg: CopilotMessage) {
        val bubble = JPanel(BorderLayout()).apply {
            background = Color(230, 242, 255)
            border = CompoundBorder(
                LineBorder(Color(190, 215, 245), 1, true),
                EmptyBorder(6, 10, 6, 10)
            )
            maximumSize = Dimension(Short.MAX_VALUE.toInt(), 500)
        }

        val header = JBLabel("🧑 You").apply {
            font = font.deriveFont(Font.BOLD, 11f)
            foreground = Color(30, 80, 150)
        }
        val content = JBTextArea(msg.content).apply {
            isEditable = false
            isOpaque = false
            lineWrap = true
            wrapStyleWord = true
            font = Font("SansSerif", Font.PLAIN, 12)
        }

        bubble.add(header, BorderLayout.NORTH)
        bubble.add(content, BorderLayout.CENTER)

        val wrapper = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = EmptyBorder(3, 30, 3, 0)
            add(bubble, BorderLayout.EAST)
        }

        messagesBox.add(wrapper)
        messagesBox.revalidate()
        scrollBottom()
    }

    private fun addCopilotMessageBubble(msg: CopilotMessage) {
        val bubble = JPanel(BorderLayout()).apply {
            background = Color.WHITE
            border = CompoundBorder(
                LineBorder(Color(220, 220, 220), 1, true),
                EmptyBorder(8, 10, 8, 10)
            )
            maximumSize = Dimension(Short.MAX_VALUE.toInt(), 800)
        }

        val header = JBLabel("✨ Gemini Data Copilot").apply {
            font = font.deriveFont(Font.BOLD, 11f)
            foreground = Color(66, 133, 244)
        }

        val content = JBTextArea(msg.content).apply {
            isEditable = false
            isOpaque = false
            lineWrap = true
            wrapStyleWord = true
            font = Font("SansSerif", Font.PLAIN, 12)
        }

        bubble.add(header, BorderLayout.NORTH)
        bubble.add(content, BorderLayout.CENTER)

        // Action Toolbar for extracted code
        val codeText = msg.extractedCode
        if (!codeText.isNullOrBlank()) {
            val code = codeText
            val lang = msg.codeLanguage ?: "sql"

            val actionToolbar = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
                isOpaque = false
                border = EmptyBorder(4, 0, 0, 0)
            }

            if (lang == "sql" || onExecuteSql != null) {
                val runBtn = JButton("▶ Run SQL").apply {
                    isFocusable = false
                    font = font.deriveFont(Font.BOLD, 10.5f)
                    foreground = Color(20, 120, 20)
                    toolTipText = "Execute this SQL query directly"
                    addActionListener {
                        onExecuteSql?.invoke(code)
                    }
                }
                actionToolbar.add(runBtn)
            }

            val copyBtn = JButton("📋 Copy Code").apply {
                isFocusable = false
                font = font.deriveFont(Font.PLAIN, 10.5f)
                addActionListener {
                    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(code), null)
                }
            }
            actionToolbar.add(copyBtn)

            val plotBtn = JButton("📈 Render Plot").apply {
                isFocusable = false
                font = font.deriveFont(Font.PLAIN, 10.5f)
                toolTipText = "Pipes visual chart of this dataset to Scientific Plots"
                addActionListener {
                    val img = java.awt.image.BufferedImage(900, 550, java.awt.image.BufferedImage.TYPE_INT_RGB)
                    val g = img.createGraphics()
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    g.color = Color(253, 246, 227)
                    g.fillRect(0, 0, 900, 550)
                    g.color = Color(38, 139, 210)
                    g.font = Font("Segoe UI", Font.BOLD, 18)
                    g.drawString("📊 AI Agent Plot: ${dataFrame.name}", 50, 45)
                    g.color = Color(100, 110, 120)
                    g.font = Font("Segoe UI", Font.PLAIN, 12)
                    g.drawString("Piped from Data Science Copilot", 50, 70)
                    val numCols = dataFrame.columns.filter { it.isNumeric }
                    if (numCols.isNotEmpty()) {
                        val numColIdx = dataFrame.getColumnIndex(numCols.first().name)
                        val vals = dataFrame.rows.take(20).mapNotNull {
                            val v = it.getOrNull(numColIdx)
                            (v as? Number)?.toDouble() ?: v?.toString()?.toDoubleOrNull()
                        }
                        val maxV = vals.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
                        g.color = Color(42, 161, 152)
                        for ((idx, v) in vals.withIndex()) {
                            val h = ((v / maxV) * 320).toInt()
                            g.fillRect(60 + idx * 40, 480 - h, 30, h)
                        }
                    }
                    g.dispose()
                    val item = org.jormungandr.core.plot.PlotItem(
                        id = java.util.UUID.randomUUID().toString(),
                        title = "📊 Agentic Chart: ${dataFrame.name}",
                        source = "Data Copilot",
                        timestamp = System.currentTimeMillis(),
                        image = img
                    )
                    org.jormungandr.core.plot.PlotManagerService.getInstance().addPlot(item)
                }
            }
            actionToolbar.add(plotBtn)

            if (onInsertNotebook != null) {
                val insertBtn = JButton("➕ Insert in Notebook").apply {
                    isFocusable = false
                    font = font.deriveFont(Font.PLAIN, 10.5f)
                    addActionListener {
                        onInsertNotebook.invoke(code)
                    }
                }
                actionToolbar.add(insertBtn)
            }

            bubble.add(actionToolbar, BorderLayout.SOUTH)
        }

        val wrapper = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = EmptyBorder(3, 0, 3, 30)
            add(bubble, BorderLayout.WEST)
        }

        messagesBox.add(wrapper)
        messagesBox.revalidate()
        scrollBottom()
    }

    private fun scrollBottom() {
        SwingUtilities.invokeLater {
            val vbar = scrollPane.verticalScrollBar
            vbar.value = vbar.maximum
        }
    }
}
