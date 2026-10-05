package org.jormungandr.dataframe.ui.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import org.jormungandr.dataframe.io.CsvDataLoader
import org.jormungandr.dataframe.model.DataFrame
import org.jormungandr.dataframe.ui.DataFrameGridPanel
import java.beans.PropertyChangeListener
import javax.swing.JComponent

class DataFrameFileEditor(
    private val project: Project,
    private val virtualFile: VirtualFile
) : UserDataHolderBase(), FileEditor {

    private val gridPanel: DataFrameGridPanel

    init {
        val initialDf = try {
            virtualFile.inputStream.use { input ->
                CsvDataLoader.loadFromStream(virtualFile.name, input)
            }
        } catch (e: Exception) {
            DataFrame.empty(virtualFile.name)
        }
        gridPanel = DataFrameGridPanel(initialDf)
    }

    override fun getComponent(): JComponent = gridPanel

    override fun getPreferredFocusedComponent(): JComponent = gridPanel

    override fun getName(): String = "Dataframe: ${virtualFile.name}"

    override fun setState(state: FileEditorState) {}

    override fun isModified(): Boolean = false

    override fun isValid(): Boolean = virtualFile.isValid

    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}

    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}

    override fun getCurrentLocation(): FileEditorLocation? = null

    override fun getFile(): VirtualFile = virtualFile

    override fun dispose() {}
}
