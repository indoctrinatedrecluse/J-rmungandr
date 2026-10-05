/*
 * Copyright (c) 2025-2026 indoctrinatedrecluse
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

import org.jormungandr.database.model.ConnectionConfig
import org.jormungandr.database.model.DatabaseDialect
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.util.UUID

class VisualQueryBuilderTest {

    private lateinit var sqliteConn: Connection
    private lateinit var duckConn: Connection

    @BeforeEach
    fun setUp() {
        val sqliteConfig = ConnectionConfig(
            id = UUID.randomUUID().toString(),
            name = "Test SQLite",
            dialect = DatabaseDialect.SQLITE,
            databaseName = ":memory:"
        )
        sqliteConn = DatabaseConnectionManager.connect(sqliteConfig)
        sqliteConn.createStatement().use { st ->
            st.execute("CREATE TABLE customers (id INTEGER PRIMARY KEY, name TEXT, country TEXT);")
            st.execute("CREATE TABLE orders (order_id INTEGER PRIMARY KEY, customer_id INTEGER, amount REAL);")

            st.execute("INSERT INTO customers VALUES (1, 'Alice', 'US'), (2, 'Bob', 'UK'), (3, 'Charlie', 'US');")
            st.execute("INSERT INTO orders VALUES (101, 1, 150.0), (102, 1, 200.5), (103, 2, 75.0);")
        }

        val duckConfig = ConnectionConfig(
            id = UUID.randomUUID().toString(),
            name = "Test DuckDB",
            dialect = DatabaseDialect.DUCKDB,
            databaseName = ":memory:"
        )
        duckConn = DatabaseConnectionManager.connect(duckConfig)
        duckConn.createStatement().use { st ->
            st.execute("CREATE TABLE products (sku VARCHAR, category VARCHAR, price DOUBLE);")
            st.execute("INSERT INTO products VALUES ('P1', 'Electronics', 499.99), ('P2', 'Electronics', 99.99), ('P3', 'Books', 24.50);")
        }
    }

    @AfterEach
    fun tearDown() {
        DatabaseConnectionManager.closeAll()
    }

    @Test
    fun `test simple single table select all`() {
        val model = VisualQueryModel(
            primaryTable = QueryTable("customers", "c"),
            limit = 50
        )
        val sql = VisualQueryGenerator.generateSql(model, DatabaseDialect.SQLITE)

        assertTrue(sql.contains("SELECT"))
        assertTrue(sql.contains("*"))
        assertTrue(sql.contains("FROM customers c"))
        assertTrue(sql.contains("LIMIT 50;"))

        val result = SqlQueryExecutor.execute(sqliteConn, sql)
        assertTrue(result.isSuccess)
        assertEquals(3, result.dataFrame?.rowCount)
    }

    @Test
    fun `test multi-table join with inner join and custom columns`() {
        val model = VisualQueryModel(
            primaryTable = QueryTable("customers", "c"),
            joins = mutableListOf(
                QueryJoin(
                    type = JoinType.INNER,
                    table = QueryTable("orders", "o"),
                    condition = JoinCondition("c", "id", "=", "o", "customer_id")
                )
            ),
            columns = mutableListOf(
                QueryColumn("c", "name", outputAlias = "customer_name"),
                QueryColumn("o", "order_id"),
                QueryColumn("o", "amount")
            ),
            sorts = mutableListOf(
                QuerySort("o", "amount", SortDirection.DESC)
            ),
            limit = 100
        )

        val sql = VisualQueryGenerator.generateSql(model, DatabaseDialect.SQLITE)

        assertTrue(sql.contains("SELECT"))
        assertTrue(sql.contains("c.name AS customer_name"))
        assertTrue(sql.contains("o.order_id"))
        assertTrue(sql.contains("o.amount"))
        assertTrue(sql.contains("FROM customers c"))
        assertTrue(sql.contains("INNER JOIN orders o ON c.id = o.customer_id"))
        assertTrue(sql.contains("ORDER BY o.amount DESC"))

        val result = SqlQueryExecutor.execute(sqliteConn, sql)
        assertTrue(result.isSuccess)
        assertEquals(3, result.dataFrame?.rowCount)
        // Highest order first: 200.5
        assertEquals(200.5, (result.dataFrame?.rows?.get(0)?.get(2) as Number).toDouble())
    }

    @Test
    fun `test left outer join`() {
        val model = VisualQueryModel(
            primaryTable = QueryTable("customers", "c"),
            joins = mutableListOf(
                QueryJoin(
                    type = JoinType.LEFT,
                    table = QueryTable("orders", "o"),
                    condition = JoinCondition("c", "id", "=", "o", "customer_id")
                )
            ),
            columns = mutableListOf(
                QueryColumn("c", "name"),
                QueryColumn("o", "order_id")
            )
        )

        val sql = VisualQueryGenerator.generateSql(model, DatabaseDialect.SQLITE)
        assertTrue(sql.contains("LEFT JOIN orders o ON c.id = o.customer_id"))

        val result = SqlQueryExecutor.execute(sqliteConn, sql)
        assertTrue(result.isSuccess)
        // Charlie has no orders, so 4 rows total
        assertEquals(4, result.dataFrame?.rowCount)
    }

    @Test
    fun `test aggregations and automatic group by inference`() {
        val model = VisualQueryModel(
            primaryTable = QueryTable("customers", "c"),
            joins = mutableListOf(
                QueryJoin(
                    type = JoinType.INNER,
                    table = QueryTable("orders", "o"),
                    condition = JoinCondition("c", "id", "=", "o", "customer_id")
                )
            ),
            columns = mutableListOf(
                QueryColumn("c", "country"),
                QueryColumn("o", "amount", outputAlias = "total_spent", aggregate = AggregateFunction.SUM),
                QueryColumn("o", "order_id", outputAlias = "order_count", aggregate = AggregateFunction.COUNT)
            )
        )

        val sql = VisualQueryGenerator.generateSql(model, DatabaseDialect.SQLITE)

        assertTrue(sql.contains("SUM(o.amount) AS total_spent"))
        assertTrue(sql.contains("COUNT(o.order_id) AS order_count"))
        assertTrue(sql.contains("GROUP BY c.country"))

        val result = SqlQueryExecutor.execute(sqliteConn, sql)
        assertTrue(result.isSuccess)
        assertEquals(2, result.dataFrame?.rowCount) // US and UK
    }

    @Test
    fun `test where filters and operators`() {
        val model = VisualQueryModel(
            primaryTable = QueryTable("customers", "c"),
            filters = mutableListOf(
                QueryFilter("c", "country", ComparisonOperator.EQUALS, "US"),
                QueryFilter("c", "id", ComparisonOperator.GREATER, "1", connector = LogicalOperator.AND)
            )
        )

        val sql = VisualQueryGenerator.generateSql(model, DatabaseDialect.SQLITE)
        assertTrue(sql.contains("WHERE"))
        assertTrue(sql.contains("c.country = 'US'"))
        assertTrue(sql.contains("AND c.id > 1"))

        val result = SqlQueryExecutor.execute(sqliteConn, sql)
        assertTrue(result.isSuccess)
        // Only Charlie (id=3, country=US) satisfies id > 1 and country='US'
        assertEquals(1, result.dataFrame?.rowCount)
        assertEquals("Charlie", result.dataFrame?.rows?.get(0)?.get(1))
    }

    @Test
    fun `test dialect specific pagination`() {
        val model = VisualQueryModel(
            primaryTable = QueryTable("products", "p"),
            limit = 10,
            offset = 20
        )

        val oracleSql = VisualQueryGenerator.generateSql(model, DatabaseDialect.ORACLE_PLSQL)
        assertTrue(oracleSql.contains("OFFSET 20 ROWS FETCH NEXT 10 ROWS ONLY;"))

        val sqliteSql = VisualQueryGenerator.generateSql(model, DatabaseDialect.SQLITE)
        assertTrue(sqliteSql.contains("LIMIT 10 OFFSET 20;"))

        val cassandraSql = VisualQueryGenerator.generateSql(model, DatabaseDialect.CASSANDRA)
        assertTrue(cassandraSql.contains("LIMIT 10;"))
    }

    @Test
    fun `test duckdb execution and python generators`() {
        val model = VisualQueryModel(
            primaryTable = QueryTable("products", "p"),
            columns = mutableListOf(
                QueryColumn("p", "category"),
                QueryColumn("p", "price", outputAlias = "avg_price", aggregate = AggregateFunction.AVG)
            ),
            limit = 10
        )

        val duckSql = VisualQueryGenerator.generateSql(model, DatabaseDialect.DUCKDB)
        val result = SqlQueryExecutor.execute(duckConn, duckSql)
        assertTrue(result.isSuccess)
        assertEquals(2, result.dataFrame?.rowCount) // Books, Electronics

        val pandasCode = VisualQueryGenerator.generatePandasCode(model)
        assertTrue(pandasCode.contains("import pandas as pd"))
        assertTrue(pandasCode.contains("groupby"))

        val duckDbScript = VisualQueryGenerator.generateDuckDbPythonCode(model)
        assertTrue(duckDbScript.contains("import duckdb"))
        assertTrue(duckDbScript.contains("conn.sql"))
    }
}
