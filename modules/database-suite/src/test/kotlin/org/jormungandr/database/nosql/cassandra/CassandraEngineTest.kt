package org.jormungandr.database.nosql.cassandra

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CassandraEngineTest {

    @Test
    fun `test list keyspaces and table metadata`() {
        val keyspaces = CassandraEngine.listKeyspaces()
        assertTrue(keyspaces.any { it.name == "ecommerce_ks" })
        assertTrue(keyspaces.any { it.name == "telemetry_ks" })

        val table = CassandraEngine.getTable("ecommerce_ks", "user_sessions")
        assertNotNull(table)
        assertTrue(table!!.columns.any { it.name == "session_id" && it.isPartitionKey })
        assertTrue(table.columns.any { it.name == "event_time" && it.isClusteringKey })
    }

    @Test
    fun `test execute cql select query`() {
        val result = CassandraEngine.executeCql(
            cql = "SELECT * FROM ecommerce_ks.user_sessions LIMIT 5;"
        )
        assertTrue(result.isSuccess)
        assertNotNull(result.dataFrame)
        assertTrue((result.dataFrame?.rowCount ?: 0) > 0)
        assertTrue(result.dataFrame!!.columns.any { it.name == "session_id" })
        assertTrue(result.dataFrame!!.columns.any { it.name == "action" })
    }

    @Test
    fun `test cql insert query`() {
        val result = CassandraEngine.executeCql(
            cql = "INSERT INTO ecommerce_ks.user_sessions (session_id, user_id) VALUES ('sess_new', 999);"
        )
        assertTrue(result.isSuccess)
    }
}
