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

package org.jormungandr.shell.r

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import kotlinx.coroutines.*
import java.awt.*
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.*
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder

/**
 * Interactive REPL Console Panel for R programming in Jörmungandr.
 * Supports multi-line input, history recall, script execution,
 * automatic ggplot2 plot bridging to Scientific Plots Panel,
 * and tabular inspection bridging to DataFrame Viewer Studio.
 */
class RReplConsolePanel(
    val project: Project? = null
) : JPanel(BorderLayout()) {

    private val engine = RReplEngine()
    private val discoveryService = RDiscoveryService.getInstance()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val outputArea = JBTextArea()
    private val inputArea = JBTextArea(4, 40)
    private val statusLabel = JBLabel("Ready")
    private val rInfoLabel = JBLabel()

    private val history = mutableListOf<String>()
    private var historyIndex = -1

    init {
        buildUi()
        updateEnvironmentBadge()
        appendBanner()
    }

    private fun buildUi() {
        // --- 1. Top Toolbar ---
        val toolbar = JPanel(BorderLayout(8, 0)).apply {
            border = CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Color(220, 220, 220)),
                EmptyBorder(6, 10, 6, 10)
            )
            background = Color(248, 249, 250)
        }

        val leftTools = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply { isOpaque = false }
        val runBtn = JButton("▶ Run (Ctrl+Enter)").apply {
            isFocusable = false
            font = font.deriveFont(Font.BOLD, 12f)
            background = Color(42, 161, 152)
            foreground = Color.WHITE
            addActionListener { executeCurrentInput() }
        }
        leftTools.add(runBtn)

        val snippetCombo = JComboBox(
            arrayOf(
                "Quick Snippets / Templates...",
                "ggplot2 Scatter & Regression Trend",
                "mtcars Statistical Summary & Model",
                "iris Species Distribution Boxplot",
                "Arrow / Columnar Dataframe Load",
                "Monte Carlo Normal Simulation"
            )
        ).apply {
            isFocusable = false
            addActionListener {
                val selected = selectedItem as? String ?: return@addActionListener
                loadSnippet(selected)
            }
        }
        leftTools.add(snippetCombo)

        val clearBtn = JButton("🧹 Clear").apply {
            isFocusable = false
            addActionListener {
                outputArea.text = ""
                appendBanner()
            }
        }
        leftTools.add(clearBtn)

        val rightTools = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply { isOpaque = false }

        val openDfBtn = JButton("📊 DataFrame Studio").apply {
            isFocusable = false
            toolTipText = "Inspect active dataframes in DataFrame Viewer Studio"
            addActionListener {
                project?.let { p ->
                    ToolWindowManager.getInstance(p).getToolWindow("DataFrame Viewer")?.activate(null)
                }
            }
        }
        rightTools.add(openDfBtn)

        val openPlotsBtn = JButton("📈 Scientific Plots").apply {
            isFocusable = false
            toolTipText = "Open centralized Scientific Plots Panel"
            addActionListener {
                project?.let { p ->
                    ToolWindowManager.getInstance(p).getToolWindow("Scientific Plots")?.activate(null)
                }
            }
        }
        rightTools.add(openPlotsBtn)

        toolbar.add(leftTools, BorderLayout.WEST)
        toolbar.add(rightTools, BorderLayout.EAST)
        add(toolbar, BorderLayout.NORTH)

        // --- 2. Main Terminal & Editor Split Pane ---
        outputArea.apply {
            isEditable = false
            font = Font("Consolas", Font.PLAIN, 13)
            background = Color(253, 246, 227) // Solarized Base3
            foreground = Color(7, 54, 66)     // Solarized Base02
            border = EmptyBorder(8, 8, 8, 8)
            lineWrap = true
            wrapStyleWord = true
        }

        inputArea.apply {
            font = Font("Consolas", Font.PLAIN, 13)
            background = Color(255, 255, 255)
            foreground = Color(0, 0, 0)
            border = EmptyBorder(6, 6, 6, 6)
            lineWrap = true
            wrapStyleWord = true
            addKeyListener(object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent) {
                    if (e.isControlDown && e.keyCode == KeyEvent.VK_ENTER) {
                        e.consume()
                        executeCurrentInput()
                    } else if (e.keyCode == KeyEvent.VK_UP && text.lines().size <= 1) {
                        recallHistory(-1)
                    } else if (e.keyCode == KeyEvent.VK_DOWN && text.lines().size <= 1) {
                        recallHistory(1)
                    }
                }
            })
        }

        val outputScroll = JBScrollPane(outputArea).apply {
            border = BorderFactory.createEmptyBorder()
        }

        val inputPanel = JPanel(BorderLayout()).apply {
            border = CompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Color(210, 210, 210)),
                EmptyBorder(4, 4, 4, 4)
            )
            background = Color(245, 245, 245)
            val promptLabel = JBLabel(" R >  ").apply {
                font = Font("Consolas", Font.BOLD, 13)
                foreground = Color(38, 139, 210) // Solarized Blue
            }
            add(promptLabel, BorderLayout.WEST)
            add(JBScrollPane(inputArea), BorderLayout.CENTER)
        }

        val splitPane = JSplitPane(JSplitPane.VERTICAL_SPLIT, outputScroll, inputPanel).apply {
            resizeWeight = 0.8
            dividerSize = 4
            border = BorderFactory.createEmptyBorder()
        }
        add(splitPane, BorderLayout.CENTER)

        // --- 3. Bottom Status Bar ---
        val statusBar = JPanel(BorderLayout()).apply {
            border = CompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Color(220, 220, 220)),
                EmptyBorder(4, 10, 4, 10)
            )
            background = Color(248, 249, 250)
        }
        statusBar.add(statusLabel, BorderLayout.WEST)
        statusBar.add(rInfoLabel, BorderLayout.EAST)
        add(statusBar, BorderLayout.SOUTH)
    }

    private fun updateEnvironmentBadge() {
        val install = discoveryService.getInstallation()
        if (install.isAvailable) {
            val pkgs = if (install.installedPackages.isNotEmpty()) {
                " | ${install.installedPackages.take(5).joinToString(", ")}"
            } else ""
            rInfoLabel.text = "🟢 ${install.majorMinorVersion}$pkgs"
            rInfoLabel.foreground = Color(42, 161, 152)
        } else {
            rInfoLabel.text = "🟡 R Not Detected (Simulated Mode Active)"
            rInfoLabel.foreground = Color(181, 137, 0)
        }
    }

    private fun appendBanner() {
        val banner = """
            ================================================================================
             J Ö R M U N G A N D R   R   R E P L   C O N S O L E
             Interactive Statistical & Graphical Environment
             • Press Ctrl+Enter to evaluate R expressions
             • ggplot2 / plot() graphics auto-bridge to Scientific Plots Panel
             • head() / view() dataframes auto-bridge to DataFrame Viewer Studio
            ================================================================================
            
        """.trimIndent()
        outputArea.append(banner)
    }

    private fun executeCurrentInput() {
        val code = inputArea.text.trim()
        if (code.isEmpty()) return

        history.add(code)
        historyIndex = history.size
        inputArea.text = ""

        statusLabel.text = "Executing R statement..."
        outputArea.append("R> $code\n")

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                engine.execute(code)
            }

            if (result.stdout.isNotEmpty()) {
                outputArea.append(result.stdout)
                if (!result.stdout.endsWith("\n")) outputArea.append("\n")
            }
            if (result.stderr.isNotEmpty()) {
                outputArea.append("[STDERR] ${result.stderr}\n")
            }

            val plotMsg = if (result.generatedPlotFiles.isNotEmpty()) " (${result.generatedPlotFiles.size} plot bridged)" else ""
            val csvMsg = if (result.exportedCsvFiles.isNotEmpty()) " (${result.exportedCsvFiles.size} dataframe bridged)" else ""
            statusLabel.text = "Finished in ${result.executionTimeMs}ms$plotMsg$csvMsg"

            outputArea.caretPosition = outputArea.document.length
        }
    }

    private fun recallHistory(direction: Int) {
        if (history.isEmpty()) return
        historyIndex = (historyIndex + direction).coerceIn(0, history.size - 1)
        inputArea.text = history[historyIndex]
        inputArea.caretPosition = inputArea.text.length
    }

    private fun loadSnippet(name: String) {
        val snippet = when (name) {
            "ggplot2 Scatter & Regression Trend" -> """
                library(ggplot2)
                ggplot(mtcars, aes(x = wt, y = mpg, color = factor(cyl))) +
                  geom_point(size = 3) +
                  geom_smooth(method = "lm", se = TRUE) +
                  theme_minimal() +
                  labs(title = "Fuel Economy vs Weight by Engine Cylinders",
                       x = "Weight (1000 lbs)", y = "Miles/(US) Gallon")
            """.trimIndent()

            "mtcars Statistical Summary & Model" -> """
                summary(mtcars)
                model <- lm(mpg ~ wt + hp + cyl, data = mtcars)
                summary(model)
            """.trimIndent()

            "iris Species Distribution Boxplot" -> """
                library(ggplot2)
                ggplot(iris, aes(x = Species, y = Sepal.Length, fill = Species)) +
                  geom_boxplot(alpha = 0.8) +
                  geom_jitter(width = 0.2, alpha = 0.5) +
                  theme_light() +
                  labs(title = "Iris Sepal Length by Species")
            """.trimIndent()

            "Arrow / Columnar Dataframe Load" -> """
                library(arrow)
                df <- as.data.frame(read_parquet("dataset.parquet"))
                head(df)
            """.trimIndent()

            "Monte Carlo Normal Simulation" -> """
                set.seed(42)
                sim_data <- rnorm(5000, mean = 100, sd = 15)
                hist(sim_data, breaks = 40, col = "skyblue", 
                     main = "Monte Carlo Normal Distribution Simulation",
                     xlab = "Simulated Values")
            """.trimIndent()

            else -> return
        }

        inputArea.text = snippet
        inputArea.requestFocus()
    }
}
