package org.jormungandr.shell.perspective

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.wm.ToolWindowManager

private val LOG = logger<SwitchToAnalysisPerspectiveAction>()

class SwitchToAnalysisPerspectiveAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        LOG.info("Switching to Analysis Perspective for project: ${project.name}")
        val wm = ToolWindowManager.getInstance(project)
        wm.getToolWindow("DataFrame Viewer")?.show()
        wm.getToolWindow("Jupyter Kernels")?.show()
    }
}

class SwitchToNotebookPerspectiveAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        LOG.info("Switching to Notebook Perspective for project: ${project.name}")
        val wm = ToolWindowManager.getInstance(project)
        wm.getToolWindow("Jupyter Kernels")?.show()
    }
}

class SwitchToDatabasePerspectiveAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        LOG.info("Switching to Database Perspective for project: ${project.name}")
        val wm = ToolWindowManager.getInstance(project)
        wm.getToolWindow("Database Studio")?.show()
    }
}

class SwitchToCodePerspectiveAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        LOG.info("Switching to Code Perspective for project: ${project.name}")
    }
}
