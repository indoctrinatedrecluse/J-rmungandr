package org.jormungandr.database.engine

import org.jormungandr.database.model.*
import org.jormungandr.dataframe.model.DataTypeCategory
import java.sql.Connection
import java.sql.Types

/**
 * Introspects database metadata, schemas, tables, and column structures via JDBC.
 */
object SchemaIntrospector {

    fun introspect(connection: Connection, connectionId: String, connectionName: String): CatalogMetadata {
        val meta = connection.metaData
        val tables = mutableListOf<TableMetadata>()

        // Discover Tables and Views
        meta.getTables(null, null, "%", arrayOf("TABLE", "VIEW", "SYSTEM TABLE")).use { rs ->
            while (rs.next()) {
                val tableName = rs.getString("TABLE_NAME") ?: continue
                val schemaName = rs.getString("TABLE_SCHEM") ?: "main"
                val tableType = rs.getString("TABLE_TYPE") ?: "TABLE"

                // Fetch primary keys for this table
                val pkColumns = mutableSetOf<String>()
                runCatching {
                    meta.getPrimaryKeys(null, schemaName, tableName).use { pkRs ->
                        while (pkRs.next()) {
                            pkRs.getString("COLUMN_NAME")?.let { pkColumns.add(it) }
                        }
                    }
                }

                // Fetch columns
                val columns = mutableListOf<TableColumnMetadata>()
                meta.getColumns(null, schemaName, tableName, "%").use { colRs ->
                    while (colRs.next()) {
                        val colName = colRs.getString("COLUMN_NAME") ?: continue
                        val dataType = colRs.getInt("DATA_TYPE")
                        val typeName = colRs.getString("TYPE_NAME") ?: "TEXT"
                        val nullable = colRs.getInt("NULLABLE") != 0
                        val ordPos = colRs.getInt("ORDINAL_POSITION")
                        val category = mapSqlTypeToCategory(dataType, typeName)

                        columns.add(
                            TableColumnMetadata(
                                name = colName,
                                typeName = typeName,
                                category = category,
                                isNullable = nullable,
                                isPrimaryKey = pkColumns.contains(colName),
                                ordinalPosition = ordPos
                            )
                        )
                    }
                }

                tables.add(TableMetadata(tableName, schemaName, tableType, columns))
            }
        }

        // Group tables by schema
        val schemaGroups = tables.groupBy { it.schemaName }.map { (sName, tList) ->
            SchemaMetadata(sName, tList)
        }

        return CatalogMetadata(connectionId, connectionName, schemaGroups)
    }

    fun mapSqlTypeToCategory(sqlType: Int, typeName: String): DataTypeCategory {
        val lower = typeName.lowercase()
        if (lower.contains("int")) return DataTypeCategory.INTEGER
        if (lower.contains("char") || lower.contains("text") || lower.contains("clob")) return DataTypeCategory.STRING
        if (lower.contains("float") || lower.contains("double") || lower.contains("real") || lower.contains("numeric") || lower.contains("decimal")) return DataTypeCategory.FLOAT
        if (lower.contains("bool")) return DataTypeCategory.BOOLEAN
        if (lower.contains("date") || lower.contains("time")) return DataTypeCategory.DATETIME
        if (lower.contains("blob") || lower.contains("binary")) return DataTypeCategory.BINARY

        return when (sqlType) {
            Types.BIGINT, Types.INTEGER, Types.SMALLINT, Types.TINYINT -> DataTypeCategory.INTEGER
            Types.FLOAT, Types.DOUBLE, Types.REAL, Types.DECIMAL, Types.NUMERIC -> DataTypeCategory.FLOAT
            Types.BOOLEAN, Types.BIT -> DataTypeCategory.BOOLEAN
            Types.DATE, Types.TIME, Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> DataTypeCategory.DATETIME
            Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> DataTypeCategory.BINARY
            else -> DataTypeCategory.STRING
        }
    }
}
