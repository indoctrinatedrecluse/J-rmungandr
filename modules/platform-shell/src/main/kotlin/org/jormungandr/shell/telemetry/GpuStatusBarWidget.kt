package org.jormungandr.shell.telemetry

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Cursor
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities

/**
 * Custom Status Bar Widget displaying real-time GPU/VRAM or CPU/RAM utilization.
 */
class GpuStatusBarWidget(private val project: Project) : CustomStatusBarWidget {

    private val telemetryService = HardwareTelemetryService.getInstance()
    private val label = JBLabel("🎮 GPU: --% | VRAM: --%").apply {
        border = JBUI.Borders.empty(0, 4)
        font = font.deriveFont(11f)
    }

    private val panel = JPanel(FlowLayout(FlowLayout.CENTER, 0, 0)).apply {
        isOpaque = false
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        toolTipText = "Click to inspect GPU & Hardware Compute Monitor"
        add(label)
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                GpuMonitorDialog(project).show()
            }
        })
    }

    private val listener = HardwareTelemetryListener { snapshot ->
        SwingUtilities.invokeLater { updateWidget(snapshot) }
    }

    override fun ID(): String = WIDGET_ID

    override fun getComponent(): JComponent = panel

    override fun install(statusBar: StatusBar) {
        telemetryService.addListener(listener)
        updateWidget(telemetryService.currentSnapshot)
    }

    private fun updateWidget(s: HardwareTelemetrySnapshot) {
        val text = if (s.hasDedicatedGpu) {
            "🎮 GPU: ${s.gpuComputePercent}% | VRAM: ${s.vramUsagePercent.toInt()}% (${s.vramUsedMb / 1024}G/${s.vramTotalMb / 1024}G) ${s.gpuTemperatureCelsius}°C"
        } else {
            "💻 Host RAM: ${s.systemRamUsagePercent.toInt()}% | CPU: ${s.cpuUsagePercent}%"
        }
        label.text = text

        label.foreground = when (s.oomRiskState) {
            HardwareTelemetrySnapshot.OomRiskState.CRITICAL_OOM_RISK -> JBColor(Color(220, 53, 69), Color(255, 100, 110))
            HardwareTelemetrySnapshot.OomRiskState.ELEVATED -> JBColor(Color(230, 140, 0), Color(255, 180, 50))
            HardwareTelemetrySnapshot.OomRiskState.STABLE -> JBColor.foreground()
        }
    }

    override fun dispose() {
        telemetryService.removeListener(listener)
    }

    companion object {
        const val WIDGET_ID = "org.jormungandr.shell.telemetry.GpuStatusBarWidget"
    }
}

/**
 * Factory for creating the GpuStatusBarWidget in IntelliJ's status bar.
 */
class GpuStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = GpuStatusBarWidget.WIDGET_ID

    override fun getDisplayName(): String = "Jörmungandr GPU & Hardware Telemetry"

    override fun isAvailable(project: Project): Boolean = true

    override fun createWidget(project: Project): StatusBarWidget = GpuStatusBarWidget(project)

    override fun disposeWidget(widget: StatusBarWidget) {
        widget.dispose()
    }

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}
