package org.jormungandr.database.engine

import com.intellij.openapi.diagnostic.logger
import org.jormungandr.database.model.ConnectionConfig
import org.jormungandr.database.model.DatabaseDialect
import java.sql.Connection
import java.sql.DriverManager
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

private val LOG = logger<DatabaseConnectionManager>()

/**
 * Manages database JDBC connections and driver registrations for Jörmungandr.
 */
object DatabaseConnectionManager {

    private val activeConnections = ConcurrentHashMap<String, Connection>()
    private val connectionConfigs = ConcurrentHashMap<String, ConnectionConfig>()

    init {
        // Pre-register SQLite driver
        runCatching {
            Class.forName("org.sqlite.JDBC")
            LOG.info("Registered SQLite JDBC driver.")
        }
    }

    fun connect(config: ConnectionConfig): Connection {
        disconnect(config.id)

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
        connectionConfigs[config.id] = config
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

    fun disconnect(id: String) {
        activeConnections.remove(id)?.let { conn ->
            runCatching { conn.close() }
        }
    }

    fun testConnection(config: ConnectionConfig): Result<Boolean> {
        return runCatching {
            if (config.dialect.defaultDriverClass.isNotBlank()) {
                Class.forName(config.dialect.defaultDriverClass)
            }
            val props = Properties()
            if (config.username.isNotBlank()) props.setProperty("user", config.username)
            if (config.password.isNotBlank()) props.setProperty("password", config.password)

            DriverManager.getConnection(config.jdbcUrl, props).use { conn ->
                !conn.isClosed
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
