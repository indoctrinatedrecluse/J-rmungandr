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

package org.jormungandr.database.streaming

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import org.jormungandr.dataframe.model.DataFrame
import java.awt.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.*
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder
import javax.swing.table.DefaultTableModel

/**
 * Interactive Real-Time Streaming Data & Timeseries Studio Panel.
 * Features live animated timeseries oscilloscope, continuous JSON event stream inspector,
 * and 1-click "Freeze to DataFrame" snapshotting.
 */
class StreamingTimeseriesStudioPanel(
    private val project: Project? = null,
    private val onDataFrameFrozen: ((DataFrame) -> Unit)? = null
) : JPanel(BorderLayout()) {

    val engine = StreamingDataEngine()

    private val sourceCombo = JComboBox(StreamSourceType.values())
    private val toggleStreamBtn = JButton("▶ Start Stream").apply {
        font = font.deriveFont(Font.BOLD, 11f)
        foreground = Color(16, 185, 129)
        isFocusable = false
    }
    private val bufferSpinner = JSpinner(SpinnerNumberModel(300, 50, 2000, 50)).apply {
        toolTipText = "Sliding Window Buffer Capacity (events)"
        addChangeListener {
            engine.bufferCapacity = (value as? Number)?.toInt() ?: 300
        }
    }
    private val freezeBtn = JButton("❄️ Freeze to DataFrame").apply {
        font = font.deriveFont(Font.BOLD, 11f)
        foreground = Color(37, 99, 235)
        isFocusable = false
        toolTipText = "Snapshot current sliding buffer into an immutable DataFrame"
        addActionListener { freezeBuffer() }
    }
    private val clearBtn = JButton("🧹 Clear").apply {
        font = font.deriveFont(Font.PLAIN, 11f)
        isFocusable = false
        addActionListener {
            engine.clearBuffer()
            tableModel.rowCount = 0
            jsonArea.text = ""
            chartCanvas.repaint()
            updateStatsDisplay()
        }
    }

    private val statsLabel = JBLabel("Idle | 0 events ingested").apply {
        font = font.deriveFont(Font.PLAIN, 11f)
        foreground = Color(100, 116, 139)
    }

    private val chartCanvas = StreamingTimeseriesChartCanvas()

    private val tableModel = DefaultTableModel(
        arrayOf("Time", "ID", "Source", "Metrics", "Payload"), 0
    )
    private val eventTable = JTable(tableModel).apply {
        rowHeight = 22
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        selectionModel.addListSelectionListener {
            val sel = selectedRow
            if (sel in 0 until tableModel.rowCount) {
                val payload = tableModel.getValueAt(sel, 4) as? String ?: ""
                jsonArea.text = formatJson(payload)
                jsonArea.caretPosition = 0
            }
        }
    }

    private val jsonArea = JBTextArea().apply {
        font = Font("Monospaced", Font.PLAIN, 12)
        isEditable = false
        background = Color(248, 250, 252)
        margin = Insets(6, 6, 6, 6)
    }

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())

    init {
        setupUI()
        setupEngineListener()
    }

    private fun setupUI() {
        val topBar = JPanel(BorderLayout()).apply {
            border = CompoundBorder(
                LineBorder(Color(226, 232, 240), 1),
                EmptyBorder(6, 8, 6, 8)
            )
            background = Color(248, 250, 252)
        }

        val left = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply { isOpaque = false }
        left.add(JBLabel("Stream:").apply { font = font.deriveFont(Font.BOLD, 11f) })
        left.add(sourceCombo)
        left.add(toggleStreamBtn)
        left.add(Box.createHorizontalStrut(6))
        left.add(JBLabel("Buffer:"))
        left.add(bufferSpinner)
        left.add(clearBtn)

        val right = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply { isOpaque = false }
        right.add(statsLabel)
        right.add(Box.createHorizontalStrut(8))
        right.add(freezeBtn)

        topBar.add(left, BorderLayout.WEST)
        topBar.add(right, BorderLayout.EAST)
        add(topBar, BorderLayout.NORTH)

        // Split view: Top Chart (oscilloscope), Bottom Split (Event Table & JSON Payload Inspector)
        val bottomSplit = JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT,
            JBScrollPane(eventTable),
            JBScrollPane(jsonArea)
        ).apply {
            dividerLocation = 550
            resizeWeight = 0.65
        }

        val mainSplit = JSplitPane(
            JSplitPane.VERTICAL_SPLIT,
            chartCanvas,
            bottomSplit
        ).apply {
            dividerLocation = 220
            resizeWeight = 0.45
        }

        add(mainSplit, BorderLayout.CENTER)

        toggleStreamBtn.addActionListener {
            if (engine.isStreaming()) {
                engine.stopStream()
                toggleStreamBtn.text = "▶ Start Stream"
                toggleStreamBtn.foreground = Color(16, 185, 129)
            } else {
                val src = sourceCombo.selectedItem as? StreamSourceType ?: StreamSourceType.IOT_TELEMETRY
                engine.startStream(src, intervalMs = 120)
                toggleStreamBtn.text = "⏹ Pause Stream"
                toggleStreamBtn.foreground = Color(239, 68, 68)
            }
        }
    }

    private fun setupEngineListener() {
        engine.addListener { event ->
            SwingUtilities.invokeLater {
                val timeStr = timeFormatter.format(Instant.ofEpochMilli(event.timestamp))
                val metricsStr = event.metrics.entries.joinToString(", ") { "${it.key}: ${String.format("%.1f", it.value)}" }
                tableModel.insertRow(0, arrayOf(timeStr, event.id, event.source, metricsStr, event.payloadJson))

                // Keep table limited to 200 rows for smooth UI
                if (tableModel.rowCount > 200) {
                    tableModel.removeRow(tableModel.rowCount - 1)
                }

                updateStatsDisplay()
                chartCanvas.repaint()
            }
        }
    }

    private fun updateStatsDisplay() {
        val stats = engine.getStats()
        statsLabel.text = "Ingested: ${stats.totalEventsIngested} | Rate: ${String.format("%.1f", stats.eventsPerSecond)} ev/s | Buffer: ${stats.bufferSize}"
    }

    private fun freezeBuffer() {
        val df = engine.freezeToDataFrame()
        if (df.isEmpty) {
            Messages.showWarningDialog(project, "Sliding buffer is currently empty. Start streaming to accumulate events first.", "Buffer Empty")
            return
        }

        onDataFrameFrozen?.invoke(df)
        Messages.showInfoMessage(
            project,
            "Successfully snapshot ${df.rowCount} streaming events across ${df.columnCount} columns into DataFrame!\nColumns: ${df.columns.joinToString { it.name }}",
            "DataFrame Frozen"
        )
    }

    private fun formatJson(raw: String): String {
        return raw.replace(",", ",\n  ")
            .replace("{", "{\n  ")
            .replace("}", "\n}")
    }

    fun dispose() {
        engine.stopStream()
    }

    private inner class StreamingTimeseriesChartCanvas : JPanel() {
        private val colors = listOf(
            Color(59, 130, 246), // Blue
            Color(16, 185, 129), // Emerald Green
            Color(245, 158, 11), // Amber
            Color(239, 68, 68),  // Red
            Color(168, 85, 247)  // Purple
        )

        init {
            background = Color(15, 23, 42) // Deep dark oscilloscope background
            preferredSize = Dimension(600, 220)
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2 = g as? Graphics2D ?: return
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

            val events = engine.getBufferEvents()
            val w = width
            val h = height

            // Draw grid lines
            g2.color = Color(30, 41, 59)
            g2.stroke = BasicStroke(1.0f)
            for (y in 30 until h step 40) {
                g2.drawLine(40, y, w - 20, y)
            }
            for (x in 40 until w step 80) {
                g2.drawLine(x, 20, x, h - 30)
            }

            if (events.isEmpty()) {
                g2.color = Color(148, 163, 184)
                g2.font = Font("SansSerif", Font.ITALIC, 12)
                g2.drawString("Stream is paused. Click '▶ Start Stream' to stream real-time telemetry.", 60, h / 2)
                return
            }

            val metricKeys = events.flatMap { it.metrics.keys }.distinct().take(4)
            val chartX = 50
            val chartY = 25
            val chartW = w - chartX - 25
            val chartH = h - chartY - 35

            val nPts = events.size
            if (nPts < 2) return

            metricKeys.forEachIndexed { idx, metric ->
                val color = colors[idx % colors.size]
                val vals = events.map { it.metrics[metric] ?: 0.0 }
                val minV = vals.minOrNull() ?: 0.0
                val maxV = vals.maxOrNull() ?: 1.0
                val span = (maxV - minV).coerceAtLeast(1e-4)

                fun toY(v: Double): Int = chartY + chartH - (((v - minV) / span) * chartH).toInt()
                fun toX(i: Int): Int = chartX + (i * chartW) / (nPts - 1)

                // Draw line series
                g2.color = color
                g2.stroke = BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)

                for (i in 0 until nPts - 1) {
                    val x1 = toX(i)
                    val y1 = toY(vals[i])
                    val x2 = toX(i + 1)
                    val y2 = toY(vals[i + 1])
                    g2.drawLine(x1, y1, x2, y2)
                }

                // Legend at top
                g2.font = Font("Monospaced", Font.BOLD, 11)
                val currVal = vals.lastOrNull() ?: 0.0
                val legendStr = "● $metric: ${String.format("%.2f", currVal)}"
                g2.drawString(legendStr, 60 + idx * 160, 18)
            }
        }
    }
}
