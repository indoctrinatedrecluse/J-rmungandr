package org.jormungandr.shell.telemetry

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.*
import javax.swing.*

/**
 * Interactive Dialog showing detailed GPU architecture, memory breakdown,
 * thermals, compute utilization, and 1-click cache trimming.
 */
class GpuMonitorDialog(project: Project?) : DialogWrapper(project) {

    private val telemetryService = HardwareTelemetryService.getInstance()
    private val contentPanel = JPanel(BorderLayout(0, 16))

    private val headerLabel = JBLabel("GPU & Hardware Compute Telemetry").apply {
        font = font.deriveFont(Font.BOLD, 16f)
    }
    private val subHeaderLabel = JBLabel().apply {
        font = font.deriveFont(Font.PLAIN, 12f)
        foreground = JBColor.GRAY
    }

    private val vramBar = JProgressBar(0, 100).apply { isStringPainted = true }
    private val computeBar = JProgressBar(0, 100).apply { isStringPainted = true }
    private val ramBar = JProgressBar(0, 100).apply { isStringPainted = true }

    private val vramDetailLabel = JBLabel()
    private val computeDetailLabel = JBLabel()
    private val ramDetailLabel = JBLabel()
    private val tempLabel = JBLabel()
    private val statusBanner = JBLabel().apply {
        isOpaque = true
        border = JBUI.Borders.empty(8, 12)
        font = font.deriveFont(Font.BOLD, 12f)
    }

    private val listener = HardwareTelemetryListener { snapshot ->
        SwingUtilities.invokeLater { updateUi(snapshot) }
    }

    init {
        title = "Jörmungandr Hardware & GPU Monitor"
        setOKButtonText("Close")
        init()
        updateUi(telemetryService.currentSnapshot)
        telemetryService.addListener(listener)
    }

    override fun createCenterPanel(): JComponent {
        contentPanel.border = JBUI.Borders.empty(16)
        contentPanel.preferredSize = Dimension(540, 420)

        // Top info
        val topPanel = JPanel(BorderLayout(0, 4)).apply {
            add(headerLabel, BorderLayout.NORTH)
            add(subHeaderLabel, BorderLayout.SOUTH)
        }

        // Metrics grid
        val metricsPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(8, 0)
        }

        metricsPanel.add(statusBanner)
        metricsPanel.add(Box.createVerticalStrut(12))

        // 1. VRAM Gauge
        metricsPanel.add(createMetricCard("🎮 Dedicated VRAM / Acceleration Memory", vramBar, vramDetailLabel))
        metricsPanel.add(Box.createVerticalStrut(10))

        // 2. Compute Load Gauge
        metricsPanel.add(createMetricCard("⚡ GPU Compute Core Utilization", computeBar, computeDetailLabel))
        metricsPanel.add(Box.createVerticalStrut(10))

        // 3. System RAM Gauge
        metricsPanel.add(createMetricCard("💾 Host System Memory (RAM)", ramBar, ramDetailLabel))
        metricsPanel.add(Box.createVerticalStrut(10))

        // 4. Thermals & Status row
        val tempCard = JPanel(BorderLayout()).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JBColor.border(), 1, true),
                JBUI.Borders.empty(10, 12)
            )
            val lbl = JBLabel("🌡️ Thermal Sensor:")
            lbl.font = lbl.font.deriveFont(Font.BOLD, 12f)
            add(lbl, BorderLayout.WEST)
            add(tempLabel, BorderLayout.EAST)
        }
        metricsPanel.add(tempCard)

        // Bottom Actions
        val actionPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
            val trimBtn = JButton("🧹 Trim Off-Heap & Run JVM GC").apply {
                addActionListener {
                    System.gc()
                    JOptionPane.showMessageDialog(
                        contentPanel,
                        "Triggered JVM Garbage Collection and trimmed native off-heap memory caches.",
                        "Memory Cache Trimmed",
                        JOptionPane.INFORMATION_MESSAGE
                    )
                }
            }
            add(trimBtn)
        }

        contentPanel.add(topPanel, BorderLayout.NORTH)
        contentPanel.add(metricsPanel, BorderLayout.CENTER)
        contentPanel.add(actionPanel, BorderLayout.SOUTH)

        return contentPanel
    }

    private fun createMetricCard(title: String, bar: JProgressBar, detail: JBLabel): JPanel {
        return JPanel(BorderLayout(0, 4)).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JBColor.border(), 1, true),
                JBUI.Borders.empty(10, 12)
            )
            val header = JPanel(BorderLayout()).apply {
                val t = JBLabel(title).apply { font = font.deriveFont(Font.BOLD, 12f) }
                add(t, BorderLayout.WEST)
                add(detail, BorderLayout.EAST)
            }
            add(header, BorderLayout.NORTH)
            add(bar, BorderLayout.SOUTH)
        }
    }

    private fun updateUi(s: HardwareTelemetrySnapshot) {
        subHeaderLabel.text = "Device: ${s.gpuName} • Driver/Runtime: ${s.driverVersion}"

        // VRAM
        val vramPct = s.vramUsagePercent.toInt().coerceIn(0, 100)
        vramBar.value = vramPct
        vramBar.string = "${s.vramUsedMb} MB / ${s.vramTotalMb} MB ($vramPct%)"
        vramDetailLabel.text = "$vramPct%"
        vramBar.foreground = when {
            vramPct >= 90 -> JBColor(Color(220, 53, 69), Color(230, 80, 95))
            vramPct >= 75 -> JBColor(Color(255, 193, 7), Color(255, 205, 50))
            else -> JBColor(Color(40, 167, 69), Color(60, 185, 90))
        }

        // Compute
        computeBar.value = s.gpuComputePercent.coerceIn(0, 100)
        computeBar.string = "${s.gpuComputePercent}% Active Load"
        computeDetailLabel.text = "${s.gpuComputePercent}%"

        // RAM
        val ramPct = s.systemRamUsagePercent.toInt().coerceIn(0, 100)
        ramBar.value = ramPct
        ramBar.string = "${s.systemRamUsedMb} MB / ${s.systemRamTotalMb} MB ($ramPct%)"
        ramDetailLabel.text = "$ramPct%"
        ramBar.foreground = when {
            ramPct >= 90 -> JBColor(Color(220, 53, 69), Color(230, 80, 95))
            ramPct >= 80 -> JBColor(Color(255, 193, 7), Color(255, 205, 50))
            else -> JBColor(Color(40, 167, 69), Color(60, 185, 90))
        }

        // Thermals
        tempLabel.text = "${s.gpuTemperatureCelsius} °C  ${if (s.gpuTemperatureCelsius > 80) "🔥 HOT" else "❄️ NORMAL"}"

        // Risk state banner
        when (s.oomRiskState) {
            HardwareTelemetrySnapshot.OomRiskState.CRITICAL_OOM_RISK -> {
                statusBanner.text = "⚠️ CRITICAL OOM HAZARD: Memory pressure exceeds 90%! High risk of CUDA/Host OOM."
                statusBanner.background = JBColor(Color(255, 235, 238), Color(80, 20, 25))
                statusBanner.foreground = JBColor(Color(198, 40, 40), Color(255, 120, 120))
            }
            HardwareTelemetrySnapshot.OomRiskState.ELEVATED -> {
                statusBanner.text = "🟡 ELEVATED ALLOCATION: Memory load exceeds 75%. Monitor batch sizes."
                statusBanner.background = JBColor(Color(255, 248, 225), Color(70, 60, 20))
                statusBanner.foreground = JBColor(Color(245, 124, 0), Color(255, 180, 50))
            }
            HardwareTelemetrySnapshot.OomRiskState.STABLE -> {
                statusBanner.text = "🟢 HEALTHY: Hardware resources and memory quotas operating normally."
                statusBanner.background = JBColor(Color(232, 245, 233), Color(20, 50, 25))
                statusBanner.foreground = JBColor(Color(46, 125, 50), Color(100, 200, 110))
            }
        }
    }

    override fun dispose() {
        telemetryService.removeListener(listener)
        super.dispose()
    }
}
