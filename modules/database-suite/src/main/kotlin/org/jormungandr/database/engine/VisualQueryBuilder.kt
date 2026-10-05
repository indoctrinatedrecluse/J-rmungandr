/*
 * Copyright (c) 2025-2026 indoctrinatedrecluse
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jormungandr.database.engine

import org.jormungandr.database.model.DatabaseDialect

/**
 * Represents a table referenced in a visual query.
 */
data class QueryTable(
    val tableName: String,
    val alias: String,
    val schemaName: String? = null
) {
    val qualifiedName: String
        get() = if (schemaName.isNullOrBlank() || schemaName == "main" || schemaName == "public") {
            tableName
        } else {
            "$schemaName.$tableName"
        }
}

/**
 * Supported relational join types for visual query generation.
 */
enum class JoinType(val keyword: String, val shortName: String) {
    INNER("INNER JOIN", "Inner"),
    LEFT("LEFT JOIN", "Left Outer"),
    RIGHT("RIGHT JOIN", "Right Outer"),
    FULL_OUTER("FULL OUTER JOIN", "Full Outer"),
    CROSS("CROSS JOIN", "Cross")
}

/**
 * Equality or comparison condition binding two joined tables.
 */
data class JoinCondition(
    val leftTableAlias: String,
    val leftColumn: String,
    val operator: String = "=",
    val rightTableAlias: String,
    val rightColumn: String
)

/**
 * A join specification linking a new table into the query.
 */
data class QueryJoin(
    val type: JoinType,
    val table: QueryTable,
    val condition: JoinCondition? = null
)

/**
 * SQL aggregate functions.
 */
enum class AggregateFunction(val displayName: String, val sqlFunc: String?) {
    NONE("None", null),
    COUNT("COUNT", "COUNT"),
    COUNT_DISTINCT("COUNT(DISTINCT)", "COUNT(DISTINCT)"),
    SUM("SUM", "SUM"),
    AVG("AVG", "AVG"),
    MIN("MIN", "MIN"),
    MAX("MAX", "MAX")
}

/**
 * Column or projection field in the SELECT statement.
 */
data class QueryColumn(
    val tableAlias: String,
    val columnName: String,
    val outputAlias: String = "",
    val aggregate: AggregateFunction = AggregateFunction.NONE,
    val isIncluded: Boolean = true
) {
    fun toSqlExpression(dialect: DatabaseDialect = DatabaseDialect.SQLITE): String {
        val colRef = if (tableAlias.isNotBlank()) "$tableAlias.$columnName" else columnName
        val expr = when (aggregate) {
            AggregateFunction.NONE -> colRef
            AggregateFunction.COUNT -> "COUNT($colRef)"
            AggregateFunction.COUNT_DISTINCT -> "COUNT(DISTINCT $colRef)"
            AggregateFunction.SUM -> "SUM($colRef)"
            AggregateFunction.AVG -> "AVG($colRef)"
            AggregateFunction.MIN -> "MIN($colRef)"
            AggregateFunction.MAX -> "MAX($colRef)"
        }
        return if (outputAlias.isNotBlank()) {
            "$expr AS $outputAlias"
        } else {
            expr
        }
    }
}

/**
 * Comparison operators for WHERE and HAVING filter clauses.
 */
enum class ComparisonOperator(
    val symbol: String,
    val requiresValue: Boolean = true,
    val isMultiValue: Boolean = false
) {
    EQUALS("="),
    NOT_EQUALS("!="),
    GREATER(">"),
    GREATER_EQUAL(">="),
    LESS("<"),
    LESS_EQUAL("<="),
    LIKE("LIKE"),
    NOT_LIKE("NOT LIKE"),
    ILIKE("ILIKE"),
    IN("IN", isMultiValue = true),
    NOT_IN("NOT IN", isMultiValue = true),
    IS_NULL("IS NULL", requiresValue = false),
    IS_NOT_NULL("IS NOT NULL", requiresValue = false),
    BETWEEN("BETWEEN")
}

/**
 * Boolean connectors between filter conditions.
 */
enum class LogicalOperator {
    AND, OR
}

/**
 * Filter predicate condition.
 */
data class QueryFilter(
    val tableAlias: String,
    val columnName: String,
    val operator: ComparisonOperator,
    val value: String = "",
    val connector: LogicalOperator = LogicalOperator.AND,
    val isHaving: Boolean = false
) {
    fun toSqlExpression(dialect: DatabaseDialect = DatabaseDialect.SQLITE): String {
        val colRef = if (tableAlias.isNotBlank()) "$tableAlias.$columnName" else columnName
        if (!operator.requiresValue) {
            return "$colRef ${operator.symbol}"
        }

        val trimmedValue = value.trim()
        val formattedValue = when {
            operator == ComparisonOperator.IN || operator == ComparisonOperator.NOT_IN -> {
                if (trimmedValue.startsWith("(") && trimmedValue.endsWith(")")) {
                    trimmedValue
                } else {
                    // Split comma-separated tokens, quote strings if unquoted
                    val items = trimmedValue.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    val quoted = items.joinToString(", ") { item ->
                        if (item.matches(Regex("^-?\\d+(\\.\\d+)?$")) || item.startsWith("'")) item
                        else "'${item.replace("'", "''")}'"
                    }
                    "($quoted)"
                }
            }
            operator == ComparisonOperator.LIKE || operator == ComparisonOperator.NOT_LIKE || operator == ComparisonOperator.ILIKE -> {
                if (trimmedValue.startsWith("'") && trimmedValue.endsWith("'")) trimmedValue
                else "'$trimmedValue'"
            }
            operator == ComparisonOperator.BETWEEN -> {
                // Expecting "val1 AND val2" or "val1, val2"
                if (trimmedValue.contains(" AND ", ignoreCase = true)) {
                    trimmedValue
                } else if (trimmedValue.contains(",")) {
                    val parts = trimmedValue.split(",").map { it.trim() }
                    if (parts.size >= 2) "${parts[0]} AND ${parts[1]}" else trimmedValue
                } else {
                    trimmedValue
                }
            }
            else -> {
                if (trimmedValue.matches(Regex("^-?\\d+(\\.\\d+)?$")) ||
                    trimmedValue.startsWith("'") ||
                    trimmedValue.equals("NULL", ignoreCase = true) ||
                    trimmedValue.equals("TRUE", ignoreCase = true) ||
                    trimmedValue.equals("FALSE", ignoreCase = true)
                ) {
                    trimmedValue
                } else {
                    "'${trimmedValue.replace("'", "''")}'"
                }
            }
        }

        return "$colRef ${operator.symbol} $formattedValue"
    }
}

/**
 * Sort direction for ORDER BY clause.
 */
enum class SortDirection {
    ASC, DESC
}

/**
 * Sort specification.
 */
data class QuerySort(
    val tableAlias: String,
    val columnName: String,
    val direction: SortDirection = SortDirection.ASC
)

/**
 * Full state model of a visual query.
 */
data class VisualQueryModel(
    var primaryTable: QueryTable? = null,
    val joins: MutableList<QueryJoin> = mutableListOf(),
    val columns: MutableList<QueryColumn> = mutableListOf(),
    val filters: MutableList<QueryFilter> = mutableListOf(),
    val customGroupBy: MutableList<String> = mutableListOf(),
    val sorts: MutableList<QuerySort> = mutableListOf(),
    var isDistinct: Boolean = false,
    var limit: Int = 100,
    var offset: Int = 0
) {
    fun hasAggregations(): Boolean = columns.any { it.isIncluded && it.aggregate != AggregateFunction.NONE }

    fun getAutoGroupByColumns(): List<String> {
        if (!hasAggregations()) return emptyList()
        // Standard SQL ANSI rule: Any column in SELECT that is not aggregated must be in GROUP BY
        return columns.filter { it.isIncluded && it.aggregate == AggregateFunction.NONE }
            .map { if (it.tableAlias.isNotBlank()) "${it.tableAlias}.${it.columnName}" else it.columnName }
            .distinct()
    }
}

/**
 * Generator engine producing clean, dialect-compliant SQL and reproducible Python scripts
 * from [VisualQueryModel].
 */
object VisualQueryGenerator {

    fun generateSql(model: VisualQueryModel, dialect: DatabaseDialect = DatabaseDialect.SQLITE): String {
        val primary = model.primaryTable ?: return "-- Please select a table to begin query construction"

        val sb = StringBuilder()

        // 1. SELECT clause
        sb.append("SELECT")
        if (model.isDistinct) {
            sb.append(" DISTINCT")
        }

        val includedCols = model.columns.filter { it.isIncluded }
        if (includedCols.isEmpty()) {
            sb.append("\n    *")
        } else {
            sb.append("\n")
            includedCols.forEachIndexed { index, col ->
                sb.append("    ").append(col.toSqlExpression(dialect))
                if (index < includedCols.size - 1) {
                    sb.append(",")
                }
                sb.append("\n")
            }
        }

        // 2. FROM clause
        sb.append("FROM ")
        sb.append(primary.qualifiedName)
        if (primary.alias.isNotBlank() && primary.alias != primary.tableName) {
            sb.append(" ").append(primary.alias)
        }
        sb.append("\n")

        // 3. JOIN clauses
        for (join in model.joins) {
            sb.append(join.type.keyword).append(" ").append(join.table.qualifiedName)
            if (join.table.alias.isNotBlank() && join.table.alias != join.table.tableName) {
                sb.append(" ").append(join.table.alias)
            }

            if (join.type != JoinType.CROSS && join.condition != null) {
                val cond = join.condition
                val left = if (cond.leftTableAlias.isNotBlank()) "${cond.leftTableAlias}.${cond.leftColumn}" else cond.leftColumn
                val right = if (cond.rightTableAlias.isNotBlank()) "${cond.rightTableAlias}.${cond.rightColumn}" else cond.rightColumn
                sb.append(" ON ").append(left).append(" ").append(cond.operator).append(" ").append(right)
            }
            sb.append("\n")
        }

        // 4. WHERE clause (non-having filters)
        val whereFilters = model.filters.filter { !it.isHaving }
        if (whereFilters.isNotEmpty()) {
            sb.append("WHERE\n")
            whereFilters.forEachIndexed { index, filter ->
                if (index == 0) {
                    sb.append("    ").append(filter.toSqlExpression(dialect)).append("\n")
                } else {
                    sb.append("    ").append(filter.connector.name).append(" ")
                        .append(filter.toSqlExpression(dialect)).append("\n")
                }
            }
        }

        // 5. GROUP BY clause
        val groupByCols = (model.getAutoGroupByColumns() + model.customGroupBy).distinct()
        if (groupByCols.isNotEmpty()) {
            sb.append("GROUP BY ")
            sb.append(groupByCols.joinToString(", "))
            sb.append("\n")
        }

        // 6. HAVING clause
        val havingFilters = model.filters.filter { it.isHaving }
        if (havingFilters.isNotEmpty()) {
            sb.append("HAVING\n")
            havingFilters.forEachIndexed { index, filter ->
                if (index == 0) {
                    sb.append("    ").append(filter.toSqlExpression(dialect)).append("\n")
                } else {
                    sb.append("    ").append(filter.connector.name).append(" ")
                        .append(filter.toSqlExpression(dialect)).append("\n")
                }
            }
        }

        // 7. ORDER BY clause
        if (model.sorts.isNotEmpty()) {
            sb.append("ORDER BY ")
            val sortClauses = model.sorts.map { sort ->
                val colRef = if (sort.tableAlias.isNotBlank()) "${sort.tableAlias}.${sort.columnName}" else sort.columnName
                "$colRef ${sort.direction.name}"
            }
            sb.append(sortClauses.joinToString(", "))
            sb.append("\n")
        }

        // 8. LIMIT and OFFSET pagination
        if (model.limit > 0) {
            when (dialect) {
                DatabaseDialect.ORACLE_PLSQL -> {
                    if (model.offset > 0) {
                        sb.append("OFFSET ${model.offset} ROWS FETCH NEXT ${model.limit} ROWS ONLY;\n")
                    } else {
                        sb.append("FETCH FIRST ${model.limit} ROWS ONLY;\n")
                    }
                }
                DatabaseDialect.CASSANDRA -> {
                    sb.append("LIMIT ${model.limit};\n")
                }
                else -> {
                    sb.append("LIMIT ${model.limit}")
                    if (model.offset > 0) {
                        sb.append(" OFFSET ${model.offset}")
                    }
                    sb.append(";\n")
                }
            }
        } else {
            sb.append(";\n")
        }

        return sb.toString().trimEnd()
    }

    /**
     * Generates reproducible Python / Pandas pipeline code performing equivalent operations.
     */
    fun generatePandasCode(model: VisualQueryModel): String {
        val primary = model.primaryTable ?: return "# Select a table to generate pandas code"
        val sb = StringBuilder()
        sb.append("import pandas as pd\n\n")
        sb.append("# 1. Load primary table\n")
        sb.append("df = pd.read_sql(\"SELECT * FROM ${primary.tableName}\", con=engine)\n")

        for (join in model.joins) {
            val cond = join.condition
            val how = when (join.type) {
                JoinType.INNER -> "inner"
                JoinType.LEFT -> "left"
                JoinType.RIGHT -> "right"
                JoinType.FULL_OUTER -> "outer"
                JoinType.CROSS -> "cross"
            }
            sb.append("\n# Join ${join.table.tableName} (${how})\n")
            sb.append("df_${join.table.alias} = pd.read_sql(\"SELECT * FROM ${join.table.tableName}\", con=engine)\n")
            if (cond != null && join.type != JoinType.CROSS) {
                sb.append("df = df.merge(\n")
                sb.append("    df_${join.table.alias},\n")
                sb.append("    how='${how}',\n")
                sb.append("    left_on='${cond.leftColumn}',\n")
                sb.append("    right_on='${cond.rightColumn}'\n")
                sb.append(")\n")
            } else {
                sb.append("df = df.merge(df_${join.table.alias}, how='${how}')\n")
            }
        }

        // Filters
        val whereFilters = model.filters.filter { !it.isHaving }
        if (whereFilters.isNotEmpty()) {
            sb.append("\n# Apply filters\n")
            val queryParts = whereFilters.map { f ->
                val col = f.columnName
                val op = when (f.operator) {
                    ComparisonOperator.EQUALS -> "=="
                    ComparisonOperator.NOT_EQUALS -> "!="
                    ComparisonOperator.GREATER -> ">"
                    ComparisonOperator.GREATER_EQUAL -> ">="
                    ComparisonOperator.LESS -> "<"
                    ComparisonOperator.LESS_EQUAL -> "<="
                    else -> "=="
                }
                "$col $op ${f.value}"
            }
            sb.append("df = df.query(\"${queryParts.joinToString(" and ")}\")\n")
        }

        // Group by & Aggregations
        if (model.hasAggregations()) {
            sb.append("\n# Grouping and Aggregations\n")
            val groupCols = model.getAutoGroupByColumns().map { it.substringAfter(".") }
            val aggCols = model.columns.filter { it.isIncluded && it.aggregate != AggregateFunction.NONE }

            val aggMap = aggCols.joinToString(", ") { col ->
                val func = when (col.aggregate) {
                    AggregateFunction.COUNT, AggregateFunction.COUNT_DISTINCT -> "count"
                    AggregateFunction.SUM -> "sum"
                    AggregateFunction.AVG -> "mean"
                    AggregateFunction.MIN -> "min"
                    AggregateFunction.MAX -> "max"
                    else -> "first"
                }
                "'${col.columnName}': '$func'"
            }
            sb.append("df = df.groupby([${groupCols.joinToString(", ") { "'$it'" }}]).agg({$aggMap}).reset_index()\n")
        }

        // Sorts
        if (model.sorts.isNotEmpty()) {
            val sortCols = model.sorts.map { "'${it.columnName}'" }
            val ascending = model.sorts.map { it.direction == SortDirection.ASC }
            sb.append("\n# Sorting\n")
            sb.append("df = df.sort_values(by=[${sortCols.joinToString(", ")}], ascending=[${ascending.joinToString(", ")}])\n")
        }

        // Limit
        if (model.limit > 0) {
            sb.append("\n# Limit rows\n")
            sb.append("df = df.head(${model.limit})\n")
        }

        sb.append("\nprint(df.head())\n")
        return sb.toString()
    }

    /**
     * Generates instant DuckDB embedded Python script.
     */
    fun generateDuckDbPythonCode(model: VisualQueryModel): String {
        val sql = generateSql(model, DatabaseDialect.DUCKDB).replace("\"\"\"", "\\\"\\\"\\\"")
        return """
import duckdb

conn = duckdb.connect()
df = conn.sql("'''
$sql
'''").df()

print(df.info())
print(df.head())
""".trimIndent()
    }
}
