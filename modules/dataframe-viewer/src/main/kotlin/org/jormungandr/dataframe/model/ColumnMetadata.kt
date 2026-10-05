package org.jormungandr.dataframe.model

enum class DataTypeCategory(val displayName: String, val badgeShort: String) {
    INTEGER("Integer", "int"),
    FLOAT("Float", "float"),
    STRING("String", "str"),
    BOOLEAN("Boolean", "bool"),
    DATETIME("DateTime", "time"),
    BINARY("Binary", "bin"),
    OBJECT("Object", "obj")
}

/**
 * Detailed metadata and summary statistics for a dataframe column.
 */
data class ColumnMetadata(
    val name: String,
    val typeName: String,
    val category: DataTypeCategory,
    val nullCount: Long = 0,
    val totalCount: Long = 0,
    val distinctCount: Long = 0,
    val minVal: String? = null,
    val maxVal: String? = null,
    val meanVal: Double? = null,
    val stdDev: Double? = null,
    val medianVal: Double? = null,
    val histogramBins: List<HistogramBin> = emptyList()
) {
    val nullPercentage: Double
        get() = if (totalCount > 0) (nullCount.toDouble() / totalCount.toDouble()) * 100.0 else 0.0
}

data class HistogramBin(
    val label: String,
    val count: Int
)
