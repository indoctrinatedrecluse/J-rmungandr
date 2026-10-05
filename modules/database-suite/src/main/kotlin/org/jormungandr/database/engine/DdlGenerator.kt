/*
 * Copyright 2025–2026 indoctrinatedrecluse
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jormungandr.database.engine

import org.jormungandr.database.model.DatabaseDialect
import org.jormungandr.database.model.TableColumnMetadata
import org.jormungandr.database.model.TableMetadata
import org.jormungandr.dataframe.model.DataTypeCategory

/**
 * Generates dialect-aware SQL DDL statements and query templates from table metadata.
 */
object DdlGenerator {

    /**
     * Generates a CREATE TABLE DDL statement for the given table and dialect.
     */
    fun generateCreateTable(
        table: TableMetadata,
        dialect: DatabaseDialect = DatabaseDialect.SQLITE,
        ifNotExists: Boolean = true
    ): String {
        val sb = StringBuilder()
        val ifNotExistsClause = if (ifNotExists) "IF NOT EXISTS " else ""
        val qualifiedName = formatTableName(table, dialect)

        sb.append("CREATE TABLE $ifNotExistsClause$qualifiedName (\n")

        val colDefs = table.columns.map { col ->
            val colType = mapColumnType(col, dialect)
            val nullClause = if (!col.isNullable && !col.isPrimaryKey) " NOT NULL" else ""
            val pkClause = if (col.isPrimaryKey) " PRIMARY KEY" else ""
            "    ${quoteIdentifier(col.name, dialect)} $colType$pkClause$nullClause"
        }

        sb.append(colDefs.joinToString(",\n"))
        sb.append("\n);")

        return sb.toString()
    }

    /**
     * Generates a DROP TABLE statement.
     */
    fun generateDropTable(
        table: TableMetadata,
        dialect: DatabaseDialect = DatabaseDialect.SQLITE,
        ifExists: Boolean = true
    ): String {
        val ifExistsClause = if (ifExists) "IF EXISTS " else ""
        return "DROP TABLE $ifExistsClause${formatTableName(table, dialect)};"
    }

    /**
     * Generates a SELECT * template with limit.
     */
    fun generateSelectTemplate(
        table: TableMetadata,
        dialect: DatabaseDialect = DatabaseDialect.SQLITE,
        limit: Int = 100
    ): String {
        val qualifiedName = formatTableName(table, dialect)
        val colList = if (table.columns.isNotEmpty()) {
            table.columns.joinToString(", ") { quoteIdentifier(it.name, dialect) }
        } else {
            "*"
        }

        return when (dialect) {
            DatabaseDialect.SQLITE,
            DatabaseDialect.DUCKDB,
            DatabaseDialect.POSTGRESQL,
            DatabaseDialect.MYSQL -> "SELECT $colList FROM $qualifiedName LIMIT $limit;"
            DatabaseDialect.ORACLE_PLSQL -> "SELECT $colList FROM $qualifiedName FETCH FIRST $limit ROWS ONLY;"
            DatabaseDialect.SNOWFLAKE -> "SELECT $colList FROM $qualifiedName LIMIT $limit;"
            DatabaseDialect.CASSANDRA -> "SELECT $colList FROM $qualifiedName LIMIT $limit;"
            DatabaseDialect.MONGODB -> "db.${table.name}.find().limit($limit)"
            DatabaseDialect.REDIS -> "KEYS *"
            DatabaseDialect.KAFKA -> "CONSUME ${table.name} LIMIT $limit"
            DatabaseDialect.S3_DATA_LAKE,
            DatabaseDialect.GCS_DATA_LAKE,
            DatabaseDialect.HTTP_DATA_LAKE -> "SELECT $colList FROM read_parquet('$qualifiedName') LIMIT $limit;"
        }
    }

    /**
     * Generates an INSERT INTO template with placeholders.
     */
    fun generateInsertTemplate(
        table: TableMetadata,
        dialect: DatabaseDialect = DatabaseDialect.SQLITE
    ): String {
        val qualifiedName = formatTableName(table, dialect)
        val nonPkCols = table.columns.filter { !it.isPrimaryKey }
        val targetCols = if (nonPkCols.isNotEmpty()) nonPkCols else table.columns
        val colNames = targetCols.joinToString(", ") { quoteIdentifier(it.name, dialect) }
        val placeholders = targetCols.joinToString(", ") { "?" }

        return "INSERT INTO $qualifiedName ($colNames) VALUES ($placeholders);"
    }

    private fun formatTableName(table: TableMetadata, dialect: DatabaseDialect): String {
        val quotedTable = quoteIdentifier(table.name, dialect)
        return if (table.schemaName.isNotBlank() && table.schemaName != "public" && table.schemaName != "main") {
            "${quoteIdentifier(table.schemaName, dialect)}.$quotedTable"
        } else {
            quotedTable
        }
    }

    private fun quoteIdentifier(name: String, dialect: DatabaseDialect): String {
        return when (dialect) {
            DatabaseDialect.MYSQL -> "`$name`"
            DatabaseDialect.POSTGRESQL,
            DatabaseDialect.DUCKDB,
            DatabaseDialect.SNOWFLAKE -> "\"$name\""
            DatabaseDialect.SQLITE -> "\"$name\""
            else -> name
        }
    }

    private fun mapColumnType(col: TableColumnMetadata, dialect: DatabaseDialect): String {
        // If typeName is already specific, check if dialect prefers something else
        return when (dialect) {
            DatabaseDialect.SQLITE -> when (col.category) {
                DataTypeCategory.INTEGER -> "INTEGER"
                DataTypeCategory.FLOAT -> "REAL"
                DataTypeCategory.BOOLEAN -> "INTEGER"
                DataTypeCategory.DATETIME -> "TEXT"
                DataTypeCategory.BINARY -> "BLOB"
                else -> "TEXT"
            }
            DatabaseDialect.POSTGRESQL -> when (col.category) {
                DataTypeCategory.INTEGER -> if (col.isPrimaryKey) "BIGSERIAL" else "BIGINT"
                DataTypeCategory.FLOAT -> "DOUBLE PRECISION"
                DataTypeCategory.BOOLEAN -> "BOOLEAN"
                DataTypeCategory.DATETIME -> "TIMESTAMP WITH TIME ZONE"
                DataTypeCategory.BINARY -> "BYTEA"
                else -> "VARCHAR(255)"
            }
            DatabaseDialect.MYSQL -> when (col.category) {
                DataTypeCategory.INTEGER -> if (col.isPrimaryKey) "BIGINT AUTO_INCREMENT" else "BIGINT"
                DataTypeCategory.FLOAT -> "DOUBLE"
                DataTypeCategory.BOOLEAN -> "TINYINT(1)"
                DataTypeCategory.DATETIME -> "DATETIME"
                DataTypeCategory.BINARY -> "BLOB"
                else -> "VARCHAR(255)"
            }
            DatabaseDialect.DUCKDB -> when (col.category) {
                DataTypeCategory.INTEGER -> "BIGINT"
                DataTypeCategory.FLOAT -> "DOUBLE"
                DataTypeCategory.BOOLEAN -> "BOOLEAN"
                DataTypeCategory.DATETIME -> "TIMESTAMP"
                DataTypeCategory.BINARY -> "BLOB"
                else -> "VARCHAR"
            }
            DatabaseDialect.SNOWFLAKE -> when (col.category) {
                DataTypeCategory.INTEGER -> "NUMBER(38,0)"
                DataTypeCategory.FLOAT -> "FLOAT"
                DataTypeCategory.BOOLEAN -> "BOOLEAN"
                DataTypeCategory.DATETIME -> "TIMESTAMP_NTZ"
                DataTypeCategory.BINARY -> "BINARY"
                else -> "VARCHAR(16777216)"
            }
            else -> col.typeName.ifBlank { "TEXT" }
        }
    }
}
