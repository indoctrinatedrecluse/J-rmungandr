package org.jormungandr.database.model

/**
 * Supported SQL and NoSQL database engine dialects in Jörmungandr.
 */
enum class DatabaseDialect(
    val displayName: String,
    val isRelational: Boolean,
    val defaultPort: Int,
    val defaultDriverClass: String
) {
    SQLITE("SQLite (Embedded)", true, 0, "org.sqlite.JDBC"),
    DUCKDB("DuckDB (Embedded)", true, 0, "org.duckdb.DuckDBDriver"),
    POSTGRESQL("PostgreSQL", true, 5432, "org.postgresql.Driver"),
    MYSQL("MySQL", true, 3306, "com.mysql.cj.jdbc.Driver"),
    SNOWFLAKE("Snowflake", true, 443, "net.snowflake.client.jdbc.SnowflakeDriver"),
    MONGODB("MongoDB", false, 27017, ""),
    REDIS("Redis", false, 6379, "")
}

data class ConnectionConfig(
    val id: String,
    val name: String,
    val dialect: DatabaseDialect,
    val host: String = "localhost",
    val port: Int = dialect.defaultPort,
    val databaseName: String = "",
    val username: String = "",
    val password: String = "",
    val customJdbcUrl: String? = null,
    val isReadOnly: Boolean = false
) {
    val jdbcUrl: String
        get() {
            if (!customJdbcUrl.isNullOrBlank()) return customJdbcUrl
            return when (dialect) {
                DatabaseDialect.SQLITE -> {
                    if (databaseName.isBlank() || databaseName == ":memory:") "jdbc:sqlite::memory:"
                    else "jdbc:sqlite:$databaseName"
                }
                DatabaseDialect.DUCKDB -> {
                    if (databaseName.isBlank() || databaseName == ":memory:") "jdbc:duckdb:"
                    else "jdbc:duckdb:$databaseName"
                }
                DatabaseDialect.POSTGRESQL -> "jdbc:postgresql://$host:$port/$databaseName"
                DatabaseDialect.MYSQL -> "jdbc:mysql://$host:$port/$databaseName"
                DatabaseDialect.SNOWFLAKE -> "jdbc:snowflake://$host/?db=$databaseName"
                DatabaseDialect.MONGODB -> "mongodb://$host:$port/$databaseName"
                DatabaseDialect.REDIS -> "redis://$host:$port"
            }
        }
}
