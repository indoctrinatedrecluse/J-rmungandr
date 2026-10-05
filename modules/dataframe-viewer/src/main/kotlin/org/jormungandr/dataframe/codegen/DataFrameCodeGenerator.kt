package org.jormungandr.dataframe.codegen

import org.jormungandr.dataframe.model.DataFrame
import org.jormungandr.dataframe.model.DataTypeCategory

object DataFrameCodeGenerator {

    fun toPandasCode(df: DataFrame, maxRows: Int = 100): String {
        val sb = StringBuilder()
        sb.append("import pandas as pd\n\n")
        sb.append("data = {\n")

        val displayRows = if (df.rowCount > maxRows) df.rows.take(maxRows) else df.rows

        for ((idx, col) in df.columns.withIndex()) {
            val values = displayRows.map { row ->
                val v = row.getOrNull(idx)
                when (v) {
                    null -> "None"
                    is String -> "\"${v.replace("\"", "\\\"")}\""
                    is Boolean -> if (v) "True" else "False"
                    else -> v.toString()
                }
            }
            sb.append("    \"${col.name}\": [${values.joinToString(", ")}],\n")
        }
        sb.append("}\n\n")
        sb.append("df = pd.DataFrame(data)\n")
        return sb.toString()
    }

    fun toPolarsCode(df: DataFrame, maxRows: Int = 100): String {
        val sb = StringBuilder()
        sb.append("import polars as pl\n\n")
        sb.append("df = pl.DataFrame({\n")

        val displayRows = if (df.rowCount > maxRows) df.rows.take(maxRows) else df.rows

        for ((idx, col) in df.columns.withIndex()) {
            val values = displayRows.map { row ->
                val v = row.getOrNull(idx)
                when (v) {
                    null -> "None"
                    is String -> "\"${v.replace("\"", "\\\"")}\""
                    is Boolean -> if (v) "True" else "False"
                    else -> v.toString()
                }
            }
            sb.append("    \"${col.name}\": [${values.joinToString(", ")}],\n")
        }
        sb.append("})\n")
        return sb.toString()
    }

    fun toSqlDdlAndInserts(df: DataFrame, tableName: String = df.name.ifBlank { "dataset" }, maxRows: Int = 100): String {
        val sb = StringBuilder()
        val cleanName = tableName.replace(Regex("[^a-zA-Z0-9_]"), "_").lowercase()

        sb.append("CREATE TABLE IF NOT EXISTS $cleanName (\n")
        val colDefs = df.columns.map { col ->
            val sqlType = when (col.category) {
                DataTypeCategory.INTEGER -> "BIGINT"
                DataTypeCategory.FLOAT -> "DOUBLE PRECISION"
                DataTypeCategory.BOOLEAN -> "BOOLEAN"
                DataTypeCategory.DATETIME -> "TIMESTAMP"
                DataTypeCategory.BINARY -> "BYTEA"
                else -> "TEXT"
            }
            "    ${col.name.replace(" ", "_")} $sqlType"
        }
        sb.append(colDefs.joinToString(",\n"))
        sb.append("\n);\n\n")

        val displayRows = if (df.rowCount > maxRows) df.rows.take(maxRows) else df.rows
        if (displayRows.isNotEmpty()) {
            val colNames = df.columns.joinToString(", ") { it.name.replace(" ", "_") }
            for (row in displayRows) {
                val values = row.map { v ->
                    when (v) {
                        null -> "NULL"
                        is Number -> v.toString()
                        is Boolean -> if (v) "TRUE" else "FALSE"
                        else -> "'${v.toString().replace("'", "''")}'"
                    }
                }
                sb.append("INSERT INTO $cleanName ($colNames) VALUES (${values.joinToString(", ")});\n")
            }
        }

        return sb.toString()
    }
}
