package org.jormungandr.dataframe.model

enum class DataTypeCategory {
    INTEGER,
    FLOAT,
    STRING,
    BOOLEAN,
    DATETIME,
    BINARY,
    OBJECT
}

/**
 * Metadata and summary statistics for a dataframe column.
 */
data class ColumnMetadata(
    val name: String,
    val typeName: String,
    val category: DataTypeCategory,
    val nullCount: Long = 0,
    val totalCount: Long = 0,
    val minVal: String? = null,
    val maxVal: String? = null,
    val meanVal: Double? = null,
    val stdDev: Double? = null
)
