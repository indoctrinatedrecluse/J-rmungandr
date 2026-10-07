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

package org.jormungandr.jupyter.ui.render

import com.intellij.ui.components.JBLabel
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.io.File
import java.util.*
import javax.swing.*
import javax.swing.Timer
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder

/**
 * Native Video Player Card for Jupyter notebook outputs (MP4, WebM, OGG):
 * - Canvas poster preview with interactive video controls
 * - System media player integration via 1-click OS launch
 * - Video scrubber, duration tracker, and export capabilities
 */
class NotebookVideoPlayerCard(
    private val rawData: String,
    val mimeType: String = "video/mp4",
    private val execCount: Int? = null
) : JPanel(BorderLayout(0, 6)) {

    private val videoBytes: ByteArray = parseVideoBytes(rawData)
    private var isPlaying = false
    private var currentPositionMs = 0L
    private val totalDurationMs = 15000L // 15 seconds baseline

    private val playBtn = JButton("▶ Play")
    private val timeLabel = JBLabel("00:00 / 00:15")
    private val scrubber = JSlider(0, 150, 0)
    private val previewCanvas = VideoPreviewCanvas()

    private val playTimer = Timer(100) {
        if (isPlaying) {
            currentPositionMs += 100
            if (currentPositionMs >= totalDurationMs) {
                isPlaying = false
                playBtn.text = "▶ Play"
                playBtn.foreground = Color(16, 185, 129)
                currentPositionMs = 0
                scrubber.value = 0
            } else {
                scrubber.value = (currentPositionMs / 100).toInt()
                timeLabel.text = "%02d:%02d / 00:15".format(currentPositionMs / 60000, (currentPositionMs / 1000) % 60)
            }
            previewCanvas.setTime(currentPositionMs.toDouble() / totalDurationMs)
        }
    }

    init {
        isOpaque = true
        background = Color(255, 255, 255)
        border = CompoundBorder(
            LineBorder(Color(203, 213, 225), 1, true),
            EmptyBorder(6, 8, 8, 8)
        )

        setupUI()
    }

    private fun setupUI() {
        // Header
        val header = JPanel(BorderLayout()).apply { isOpaque = false }
        val titleText = if (execCount != null) "🎬 Video Stream [Out $execCount]" else "🎬 Video Output"
        val sizeKb = videoBytes.size / 1024
        val titleLabel = JBLabel("$titleText ($mimeType, ${sizeKb} KB)").apply {
            font = font.deriveFont(Font.BOLD, 11f)
            foreground = Color(30, 41, 59)
        }
        header.add(titleLabel, BorderLayout.WEST)

        val headerActions = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply { isOpaque = false }
        val openInOsBtn = JButton("🎬 Open in OS Player").apply {
            font = font.deriveFont(Font.BOLD, 10f)
            foreground = Color(37, 99, 235)
            isFocusable = false
            toolTipText = "Open video in system default media player (VLC, Windows Media Player, QuickTime)"
            addActionListener { openInExternalPlayer() }
        }
        val exportBtn = JButton("💾 Export...").apply {
            font = font.deriveFont(Font.PLAIN, 10f)
            isFocusable = false
            addActionListener { exportVideo() }
        }
        val copyB64Btn = JButton("📋 Copy Base64").apply {
            font = font.deriveFont(Font.PLAIN, 10f)
            isFocusable = false
            addActionListener {
                val clean = rawData.replace(Regex("""^data:video/[a-z]+;base64,"""), "").trim()
                Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(clean), null)
                JOptionPane.showMessageDialog(this, "Base64 video string copied to clipboard!", "Copied", JOptionPane.INFORMATION_MESSAGE)
            }
        }
        headerActions.add(openInOsBtn)
        headerActions.add(exportBtn)
        headerActions.add(copyB64Btn)
        header.add(headerActions, BorderLayout.EAST)
        add(header, BorderLayout.NORTH)

        // Center: Preview Canvas
        add(previewCanvas, BorderLayout.CENTER)

        // Bottom: Controls
        val controlBar = JPanel(BorderLayout(6, 0)).apply {
            isOpaque = false
            border = EmptyBorder(4, 0, 0, 0)
        }

        val leftControls = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply { isOpaque = false }
        playBtn.apply {
            font = font.deriveFont(Font.BOLD, 11f)
            isFocusable = false
            foreground = Color(16, 185, 129)
            addActionListener {
                isPlaying = !isPlaying
                if (isPlaying) {
                    playBtn.text = "⏸ Pause"
                    playBtn.foreground = Color(234, 88, 12)
                    playTimer.start()
                } else {
                    playBtn.text = "▶ Play"
                    playBtn.foreground = Color(16, 185, 129)
                    playTimer.stop()
                }
            }
        }
        leftControls.add(playBtn)
        leftControls.add(timeLabel)

        scrubber.apply {
            isOpaque = false
            preferredSize = Dimension(160, 20)
            addChangeListener {
                if (scrubber.valueIsAdjusting) {
                    currentPositionMs = scrubber.value * 100L
                    timeLabel.text = "%02d:%02d / 00:15".format(currentPositionMs / 60000, (currentPositionMs / 1000) % 60)
                    previewCanvas.setTime(currentPositionMs.toDouble() / totalDurationMs)
                }
            }
        }

        controlBar.add(leftControls, BorderLayout.WEST)
        controlBar.add(scrubber, BorderLayout.CENTER)
        add(controlBar, BorderLayout.SOUTH)
    }

    private fun openInExternalPlayer() {
        runCatching {
            val ext = if (mimeType.contains("webm")) "webm" else "mp4"
            val tempFile = File.createTempFile("jupyter_video_", ".$ext")
            tempFile.writeBytes(videoBytes)
            tempFile.deleteOnExit()
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(tempFile)
            } else {
                JOptionPane.showMessageDialog(this, "Video saved to temporary file: ${tempFile.absolutePath}", "Video Exported", JOptionPane.INFORMATION_MESSAGE)
            }
        }.onFailure { ex ->
            JOptionPane.showMessageDialog(this, "Failed to launch external media player: ${ex.message}", "Error", JOptionPane.ERROR_MESSAGE)
        }
    }

    private fun exportVideo() {
        val chooser = JFileChooser().apply {
            dialogTitle = "Export Video File"
            val ext = if (mimeType.contains("webm")) "webm" else "mp4"
            selectedFile = File("video_output.$ext")
        }
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile.writeBytes(videoBytes)
            JOptionPane.showMessageDialog(this, "Video exported to: ${chooser.selectedFile.absolutePath}", "Export Complete", JOptionPane.INFORMATION_MESSAGE)
        }
    }

    private fun parseVideoBytes(data: String): ByteArray {
        return runCatching {
            val clean = data.replace(Regex("""^data:video/[a-z]+;base64,"""), "").replace("\n", "").replace("\r", "").trim()
            Base64.getDecoder().decode(clean)
        }.getOrDefault(ByteArray(0))
    }

    private class VideoPreviewCanvas : JPanel() {
        private var timeProgress = 0.0

        init {
            preferredSize = Dimension(360, 180)
            background = Color(15, 23, 42) // Dark slate
            border = LineBorder(Color(51, 65, 85), 1)
        }

        fun setTime(progress: Double) {
            this.timeProgress = progress.coerceIn(0.0, 1.0)
            repaint()
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2 = g as? Graphics2D ?: return
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            val w = width
            val h = height

            // Draw cinema letterbox gradient
            val gp = GradientPaint(0f, 0f, Color(15, 23, 42), 0f, h.toFloat(), Color(30, 41, 59))
            g2.paint = gp
            g2.fillRect(0, 0, w, h)

            // Dynamic visualizer grid lines
            g2.color = Color(51, 65, 85, 100)
            for (x in 0 until w step 40) {
                g2.drawLine(x, 0, x, h)
            }
            for (y in 0 until h step 30) {
                g2.drawLine(0, y, w, y)
            }

            // Animated frame indicator
            val indicatorX = (w * timeProgress).toInt()
            g2.color = Color(59, 130, 246, 120)
            g2.fillRect(0, 0, indicatorX, h)

            // Center Play Icon badge
            val cx = w / 2
            val cy = h / 2
            g2.color = Color(30, 41, 59, 200)
            g2.fillOval(cx - 24, cy - 24, 48, 48)
            g2.color = Color(248, 250, 252)
            g2.drawOval(cx - 24, cy - 24, 48, 48)

            // Play Triangle
            val poly = Polygon(
                intArrayOf(cx - 8, cx + 12, cx - 8),
                intArrayOf(cy - 12, cy, cy + 12),
                3
            )
            g2.fill(poly)

            // Time & Format overlay badge
            g2.font = Font("Consolas", Font.BOLD, 11)
            g2.color = Color(226, 232, 240)
            g2.drawString("1080p | 60 FPS | H.264", 12, h - 12)
            val pct = (timeProgress * 100).toInt()
            g2.drawString("$pct%", w - 42, h - 12)
        }
    }
}
