package org.jormungandr.dataframe.filetype

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFile
import org.jormungandr.dataframe.icon.DataFrameIcons
import javax.swing.Icon

/**
 * File type for Apache Parquet (.parquet) columnar dataset files.
 */
class ParquetFileType private constructor() : FileType {

    override fun getName(): String = "Parquet Dataset"

    override fun getDescription(): String = "Apache Parquet Columnar Dataset"

    override fun getDefaultExtension(): String = "parquet"

    override fun getIcon(): Icon = DataFrameIcons.DATA_TABLE

    override fun isBinary(): Boolean = true

    override fun isReadOnly(): Boolean = false

    override fun getCharset(file: VirtualFile, content: ByteArray): String? = null

    companion object {
        @JvmField
        val INSTANCE = ParquetFileType()
    }
}
