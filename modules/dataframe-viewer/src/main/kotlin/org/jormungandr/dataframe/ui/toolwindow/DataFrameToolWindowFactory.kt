package org.jormungandr.dataframe.ui.toolwindow

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import org.jormungandr.dataframe.model.DataFrame
import org.jormungandr.dataframe.service.DataFrameService
import org.jormungandr.dataframe.ui.DataFrameGridPanel
import java.awt.BorderLayout
import javax.swing.JPanel

class DataFrameToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val rootPanel = JPanel(BorderLayout())
        val gridPanel = DataFrameGridPanel(DataFrame.empty("No Dataset Active"))
        rootPanel.add(gridPanel, BorderLayout.CENTER)

        val service = ApplicationManager.getApplication().getService(DataFrameService::class.java)
        service?.activeDataFrame?.value?.let { df ->
            gridPanel.dataFrame = df
        }

        val content = ContentFactory.getInstance().createContent(rootPanel, "Data Grid", false)
        toolWindow.contentManager.addContent(content)
    }
}
