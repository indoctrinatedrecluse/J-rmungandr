package org.jormungandr.dataframe.lakehouse

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import java.awt.Dimension
import java.io.File
import javax.swing.JComponent

class LakehouseInspectorDialog(
    project: Project,
    private val targetFile: File
) : DialogWrapper(project) {

    init {
        title = "Modern Lakehouse & Deep Parquet Inspector — ${targetFile.name}"
        setOKButtonText("Close")
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = LakehouseStudioPanel(project = this.peer.owner as? Project ?: com.intellij.openapi.project.ProjectManager.getInstance().defaultProject, targetFile = targetFile)
        panel.preferredSize = Dimension(820, 560)
        return panel
    }
}

class LakehouseInspectorAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val vFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val file = if (vFile != null) File(vFile.path) else File(project.basePath ?: ".")

        LakehouseInspectorDialog(project, file).show()
    }

    override fun update(e: AnActionEvent) {
        val vFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val isParquetOrDir = vFile != null && (vFile.isDirectory || vFile.name.endsWith(".parquet", ignoreCase = true) || vFile.name.endsWith(".parq", ignoreCase = true))
        e.presentation.isEnabledAndVisible = isParquetOrDir || e.project != null
    }
}
