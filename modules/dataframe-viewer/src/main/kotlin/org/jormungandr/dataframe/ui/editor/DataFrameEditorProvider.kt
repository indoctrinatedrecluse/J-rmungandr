package org.jormungandr.dataframe.ui.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class DataFrameEditorProvider : FileEditorProvider, DumbAware {

    override fun accept(project: Project, file: VirtualFile): Boolean {
        val ext = file.extension?.lowercase() ?: return false
        return ext == "csv" || ext == "tsv" || ext == "tab" || ext == "parquet"
    }

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        return DataFrameFileEditor(project, file)
    }

    override fun getEditorTypeId(): String = "jormungandr-dataframe-editor"

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.PLACE_BEFORE_DEFAULT_EDITOR
}
