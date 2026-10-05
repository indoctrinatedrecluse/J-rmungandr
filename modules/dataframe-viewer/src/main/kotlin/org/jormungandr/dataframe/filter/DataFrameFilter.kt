package org.jormungandr.dataframe.filter

import org.jormungandr.dataframe.model.DataFrame
import org.jormungandr.dataframe.model.DataTypeCategory

enum class FilterOperator(val symbol: String, val requiresValue: Boolean) {
    EQUALS("=", true),
    NOT_EQUALS("≠", true),
    GREATER_THAN(">", true),
    LESS_THAN("<", true),
    GREATER_OR_EQUAL("≥", true),
    LESS_OR_EQUAL("≤", true),
    CONTAINS("contains", true),
    NOT_CONTAINS("not contains", true),
    STARTS_WITH("starts with", true),
    ENDS_WITH("ends with", true),
    IS_NULL("is null", false),
    IS_NOT_NULL("is not null", false),
    REGEX("matches regex", true)
}

enum class Conjunction {
    AND, OR
}

data class FilterCondition(
    val columnName: String,
    val operator: FilterOperator,
    val value: String = ""
) {
    fun evaluate(cell: Any?, category: DataTypeCategory): Boolean {
        if (operator == FilterOperator.IS_NULL) return cell == null
        if (operator == FilterOperator.IS_NOT_NULL) return cell != null
        if (cell == null) return false

        val cellStr = cell.toString()
        val targetVal = value.trim()

        if (category == DataTypeCategory.INTEGER || category == DataTypeCategory.FLOAT) {
            val cellNum = when (cell) {
                is Number -> cell.toDouble()
                else -> cellStr.toDoubleOrNull()
            }
            val targetNum = targetVal.toDoubleOrNull()

            if (cellNum != null && targetNum != null) {
                return when (operator) {
                    FilterOperator.EQUALS -> cellNum == targetNum
                    FilterOperator.NOT_EQUALS -> cellNum != targetNum
                    FilterOperator.GREATER_THAN -> cellNum > targetNum
                    FilterOperator.LESS_THAN -> cellNum < targetNum
                    FilterOperator.GREATER_OR_EQUAL -> cellNum >= targetNum
                    FilterOperator.LESS_OR_EQUAL -> cellNum <= targetNum
                    FilterOperator.CONTAINS -> cellStr.contains(targetVal, ignoreCase = true)
                    FilterOperator.NOT_CONTAINS -> !cellStr.contains(targetVal, ignoreCase = true)
                    FilterOperator.STARTS_WITH -> cellStr.startsWith(targetVal, ignoreCase = true)
                    FilterOperator.ENDS_WITH -> cellStr.endsWith(targetVal, ignoreCase = true)
                    FilterOperator.REGEX -> runCatching { Regex(targetVal).containsMatchIn(cellStr) }.getOrDefault(false)
                    else -> false
                }
            }
        }

        return when (operator) {
            FilterOperator.EQUALS -> cellStr.equals(targetVal, ignoreCase = true)
            FilterOperator.NOT_EQUALS -> !cellStr.equals(targetVal, ignoreCase = true)
            FilterOperator.CONTAINS -> cellStr.contains(targetVal, ignoreCase = true)
            FilterOperator.NOT_CONTAINS -> !cellStr.contains(targetVal, ignoreCase = true)
            FilterOperator.STARTS_WITH -> cellStr.startsWith(targetVal, ignoreCase = true)
            FilterOperator.ENDS_WITH -> cellStr.endsWith(targetVal, ignoreCase = true)
            FilterOperator.GREATER_THAN -> cellStr.compareTo(targetVal, ignoreCase = true) > 0
            FilterOperator.LESS_THAN -> cellStr.compareTo(targetVal, ignoreCase = true) < 0
            FilterOperator.GREATER_OR_EQUAL -> cellStr.compareTo(targetVal, ignoreCase = true) >= 0
            FilterOperator.LESS_OR_EQUAL -> cellStr.compareTo(targetVal, ignoreCase = true) <= 0
            FilterOperator.REGEX -> runCatching { Regex(targetVal, RegexOption.IGNORE_CASE).containsMatchIn(cellStr) }.getOrDefault(false)
            else -> false
        }
    }
}

data class CompoundFilter(
    val conditions: List<FilterCondition>,
    val conjunction: Conjunction = Conjunction.AND
) {
    fun applyTo(df: DataFrame): DataFrame {
        if (conditions.isEmpty()) return df

        val colIndices = conditions.map { cond ->
            val idx = df.getColumnIndex(cond.columnName)
            val cat = if (idx >= 0) df.columns[idx].category else DataTypeCategory.STRING
            Triple(idx, cond, cat)
        }

        val filteredRows = df.rows.filter { row ->
            if (conjunction == Conjunction.AND) {
                colIndices.all { (idx, cond, cat) ->
                    if (idx < 0) true else cond.evaluate(row.getOrNull(idx), cat)
                }
            } else {
                colIndices.any { (idx, cond, cat) ->
                    if (idx < 0) false else cond.evaluate(row.getOrNull(idx), cat)
                }
            }
        }

        return df.copy(rows = filteredRows)
    }
}
