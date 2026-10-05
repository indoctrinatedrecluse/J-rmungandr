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
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DdlGeneratorTest {

    private val sampleTable = TableMetadata(
        name = "users",
        schemaName = "public",
        columns = listOf(
            TableColumnMetadata("id", "INTEGER", DataTypeCategory.INTEGER, isNullable = false, isPrimaryKey = true),
            TableColumnMetadata("name", "TEXT", DataTypeCategory.STRING, isNullable = false),
            TableColumnMetadata("balance", "REAL", DataTypeCategory.FLOAT, isNullable = true),
            TableColumnMetadata("is_active", "BOOLEAN", DataTypeCategory.BOOLEAN, isNullable = true)
        )
    )

    @Test
    fun `generateCreateTable for SQLite produces valid DDL`() {
        val ddl = DdlGenerator.generateCreateTable(sampleTable, DatabaseDialect.SQLITE)
        assertTrue(ddl.contains("CREATE TABLE IF NOT EXISTS \"users\""))
        assertTrue(ddl.contains("\"id\" INTEGER PRIMARY KEY"))
        assertTrue(ddl.contains("\"name\" TEXT NOT NULL"))
        assertTrue(ddl.contains("\"balance\" REAL"))
        assertTrue(ddl.endsWith(";"))
    }

    @Test
    fun `generateCreateTable for PostgreSQL produces valid DDL`() {
        val ddl = DdlGenerator.generateCreateTable(sampleTable, DatabaseDialect.POSTGRESQL)
        assertTrue(ddl.contains("CREATE TABLE IF NOT EXISTS \"users\""))
        assertTrue(ddl.contains("\"id\" BIGSERIAL PRIMARY KEY"))
        assertTrue(ddl.contains("\"name\" VARCHAR(255) NOT NULL"))
        assertTrue(ddl.contains("\"balance\" DOUBLE PRECISION"))
        assertTrue(ddl.contains("\"is_active\" BOOLEAN"))
    }

    @Test
    fun `generateCreateTable for MySQL produces valid DDL`() {
        val ddl = DdlGenerator.generateCreateTable(sampleTable, DatabaseDialect.MYSQL)
        assertTrue(ddl.contains("CREATE TABLE IF NOT EXISTS `users`"))
        assertTrue(ddl.contains("`id` BIGINT AUTO_INCREMENT PRIMARY KEY"))
        assertTrue(ddl.contains("`name` VARCHAR(255) NOT NULL"))
    }

    @Test
    fun `generateDropTable produces valid DROP statements`() {
        val dropSqlite = DdlGenerator.generateDropTable(sampleTable, DatabaseDialect.SQLITE)
        assertEquals("DROP TABLE IF EXISTS \"users\";", dropSqlite)

        val dropMysql = DdlGenerator.generateDropTable(sampleTable, DatabaseDialect.MYSQL, ifExists = false)
        assertEquals("DROP TABLE `users`;", dropMysql)
    }

    @Test
    fun `generateSelectTemplate produces valid queries`() {
        val selectSql = DdlGenerator.generateSelectTemplate(sampleTable, DatabaseDialect.SQLITE, limit = 50)
        assertTrue(selectSql.startsWith("SELECT \"id\", \"name\", \"balance\", \"is_active\" FROM \"users\" LIMIT 50;"))
    }

    @Test
    fun `generateInsertTemplate generates placeholders for non-pk columns`() {
        val insertSql = DdlGenerator.generateInsertTemplate(sampleTable, DatabaseDialect.SQLITE)
        assertTrue(insertSql.contains("INSERT INTO \"users\" (\"name\", \"balance\", \"is_active\") VALUES (?, ?, ?);"))
    }
}
