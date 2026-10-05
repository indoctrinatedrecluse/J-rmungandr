package org.jormungandr.database.engine

import org.jormungandr.database.model.ConnectionConfig
import org.jormungandr.database.model.DatabaseDialect
import org.jormungandr.dataframe.model.DataTypeCategory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.util.UUID

class SqlQueryExecutorTest {

    private lateinit var connection: Connection

    @BeforeEach
    fun setUp() {
        val config = ConnectionConfig(
            id = UUID.randomUUID().toString(),
            name = "Test DB",
            dialect = DatabaseDialect.SQLITE,
            databaseName = ":memory:"
        )
        connection = DatabaseConnectionManager.connect(config)

        connection.createStatement().use { st ->
            st.execute("CREATE TABLE inventory (item_id INTEGER PRIMARY KEY, item_name TEXT, price REAL, in_stock INTEGER);")
            st.execute("INSERT INTO inventory VALUES (1, 'Apples', 1.99, 50);")
            st.execute("INSERT INTO inventory VALUES (2, 'Bananas', 0.89, 120);")
            st.execute("INSERT INTO inventory VALUES (3, 'Cherries', 4.50, 15);")
        }
    }

    @AfterEach
    fun tearDown() {
        DatabaseConnectionManager.closeAll()
    }

    @Test
    fun `test select query execution and dataframe conversion`() {
        val result = SqlQueryExecutor.execute(connection, "SELECT * FROM inventory ORDER BY price ASC;", maxRows = 100)

        assertTrue(result.isSuccess)
        assertNull(result.errorMessage)
        val df = result.dataFrame
        assertNotNull(df)
        assertEquals(3, df?.rowCount)
        assertEquals(4, df?.columnCount)

        val priceCol = df?.columns?.find { it.name.equals("price", ignoreCase = true) }
        assertNotNull(priceCol)
        assertEquals(DataTypeCategory.FLOAT, priceCol?.category)
        assertEquals("0.8900", priceCol?.minVal)
        assertEquals("4.5000", priceCol?.maxVal)

        // Sorted by price asc: Bananas first
        assertEquals("Bananas", df?.rows?.get(0)?.get(1))
    }

    @Test
    fun `test row limit enforcement`() {
        val result = SqlQueryExecutor.execute(connection, "SELECT * FROM inventory;", maxRows = 2)

        assertTrue(result.isSuccess)
        assertEquals(2, result.dataFrame?.rowCount)
    }

    @Test
    fun `test invalid sql query error handling`() {
        val result = SqlQueryExecutor.execute(connection, "SELECT non_existent_column FROM table_does_not_exist;")

        assertFalse(result.isSuccess)
        assertNull(result.dataFrame)
        assertNotNull(result.errorMessage)
    }
}
