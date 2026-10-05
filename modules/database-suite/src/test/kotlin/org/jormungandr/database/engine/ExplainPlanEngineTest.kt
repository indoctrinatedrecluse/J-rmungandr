package org.jormungandr.database.engine

import org.jormungandr.database.model.ConnectionConfig
import org.jormungandr.database.model.DatabaseDialect
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.util.UUID

class ExplainPlanEngineTest {

    private lateinit var sqliteConn: Connection
    private lateinit var duckConn: Connection
    private lateinit var sqliteId: String
    private lateinit var duckId: String

    @BeforeEach
    fun setUp() {
        sqliteId = UUID.randomUUID().toString()
        duckId = UUID.randomUUID().toString()

        val sqliteConfig = ConnectionConfig(sqliteId, "SQLite Test", DatabaseDialect.SQLITE, databaseName = ":memory:")
        sqliteConn = DatabaseConnectionManager.connect(sqliteConfig)
        sqliteConn.createStatement().use { st ->
            st.execute("CREATE TABLE customers (id INTEGER PRIMARY KEY, name TEXT, email TEXT);")
            st.execute("CREATE TABLE orders (order_id INTEGER PRIMARY KEY, customer_id INTEGER, amount REAL, FOREIGN KEY(customer_id) REFERENCES customers(id));")
            st.execute("INSERT INTO customers VALUES (1, 'Alice', 'alice@test.com'), (2, 'Bob', 'bob@test.com');")
            st.execute("INSERT INTO orders VALUES (101, 1, 99.5), (102, 1, 149.0), (103, 2, 45.0);")
        }

        val duckConfig = ConnectionConfig(duckId, "DuckDB Test", DatabaseDialect.DUCKDB, databaseName = ":memory:")
        duckConn = DatabaseConnectionManager.connect(duckConfig)
        duckConn.createStatement().use { st ->
            st.execute("CREATE TABLE metrics (name VARCHAR, val DOUBLE);")
            st.execute("INSERT INTO metrics VALUES ('cpu', 45.5), ('ram', 68.2);")
        }
    }

    @AfterEach
    fun tearDown() {
        DatabaseConnectionManager.disconnect(sqliteId)
        DatabaseConnectionManager.disconnect(duckId)
    }

    @Test
    fun `test sqlite explain query plan identifies scans`() {
        val plan = ExplainPlanEngine.explain(
            sqliteConn,
            DatabaseDialect.SQLITE,
            "SELECT * FROM customers WHERE email = 'alice@test.com';"
        )

        assertEquals("SQLite (Embedded)", plan.dialect)
        assertTrue(plan.rootNodes.isNotEmpty())
        assertTrue(plan.rawText.contains("SCAN customers", ignoreCase = true))
        assertTrue(plan.rootNodes.any { it.isScanWarning })
        assertTrue(plan.durationMs >= 0)
    }

    @Test
    fun `test sqlite explain query plan with index search`() {
        // Query using primary key index
        val plan = ExplainPlanEngine.explain(
            sqliteConn,
            DatabaseDialect.SQLITE,
            "SELECT * FROM customers WHERE id = 1;"
        )

        assertTrue(plan.rootNodes.isNotEmpty())
        assertTrue(plan.rawText.contains("SEARCH customers", ignoreCase = true) || plan.rawText.contains("PRIMARY KEY", ignoreCase = true))
    }

    @Test
    fun `test duckdb explain plan execution`() {
        val plan = ExplainPlanEngine.explain(
            duckConn,
            DatabaseDialect.DUCKDB,
            "SELECT name, AVG(val) FROM metrics GROUP BY name;"
        )

        assertEquals("DuckDB (Embedded)", plan.dialect)
        assertTrue(plan.rootNodes.isNotEmpty())
        assertTrue(plan.rawText.isNotEmpty())
        assertTrue(plan.durationMs >= 0)
    }

    @Test
    fun `test explain on syntax error reports graceful error node`() {
        val plan = ExplainPlanEngine.explain(
            sqliteConn,
            DatabaseDialect.SQLITE,
            "SELECT FROM INVALID SYNTAX"
        )

        assertTrue(plan.rootNodes.any { it.title.contains("Error", ignoreCase = true) })
        assertTrue(plan.rawText.contains("EXPLAIN execution failed", ignoreCase = true))
    }
}
