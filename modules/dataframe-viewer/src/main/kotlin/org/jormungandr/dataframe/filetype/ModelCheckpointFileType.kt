package org.jormungandr.dataframe.filetype

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFile
import org.jormungandr.dataframe.icon.DataFrameIcons
import javax.swing.Icon

/**
 * File type for Deep Learning and Machine Learning Model Checkpoints (.safetensors, .onnx, .pt, .pth, .h5).
 */
class ModelCheckpointFileType private constructor() : FileType {

    override fun getName(): String = "Model Checkpoint"

    override fun getDescription(): String = "Deep Learning Neural Network Model Checkpoint"

    override fun getDefaultExtension(): String = "safetensors"

    override fun getIcon(): Icon = DataFrameIcons.DATA_TABLE

    override fun isBinary(): Boolean = true

    override fun isReadOnly(): Boolean = false

    override fun getCharset(file: VirtualFile, content: ByteArray): String? = null

    companion object {
        @JvmField
        val INSTANCE = ModelCheckpointFileType()
    }
}
