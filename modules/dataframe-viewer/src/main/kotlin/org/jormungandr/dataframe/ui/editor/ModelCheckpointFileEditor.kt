package org.jormungandr.dataframe.ui.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import org.jormungandr.dataframe.ml.checkpoint.ModelCheckpointInspector
import org.jormungandr.dataframe.ml.checkpoint.ModelCheckpointViewerPanel
import java.beans.PropertyChangeListener
import java.io.File
import javax.swing.JComponent

class ModelCheckpointFileEditor(
    private val project: Project,
    private val virtualFile: VirtualFile
) : UserDataHolderBase(), FileEditor {

    private val viewerPanel: ModelCheckpointViewerPanel

    init {
        val ioFile = File(virtualFile.path)
        val fileToInspect = if (ioFile.exists()) {
            ioFile
        } else {
            val tempFile = File.createTempFile("chkpt_preview_", ".${virtualFile.extension ?: "bin"}")
            tempFile.deleteOnExit()
            virtualFile.inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            tempFile
        }
        viewerPanel = ModelCheckpointViewerPanel(project, fileToInspect)
    }

    override fun getComponent(): JComponent = viewerPanel

    override fun getPreferredFocusedComponent(): JComponent = viewerPanel

    override fun getName(): String = "Model Checkpoint: ${virtualFile.name}"

    override fun setState(state: FileEditorState) {}

    override fun isModified(): Boolean = false

    override fun isValid(): Boolean = virtualFile.isValid

    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}

    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}

    override fun getCurrentLocation(): FileEditorLocation? = null

    override fun getFile(): VirtualFile = virtualFile

    override fun dispose() {}
}
