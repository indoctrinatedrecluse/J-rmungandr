package org.jormungandr.database.engine

import org.jormungandr.database.model.QueryResult
import org.jormungandr.dataframe.model.DataFrame
import org.jormungandr.dataframe.model.DataTypeCategory
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException

/**
 * Asynchronous SQL query executor converting JDBC result sets into Jörmungandr DataFrame models.
 */
object SqlQueryExecutor {

    fun execute(
        connection: Connection,
        sql: String,
        maxRows: Int = 1000,
        resultName: String = "query_result"
    ): QueryResult {
        val trimmed = sql.trim()
        if (trimmed.isBlank()) {
            return QueryResult(sql, true, DataFrame.empty(resultName), 0, 0L)
        }

        val startTime = System.currentTimeMillis()
        return try {
            connection.createStatement().use { statement ->
                statement.maxRows = maxRows.coerceAtLeast(1)

                val hasResultSet = statement.execute(trimmed)
                val duration = System.currentTimeMillis() - startTime

                if (hasResultSet) {
                    statement.resultSet.use { rs ->
                        val df = extractDataFrame(rs, resultName)
                        QueryResult(
                            query = sql,
                            isSuccess = true,
                            dataFrame = df,
                            rowsAffected = df.rowCount,
                            executionTimeMs = duration
                        )
                    }
                } else {
                    val affected = statement.updateCount
                    QueryResult(
                        query = sql,
                        isSuccess = true,
                        dataFrame = DataFrame.empty(resultName),
                        rowsAffected = affected,
                        executionTimeMs = duration
                    )
                }
            }
        } catch (e: SQLException) {
            val duration = System.currentTimeMillis() - startTime
            QueryResult(
                query = sql,
                isSuccess = false,
                dataFrame = null,
                rowsAffected = 0,
                executionTimeMs = duration,
                errorMessage = e.message ?: "SQL execution failed"
            )
        }
    }

    private fun extractDataFrame(rs: ResultSet, name: String): DataFrame {
        val meta = rs.metaData
        val columnCount = meta.columnCount

        val columnDescs = mutableListOf<Pair<String, DataTypeCategory>>()
        for (i in 1..columnCount) {
            val colName = meta.getColumnLabel(i) ?: meta.getColumnName(i) ?: "col_$i"
            val sqlType = meta.getColumnType(i)
            val typeName = meta.getColumnTypeName(i) ?: "UNKNOWN"
            val category = SchemaIntrospector.mapSqlTypeToCategory(sqlType, typeName)
            columnDescs.add(Pair(colName, category))
        }

        val rows = mutableListOf<List<Any?>>()
        while (rs.next()) {
            val row = mutableListOf<Any?>()
            for (i in 1..columnCount) {
                val value = rs.getObject(i)
                row.add(value)
            }
            rows.add(row)
        }

        return DataFrame.buildWithStatistics(name, columnDescs, rows)
    }
}
