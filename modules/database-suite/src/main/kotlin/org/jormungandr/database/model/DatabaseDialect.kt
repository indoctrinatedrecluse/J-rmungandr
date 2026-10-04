package org.jormungandr.database.model

/**
 * Supported SQL and NoSQL database engine dialects in Jörmungandr.
 */
enum class DatabaseDialect(val displayName: String, val isRelational: Boolean, val defaultPort: Int) {
    POSTGRESQL("PostgreSQL", true, 5432),
    DUCKDB("DuckDB (Embedded)", true, 0),
    SQLITE("SQLite (Embedded)", true, 0),
    MYSQL("MySQL", true, 3306),
    SNOWFLAKE("Snowflake", true, 443),
    MONGODB("MongoDB", false, 27017),
    REDIS("Redis", false, 6379)
}

data class ConnectionConfig(
    val id: String,
    val name: String,
    val dialect: DatabaseDialect,
    val host: String = "localhost",
    val port: Int = dialect.defaultPort,
    val databaseName: String = "",
    val username: String = "",
    val isReadOnly: Boolean = false
)
