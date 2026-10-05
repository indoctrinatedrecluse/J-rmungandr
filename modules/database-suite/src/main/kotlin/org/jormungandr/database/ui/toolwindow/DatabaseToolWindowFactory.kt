package org.jormungandr.database.ui.toolwindow

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import org.jormungandr.database.ui.DatabaseStudioPanel

class DatabaseToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val studioPanel = DatabaseStudioPanel(project)
        val content = ContentFactory.getInstance().createContent(studioPanel, "Database Studio", false)
        toolWindow.contentManager.addContent(content)
    }
}
