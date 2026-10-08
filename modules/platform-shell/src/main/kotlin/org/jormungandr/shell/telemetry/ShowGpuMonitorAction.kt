package org.jormungandr.shell.telemetry

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class ShowGpuMonitorAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        GpuMonitorDialog(e.project).show()
    }
}
