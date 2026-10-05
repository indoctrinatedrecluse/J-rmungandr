package org.jormungandr.database.model

/**
 * High-level engine categories for database, NoSQL, and streaming systems.
 */
enum class DialectCategory(val displayName: String) {
    RELATIONAL("Relational & Analytical SQL"),
    DOCUMENT_STORE("Document Store NoSQL"),
    KEY_VALUE("In-Memory Key-Value"),
    WIDE_COLUMN("Wide-Column Store"),
    EVENT_STREAMING("Distributed Event Streaming"),
    DATA_LAKE("Remote Data Lakes & Object Storage")
}

/**
 * Supported SQL, NoSQL, streaming, and object storage data lake engine dialects in Jörmungandr.
 */
enum class DatabaseDialect(
    val displayName: String,
    val isRelational: Boolean,
    val defaultPort: Int,
    val defaultDriverClass: String,
    val category: DialectCategory
) {
    SQLITE("SQLite (Embedded)", true, 0, "org.sqlite.JDBC", DialectCategory.RELATIONAL),
    DUCKDB("DuckDB (Embedded)", true, 0, "org.duckdb.DuckDBDriver", DialectCategory.RELATIONAL),
    POSTGRESQL("PostgreSQL", true, 5432, "org.postgresql.Driver", DialectCategory.RELATIONAL),
    MYSQL("MySQL", true, 3306, "com.mysql.cj.jdbc.Driver", DialectCategory.RELATIONAL),
    ORACLE_PLSQL("Oracle (PL/SQL)", true, 1521, "oracle.jdbc.OracleDriver", DialectCategory.RELATIONAL),
    SNOWFLAKE("Snowflake", true, 443, "net.snowflake.client.jdbc.SnowflakeDriver", DialectCategory.RELATIONAL),
    CASSANDRA("Apache Cassandra (CQL)", false, 9042, "", DialectCategory.WIDE_COLUMN),
    MONGODB("MongoDB", false, 27017, "", DialectCategory.DOCUMENT_STORE),
    REDIS("Redis", false, 6379, "", DialectCategory.KEY_VALUE),
    KAFKA("Apache Kafka", false, 9092, "", DialectCategory.EVENT_STREAMING),
    S3_DATA_LAKE("Amazon S3 & MinIO Data Lake", false, 443, "", DialectCategory.DATA_LAKE),
    GCS_DATA_LAKE("Google Cloud Storage (GCS)", false, 443, "", DialectCategory.DATA_LAKE),
    HTTP_DATA_LAKE("Remote HTTP/Parquet Endpoint", false, 443, "", DialectCategory.DATA_LAKE)
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
    val isReadOnly: Boolean = false,
    val extraProperties: Map<String, String> = emptyMap()
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
                DatabaseDialect.ORACLE_PLSQL -> "jdbc:oracle:thin:@//$host:$port/$databaseName"
                DatabaseDialect.SNOWFLAKE -> "jdbc:snowflake://$host/?db=$databaseName"
                DatabaseDialect.CASSANDRA -> "cql://$host:$port/$databaseName"
                DatabaseDialect.MONGODB -> "mongodb://$host:$port/$databaseName"
                DatabaseDialect.REDIS -> "redis://$host:$port"
                DatabaseDialect.KAFKA -> "kafka://$host:$port"
                DatabaseDialect.S3_DATA_LAKE -> "s3://$host/$databaseName"
                DatabaseDialect.GCS_DATA_LAKE -> "gs://$host/$databaseName"
                DatabaseDialect.HTTP_DATA_LAKE -> "https://$host/$databaseName"
            }
        }
}
