package org.jormungandr.dataframe.io

import org.jormungandr.dataframe.model.DataTypeCategory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CsvDataLoaderTest {

    @Test
    fun `test comma-separated csv parsing and type inference`() {
        val csv = """
            id,name,age,salary,is_active
            1,Alice,29,75000.50,true
            2,Bob,34,92000.00,false
            3,Charlie,22,51000.25,true
        """.trimIndent()

        val df = CsvDataLoader.loadFromString("employees.csv", csv)

        assertEquals(3, df.rowCount)
        assertEquals(5, df.columnCount)

        assertEquals("id", df.columns[0].name)
        assertEquals(DataTypeCategory.INTEGER, df.columns[0].category)

        assertEquals("name", df.columns[1].name)
        assertEquals(DataTypeCategory.STRING, df.columns[1].category)

        assertEquals("salary", df.columns[3].name)
        assertEquals(DataTypeCategory.FLOAT, df.columns[3].category)

        assertEquals("is_active", df.columns[4].name)
        assertEquals(DataTypeCategory.BOOLEAN, df.columns[4].category)
    }

    @Test
    fun `test tab-separated detection and quoted fields with commas`() {
        val tsv = "product\tprice\tdescription\n" +
            "Laptop\t1299.99\t\"Fast, lightweight\"\n" +
            "Keyboard\t89.50\t\"Mechanical, RGB\"\n"

        val df = CsvDataLoader.loadFromString("products.tsv", tsv)

        assertEquals(2, df.rowCount)
        assertEquals(3, df.columnCount)
        assertEquals("Laptop", df.rows[0][0])
        assertEquals("Fast, lightweight", df.rows[0][2])
    }

    @Test
    fun `test missing and null value handling`() {
        val csv = """
            name,score
            Alice,95
            Bob,NA
            Charlie,null
            David,88
        """.trimIndent()

        val df = CsvDataLoader.loadFromString("scores.csv", csv)
        assertEquals(4, df.rowCount)
        val scoreCol = df.columns[1]
        assertEquals(2L, scoreCol.nullCount)
        assertEquals(50.0, scoreCol.nullPercentage)
    }
}
