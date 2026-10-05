package org.jormungandr.dataframe.sql

import org.jormungandr.dataframe.model.ColumnMetadata
import org.jormungandr.dataframe.model.DataFrame
import org.jormungandr.dataframe.model.DataTypeCategory
import kotlin.system.measureTimeMillis

sealed class SqlExecutionResult {
    data class Success(
        val dataFrame: DataFrame,
        val executionTimeMs: Long,
        val rowCount: Int,
        val originalQuery: String
    ) : SqlExecutionResult()

    data class Error(
        val message: String,
        val originalQuery: String
    ) : SqlExecutionResult()
}

/**
 * Embedded in-memory SQL evaluation engine for Jörmungandr DataFrame Studio.
 * Executes ANSI-style SQL queries directly against [DataFrame] instances in memory.
 */
object DataFrameSqlEngine {

    fun execute(rawSql: String, df: DataFrame): SqlExecutionResult = execute(df, rawSql)

    fun execute(df: DataFrame, rawSql: String): SqlExecutionResult {
        val query = rawSql.trim().trimEnd(';')
        if (query.isBlank()) {
            return SqlExecutionResult.Error("Query is empty", rawSql)
        }

        var resultDf: DataFrame? = null
        var errorMsg: String? = null

        val duration = measureTimeMillis {
            try {
                resultDf = evaluateQuery(df, query)
            } catch (e: Exception) {
                errorMsg = e.message ?: "SQL execution error"
            }
        }

        return if (resultDf != null) {
            SqlExecutionResult.Success(
                dataFrame = resultDf!!,
                executionTimeMs = duration,
                rowCount = resultDf!!.rowCount,
                originalQuery = rawSql
            )
        } else {
            SqlExecutionResult.Error(
                message = errorMsg ?: "Unknown error",
                originalQuery = rawSql
            )
        }
    }

    private fun evaluateQuery(df: DataFrame, sql: String): DataFrame {
        val normalized = sql.replace("\n", " ").replace("\r", " ").trim()
        if (!normalized.startsWith("SELECT", ignoreCase = true)) {
            throw IllegalArgumentException("Only SELECT queries are supported in DataFrame In-Memory SQL Console.")
        }

        // Tokenize clauses
        val selectIdx = 0
        val fromIdx = findKeyword(normalized, "FROM") ?: throw IllegalArgumentException("Missing FROM clause in SELECT query.")
        val whereIdx = findKeyword(normalized, "WHERE")
        val groupByIdx = findKeyword(normalized, "GROUP BY")
        val havingIdx = findKeyword(normalized, "HAVING")
        val orderByIdx = findKeyword(normalized, "ORDER BY")
        val limitIdx = findKeyword(normalized, "LIMIT")

        // 1. SELECT clause
        val selectClause = normalized.substring(6, fromIdx).trim()
        val isDistinct = selectClause.startsWith("DISTINCT ", ignoreCase = true)
        val rawSelectCols = if (isDistinct) selectClause.substring(9).trim() else selectClause

        // Validate FROM table
        val fromEnd = whereIdx ?: groupByIdx ?: havingIdx ?: orderByIdx ?: limitIdx ?: normalized.length
        val tableName = normalized.substring(fromIdx + 4, fromEnd).trim().split(Regex("\\s+"))[0].trim()
        if (tableName.isNotBlank() && !tableName.equals("df", ignoreCase = true) && !tableName.equals(df.name, ignoreCase = true)) {
            throw IllegalArgumentException("Unknown table '$tableName'. Only '${df.name}' or 'df' can be queried.")
        }

        // 2. WHERE clause
        val whereEnd = groupByIdx ?: havingIdx ?: orderByIdx ?: limitIdx ?: normalized.length
        val whereClause = if (whereIdx != null) normalized.substring(whereIdx + 5, whereEnd).trim() else null

        // 3. GROUP BY clause
        val groupByEnd = havingIdx ?: orderByIdx ?: limitIdx ?: normalized.length
        val groupByClause = if (groupByIdx != null) normalized.substring(groupByIdx + 8, groupByEnd).trim() else null

        // 4. HAVING clause
        val havingEnd = orderByIdx ?: limitIdx ?: normalized.length
        val havingClause = if (havingIdx != null) normalized.substring(havingIdx + 6, havingEnd).trim() else null

        // 5. ORDER BY clause
        val orderEnd = limitIdx ?: normalized.length
        val orderByClause = if (orderByIdx != null) normalized.substring(orderByIdx + 8, orderEnd).trim() else null

        // 6. LIMIT clause
        val limitClause = if (limitIdx != null) normalized.substring(limitIdx + 5).trim() else null

        // Step A: Filter rows via WHERE
        var filteredRows = if (whereClause != null && whereClause.isNotBlank()) {
            filterRows(df, whereClause)
        } else {
            df.rows
        }

        // Step B: GROUP BY & Aggregations
        if (groupByClause != null && groupByClause.isNotBlank()) {
            return evaluateGroupBy(df, filteredRows, rawSelectCols, groupByClause, havingClause, orderByClause, limitClause)
        }

        // Step C: Simple / Scalar Aggregations without GROUP BY
        val selectItems = parseSelectItems(rawSelectCols)
        val hasAggregates = selectItems.any { it.isAggregate }
        if (hasAggregates) {
            return evaluateGlobalAggregate(df, filteredRows, selectItems)
        }

        // Step D: Project columns
        val projectedCols = mutableListOf<ColumnMetadata>()
        val colIndices = mutableListOf<Int>()

        if (rawSelectCols.trim() == "*") {
            projectedCols.addAll(df.columns)
            colIndices.addAll(df.columns.indices)
        } else {
            for (item in selectItems) {
                val idx = df.getColumnIndex(item.expression)
                if (idx < 0) {
                    throw IllegalArgumentException("Unknown column '${item.expression}' in SELECT clause.")
                }
                val originalCol = df.columns[idx]
                projectedCols.add(originalCol.copy(name = item.alias ?: originalCol.name))
                colIndices.add(idx)
            }
        }

        var projectedRows = filteredRows.map { row ->
            colIndices.map { idx -> row.getOrNull(idx) }
        }

        // Step E: DISTINCT
        if (isDistinct) {
            projectedRows = projectedRows.distinct()
        }

        // Step F: ORDER BY
        if (orderByClause != null && orderByClause.isNotBlank()) {
            projectedRows = sortRows(projectedCols, projectedRows, orderByClause)
        }

        // Step G: LIMIT
        if (limitClause != null && limitClause.isNotBlank()) {
            val limitParts = limitClause.split(Regex("\\s+OFFSET\\s+|\\s*,\\s*", RegexOption.IGNORE_CASE))
            val limitNum = limitParts[0].trim().toIntOrNull() ?: 100
            val offsetNum = if (limitParts.size > 1) limitParts[1].trim().toIntOrNull() ?: 0 else 0
            projectedRows = projectedRows.drop(offsetNum).take(limitNum)
        }

        return DataFrame(
            name = "${df.name}_query",
            columns = projectedCols,
            rows = projectedRows
        )
    }

    private data class SelectItem(
        val expression: String,
        val alias: String? = null,
        val isAggregate: Boolean = false,
        val aggFunction: String? = null,
        val aggTarget: String? = null
    )

    private fun parseSelectItems(clause: String): List<SelectItem> {
        val items = mutableListOf<SelectItem>()
        val parts = splitCommasTopLevel(clause)
        for (p in parts) {
            val trimmed = p.trim()
            val asParts = trimmed.split(Regex("\\s+AS\\s+", RegexOption.IGNORE_CASE))
            val expr = asParts[0].trim()
            val alias = if (asParts.size > 1) asParts[1].trim() else null

            val aggMatch = Regex("(COUNT|SUM|AVG|MEAN|MIN|MAX)\\s*\\((.*)\\)", RegexOption.IGNORE_CASE).matchEntire(expr)
            if (aggMatch != null) {
                val fn = aggMatch.groupValues[1].uppercase()
                val target = aggMatch.groupValues[2].trim()
                items.add(
                    SelectItem(
                        expression = expr,
                        alias = alias ?: expr,
                        isAggregate = true,
                        aggFunction = fn,
                        aggTarget = target
                    )
                )
            } else {
                items.add(SelectItem(expression = expr, alias = alias))
            }
        }
        return items
    }

    private fun splitCommasTopLevel(str: String): List<String> {
        val results = mutableListOf<String>()
        var depth = 0
        var start = 0
        for (i in str.indices) {
            when (str[i]) {
                '(' -> depth++
                ')' -> depth--
                ',' -> if (depth == 0) {
                    results.add(str.substring(start, i))
                    start = i + 1
                }
            }
        }
        if (start < str.length) {
            results.add(str.substring(start))
        }
        return results
    }

    private fun filterRows(df: DataFrame, whereClause: String): List<List<Any?>> {
        // Simple condition parser handling AND/OR
        val orParts = whereClause.split(Regex("\\s+OR\\s+", RegexOption.IGNORE_CASE))
        return df.rows.filter { row ->
            orParts.any { orBlock ->
                val andParts = orBlock.split(Regex("\\s+AND\\s+", RegexOption.IGNORE_CASE))
                andParts.all { condition -> evaluateCondition(df, row, condition.trim()) }
            }
        }
    }

    private fun substringBeforeIgnoreCase(s: String, delimiter: String): String {
        val idx = s.indexOf(delimiter, ignoreCase = true)
        return if (idx >= 0) s.substring(0, idx) else s
    }

    private fun substringAfterIgnoreCase(s: String, delimiter: String): String {
        val idx = s.indexOf(delimiter, ignoreCase = true)
        return if (idx >= 0) s.substring(idx + delimiter.length) else s
    }

    private fun evaluateCondition(df: DataFrame, row: List<Any?>, condition: String): Boolean {
        // Operators: >=, <=, !=, <>, =, >, <, LIKE, IN, IS NULL, IS NOT NULL
        val cond = condition.trim()

        if (cond.contains(" IS NOT NULL", ignoreCase = true)) {
            val col = substringBeforeIgnoreCase(cond, " IS NOT NULL").trim()
            val idx = df.getColumnIndex(col)
            if (idx < 0) throw IllegalArgumentException("Unknown column '$col' in WHERE clause.")
            return row.getOrNull(idx) != null
        }
        if (cond.contains(" IS NULL", ignoreCase = true)) {
            val col = substringBeforeIgnoreCase(cond, " IS NULL").trim()
            val idx = df.getColumnIndex(col)
            if (idx < 0) throw IllegalArgumentException("Unknown column '$col' in WHERE clause.")
            return row.getOrNull(idx) == null
        }

        if (cond.contains(" IN ", ignoreCase = true) || cond.contains(" IN(", ignoreCase = true)) {
            val delim = if (cond.contains(" IN ", ignoreCase = true)) " IN " else " IN("
            val leftPart = substringBeforeIgnoreCase(cond, delim).trim()
            val rightPart = substringAfterIgnoreCase(cond, delim).trim()
            val colIdx = df.getColumnIndex(leftPart)
            if (colIdx < 0) throw IllegalArgumentException("Unknown column '$leftPart' in WHERE clause.")
            val cellVal = row.getOrNull(colIdx) ?: return false
            val inValues = (if (delim.endsWith("(")) rightPart.removeSuffix(")") else rightPart.removeSurrounding("(", ")"))
                .split(",")
                .map { it.trim().removeSurrounding("'", "'").removeSurrounding("\"", "\"") }
            return inValues.any { it.equals(cellVal.toString(), ignoreCase = true) }
        }

        val op = when {
            cond.contains(">=") -> ">="
            cond.contains("<=") -> "<="
            cond.contains("!=") -> "!="
            cond.contains("<>") -> "<>"
            cond.contains(">") -> ">"
            cond.contains("<") -> "<"
            cond.contains("=") -> "="
            cond.contains(" LIKE ", ignoreCase = true) -> "LIKE"
            else -> return true
        }

        val leftPart = if (op == "LIKE") substringBeforeIgnoreCase(cond, " LIKE ").trim() else cond.substringBefore(op).trim()
        val rightPart = (if (op == "LIKE") substringAfterIgnoreCase(cond, " LIKE ") else cond.substringAfter(op)).trim().removeSurrounding("'", "'").removeSurrounding("\"", "\"")

        val colIdx = df.getColumnIndex(leftPart)
        if (colIdx < 0) throw IllegalArgumentException("Unknown column '$leftPart' in WHERE clause.")
        val cellVal = row.getOrNull(colIdx) ?: return false

        val cellStr = cellVal.toString()
        val numCell = cellStr.toDoubleOrNull()
        val numRight = rightPart.toDoubleOrNull()

        return when (op) {
            "=" -> if (numCell != null && numRight != null) numCell == numRight else cellStr.equals(rightPart, ignoreCase = true)
            "!=", "<>" -> if (numCell != null && numRight != null) numCell != numRight else !cellStr.equals(rightPart, ignoreCase = true)
            ">" -> if (numCell != null && numRight != null) numCell > numRight else cellStr > rightPart
            "<" -> if (numCell != null && numRight != null) numCell < numRight else cellStr < rightPart
            ">=" -> if (numCell != null && numRight != null) numCell >= numRight else cellStr >= rightPart
            "<=" -> if (numCell != null && numRight != null) numCell <= numRight else cellStr <= rightPart
            "LIKE" -> {
                val pattern = rightPart.replace("%", ".*").replace("_", ".")
                cellStr.matches(Regex(pattern, RegexOption.IGNORE_CASE))
            }
            else -> true
        }
    }

    private fun evaluateGroupBy(
        df: DataFrame,
        rows: List<List<Any?>>,
        selectClause: String,
        groupByClause: String,
        havingClause: String?,
        orderByClause: String?,
        limitClause: String?
    ): DataFrame {
        val groupCols = groupByClause.split(",").map { it.trim() }
        val groupIndices = groupCols.map { colName ->
            val idx = df.getColumnIndex(colName)
            if (idx < 0) throw IllegalArgumentException("Unknown column '$colName' in GROUP BY.")
            idx
        }

        val groups = rows.groupBy { row ->
            groupIndices.map { idx -> row.getOrNull(idx)?.toString() ?: "NULL" }
        }

        val selectItems = parseSelectItems(selectClause)
        val resultCols = mutableListOf<ColumnMetadata>()

        selectItems.forEach { item ->
            val cat = when {
                item.aggFunction == "COUNT" -> DataTypeCategory.INTEGER
                item.aggFunction in listOf("SUM", "AVG", "MEAN") -> DataTypeCategory.FLOAT
                else -> DataTypeCategory.STRING
            }
            resultCols.add(
                ColumnMetadata(
                    name = item.alias ?: item.expression,
                    typeName = cat.displayName,
                    category = cat
                )
            )
        }

        var resultRows = groups.map { (key, groupRows) ->
            selectItems.mapIndexed { _, item ->
                when {
                    item.isAggregate -> computeAggregate(df, groupRows, item.aggFunction!!, item.aggTarget!!)
                    else -> {
                        val keyIdx = groupCols.indexOfFirst { it.equals(item.expression, ignoreCase = true) }
                        if (keyIdx >= 0) key[keyIdx] else groupRows.firstOrNull()?.getOrNull(df.getColumnIndex(item.expression))
                    }
                }
            }
        }

        if (havingClause != null && havingClause.isNotBlank()) {
            val havingDf = DataFrame("${df.name}_having_tmp", resultCols, resultRows)
            resultRows = filterRows(havingDf, havingClause)
        }

        if (orderByClause != null && orderByClause.isNotBlank()) {
            resultRows = sortRows(resultCols, resultRows, orderByClause)
        }

        if (limitClause != null && limitClause.isNotBlank()) {
            val limitNum = limitClause.trim().toIntOrNull() ?: 100
            resultRows = resultRows.take(limitNum)
        }

        return DataFrame(
            name = "${df.name}_grouped",
            columns = resultCols,
            rows = resultRows
        )
    }

    private fun evaluateGlobalAggregate(
        df: DataFrame,
        rows: List<List<Any?>>,
        items: List<SelectItem>
    ): DataFrame {
        val resultCols = items.map { item ->
            val cat = if (item.aggFunction == "COUNT") DataTypeCategory.INTEGER else DataTypeCategory.FLOAT
            ColumnMetadata(name = item.alias ?: item.expression, typeName = cat.displayName, category = cat)
        }
        val singleRow = items.map { item ->
            if (item.isAggregate) computeAggregate(df, rows, item.aggFunction!!, item.aggTarget!!) else null
        }
        return DataFrame(name = "${df.name}_agg", columns = resultCols, rows = listOf(singleRow))
    }

    private fun computeAggregate(df: DataFrame, rows: List<List<Any?>>, func: String, target: String): Any? {
        if (target == "*" || target.isBlank()) {
            return rows.size.toLong()
        }
        val colIdx = df.getColumnIndex(target)
        if (colIdx < 0) return 0L

        val values = rows.mapNotNull { it.getOrNull(colIdx) }
        val numericVals = values.mapNotNull { it.toString().toDoubleOrNull() }

        return when (func.uppercase()) {
            "COUNT" -> values.size.toLong()
            "SUM" -> numericVals.sum()
            "AVG", "MEAN" -> if (numericVals.isNotEmpty()) numericVals.average() else 0.0
            "MIN" -> numericVals.minOrNull() ?: values.minByOrNull { it.toString() }
            "MAX" -> numericVals.maxOrNull() ?: values.maxByOrNull { it.toString() }
            else -> 0L
        }
    }

    private fun sortRows(cols: List<ColumnMetadata>, rows: List<List<Any?>>, orderByClause: String): List<List<Any?>> {
        val parts = orderByClause.split(",").map { it.trim() }
        var sorted = rows
        for (part in parts.reversed()) {
            val isDesc = part.endsWith("DESC", ignoreCase = true)
            val colName = part.replace("ASC", "", ignoreCase = true).replace("DESC", "", ignoreCase = true).trim()
            val colIdx = cols.indexOfFirst { it.name.equals(colName, ignoreCase = true) }
            if (colIdx >= 0) {
                sorted = sorted.sortedWith(Comparator { r1, r2 ->
                    val v1 = r1.getOrNull(colIdx)
                    val v2 = r2.getOrNull(colIdx)
                    val n1 = v1?.toString()?.toDoubleOrNull()
                    val n2 = v2?.toString()?.toDoubleOrNull()
                    val cmp = if (n1 != null && n2 != null) {
                        n1.compareTo(n2)
                    } else {
                        compareValues(v1?.toString(), v2?.toString())
                    }
                    if (isDesc) -cmp else cmp
                })
            }
        }
        return sorted
    }

    private fun findKeyword(sql: String, keyword: String): Int? {
        val regex = Regex("\\b$keyword\\b", RegexOption.IGNORE_CASE)
        val match = regex.find(sql)
        return match?.range?.first
    }
}
