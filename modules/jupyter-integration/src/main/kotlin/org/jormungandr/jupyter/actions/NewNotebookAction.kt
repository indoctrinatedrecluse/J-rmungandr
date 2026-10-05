package org.jormungandr.jupyter.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VfsUtil
import org.jormungandr.jupyter.format.NotebookFormat
import org.jormungandr.jupyter.icon.JupyterIcons
import org.jormungandr.jupyter.model.NotebookModel

/**
 * Action to create a new interactive Jupyter Notebook (.ipynb) in the current directory.
 */
class NewNotebookAction : AnAction("Jupyter Notebook (.ipynb)", "Create a new interactive Jupyter notebook", JupyterIcons.NOTEBOOK_16) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val currentDir = e.getData(CommonDataKeys.VIRTUAL_FILE)?.let {
            if (it.isDirectory) it else it.parent
        } ?: project.baseDir ?: return

        var name = Messages.showInputDialog(
            project,
            "Enter notebook name:",
            "New Jupyter Notebook",
            Messages.getQuestionIcon(),
            "Untitled",
            null
        ) ?: return

        if (!name.endsWith(".ipynb", ignoreCase = true)) {
            name += ".ipynb"
        }

        val initialContent = NotebookFormat.writeNotebook(NotebookModel.createDefaultPythonNotebook())

        val newFile = com.intellij.openapi.application.ApplicationManager.getApplication().runWriteAction<com.intellij.openapi.vfs.VirtualFile> {
            val file = currentDir.createChildData(this, name)
            VfsUtil.saveText(file, initialContent)
            file
        }

        if (newFile != null) {
            FileEditorManager.getInstance(project).openFile(newFile, true)
        }
    }
}
