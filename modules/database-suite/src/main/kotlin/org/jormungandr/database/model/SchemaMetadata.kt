package org.jormungandr.database.model

import org.jormungandr.dataframe.model.DataTypeCategory

data class TableColumnMetadata(
    val name: String,
    val typeName: String,
    val category: DataTypeCategory,
    val isNullable: Boolean = true,
    val isPrimaryKey: Boolean = false,
    val ordinalPosition: Int = 0
)

data class TableMetadata(
    val name: String,
    val schemaName: String = "public",
    val type: String = "TABLE", // TABLE, VIEW, SYSTEM TABLE, COLLECTION
    val columns: List<TableColumnMetadata> = emptyList(),
    val rowCountEstimate: Long = -1
)

data class SchemaMetadata(
    val name: String,
    val tables: List<TableMetadata> = emptyList()
)

data class CatalogMetadata(
    val connectionId: String,
    val connectionName: String,
    val schemas: List<SchemaMetadata> = emptyList()
)
