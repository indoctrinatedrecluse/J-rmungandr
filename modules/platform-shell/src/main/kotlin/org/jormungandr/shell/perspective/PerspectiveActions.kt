package org.jormungandr.shell.perspective

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.logger

private val LOG = logger<SwitchToAnalysisPerspectiveAction>()

class SwitchToAnalysisPerspectiveAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        LOG.info("Switching to Analysis Perspective for project: ${project.name}")
        // Perspective layout switcher logic
    }
}

class SwitchToDatabasePerspectiveAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        LOG.info("Switching to Database Perspective for project: ${project.name}")
        // Perspective layout switcher logic
    }
}

class SwitchToCodePerspectiveAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        LOG.info("Switching to Code Perspective for project: ${project.name}")
        // Perspective layout switcher logic
    }
}
