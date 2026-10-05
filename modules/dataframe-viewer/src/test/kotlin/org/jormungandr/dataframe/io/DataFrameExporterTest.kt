package org.jormungandr.dataframe.io

import org.jormungandr.dataframe.model.DataFrame
import org.jormungandr.dataframe.model.DataTypeCategory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DataFrameExporterTest {

    @Test
    fun `test export to markdown, csv, and json`() {
        val df = DataFrame.buildWithStatistics(
            "sample",
            listOf(Pair("id", DataTypeCategory.INTEGER), Pair("city", DataTypeCategory.STRING)),
            listOf(listOf(101L, "Oslo"), listOf(102L, "Tokyo"))
        )

        val csv = DataFrameExporter.toCsv(df)
        assertTrue(csv.contains("id,city"))
        assertTrue(csv.contains("101,Oslo"))

        val md = DataFrameExporter.toMarkdown(df)
        assertTrue(md.contains("| id | city |"))
        assertTrue(md.contains("| 101 | Oslo |"))

        val json = DataFrameExporter.toJson(df)
        assertTrue(json.contains("\"id\": 101"))
        assertTrue(json.contains("\"city\": \"Tokyo\""))
    }
}
