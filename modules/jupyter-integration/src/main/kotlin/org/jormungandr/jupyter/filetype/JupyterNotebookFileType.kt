package org.jormungandr.jupyter.filetype

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFile
import org.jormungandr.jupyter.icon.JupyterIcons
import javax.swing.Icon

/**
 * FileType registration for Jupyter Notebook (.ipynb) files in Jörmungandr.
 */
class JupyterNotebookFileType private constructor() : FileType {

    companion object {
        @JvmField
        val INSTANCE = JupyterNotebookFileType()
    }

    override fun getName(): String = "Jupyter Notebook"

    override fun getDescription(): String = "Jupyter Interactive Notebook (.ipynb)"

    override fun getDefaultExtension(): String = "ipynb"

    override fun getIcon(): Icon = JupyterIcons.NOTEBOOK_16

    override fun isBinary(): Boolean = false

    override fun isReadOnly(): Boolean = false

    override fun getCharset(file: VirtualFile, content: ByteArray): String = "UTF-8"
}
