package org.jormungandr.database.engine

import com.intellij.openapi.diagnostic.logger
import org.jormungandr.database.model.ConnectionConfig
import org.jormungandr.database.model.DatabaseDialect
import org.jormungandr.database.nosql.cassandra.CassandraEngine
import org.jormungandr.database.nosql.kafka.KafkaEngine
import org.jormungandr.database.nosql.mongo.MongoEngine
import org.jormungandr.database.nosql.redis.RedisEngine
import java.sql.Connection
import java.sql.DriverManager
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

private val LOG = logger<DatabaseConnectionManager>()

/**
 * Manages database JDBC and NoSQL connections and driver registrations for Jörmungandr.
 */
object DatabaseConnectionManager {

    private val activeConnections = ConcurrentHashMap<String, Connection>()
    private val connectionConfigs = ConcurrentHashMap<String, ConnectionConfig>()

    init {
        // Pre-register embedded drivers (SQLite and DuckDB)
        runCatching {
            Class.forName("org.sqlite.JDBC")
            LOG.info("Registered SQLite JDBC driver.")
        }
        runCatching {
            Class.forName("org.duckdb.DuckDBDriver")
            LOG.info("Registered DuckDB JDBC driver.")
        }
    }

    fun connect(config: ConnectionConfig): Connection {
        disconnect(config.id)
        connectionConfigs[config.id] = config

        if (!config.dialect.isRelational) {
            when (config.dialect) {
                DatabaseDialect.MONGODB -> MongoEngine.connect(config)
                DatabaseDialect.REDIS -> RedisEngine.connect(config)
                DatabaseDialect.CASSANDRA -> CassandraEngine.connect(config)
                DatabaseDialect.KAFKA -> KafkaEngine.connect(config)
                else -> {}
            }
            val mockConn = DriverManager.getConnection("jdbc:sqlite::memory:")
            activeConnections[config.id] = mockConn
            return mockConn
        }

        if (config.dialect.defaultDriverClass.isNotBlank()) {
            runCatching { Class.forName(config.dialect.defaultDriverClass) }
        }

        val props = Properties()
        if (config.username.isNotBlank()) props.setProperty("user", config.username)
        if (config.password.isNotBlank()) props.setProperty("password", config.password)

        LOG.info("Connecting to [${config.name}] at ${config.jdbcUrl}...")
        val conn = DriverManager.getConnection(config.jdbcUrl, props)
        if (config.isReadOnly) {
            runCatching { conn.isReadOnly = true }
        }

        activeConnections[config.id] = conn
        return conn
    }

    fun getConnection(id: String): Connection? {
        val conn = activeConnections[id] ?: return null
        return if (runCatching { conn.isClosed }.getOrDefault(true)) {
            activeConnections.remove(id)
            null
        } else {
            conn
        }
    }

    fun getConfig(id: String): ConnectionConfig? = connectionConfigs[id]

    fun getAllConfigs(): List<ConnectionConfig> = connectionConfigs.values.toList()

    fun registerConfig(config: ConnectionConfig) {
        connectionConfigs[config.id] = config
    }

    fun disconnect(id: String) {
        activeConnections.remove(id)?.let { conn ->
            runCatching { conn.close() }
        }
    }

    fun testConnection(config: ConnectionConfig): Result<Boolean> {
        return runCatching {
            if (config.dialect.isRelational) {
                if (config.dialect.defaultDriverClass.isNotBlank()) {
                    runCatching { Class.forName(config.dialect.defaultDriverClass) }
                }
                val props = Properties()
                if (config.username.isNotBlank()) props.setProperty("user", config.username)
                if (config.password.isNotBlank()) props.setProperty("password", config.password)

                DriverManager.getConnection(config.jdbcUrl, props).use { conn ->
                    !conn.isClosed
                }
            } else {
                when (config.dialect) {
                    DatabaseDialect.MONGODB -> MongoEngine.testConnection(config)
                    DatabaseDialect.REDIS -> RedisEngine.testConnection(config)
                    DatabaseDialect.CASSANDRA -> CassandraEngine.testConnection(config)
                    DatabaseDialect.KAFKA -> KafkaEngine.testConnection(config)
                    else -> true
                }
            }
        }
    }

    fun closeAll() {
        for ((id, conn) in activeConnections) {
            runCatching { conn.close() }
        }
        activeConnections.clear()
        connectionConfigs.clear()
    }
}
