package org.jormungandr.shell.hardware

import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import java.awt.*
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder

/**
 * Interactive Hardware Telemetry & GPU / VRAM Monitor Panel.
 * Displays real-time NVIDIA CUDA, AMD ROCm, Apple Metal, and Host RAM/CPU telemetry
 * with live VRAM capacity bars and one-click PyTorch CUDA cache flush.
 */
class HardwareTelemetryPanel : JPanel(BorderLayout(0, 10)) {

    private val cardsContainer = JPanel()
    private val hostRamBadge = JLabel()
    private val cpuCoresBadge = JLabel()

    init {
        isOpaque = false
        border = EmptyBorder(8, 8, 8, 8)

        buildUi()
        refreshTelemetry()
    }

    private fun buildUi() {
        // Header Toolbar
        val header = JPanel(BorderLayout(8, 0)).apply {
            isOpaque = false
            border = EmptyBorder(0, 0, 6, 0)
        }

        val titlePanel = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply { isOpaque = false }
        val titleLabel = JBLabel("⚡ Hardware Accelerators & VRAM").apply {
            font = font.deriveFont(Font.BOLD, 13f)
        }
        titlePanel.add(titleLabel)
        titlePanel.add(hostRamBadge)
        titlePanel.add(cpuCoresBadge)
        header.add(titlePanel, BorderLayout.WEST)

        val actionsPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply { isOpaque = false }

        val flushBtn = JButton("🧹 Flush VRAM Cache").apply {
            isFocusPainted = false
            font = font.deriveFont(Font.BOLD, 10.5f)
            toolTipText = "Empty PyTorch CUDA memory cache and trigger garbage collection"
            addActionListener {
                val msg = HardwareTelemetryService.flushVramCache()
                refreshTelemetry()
                Messages.showInfoMessage(this@HardwareTelemetryPanel, msg, "VRAM Flushed")
            }
        }

        val refreshBtn = JButton("🔄 Refresh").apply {
            isFocusPainted = false
            font = font.deriveFont(Font.PLAIN, 10.5f)
            addActionListener { refreshTelemetry() }
        }

        actionsPanel.add(flushBtn)
        actionsPanel.add(refreshBtn)
        header.add(actionsPanel, BorderLayout.EAST)

        add(header, BorderLayout.NORTH)

        // Cards Scroll
        cardsContainer.layout = BoxLayout(cardsContainer, BoxLayout.Y_AXIS)
        cardsContainer.isOpaque = false

        val scroll = JBScrollPane(cardsContainer).apply {
            border = LineBorder(Color(220, 220, 220), 1)
            isOpaque = false
            viewport.isOpaque = false
        }
        add(scroll, BorderLayout.CENTER)
    }

    /**
     * Re-queries hardware accelerators and redraws device cards.
     */
    fun refreshTelemetry() {
        cardsContainer.removeAll()

        val snapshot = HardwareTelemetryService.fetchTelemetry()

        hostRamBadge.apply {
            text = "Host RAM: ${snapshot.hostUsedRamMb} / ${snapshot.hostTotalRamMb} MB"
            font = font.deriveFont(Font.BOLD, 10f)
            isOpaque = true
            background = Color(238, 232, 213)
            foreground = Color(101, 123, 131)
            border = BorderFactory.createCompoundBorder(
                LineBorder(Color(180, 180, 180), 1, true),
                EmptyBorder(2, 6, 2, 6)
            )
        }

        cpuCoresBadge.apply {
            text = "CPU: ${snapshot.hostAvailableProcessors} Cores"
            font = font.deriveFont(Font.BOLD, 10f)
            isOpaque = true
            background = Color(238, 232, 213)
            foreground = Color(101, 123, 131)
            border = BorderFactory.createCompoundBorder(
                LineBorder(Color(180, 180, 180), 1, true),
                EmptyBorder(2, 6, 2, 6)
            )
        }

        for (dev in snapshot.devices) {
            cardsContainer.add(createDeviceCard(dev))
            cardsContainer.add(Box.createVerticalStrut(10))
        }

        cardsContainer.revalidate()
        cardsContainer.repaint()
    }

    private fun createDeviceCard(device: AcceleratorDevice): JPanel {
        val card = JPanel(BorderLayout(0, 8)).apply {
            border = BorderFactory.createCompoundBorder(
                LineBorder(Color(215, 215, 215), 1, true),
                EmptyBorder(10, 14, 10, 14)
            )
            background = Color(253, 246, 227) // Solarized Base3 cream
            maximumSize = Dimension(Int.MAX_VALUE, 160)
        }

        // Header
        val cardHeader = JPanel(BorderLayout(8, 0)).apply { isOpaque = false }
        val titleText = "GPU ${device.index}: ${device.name}"
        val nameLabel = JBLabel(titleText).apply { font = font.deriveFont(Font.BOLD, 12.5f) }

        val typeBadge = JLabel(device.type.displayName).apply {
            font = font.deriveFont(Font.BOLD, 10f)
            isOpaque = true
            background = Color(220, 240, 220)
            foreground = Color(46, 125, 50)
            border = BorderFactory.createCompoundBorder(
                LineBorder(Color(46, 125, 50), 1, true),
                EmptyBorder(2, 6, 2, 6)
            )
        }

        cardHeader.add(nameLabel, BorderLayout.WEST)
        cardHeader.add(typeBadge, BorderLayout.EAST)
        card.add(cardHeader, BorderLayout.NORTH)

        // Middle: VRAM Usage Bar & Stats
        val center = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
        }

        val pct = device.memoryUsagePercent
        val vramLabel = JLabel("VRAM Memory: ${device.usedMemoryMb} MB / ${device.totalMemoryMb} MB ($pct% used)").apply {
            font = font.deriveFont(Font.PLAIN, 11f)
            foreground = Color(70, 70, 70)
        }
        center.add(vramLabel)
        center.add(Box.createVerticalStrut(4))

        val progressBar = JProgressBar(0, 100).apply {
            value = pct
            isStringPainted = true
            string = "$pct%"
            preferredSize = Dimension(400, 18)
            foreground = when {
                pct >= 85 -> Color(220, 50, 47) // Red
                pct >= 65 -> Color(181, 137, 0) // Amber
                else -> Color(42, 161, 152)     // Cyan / Teal
            }
        }
        center.add(progressBar)
        center.add(Box.createVerticalStrut(8))

        // Stats Row: Utilization, Temperature, Free VRAM, Driver
        val statsRow = JPanel(FlowLayout(FlowLayout.LEFT, 12, 0)).apply { isOpaque = false }
        statsRow.add(createStatItem("Compute Load", "${device.utilizationPercent}%"))
        device.temperatureCelsius?.let { temp ->
            statsRow.add(createStatItem("Temperature", "🌡️ ${temp}°C"))
        }
        statsRow.add(createStatItem("Free VRAM", "${device.freeMemoryMb} MB"))
        device.driverVersion?.let { driver ->
            statsRow.add(createStatItem("Driver", driver))
        }

        center.add(statsRow)
        card.add(center, BorderLayout.CENTER)

        return card
    }

    private fun createStatItem(title: String, value: String): JPanel {
        return JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            isOpaque = false
            add(JLabel("$title:").apply {
                font = font.deriveFont(Font.PLAIN, 10.5f)
                foreground = Color.GRAY
            })
            add(JLabel(value).apply {
                font = font.deriveFont(Font.BOLD, 10.5f)
                foreground = Color(40, 40, 40)
            })
        }
    }
}
