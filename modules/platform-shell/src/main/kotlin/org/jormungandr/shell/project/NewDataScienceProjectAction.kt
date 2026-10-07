package org.jormungandr.shell.project

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import org.jormungandr.shell.icon.JormungandrIcons
import java.io.File

/**
 * Action allowing users to bootstrap and scaffold new Data Science, Machine Learning,
 * and OLAP workspaces from curated templates with automated environment configuration.
 */
class NewDataScienceProjectAction : AnAction(
    "New Data Science Workspace...",
    "Create a new structured data science, machine learning, or OLAP workspace from curated templates",
    JormungandrIcons.APP_ICON
) {

    override fun actionPerformed(e: AnActionEvent) {
        val currentProject = e.project
        val currentDir = e.getData(CommonDataKeys.VIRTUAL_FILE)?.let {
            if (it.isDirectory) File(it.path) else File(it.path).parentFile
        }

        val dialog = NewDataScienceProjectDialog(currentProject, currentDir)
        if (dialog.showAndGet()) {
            val result = dialog.scaffoldResult ?: return

            // Refresh Virtual File System so IDE immediately indexes created directory
            val vfsDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(result.projectDir)
            vfsDir?.refresh(true, true)

            // Open primary file if requested and current project exists
            if (dialog.isOpenPrimaryRequested && result.primaryFile != null && currentProject != null) {
                val primaryVFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(result.primaryFile)
                if (primaryVFile != null) {
                    FileEditorManager.getInstance(currentProject).openFile(primaryVFile, true)
                }
            }

            Messages.showInfoMessage(
                currentProject,
                "${result.message}\n\nGenerated ${result.filesCreated} files at:\n${result.projectDir.absolutePath}",
                "Data Science Workspace Ready"
            )
        }
    }
}
