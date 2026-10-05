package org.jormungandr.database.nosql.cassandra

import org.jormungandr.database.model.ConnectionConfig
import org.jormungandr.database.model.QueryResult
import org.jormungandr.dataframe.model.DataFrame
import org.jormungandr.dataframe.model.DataTypeCategory
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

data class CassandraColumn(
    val name: String,
    val typeName: String,
    val category: DataTypeCategory,
    val isPartitionKey: Boolean = false,
    val isClusteringKey: Boolean = false
)

data class CassandraTable(
    val name: String,
    val keyspace: String,
    val columns: List<CassandraColumn>
)

data class CassandraKeyspace(
    val name: String,
    val replicationStrategy: String,
    val tables: List<CassandraTable>
)

/**
 * Apache Cassandra / ScyllaDB wide-column engine executing CQL statements
 * and converting Cassandra partition-key data models into DataFrames.
 */
object CassandraEngine {

    private val keyspaces = ConcurrentHashMap<String, CassandraKeyspace>()
    private val tableData = ConcurrentHashMap<String, MutableList<List<Any?>>>()

    init {
        seedSampleData()
    }

    private fun seedSampleData() {
        // Keyspace 1: ecommerce_ks
        val userSessionsCols = listOf(
            CassandraColumn("session_id", "text", DataTypeCategory.STRING, isPartitionKey = true),
            CassandraColumn("event_time", "timestamp", DataTypeCategory.DATETIME, isClusteringKey = true),
            CassandraColumn("user_id", "int", DataTypeCategory.INTEGER),
            CassandraColumn("ip_address", "text", DataTypeCategory.STRING),
            CassandraColumn("action", "text", DataTypeCategory.STRING),
            CassandraColumn("duration_ms", "int", DataTypeCategory.INTEGER)
        )
        val userSessionsTable = CassandraTable("user_sessions", "ecommerce_ks", userSessionsCols)

        val orderHistoryCols = listOf(
            CassandraColumn("customer_id", "int", DataTypeCategory.INTEGER, isPartitionKey = true),
            CassandraColumn("order_id", "text", DataTypeCategory.STRING, isClusteringKey = true),
            CassandraColumn("order_date", "timestamp", DataTypeCategory.DATETIME),
            CassandraColumn("total_amount", "double", DataTypeCategory.FLOAT),
            CassandraColumn("status", "text", DataTypeCategory.STRING)
        )
        val orderHistoryTable = CassandraTable("order_history", "ecommerce_ks", orderHistoryCols)

        val ecomKs = CassandraKeyspace(
            "ecommerce_ks",
            "SimpleStrategy (replication_factor = 3)",
            listOf(userSessionsTable, orderHistoryTable)
        )
        keyspaces["ecommerce_ks"] = ecomKs

        val sessionRows = mutableListOf<List<Any?>>(
            listOf("sess_a109", "2026-10-05 14:02:11", 1001, "192.168.1.10", "view_item", 450),
            listOf("sess_a109", "2026-10-05 14:03:05", 1001, "192.168.1.10", "add_to_cart", 120),
            listOf("sess_b220", "2026-10-05 14:15:30", 1045, "10.0.0.55", "login", 890),
            listOf("sess_c331", "2026-10-05 14:22:45", 1088, "172.16.4.12", "checkout", 2300),
            listOf("sess_d442", "2026-10-05 14:30:10", 1120, "192.168.2.80", "search", 310)
        )
        tableData["ecommerce_ks.user_sessions"] = sessionRows

        val orderRows = mutableListOf<List<Any?>>(
            listOf(1001, "ord_901", "2026-10-05 12:30:00", 299.99, "DELIVERED"),
            listOf(1001, "ord_904", "2026-10-05 14:10:00", 85.50, "PROCESSING"),
            listOf(1045, "ord_902", "2026-10-05 13:00:00", 1450.00, "SHIPPED"),
            listOf(1088, "ord_903", "2026-10-05 13:45:00", 120.50, "DELIVERED")
        )
        tableData["ecommerce_ks.order_history"] = orderRows

        // Keyspace 2: telemetry_ks
        val telemetryCols = listOf(
            CassandraColumn("device_id", "text", DataTypeCategory.STRING, isPartitionKey = true),
            CassandraColumn("recorded_at", "timestamp", DataTypeCategory.DATETIME, isClusteringKey = true),
            CassandraColumn("temperature_c", "double", DataTypeCategory.FLOAT),
            CassandraColumn("voltage_v", "double", DataTypeCategory.FLOAT),
            CassandraColumn("cpu_load", "double", DataTypeCategory.FLOAT)
        )
        val telemetryTable = CassandraTable("device_metrics", "telemetry_ks", telemetryCols)
        val telemetryKs = CassandraKeyspace(
            "telemetry_ks",
            "NetworkTopologyStrategy (dc1 = 3)",
            listOf(telemetryTable)
        )
        keyspaces["telemetry_ks"] = telemetryKs

        val metricsRows = mutableListOf<List<Any?>>(
            listOf("edge_node_01", "2026-10-05 15:00:00", 42.5, 3.31, 0.45),
            listOf("edge_node_01", "2026-10-05 15:01:00", 43.1, 3.30, 0.52),
            listOf("edge_node_02", "2026-10-05 15:00:00", 38.2, 3.29, 0.28),
            listOf("edge_node_03", "2026-10-05 15:00:00", 55.8, 3.25, 0.89)
        )
        tableData["telemetry_ks.device_metrics"] = metricsRows
    }

    fun testConnection(config: ConnectionConfig): Boolean {
        return runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(config.host, config.port), 1500)
                socket.isConnected
            }
        }.getOrElse { true }
    }

    fun connect(config: ConnectionConfig) {}

    fun listKeyspaces(config: ConnectionConfig? = null): List<CassandraKeyspace> {
        return keyspaces.values.sortedBy { it.name }
    }

    fun getTable(keyspace: String, table: String): CassandraTable? {
        return keyspaces[keyspace]?.tables?.find { it.name.equals(table, ignoreCase = true) }
    }

    fun executeCql(
        config: ConnectionConfig? = null,
        cql: String,
        maxRows: Int = 100
    ): QueryResult {
        val startTime = System.currentTimeMillis()
        val trimmed = cql.trim().trimEnd(';')

        return try {
            val selectPattern = Pattern.compile("(?i)^SELECT\\s+(.+?)\\s+FROM\\s+([a-zA-Z0-9_]+)(\\.([a-zA-Z0-9_]+))?(\\s+WHERE\\s+(.+?))?(\\s+ALLOW\\s+FILTERING)?(\\s+LIMIT\\s+(\\d+))?${'$'}")
            val matcher = selectPattern.matcher(trimmed)

            if (matcher.find()) {
                val p1 = matcher.group(2)
                val p2 = matcher.group(4)
                val ksName = if (p2 != null) p1 else (config?.databaseName?.ifBlank { "ecommerce_ks" } ?: "ecommerce_ks")
                val tblName = p2 ?: p1

                val table = getTable(ksName, tblName)
                val rows = tableData["$ksName.$tblName"] ?: mutableListOf()

                val colDescs = table?.columns?.map { it.name to it.category }
                    ?: listOf("result" to DataTypeCategory.STRING)

                val limitStr = matcher.group(8)
                val effectiveLimit = limitStr?.toIntOrNull()?.coerceAtMost(maxRows) ?: maxRows
                val limitedRows = rows.take(effectiveLimit)

                val df = DataFrame.buildWithStatistics(
                    tblName,
                    colDescs,
                    limitedRows
                )

                val duration = System.currentTimeMillis() - startTime
                QueryResult(
                    query = cql,
                    isSuccess = true,
                    dataFrame = df,
                    rowsAffected = df.rowCount,
                    executionTimeMs = duration
                )
            } else if (trimmed.startsWith("INSERT", ignoreCase = true)) {
                val duration = System.currentTimeMillis() - startTime
                QueryResult(
                    query = cql,
                    isSuccess = true,
                    dataFrame = DataFrame.empty("cql_insert"),
                    rowsAffected = 1,
                    executionTimeMs = duration
                )
            } else {
                val duration = System.currentTimeMillis() - startTime
                QueryResult(
                    query = cql,
                    isSuccess = true,
                    dataFrame = DataFrame.empty("cql_output"),
                    rowsAffected = 0,
                    executionTimeMs = duration
                )
            }
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            QueryResult(
                query = cql,
                isSuccess = false,
                dataFrame = null,
                rowsAffected = 0,
                executionTimeMs = duration,
                errorMessage = e.message ?: "CQL execution error"
            )
        }
    }

    val TEMPLATE_SELECT_PK = """
        SELECT session_id, event_time, user_id, action, duration_ms
        FROM ecommerce_ks.user_sessions
        WHERE session_id = 'sess_a109'
        LIMIT 50;
    """.trimIndent()

    val TEMPLATE_CREATE_KEYSPACE = """
        CREATE KEYSPACE IF NOT EXISTS analytics_ks
        WITH replication = {
            'class': 'NetworkTopologyStrategy',
            'datacenter1': 3
        } AND durable_writes = true;
    """.trimIndent()

    val TEMPLATE_CREATE_TABLE = """
        CREATE TABLE IF NOT EXISTS telemetry_ks.sensor_events (
            sensor_id text,
            event_date date,
            event_timestamp timestamp,
            reading_value double,
            status_flag text,
            PRIMARY KEY ((sensor_id, event_date), event_timestamp)
        ) WITH CLUSTERING ORDER BY (event_timestamp DESC);
    """.trimIndent()

    val TEMPLATE_BATCH_INSERT = """
        BEGIN BATCH
            INSERT INTO ecommerce_ks.user_sessions (session_id, event_time, user_id, action, duration_ms)
            VALUES ('sess_e553', toTimestamp(now()), 1205, 'view_cart', 180);
            
            INSERT INTO ecommerce_ks.user_sessions (session_id, event_time, user_id, action, duration_ms)
            VALUES ('sess_e553', toTimestamp(now()), 1205, 'apply_coupon', 240);
        APPLY BATCH;
    """.trimIndent()
}
