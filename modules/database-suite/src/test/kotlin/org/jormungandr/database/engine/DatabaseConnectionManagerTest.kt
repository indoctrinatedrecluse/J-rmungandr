package org.jormungandr.database.engine

import org.jormungandr.database.model.ConnectionConfig
import org.jormungandr.database.model.DatabaseDialect
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class DatabaseConnectionManagerTest {

    @AfterEach
    fun tearDown() {
        DatabaseConnectionManager.closeAll()
    }

    @Test
    fun `test sqlite in-memory connection and testConnection`() {
        val config = ConnectionConfig(
            id = UUID.randomUUID().toString(),
            name = "Test SQLite",
            dialect = DatabaseDialect.SQLITE,
            databaseName = ":memory:"
        )

        val testResult = DatabaseConnectionManager.testConnection(config)
        assertTrue(testResult.isSuccess)
        assertTrue(testResult.getOrThrow())

        val conn = DatabaseConnectionManager.connect(config)
        assertNotNull(conn)
        assertFalse(conn.isClosed)

        val retrieved = DatabaseConnectionManager.getConnection(config.id)
        assertNotNull(retrieved)
        assertSame(conn, retrieved)

        DatabaseConnectionManager.disconnect(config.id)
        assertNull(DatabaseConnectionManager.getConnection(config.id))
    }

    @Test
    fun `test duckdb in-memory connection and testConnection`() {
        val config = ConnectionConfig(
            id = UUID.randomUUID().toString(),
            name = "Test DuckDB",
            dialect = DatabaseDialect.DUCKDB,
            databaseName = ":memory:"
        )

        val testResult = DatabaseConnectionManager.testConnection(config)
        assertTrue(testResult.isSuccess)
        assertTrue(testResult.getOrThrow())

        val conn = DatabaseConnectionManager.connect(config)
        assertNotNull(conn)
        assertFalse(conn.isClosed)

        val retrieved = DatabaseConnectionManager.getConnection(config.id)
        assertNotNull(retrieved)
        assertSame(conn, retrieved)

        DatabaseConnectionManager.disconnect(config.id)
        assertNull(DatabaseConnectionManager.getConnection(config.id))
    }
}
