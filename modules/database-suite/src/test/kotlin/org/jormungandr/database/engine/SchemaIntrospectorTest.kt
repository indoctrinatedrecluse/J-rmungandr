package org.jormungandr.database.engine

import org.jormungandr.database.model.ConnectionConfig
import org.jormungandr.database.model.DatabaseDialect
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class SchemaIntrospectorTest {

    @AfterEach
    fun tearDown() {
        DatabaseConnectionManager.closeAll()
    }

    @Test
    fun `test schema introspection finds tables and columns`() {
        val config = ConnectionConfig(
            id = UUID.randomUUID().toString(),
            name = "Introspect DB",
            dialect = DatabaseDialect.SQLITE,
            databaseName = ":memory:"
        )
        val conn = DatabaseConnectionManager.connect(config)

        conn.createStatement().use { st ->
            st.execute("CREATE TABLE products (sku TEXT PRIMARY KEY, name TEXT, cost REAL);")
            st.execute("CREATE TABLE orders (order_id INTEGER PRIMARY KEY, sku TEXT, qty INTEGER);")
        }

        val catalog = SchemaIntrospector.introspect(conn, config.id, config.name)
        assertNotNull(catalog)
        val allTables = catalog.schemas.flatMap { it.tables }
        assertTrue(allTables.any { it.name.equals("products", ignoreCase = true) })
        assertTrue(allTables.any { it.name.equals("orders", ignoreCase = true) })

        val prodTable = allTables.first { it.name.equals("products", ignoreCase = true) }
        val skuCol = prodTable.columns.find { it.name.equals("sku", ignoreCase = true) }
        assertNotNull(skuCol)
        assertTrue(skuCol?.isPrimaryKey == true)
    }

    @Test
    fun `test schema introspection finds foreign keys`() {
        val config = ConnectionConfig(
            id = UUID.randomUUID().toString(),
            name = "FK DB",
            dialect = DatabaseDialect.SQLITE,
            databaseName = ":memory:"
        )
        val conn = DatabaseConnectionManager.connect(config)

        conn.createStatement().use { st ->
            st.execute("CREATE TABLE authors (author_id INTEGER PRIMARY KEY, name TEXT);")
            st.execute("CREATE TABLE books (book_id INTEGER PRIMARY KEY, author_id INTEGER, title TEXT, FOREIGN KEY(author_id) REFERENCES authors(author_id));")
        }

        val catalog = SchemaIntrospector.introspect(conn, config.id, config.name)
        val allTables = catalog.schemas.flatMap { it.tables }
        val booksTable = allTables.find { it.name.equals("books", ignoreCase = true) }
        assertNotNull(booksTable)
        assertTrue(booksTable?.foreignKeys?.isNotEmpty() == true)
        val fk = booksTable?.foreignKeys?.firstOrNull()
        assertEquals("books", fk?.fromTable)
        assertEquals("author_id", fk?.fromColumn)
        assertEquals("authors", fk?.toTable)
        assertEquals("author_id", fk?.toColumn)
    }
}
