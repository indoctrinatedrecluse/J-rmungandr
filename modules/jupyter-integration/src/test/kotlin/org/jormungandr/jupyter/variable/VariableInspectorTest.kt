package org.jormungandr.jupyter.variable

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class VariableInspectorTest {

    @Test
    fun `test formatBytes formats human readable sizes`() {
        assertEquals("0 B", VariableInfo.formatBytes(0))
        assertEquals("512 B", VariableInfo.formatBytes(512))
        assertEquals("1.0 KB", VariableInfo.formatBytes(1024))
        assertEquals("1.5 KB", VariableInfo.formatBytes(1536))
        assertEquals("1.0 MB", VariableInfo.formatBytes(1024 * 1024))
        assertEquals("2.5 GB", VariableInfo.formatBytes((2.5 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun `test variable info model construction and formatting`() {
        val size = 2L * 1024L * 1024L
        val info = VariableInfo(
            name = "df_sales",
            typeName = "DataFrame",
            shape = "(1000, 12)",
            sizeBytes = size,
            sizeFormatted = VariableInfo.formatBytes(size),
            preview = "<DataFrame: 1000 rows x 12 columns>",
            isDataFrame = true
        )

        assertEquals("df_sales", info.name)
        assertEquals("DataFrame", info.typeName)
        assertEquals("(1000, 12)", info.shape)
        assertTrue(info.isDataFrame)
        assertEquals("2.0 MB", info.sizeFormatted)
    }

    @Test
    fun `test json introspection response deserialization`() {
        val jsonPayload = """
            [
                {
                    "name": "df",
                    "type": "DataFrame",
                    "shape": "(100, 5)",
                    "size": 40960,
                    "preview": "   col1  col2\n0     1     2",
                    "is_df": true
                },
                {
                    "name": "counter",
                    "type": "int",
                    "shape": "-",
                    "size": 28,
                    "preview": "42",
                    "is_df": false
                }
            ]
        """.trimIndent()

        val gson = Gson()
        val listType = object : TypeToken<List<Map<String, Any>>>() {}.type
        val rawList: List<Map<String, Any>> = gson.fromJson(jsonPayload, listType)

        assertEquals(2, rawList.size)

        val vars = rawList.map { item ->
            val name = item["name"]?.toString() ?: ""
            val typeName = item["type"]?.toString() ?: ""
            val shape = item["shape"]?.toString() ?: "-"
            val sizeBytes = (item["size"] as? Number)?.toLong() ?: 0L
            val preview = item["preview"]?.toString() ?: ""
            val isDf = item["is_df"] as? Boolean ?: false
            VariableInfo(
                name = name,
                typeName = typeName,
                shape = shape,
                sizeBytes = sizeBytes,
                sizeFormatted = VariableInfo.formatBytes(sizeBytes),
                preview = preview,
                isDataFrame = isDf
            )
        }

        assertEquals("df", vars[0].name)
        assertEquals("DataFrame", vars[0].typeName)
        assertEquals("(100, 5)", vars[0].shape)
        assertEquals(40960L, vars[0].sizeBytes)
        assertEquals("40.0 KB", vars[0].sizeFormatted)
        assertTrue(vars[0].isDataFrame)

        assertEquals("counter", vars[1].name)
        assertEquals("int", vars[1].typeName)
        assertFalse(vars[1].isDataFrame)
        assertEquals("28 B", vars[1].sizeFormatted)
    }
}
