package org.jormungandr.dataframe.filetype

import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.fileTypes.PlainTextLanguage
import org.jormungandr.dataframe.icon.DataFrameIcons
import javax.swing.Icon

/**
 * File type for CSV tabular datasets.
 */
class CsvFileType private constructor() : LanguageFileType(PlainTextLanguage.INSTANCE) {

    override fun getName(): String = "CSV Dataset"

    override fun getDescription(): String = "Comma-Separated Values Tabular Dataset"

    override fun getDefaultExtension(): String = "csv"

    override fun getIcon(): Icon = DataFrameIcons.DATA_TABLE

    companion object {
        @JvmField
        val INSTANCE = CsvFileType()
    }
}
