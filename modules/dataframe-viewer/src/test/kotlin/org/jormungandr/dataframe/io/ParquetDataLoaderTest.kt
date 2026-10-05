package org.jormungandr.dataframe.io

import org.jormungandr.dataframe.model.DataFrame
import org.jormungandr.dataframe.model.DataTypeCategory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class ParquetDataLoaderTest {

    private val sampleDf = DataFrame.buildWithStatistics(
        "users",
        listOf(
            Pair("id", DataTypeCategory.INTEGER),
            Pair("name", DataTypeCategory.STRING),
            Pair("score", DataTypeCategory.FLOAT),
            Pair("active", DataTypeCategory.BOOLEAN)
        ),
        listOf(
            listOf(1L, "Alice", 95.5, true),
            listOf(2L, "Bob", 82.0, false),
            listOf(3L, "Charlie", 74.5, true)
        )
    )

    @Test
    fun `test parquet export and parser round trip`() {
        val bytes = DataFrameExporter.toParquetBytes(sampleDf)
        assertTrue(bytes.size >= 8)

        // Verify PAR1 magic at start and end
        assertEquals('P'.code.toByte(), bytes[0])
        assertEquals('A'.code.toByte(), bytes[1])
        assertEquals('R'.code.toByte(), bytes[2])
        assertEquals('1'.code.toByte(), bytes[3])
        assertEquals('P'.code.toByte(), bytes[bytes.size - 4])
        assertEquals('A'.code.toByte(), bytes[bytes.size - 3])
        assertEquals('R'.code.toByte(), bytes[bytes.size - 2])
        assertEquals('1'.code.toByte(), bytes[bytes.size - 1])

        // Parse through ParquetDataLoader
        val loaded = ParquetDataLoader.loadFromBytes("users", bytes)
        assertEquals("users", loaded.name)
        assertEquals(4, loaded.columnCount)
        assertEquals(3, loaded.rowCount)

        val colNames = loaded.columns.map { it.name }
        assertTrue(colNames.contains("id"))
        assertTrue(colNames.contains("name"))
        assertTrue(colNames.contains("score"))
        assertTrue(colNames.contains("active"))

        // Stream load
        val streamLoaded = ParquetDataLoader.loadFromStream("stream_users", ByteArrayInputStream(bytes))
        assertEquals(3, streamLoaded.rowCount)
        assertEquals(4, streamLoaded.columnCount)
    }

    @Test
    fun `test invalid or truncated bytes return empty dataframe gracefully`() {
        val emptyBytes = ByteArray(0)
        val df1 = ParquetDataLoader.loadFromBytes("test", emptyBytes)
        assertTrue(df1.isEmpty)

        val randomBytes = "NOT_A_PARQUET_FILE".toByteArray()
        val df2 = ParquetDataLoader.loadFromBytes("test", randomBytes)
        assertTrue(df2.isEmpty)
    }

    @Test
    fun `test exporter toJsonLines, toExcelXml, and toSqlInsert`() {
        val jsonLines = DataFrameExporter.toJsonLines(sampleDf)
        val lines = jsonLines.trim().lines()
        assertEquals(3, lines.size)
        assertTrue(lines[0].contains("\"name\":\"Alice\""))
        assertTrue(lines[1].contains("\"name\":\"Bob\""))

        val excelXml = DataFrameExporter.toExcelXml(sampleDf)
        assertTrue(excelXml.contains("<?xml version=\"1.0\"?>"))
        assertTrue(excelXml.contains("<Workbook"))
        assertTrue(excelXml.contains("Alice"))
        assertTrue(excelXml.contains("</Workbook>"))

        val sqlInsert = DataFrameExporter.toSqlInsert(sampleDf, "app_users")
        assertTrue(sqlInsert.contains("CREATE TABLE IF NOT EXISTS app_users"))
        assertTrue(sqlInsert.contains("INSERT INTO app_users"))
        assertTrue(sqlInsert.contains("'Alice'"))
    }
}
