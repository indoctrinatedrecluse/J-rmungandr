package org.jormungandr.dataframe.codegen

import org.jormungandr.dataframe.model.DataFrame
import org.jormungandr.dataframe.model.DataTypeCategory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DataFrameCodeGeneratorTest {

    private val df = DataFrame.buildWithStatistics(
        "products",
        listOf(
            Pair("id", DataTypeCategory.INTEGER),
            Pair("title", DataTypeCategory.STRING),
            Pair("price", DataTypeCategory.FLOAT)
        ),
        listOf(
            listOf(1L, "Widget", 19.99),
            listOf(2L, "Gizmo", 29.50)
        )
    )

    @Test
    fun `test pandas and polars code generation`() {
        val pandas = DataFrameCodeGenerator.toPandasCode(df)
        assertTrue(pandas.contains("import pandas as pd"))
        assertTrue(pandas.contains("\"title\": [\"Widget\", \"Gizmo\"]"))
        assertTrue(pandas.contains("df = pd.DataFrame(data)"))

        val polars = DataFrameCodeGenerator.toPolarsCode(df)
        assertTrue(polars.contains("import polars as pl"))
        assertTrue(polars.contains("df = pl.DataFrame({"))
    }

    @Test
    fun `test sql ddl and insert generation`() {
        val sql = DataFrameCodeGenerator.toSqlDdlAndInserts(df)
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS products"))
        assertTrue(sql.contains("INSERT INTO products"))
        assertTrue(sql.contains("'Widget'"))
    }
}
