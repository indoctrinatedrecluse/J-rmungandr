package org.jormungandr.dataframe.ui.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class ModelCheckpointEditorProvider : FileEditorProvider, DumbAware {

    override fun accept(project: Project, file: VirtualFile): Boolean {
        val ext = file.extension?.lowercase() ?: return false
        return ext in SUPPORTED_EXTENSIONS
    }

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        return ModelCheckpointFileEditor(project, file)
    }

    override fun getEditorTypeId(): String = "jormungandr-model-checkpoint-editor"

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.PLACE_BEFORE_DEFAULT_EDITOR

    companion object {
        val SUPPORTED_EXTENSIONS = setOf("safetensors", "onnx", "pt", "pth", "h5", "hdf5")
    }
}
