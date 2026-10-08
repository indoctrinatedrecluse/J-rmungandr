package org.jormungandr.shell.copilot.local

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class PromptStudioToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = PromptStudioPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "Local AI & Prompt Studio", false)
        toolWindow.contentManager.addContent(content)
    }
}
